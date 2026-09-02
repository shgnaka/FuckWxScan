package cn.liyuyu.fuckwxscan.screenshot

import android.content.ContentObserver
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Observes the system image collection after the accessibility service is
 * connected. The service owns the scope and therefore also owns the lifetime
 * of this observer.
 */
class ScreenshotMediaObserver(
    context: Context,
    private val scope: CoroutineScope,
    private val onScreenshotReady: (ScreenshotMediaMetadata, Bitmap) -> Unit,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {
    private val contentResolver = context.contentResolver
    private val reader = ScreenshotMediaStoreReader(contentResolver)
    private val handler = Handler(Looper.getMainLooper())
    private val jobs = mutableMapOf<String, Job>()
    private var coordinator: ScreenshotObservationCoordinator? = null
    private var observing = false

    private val contentObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            if (observing) {
                enqueue(uri)
            }
        }
    }

    fun start() {
        if (observing) {
            return
        }
        coordinator = ScreenshotObservationCoordinator(
            monitorStartedAtMs = nowMs(),
        )
        observing = true
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            contentObserver,
        )
    }

    fun stop() {
        if (!observing) {
            return
        }
        observing = false
        contentResolver.unregisterContentObserver(contentObserver)
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        coordinator = null
    }

    private fun enqueue(changedUri: Uri?) {
        val targetUri = changedUri?.takeIf(reader::isItemUri)
        val jobKey = targetUri?.toString() ?: COLLECTION_KEY
        jobs[jobKey]?.cancel()

        val job = scope.launch {
            try {
                observeUntilReady(targetUri)
            } finally {
                if (jobs[jobKey] === coroutineContext[Job]) {
                    jobs.remove(jobKey)
                }
            }
        }
        jobs[jobKey] = job
    }

    private suspend fun observeUntilReady(targetUri: Uri?) {
        val localCoordinator = coordinator ?: return
        val firstObservedAtMs = nowMs()
        var attempt = 0

        while (scope.isActive) {
            val media = withContext(Dispatchers.IO) {
                if (targetUri == null) {
                    reader.readLatest()
                } else {
                    reader.read(targetUri)
                }
            }

            if (media == null) {
                if (!ScreenshotReadinessPolicy.shouldRetry(nowMs() - firstObservedAtMs)) {
                    return
                }
                delay(ScreenshotReadinessPolicy.retryDelayMs(attempt))
                attempt += 1
                continue
            }

            when (
                localCoordinator.onMediaChanged(
                    media = media,
                    nowMs = nowMs(),
                    attempt = attempt,
                )
            ) {
                ScreenshotObservationAction.IGNORE -> return
                ScreenshotObservationAction.RETRY -> {
                    delay(ScreenshotReadinessPolicy.retryDelayMs(attempt))
                    attempt += 1
                }

                ScreenshotObservationAction.PROCESS -> {
                    val bitmap = withContext(Dispatchers.IO) {
                        reader.loadBitmap(media.uri)
                    } ?: return
                    withContext(Dispatchers.Main.immediate) {
                        if (observing) {
                            onScreenshotReady(media, bitmap)
                        } else {
                            bitmap.recycle()
                        }
                    }
                    return
                }
            }
        }
    }

    private companion object {
        const val COLLECTION_KEY = "collection"
    }
}

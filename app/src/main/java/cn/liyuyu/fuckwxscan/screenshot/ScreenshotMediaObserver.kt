package cn.liyuyu.fuckwxscan.screenshot

import android.database.ContentObserver
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
            val uriDescription = uri?.toString() ?: "<collection>"
            ScreenshotDiagnosticLog.debug(
                "MediaStore onChange selfChange=$selfChange " +
                    "uri=$uriDescription observing=$observing",
            )
            if (observing) {
                enqueue(uri)
            }
        }
    }

    fun start() {
        if (observing) {
            ScreenshotDiagnosticLog.debug("start ignored: observer already active")
            return
        }
        val startedAtMs = nowMs()
        coordinator = ScreenshotObservationCoordinator(
            monitorStartedAtMs = startedAtMs,
        )
        observing = true
        try {
            contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                contentObserver,
            )
            ScreenshotDiagnosticLog.info(
                "MediaStore observer registered uri=${MediaStore.Images.Media.EXTERNAL_CONTENT_URI} " +
                    "monitorStartedAtMs=$startedAtMs",
            )
        } catch (e: Exception) {
            observing = false
            coordinator = null
            ScreenshotDiagnosticLog.error("MediaStore observer registration failed", e)
        }
    }

    fun stop() {
        if (!observing) {
            ScreenshotDiagnosticLog.debug("stop ignored: observer already inactive")
            return
        }
        observing = false
        contentResolver.unregisterContentObserver(contentObserver)
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        coordinator = null
        ScreenshotDiagnosticLog.info("MediaStore observer stopped")
    }

    private fun enqueue(changedUri: Uri?) {
        val targetUri = changedUri?.takeIf(reader::isItemUri)
        val jobKey = targetUri?.toString() ?: COLLECTION_KEY
        val previousJob = jobs[jobKey]
        previousJob?.cancel()
        val rawUriDescription = changedUri?.toString() ?: "<null>"
        val targetUriDescription = targetUri?.toString() ?: "<latest>"
        ScreenshotDiagnosticLog.debug(
            "MediaStore change enqueued rawUri=$rawUriDescription " +
                "targetUri=$targetUriDescription jobKey=$jobKey " +
                "replaced=${previousJob != null}",
        )

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
        val localCoordinator = coordinator ?: run {
            ScreenshotDiagnosticLog.warn("observation skipped: coordinator is not initialized")
            return
        }
        val firstObservedAtMs = nowMs()
        var attempt = 0
        val targetUriDescription = targetUri?.toString() ?: "<latest>"
        ScreenshotDiagnosticLog.debug(
            "observation started targetUri=$targetUriDescription",
        )

        while (scope.isActive) {
            val media = withContext(Dispatchers.IO) {
                if (targetUri == null) {
                    reader.readLatest()
                } else {
                    reader.read(targetUri)
                }
            }

            if (media == null) {
                val elapsedMs = nowMs() - firstObservedAtMs
                val shouldRetry = ScreenshotReadinessPolicy.shouldRetry(elapsedMs)
                ScreenshotDiagnosticLog.warn(
                    "MediaStore query returned null targetUri=$targetUriDescription " +
                        "attempt=$attempt elapsedMs=$elapsedMs retry=$shouldRetry",
                )
                if (!shouldRetry) {
                    return
                }
                val delayMs = ScreenshotReadinessPolicy.retryDelayMs(attempt)
                ScreenshotDiagnosticLog.debug(
                    "retrying MediaStore query delayMs=$delayMs",
                )
                delay(delayMs)
                attempt += 1
                continue
            }

            val action = localCoordinator.onMediaChanged(
                media = media,
                nowMs = nowMs(),
                attempt = attempt,
            )
            val ageMs = nowMs() - media.dateAddedMs
            ScreenshotDiagnosticLog.info(
                "MediaStore row id=${media.id} uri=${media.uri} " +
                    "name=${media.displayName} path=${media.relativePath} " +
                    "mime=${media.mimeType} pending=${media.isPending} " +
                    "ageMs=$ageMs attempt=$attempt action=$action",
            )

            when (action) {
                ScreenshotObservationAction.IGNORE -> {
                    ScreenshotDiagnosticLog.debug(
                        "MediaStore row ignored id=${media.id}",
                    )
                    return
                }

                ScreenshotObservationAction.RETRY -> {
                    val delayMs = ScreenshotReadinessPolicy.retryDelayMs(attempt)
                    ScreenshotDiagnosticLog.debug(
                        "MediaStore row pending; retrying id=${media.id} " +
                            "delayMs=$delayMs",
                    )
                    delay(delayMs)
                    attempt += 1
                }

                ScreenshotObservationAction.PROCESS -> {
                    val bitmap = withContext(Dispatchers.IO) {
                        reader.loadBitmap(media.uri)
                    } ?: run {
                        ScreenshotDiagnosticLog.error(
                            "bitmap load failed id=${media.id} uri=${media.uri}",
                        )
                        return
                    }
                    ScreenshotDiagnosticLog.info(
                        "bitmap loaded id=${media.id} width=${bitmap.width} " +
                            "height=${bitmap.height}",
                    )
                    withContext(Dispatchers.Main.immediate) {
                        if (observing) {
                            ScreenshotDiagnosticLog.info(
                                "sending screenshot to QR decoder id=${media.id}",
                            )
                            onScreenshotReady(media, bitmap)
                        } else {
                            ScreenshotDiagnosticLog.warn(
                                "observer stopped before QR decode id=${media.id}",
                            )
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

/*
 * SPDX-License-Identifier: Apache-2.0
 * New file added by contributors to shgnaka/FuckWxScan in 2026.
 */
package cn.liyuyu.fuckwxscan.capture

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MediaStoreScreenshotSaver(
    private val context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun save(bitmap: Bitmap): Result<Unit> = runCatching {
        check(!bitmap.isRecycled) { "Cannot save a recycled screenshot" }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveWithMediaStore(bitmap)
        } else {
            saveWithLegacyFile(bitmap)
        }
    }

    fun save(uri: Uri): Result<Unit> = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveWithMediaStore { output -> input.copyTo(output) }
            } else {
                saveWithLegacyFile { output -> input.copyTo(output) }
            }
        } ?: error("Unable to open temporary screenshot")
    }

    private fun saveWithMediaStore(bitmap: Bitmap) {
        saveWithMediaStore { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                "Unable to encode screenshot"
            }
        }
    }

    private fun saveWithMediaStore(copyTo: (java.io.OutputStream) -> Unit) {
        val filename = createFilename(clock())
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, MIME_TYPE)
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/Screenshots",
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Unable to create screenshot MediaStore entry")
        try {
            resolver.openOutputStream(uri)?.use { output ->
                copyTo(output)
            } ?: error("Unable to open screenshot output stream")

            resolver.update(
                uri,
                ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                },
                null,
                null,
            )
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    @Suppress("DEPRECATION")
    private fun saveWithLegacyFile(bitmap: Bitmap) {
        saveWithLegacyFile { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                "Unable to encode screenshot"
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun saveWithLegacyFile(copyTo: (java.io.OutputStream) -> Unit) {
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "Screenshots",
        )
        check(directory.exists() || directory.mkdirs()) {
            "Unable to create screenshot directory"
        }

        val file = File(directory, createFilename(clock()))
        FileOutputStream(file).use { output ->
            copyTo(output)
        }
        MediaScannerConnection.scanFile(
            context,
            arrayOf(file.absolutePath),
            arrayOf(MIME_TYPE),
            null,
        )
    }

    companion object {
        private const val MIME_TYPE = "image/png"
        private const val FILENAME_PATTERN = "'Screenshot_'yyyyMMdd_HHmmss_SSS'.png'"

        internal fun createFilename(timestampMs: Long): String =
            SimpleDateFormat(FILENAME_PATTERN, Locale.US).format(Date(timestampMs))
    }
}

class TemporaryScreenshotStore(
    private val context: Context,
) {
    fun create(bitmap: Bitmap): Result<Uri> = runCatching {
        check(!bitmap.isRecycled) { "Cannot store a recycled screenshot" }
        val directory = context.getExternalFilesDir(null)
            ?: error("External files directory is unavailable")
        check(directory.exists() || directory.mkdirs()) {
            "Unable to create temporary screenshot directory"
        }
        val file = File(directory, TEMPORARY_FILENAME)
        FileOutputStream(file).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                "Unable to encode temporary screenshot"
            }
        }
        FileProvider.getUriForFile(
            context,
            context.packageName + ".provider",
            file,
        )
    }.onFailure { delete() }

    fun delete() {
        val directory = context.getExternalFilesDir(null) ?: return
        File(directory, TEMPORARY_FILENAME).delete()
    }

    companion object {
        private const val TEMPORARY_FILENAME = "pending_screenshot.png"
    }
}

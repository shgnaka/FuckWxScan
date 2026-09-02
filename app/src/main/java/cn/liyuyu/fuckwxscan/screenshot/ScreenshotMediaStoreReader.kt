package cn.liyuyu.fuckwxscan.screenshot

import android.content.ContentResolver
import android.content.ContentUris
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

/**
 * Reads only the metadata and bitmap needed by the screenshot monitor.
 */
class ScreenshotMediaStoreReader(
    private val contentResolver: ContentResolver,
) {
    private val collectionUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    fun read(uri: Uri): ScreenshotMediaMetadata? {
        val queryUri = if (isItemUri(uri)) uri else collectionUri
        return query(queryUri)
    }

    fun readLatest(): ScreenshotMediaMetadata? = query(collectionUri)

    fun loadBitmap(uri: String): Bitmap? =
        try {
            contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
                BitmapFactory.decodeStream(input)
            }
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    fun isItemUri(uri: Uri): Boolean =
        uri.lastPathSegment?.toLongOrNull() != null

    private fun query(queryUri: Uri): ScreenshotMediaMetadata? {
        val projection = mutableListOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_ADDED,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection += MediaStore.Images.Media.RELATIVE_PATH
            projection += MediaStore.Images.Media.IS_PENDING
        }

        return try {
            contentResolver.query(
                queryUri,
                projection.toTypedArray(),
                null,
                null,
                if (isItemUri(queryUri)) {
                    null
                } else {
                    MediaStore.Images.Media.DATE_ADDED + " DESC"
                },
            )?.use { cursor ->
                if (!cursor.moveToFirst()) {
                    return null
                }

                val id = cursor.longValue(MediaStore.Images.Media._ID) ?: return null
                val dateAddedSeconds =
                    cursor.longValue(MediaStore.Images.Media.DATE_ADDED) ?: return null
                val contentUri = if (isItemUri(queryUri)) {
                    queryUri
                } else {
                    ContentUris.withAppendedId(collectionUri, id)
                }
                ScreenshotMediaMetadata(
                    id = id,
                    uri = contentUri.toString(),
                    displayName = cursor.stringValue(MediaStore.Images.Media.DISPLAY_NAME),
                    relativePath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cursor.stringValue(MediaStore.Images.Media.RELATIVE_PATH)
                    } else {
                        null
                    },
                    mimeType = cursor.stringValue(MediaStore.Images.Media.MIME_TYPE),
                    dateAddedMs = dateAddedSeconds * 1_000L,
                    isPending = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cursor.intValue(MediaStore.Images.Media.IS_PENDING) == 1
                    } else {
                        false
                    },
                )
            }
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun Cursor.stringValue(column: String): String? {
        val index = getColumnIndex(column)
        return if (index >= 0 && !isNull(index)) getString(index) else null
    }

    private fun Cursor.longValue(column: String): Long? {
        val index = getColumnIndex(column)
        return if (index >= 0 && !isNull(index)) getLong(index) else null
    }

    private fun Cursor.intValue(column: String): Int? {
        val index = getColumnIndex(column)
        return if (index >= 0 && !isNull(index)) getInt(index) else null
    }
}

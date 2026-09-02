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
        ScreenshotDiagnosticLog.debug("querying MediaStore item uri=$queryUri")
        return query(queryUri)
    }

    fun readLatest(): ScreenshotMediaMetadata? {
        ScreenshotDiagnosticLog.debug("querying MediaStore latest image")
        return query(collectionUri)
    }

    fun loadBitmap(uri: String): Bitmap? {
        return try {
            val input = contentResolver.openInputStream(Uri.parse(uri))
            if (input == null) {
                ScreenshotDiagnosticLog.warn(
                    "openInputStream returned null uri=$uri",
                )
                null
            } else {
                input.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    if (bitmap == null) {
                        ScreenshotDiagnosticLog.warn(
                            "BitmapFactory returned null uri=$uri",
                        )
                    } else {
                        ScreenshotDiagnosticLog.debug(
                            "bitmap decoded uri=$uri width=${bitmap.width} " +
                                "height=${bitmap.height}",
                        )
                    }
                    bitmap
                }
            }
        } catch (e: SecurityException) {
            ScreenshotDiagnosticLog.error("bitmap permission denied uri=$uri", e)
            null
        } catch (e: IllegalArgumentException) {
            ScreenshotDiagnosticLog.error("bitmap URI invalid uri=$uri", e)
            null
        } catch (e: Exception) {
            ScreenshotDiagnosticLog.error("bitmap read failed uri=$uri", e)
            null
        }
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
                    ScreenshotDiagnosticLog.debug(
                        "MediaStore query returned zero rows uri=$queryUri",
                    )
                    return null
                }

                val id = cursor.longValue(MediaStore.Images.Media._ID) ?: run {
                    ScreenshotDiagnosticLog.warn(
                        "MediaStore row missing _ID uri=$queryUri",
                    )
                    return null
                }
                val dateAddedSeconds =
                    cursor.longValue(MediaStore.Images.Media.DATE_ADDED) ?: run {
                        ScreenshotDiagnosticLog.warn(
                            "MediaStore row missing DATE_ADDED id=$id",
                        )
                        return null
                    }
                val contentUri = if (isItemUri(queryUri)) {
                    queryUri
                } else {
                    ContentUris.withAppendedId(collectionUri, id)
                }
                val metadata = ScreenshotMediaMetadata(
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
                ScreenshotDiagnosticLog.debug(
                    "MediaStore metadata id=${metadata.id} name=${metadata.displayName} " +
                        "path=${metadata.relativePath} mime=${metadata.mimeType} " +
                        "dateAddedMs=${metadata.dateAddedMs} pending=${metadata.isPending}",
                )
                metadata
            }
        } catch (e: SecurityException) {
            ScreenshotDiagnosticLog.error(
                "MediaStore query permission denied uri=$queryUri",
                e,
            )
            null
        } catch (e: IllegalArgumentException) {
            ScreenshotDiagnosticLog.error(
                "MediaStore query URI invalid uri=$queryUri",
                e,
            )
            null
        } catch (e: Exception) {
            ScreenshotDiagnosticLog.error(
                "MediaStore query failed uri=$queryUri",
                e,
            )
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

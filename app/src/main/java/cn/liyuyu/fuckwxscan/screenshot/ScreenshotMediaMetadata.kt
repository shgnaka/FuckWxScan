package cn.liyuyu.fuckwxscan.screenshot

/**
 * The small, Android-free portion of a MediaStore image row that the screenshot
 * observation policies need in order to make a decision.
 */
data class ScreenshotMediaMetadata(
    val id: Long,
    val uri: String,
    val displayName: String?,
    val relativePath: String?,
    val mimeType: String?,
    val dateAddedMs: Long,
    val isPending: Boolean = false,
) {
    /**
     * A URI is stable across observer callbacks. The ID fallback keeps the
     * metadata usable for providers that do not expose a URI string.
     */
    val stableKey: String
        get() = uri.takeIf { it.isNotBlank() } ?: "id:$id"
}

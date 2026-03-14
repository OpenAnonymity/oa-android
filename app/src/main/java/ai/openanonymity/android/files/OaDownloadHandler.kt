package ai.openanonymity.android.files

import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import android.webkit.URLUtil
import java.lang.IllegalArgumentException

data class DownloadRequest(
    val url: String,
    val userAgent: String?,
    val contentDisposition: String?,
    val mimeType: String?,
)

class OaDownloadHandler(
    private val context: Context,
    private val openExternalUrl: (String) -> Unit,
) {
    fun enqueue(request: DownloadRequest) {
        val downloadManager = context.getSystemService(DownloadManager::class.java)
            ?: run {
                openExternalUrl(request.url)
                return
            }

        val fileName = URLUtil.guessFileName(
            request.url,
            request.contentDisposition,
            request.mimeType,
        )

        val downloadRequest = DownloadManager.Request(android.net.Uri.parse(request.url)).apply {
            setTitle(fileName)
            setMimeType(request.mimeType)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
            setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            request.userAgent?.takeIf { it.isNotBlank() }?.let { addRequestHeader("User-Agent", it) }
        }

        try {
            downloadManager.enqueue(downloadRequest)
        } catch (_: IllegalArgumentException) {
            openExternalUrl(request.url)
        }
    }
}

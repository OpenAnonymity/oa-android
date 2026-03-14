package ai.openanonymity.android.bridge

import android.app.Activity
import android.content.Intent
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.nio.charset.StandardCharsets

class SaveFilePickerBridge(
    activity: ComponentActivity,
    private val webView: WebView,
) {
    private var pendingRequest: SaveFilePickerRequest? = null

    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val request = pendingRequest ?: return@registerForActivityResult
        pendingRequest = null

        val uri = if (result.resultCode == Activity.RESULT_OK) result.data?.data else null
        if (uri == null) {
            dispatchResult(SaveFilePickerResult(request.token, saved = false))
            return@registerForActivityResult
        }

        val saved = runCatching {
            activity.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(request.body.toByteArray(StandardCharsets.UTF_8))
            } ?: error("Unable to open output stream.")
        }

        dispatchResult(
            if (saved.isSuccess) {
                SaveFilePickerResult(request.token, saved = true)
            } else {
                SaveFilePickerResult(
                    token = request.token,
                    saved = false,
                    error = saved.exceptionOrNull()?.message ?: "Failed to save file.",
                )
            }
        )
    }

    fun attach() {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(
                webView,
                SaveFilePickerPolyfill.JS_INTERFACE_NAME,
                setOf(ai.openanonymity.android.OaAppOrigin.ORIGIN_RULE),
                object : WebViewCompat.WebMessageListener {
                    override fun onPostMessage(
                        view: WebView,
                        message: WebMessageCompat,
                        sourceOrigin: android.net.Uri,
                        isMainFrame: Boolean,
                        replyProxy: JavaScriptReplyProxy,
                    ) {
                        if (!isMainFrame) return
                        handleRequest(message.data ?: return)
                    }
                }
            )
        }

        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(
                webView,
                SaveFilePickerPolyfill.documentStartScript(),
                setOf(ai.openanonymity.android.OaAppOrigin.ORIGIN_RULE),
            )
        }
    }

    fun clearPending() {
        pendingRequest = null
    }

    private fun handleRequest(rawMessage: String) {
        val request = runCatching { SaveFilePickerProtocol.parseRequest(rawMessage) }.getOrNull() ?: return
        if (pendingRequest != null) {
            dispatchResult(
                SaveFilePickerResult(
                    token = request.token,
                    saved = false,
                    error = "Another save request is already in progress.",
                )
            )
            return
        }

        pendingRequest = request
        launcher.launch(
            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = request.mimeType.ifBlank { "application/octet-stream" }
                putExtra(Intent.EXTRA_TITLE, request.suggestedName)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        )
    }

    private fun dispatchResult(result: SaveFilePickerResult) {
        webView.post {
            webView.evaluateJavascript(SaveFilePickerProtocol.toJavascript(result), null)
        }
    }
}

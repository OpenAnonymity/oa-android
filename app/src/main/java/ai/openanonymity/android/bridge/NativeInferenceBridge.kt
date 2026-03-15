package ai.openanonymity.android.bridge

import ai.openanonymity.android.background.InferenceForegroundService
import ai.openanonymity.android.background.NativeInferenceProtocol
import ai.openanonymity.android.background.NativeInferenceRegistry
import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView

class NativeInferenceBridge(
    private val context: Context,
    private val webView: WebView,
) {
    @SuppressLint("JavascriptInterface")
    fun attach() {
        webView.addJavascriptInterface(
            object {
                @JavascriptInterface
                fun startJob(rawMessage: String): String {
                    return runCatching {
                        val request = NativeInferenceProtocol.parseStartRequest(rawMessage)
                        val jobId = NativeInferenceRegistry.startJob(context.applicationContext, request)
                        InferenceForegroundService.sync(context.applicationContext)
                        NativeInferenceProtocol.startResponse(jobId)
                    }.getOrElse { error ->
                        NativeInferenceProtocol.errorResponse(
                            error.message ?: "Failed to start Android native inference job."
                        )
                    }
                }

                @JavascriptInterface
                fun pollEvents(rawMessage: String): String {
                    return runCatching {
                        val request = NativeInferenceProtocol.parsePollRequest(rawMessage)
                        val (events, terminal) = NativeInferenceRegistry.pollEvents(
                            request.jobId,
                            request.afterSequence,
                        )
                        NativeInferenceProtocol.pollResponse(events, terminal)
                    }.getOrElse { error ->
                        NativeInferenceProtocol.errorResponse(
                            error.message ?: "Failed to poll Android native inference job."
                        )
                    }
                }

                @JavascriptInterface
                fun cancelJob(rawMessage: String): String {
                    return runCatching {
                        val request = NativeInferenceProtocol.parseCancelRequest(rawMessage)
                        NativeInferenceRegistry.cancelJob(request.jobId)
                        InferenceForegroundService.sync(context.applicationContext)
                        NativeInferenceProtocol.okResponse()
                    }.getOrElse { error ->
                        NativeInferenceProtocol.errorResponse(
                            error.message ?: "Failed to cancel Android native inference job."
                        )
                    }
                }
            },
            JS_INTERFACE_NAME,
        )
    }

    companion object {
        const val JS_INTERFACE_NAME = "oaAndroidInferenceNative"
    }
}

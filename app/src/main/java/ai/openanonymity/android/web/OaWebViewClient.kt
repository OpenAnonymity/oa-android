package ai.openanonymity.android.web

import ai.openanonymity.android.navigation.ExternalNavigationPolicy
import ai.openanonymity.android.navigation.NavigationTarget
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebResourceErrorCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat

class OaWebViewClient(
    private val assetLoader: WebViewAssetLoader,
    private val navigationPolicy: ExternalNavigationPolicy,
    private val openExternalUrl: (String) -> Unit,
    private val onPageReady: () -> Unit,
    private val onMainFrameError: (String) -> Unit,
) : WebViewClientCompat() {
    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ) = assetLoader.shouldInterceptRequest(request.url)

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val decision = navigationPolicy.decide(request.url?.toString())
        return when (decision.target) {
            NavigationTarget.WEBVIEW -> false
            NavigationTarget.EXTERNAL -> {
                decision.url?.let(openExternalUrl)
                true
            }
            NavigationTarget.IGNORE -> true
        }
    }

    override fun onPageCommitVisible(view: WebView, url: String?) {
        onPageReady()
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceErrorCompat,
    ) {
        if (request.isForMainFrame) {
            onMainFrameError(
                error.description?.toString()?.takeIf { it.isNotBlank() }
                    ?: "Failed to load Open Anonymity."
            )
        }
    }
}

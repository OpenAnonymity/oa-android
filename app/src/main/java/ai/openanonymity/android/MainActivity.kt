package ai.openanonymity.android

import ai.openanonymity.android.bridge.SaveFilePickerBridge
import ai.openanonymity.android.bridge.ThemeSurfaceBridge
import ai.openanonymity.android.bridge.NativeInferenceBridge
import ai.openanonymity.android.files.DownloadRequest
import ai.openanonymity.android.files.OaDownloadHandler
import ai.openanonymity.android.files.OaFileChooserController
import ai.openanonymity.android.navigation.ExternalNavigationPolicy
import ai.openanonymity.android.navigation.NavigationTarget
import ai.openanonymity.android.navigation.OaUrlRouter
import ai.openanonymity.android.web.OaWebChromeClient
import ai.openanonymity.android.web.OaWebViewAssetLoaderFactory
import ai.openanonymity.android.web.OaWebViewClient
import ai.openanonymity.android.web.OaWebViewConfigurator
import android.content.Intent
import android.os.Build
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.widget.Button
import android.widget.TextView
import androidx.activity.addCallback
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding

class MainActivity : AppCompatActivity() {
    private lateinit var rootView: View
    private lateinit var webView: WebView
    private lateinit var loadingView: View
    private lateinit var errorView: View
    private lateinit var errorMessageView: TextView
    private lateinit var retryButton: Button
    private lateinit var windowInsetsController: WindowInsetsControllerCompat

    private val urlRouter = OaUrlRouter()
    private val externalNavigationPolicy = ExternalNavigationPolicy()

    @VisibleForTesting
    var lastRequestedUrl: String? = null
        private set

    private lateinit var fileChooserController: OaFileChooserController
    private lateinit var saveFilePickerBridge: SaveFilePickerBridge
    private lateinit var themeSurfaceBridge: ThemeSurfaceBridge
    private lateinit var nativeInferenceBridge: NativeInferenceBridge
    private lateinit var downloadHandler: OaDownloadHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        setContentView(R.layout.activity_main)
        bindViews()
        configureWindowInsets()

        fileChooserController = OaFileChooserController(this)
        downloadHandler = OaDownloadHandler(this, ::openExternalUrl)
        saveFilePickerBridge = SaveFilePickerBridge(this, webView)
        themeSurfaceBridge = ThemeSurfaceBridge(webView, ::applySurfaceColor)
        nativeInferenceBridge = NativeInferenceBridge(this, webView)

        configureWebView()
        saveFilePickerBridge.attach()
        themeSurfaceBridge.attach()
        nativeInferenceBridge.attach()

        retryButton.setOnClickListener {
            loadIntentUrl(intent, force = true)
        }

        onBackPressedDispatcher.addCallback(this) {
            if (webView.canGoBack()) {
                webView.goBack()
            } else {
                finish()
            }
        }

        val restored = savedInstanceState?.let { webView.restoreState(it) } != null
        if (!restored) {
            loadIntentUrl(intent)
        } else {
            themeSurfaceBridge.ensurePageObserver()
            showLoading(false)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        loadIntentUrl(intent, force = !intent.dataString.isNullOrBlank())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onDestroy() {
        fileChooserController.cancelPending()
        saveFilePickerBridge.clearPending()
        webView.destroy()
        super.onDestroy()
    }

    @VisibleForTesting
    fun webViewForTesting(): WebView = webView

    private fun bindViews() {
        rootView = findViewById(R.id.root_container)
        webView = findViewById(R.id.web_view)
        loadingView = findViewById(R.id.loading_container)
        errorView = findViewById(R.id.error_container)
        errorMessageView = findViewById(R.id.error_message)
        retryButton = findViewById(R.id.retry_button)
    }

    private fun configureWindowInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }

        windowInsetsController = WindowInsetsControllerCompat(window, rootView)
        applySurfaceColor(ContextCompat.getColor(this, R.color.oa_app_background))

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, windowInsets ->
            val systemBars = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            val ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            val padding = OaWindowInsets.resolvePadding(
                systemBars = systemBars,
                ime = ime,
                imeVisible = windowInsets.isVisible(WindowInsetsCompat.Type.ime()),
            )
            view.updatePadding(
                left = padding.left,
                top = padding.top,
                right = padding.right,
                bottom = padding.bottom,
            )
            windowInsets
        }
        ViewCompat.requestApplyInsets(rootView)
    }

    private fun configureWebView() {
        OaWebViewConfigurator.configure(webView)

        val assetLoader = OaWebViewAssetLoaderFactory.create(this)
        webView.webViewClient = OaWebViewClient(
            assetLoader = assetLoader,
            navigationPolicy = externalNavigationPolicy,
            openExternalUrl = ::openExternalUrl,
            onPageReady = {
                saveFilePickerBridge.ensurePageWorldPolyfill()
                themeSurfaceBridge.ensurePageObserver()
                showLoading(false)
            },
            onMainFrameError = ::showError,
        )
        webView.webChromeClient = OaWebChromeClient(fileChooserController)
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            if (url.isNullOrBlank()) return@setDownloadListener
            downloadHandler.enqueue(
                DownloadRequest(
                    url = url,
                    userAgent = userAgent,
                    contentDisposition = contentDisposition,
                    mimeType = mimeType,
                )
            )
        }
    }

    private fun loadIntentUrl(intent: Intent?, force: Boolean = false) {
        if (!force && intent?.dataString.isNullOrBlank() && !webView.url.isNullOrBlank()) {
            showLoading(false)
            return
        }
        val targetUrl = urlRouter.initialUrl(intent?.dataString)
        if (!force && targetUrl == webView.url) return
        lastRequestedUrl = targetUrl
        showLoading(true)
        webView.loadUrl(targetUrl)
    }

    private fun showLoading(loading: Boolean) {
        loadingView.visibility = if (loading) View.VISIBLE else View.GONE
        if (loading) {
            errorView.visibility = View.GONE
        }
    }

    private fun showError(message: String) {
        errorMessageView.text = message
        loadingView.visibility = View.GONE
        errorView.visibility = View.VISIBLE
    }

    private fun applySurfaceColor(backgroundColor: Int) {
        rootView.setBackgroundColor(backgroundColor)
        loadingView.setBackgroundColor(backgroundColor)
        errorView.setBackgroundColor(backgroundColor)
        webView.setBackgroundColor(backgroundColor)

        val useDarkIcons = OaSurfaceStyle.shouldUseDarkSystemBarIcons(backgroundColor)
        windowInsetsController.isAppearanceLightStatusBars = useDarkIcons
        windowInsetsController.isAppearanceLightNavigationBars = useDarkIcons
    }

    private fun openExternalUrl(url: String) {
        val decision = externalNavigationPolicy.decide(url)
        if (decision.target != NavigationTarget.EXTERNAL) return

        runCatching {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                }
            )
        }
    }
}

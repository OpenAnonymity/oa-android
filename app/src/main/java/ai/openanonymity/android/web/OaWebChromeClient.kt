package ai.openanonymity.android.web

import ai.openanonymity.android.files.OaFileChooserController
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView

class OaWebChromeClient(
    private val fileChooserController: OaFileChooserController,
) : WebChromeClient() {
    override fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: FileChooserParams?,
    ): Boolean {
        return fileChooserController.show(filePathCallback, fileChooserParams)
    }
}

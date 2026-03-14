package ai.openanonymity.android.web

import ai.openanonymity.android.OaAppOrigin
import android.content.Context
import androidx.webkit.WebViewAssetLoader

object OaWebViewAssetLoaderFactory {
    fun create(context: Context): WebViewAssetLoader {
        return WebViewAssetLoader.Builder()
            .setDomain(OaAppOrigin.HOST)
            .addPathHandler("/", OaAssetPathHandler(context))
            .build()
    }
}

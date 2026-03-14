package ai.openanonymity.android.web

import android.content.Context
import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import java.io.IOException

class OaAssetPathHandler(
    context: Context,
    private val resolver: OaAssetPathResolver = OaAssetPathResolver(),
) : WebViewAssetLoader.PathHandler {
    private val assetManager = context.assets

    override fun handle(path: String): WebResourceResponse? {
        val resolution = resolver.resolve(path) { assetExists(it) } ?: return null
        val inputStream = assetManager.open(resolution.assetPath)
        return WebResourceResponse(
            resolution.mimeType,
            OaMimeTypes.charsetFor(resolution.mimeType),
            inputStream,
        ).apply {
            responseHeaders = mapOf(
                "Cache-Control" to if (resolution.spaFallback) "no-store" else "public, max-age=31536000, immutable"
            )
        }
    }

    private fun assetExists(assetPath: String): Boolean {
        return try {
            assetManager.open(assetPath).close()
            true
        } catch (_: IOException) {
            false
        }
    }
}

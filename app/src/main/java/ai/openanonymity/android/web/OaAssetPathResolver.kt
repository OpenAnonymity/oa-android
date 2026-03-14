package ai.openanonymity.android.web

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class AssetResolution(
    val assetPath: String,
    val mimeType: String,
    val spaFallback: Boolean,
)

class OaAssetPathResolver(
    private val assetRoot: String = "oa-chat-dist",
) {
    fun resolve(requestPath: String, exists: (String) -> Boolean): AssetResolution? {
        val normalized = normalize(requestPath)
        val candidate = if (normalized.isBlank()) "index.html" else normalized
        val directAsset = prefix(candidate)
        if (exists(directAsset)) {
            return AssetResolution(
                assetPath = directAsset,
                mimeType = OaMimeTypes.forPath(candidate),
                spaFallback = false,
            )
        }

        val directoryIndex = prefix(
            if (candidate.endsWith("/")) "${candidate}index.html" else "$candidate/index.html"
        )
        if (exists(directoryIndex)) {
            return AssetResolution(
                assetPath = directoryIndex,
                mimeType = "text/html",
                spaFallback = false,
            )
        }

        if (OaMimeTypes.looksLikeStaticAsset(candidate)) {
            return null
        }

        return AssetResolution(
            assetPath = prefix("index.html"),
            mimeType = "text/html",
            spaFallback = true,
        )
    }

    private fun prefix(path: String): String = "$assetRoot/$path"

    private fun normalize(path: String): String {
        val trimmed = path.substringBefore('?').substringBefore('#').trim()
        val withoutLeadingSlash = trimmed.removePrefix("/")
        return URLDecoder.decode(withoutLeadingSlash, StandardCharsets.UTF_8)
    }
}

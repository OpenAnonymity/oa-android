package ai.openanonymity.android.web

import java.util.Locale

object OaMimeTypes {
    private val mimeTypes = mapOf(
        "css" to "text/css",
        "gif" to "image/gif",
        "html" to "text/html",
        "ico" to "image/x-icon",
        "jpeg" to "image/jpeg",
        "jpg" to "image/jpeg",
        "js" to "text/javascript",
        "json" to "application/json",
        "mjs" to "text/javascript",
        "png" to "image/png",
        "svg" to "image/svg+xml",
        "txt" to "text/plain",
        "wasm" to "application/wasm",
        "webp" to "image/webp",
        "woff" to "font/woff",
        "woff2" to "font/woff2",
        "xml" to "text/xml",
    )

    fun forPath(path: String): String {
        val extension = path.substringAfterLast('.', "").lowercase(Locale.US)
        return mimeTypes[extension] ?: "application/octet-stream"
    }

    fun looksLikeStaticAsset(path: String): Boolean {
        val extension = path.substringAfterLast('.', "")
        return extension.isNotBlank()
    }

    fun charsetFor(mimeType: String): String? {
        return if (mimeType.startsWith("text/") || mimeType == "application/json") "utf-8" else null
    }
}

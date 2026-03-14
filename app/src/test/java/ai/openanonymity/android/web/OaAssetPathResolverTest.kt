package ai.openanonymity.android.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OaAssetPathResolverTest {
    private val resolver = OaAssetPathResolver()
    private val knownAssets = setOf(
        "oa-chat-dist/index.html",
        "oa-chat-dist/assets/app-123.js",
        "oa-chat-dist/styles.css",
        "oa-chat-dist/vendor/libcurl.js",
    )

    @Test
    fun `root path resolves to index`() {
        val resolution = resolver.resolve("/") { it in knownAssets }
        assertEquals("oa-chat-dist/index.html", resolution?.assetPath)
        assertFalse(resolution?.spaFallback ?: true)
    }

    @Test
    fun `existing static asset is served directly`() {
        val resolution = resolver.resolve("/assets/app-123.js") { it in knownAssets }
        assertEquals("oa-chat-dist/assets/app-123.js", resolution?.assetPath)
        assertEquals("text/javascript", resolution?.mimeType)
    }

    @Test
    fun `extensionless routes fall back to spa index`() {
        val resolution = resolver.resolve("/tickets/ABCDEFGHIJKLMNOPQRSTUVWX") { it in knownAssets }
        assertEquals("oa-chat-dist/index.html", resolution?.assetPath)
        assertTrue(resolution?.spaFallback ?: false)
    }

    @Test
    fun `missing file-like paths do not fall back to spa`() {
        val resolution = resolver.resolve("/assets/missing.js") { it in knownAssets }
        assertNull(resolution)
    }
}

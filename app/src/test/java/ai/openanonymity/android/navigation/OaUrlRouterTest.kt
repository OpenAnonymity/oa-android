package ai.openanonymity.android.navigation

import ai.openanonymity.android.OaAppOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OaUrlRouterTest {
    private val router = OaUrlRouter()

    @Test
    fun `blank intent falls back to root origin`() {
        assertEquals(OaAppOrigin.ROOT_URL, router.initialUrl(null))
    }

    @Test
    fun `session deep link is preserved unchanged`() {
        val url = "https://chat.openanonymity.ai/?s=share-123"
        assertEquals(url, router.initialUrl(url))
    }

    @Test
    fun `ticket deep link is preserved unchanged`() {
        val url = "https://chat.openanonymity.ai/tickets/ABCDEFGHIJKLMNOPQRSTUVWX"
        assertEquals(url, router.initialUrl(url))
    }

    @Test
    fun `off-origin links stay external`() {
        val decision = router.route("https://example.com/docs")
        assertEquals(NavigationTarget.EXTERNAL, decision.target)
        assertEquals("https://example.com/docs", decision.url)
    }

    @Test
    fun `malformed links fall back to root`() {
        val decision = router.route("://bad-url")
        assertEquals(NavigationTarget.WEBVIEW, decision.target)
        assertEquals(OaAppOrigin.ROOT_URL, decision.url)
    }
}

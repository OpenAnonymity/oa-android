package ai.openanonymity.android.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class ExternalNavigationPolicyTest {
    private val policy = ExternalNavigationPolicy()

    @Test
    fun `same-origin https stays in webview`() {
        val decision = policy.decide("https://chat.openanonymity.ai/?s=session")
        assertEquals(NavigationTarget.WEBVIEW, decision.target)
    }

    @Test
    fun `blob urls stay in webview`() {
        val decision = policy.decide("blob:https://chat.openanonymity.ai/123")
        assertEquals(NavigationTarget.WEBVIEW, decision.target)
    }

    @Test
    fun `external https opens outside app`() {
        val decision = policy.decide("https://openanonymity.ai/blog")
        assertEquals(NavigationTarget.EXTERNAL, decision.target)
    }

    @Test
    fun `mailto opens outside app`() {
        val decision = policy.decide("mailto:team@openanonymity.ai")
        assertEquals(NavigationTarget.EXTERNAL, decision.target)
    }

    @Test
    fun `blank links are ignored`() {
        val decision = policy.decide("")
        assertEquals(NavigationTarget.IGNORE, decision.target)
    }
}

package ai.openanonymity.android.navigation

import ai.openanonymity.android.OaAppOrigin
import java.net.URI

class ExternalNavigationPolicy(
    private val appHost: String = OaAppOrigin.HOST,
) {
    fun decide(url: String?): NavigationDecision {
        if (url.isNullOrBlank()) {
            return NavigationDecision(NavigationTarget.IGNORE)
        }

        val parsed = try {
            URI(url)
        } catch (_: Exception) {
            return NavigationDecision(NavigationTarget.IGNORE)
        }

        val scheme = parsed.scheme?.lowercase()
        return when {
            scheme == null -> NavigationDecision(NavigationTarget.IGNORE)
            scheme in INTERNAL_SCHEMES -> NavigationDecision(NavigationTarget.WEBVIEW, url)
            scheme in EXTERNAL_SCHEMES -> NavigationDecision(NavigationTarget.EXTERNAL, url)
            (scheme == "https" || scheme == "http") && (parsed.host?.equals(appHost, ignoreCase = true) == true) ->
                NavigationDecision(NavigationTarget.WEBVIEW, url)
            scheme == "https" || scheme == "http" ->
                NavigationDecision(NavigationTarget.EXTERNAL, url)
            else -> NavigationDecision(NavigationTarget.EXTERNAL, url)
        }
    }

    companion object {
        private val INTERNAL_SCHEMES = setOf("about", "blob", "data", "javascript")
        private val EXTERNAL_SCHEMES = setOf("mailto", "tel", "sms", "intent", "market")
    }
}

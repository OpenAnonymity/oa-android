package ai.openanonymity.android.navigation

import ai.openanonymity.android.OaAppOrigin
import java.net.URI

class OaUrlRouter(
    private val appOrigin: String = OaAppOrigin.ROOT_URL,
) {
    fun initialUrl(intentUrl: String?): String {
        val decision = route(intentUrl)
        return if (decision.target == NavigationTarget.WEBVIEW) {
            decision.url ?: appOrigin
        } else {
            appOrigin
        }
    }

    fun route(intentUrl: String?): NavigationDecision {
        if (intentUrl.isNullOrBlank()) {
            return NavigationDecision(NavigationTarget.WEBVIEW, appOrigin)
        }

        val parsed = intentUrl.toUriOrNull()
            ?: return NavigationDecision(NavigationTarget.WEBVIEW, appOrigin)

        return if (isAppLink(parsed)) {
            NavigationDecision(NavigationTarget.WEBVIEW, parsed.toString())
        } else {
            NavigationDecision(NavigationTarget.EXTERNAL, parsed.toString())
        }
    }

    private fun isAppLink(uri: URI): Boolean {
        return uri.scheme.equals("https", ignoreCase = true) &&
            (uri.host?.equals(URI(appOrigin).host, ignoreCase = true) == true)
    }
}

private fun String.toUriOrNull(): URI? {
    return try {
        URI(this)
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: java.net.URISyntaxException) {
        null
    }
}

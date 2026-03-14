package ai.openanonymity.android.navigation

enum class NavigationTarget {
    WEBVIEW,
    EXTERNAL,
    IGNORE
}

data class NavigationDecision(
    val target: NavigationTarget,
    val url: String? = null,
)

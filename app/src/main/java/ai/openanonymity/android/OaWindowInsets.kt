package ai.openanonymity.android

import androidx.core.graphics.Insets

data class OaWindowPadding(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

object OaWindowInsets {
    fun resolvePadding(
        systemBars: Insets,
        ime: Insets,
        imeVisible: Boolean,
    ): OaWindowPadding {
        return OaWindowPadding(
            left = systemBars.left,
            top = systemBars.top,
            right = systemBars.right,
            bottom = if (imeVisible) maxOf(systemBars.bottom, ime.bottom) else systemBars.bottom,
        )
    }
}

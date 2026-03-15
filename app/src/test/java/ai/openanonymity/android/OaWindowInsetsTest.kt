package ai.openanonymity.android

import androidx.core.graphics.Insets
import org.junit.Assert.assertEquals
import org.junit.Test

class OaWindowInsetsTest {
    @Test
    fun returnsSystemBarsWhenImeIsHidden() {
        val padding = OaWindowInsets.resolvePadding(
            systemBars = Insets.of(4, 48, 6, 24),
            ime = Insets.of(0, 0, 0, 320),
            imeVisible = false,
        )

        assertEquals(OaWindowPadding(4, 48, 6, 24), padding)
    }

    @Test
    fun usesLargerBottomInsetWhenImeIsVisible() {
        val padding = OaWindowInsets.resolvePadding(
            systemBars = Insets.of(0, 48, 0, 24),
            ime = Insets.of(0, 0, 0, 640),
            imeVisible = true,
        )

        assertEquals(OaWindowPadding(0, 48, 0, 640), padding)
    }
}

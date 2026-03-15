package ai.openanonymity.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OaSurfaceStyleTest {
    @Test
    fun usesDarkSystemBarIconsOnLightBackgrounds() {
        assertTrue(OaSurfaceStyle.shouldUseDarkSystemBarIcons(0xFFFFFFFF.toInt()))
    }

    @Test
    fun usesLightSystemBarIconsOnDarkBackgrounds() {
        assertFalse(OaSurfaceStyle.shouldUseDarkSystemBarIcons(0xFF262626.toInt()))
    }
}

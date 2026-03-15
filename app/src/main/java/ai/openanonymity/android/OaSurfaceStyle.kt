package ai.openanonymity.android

object OaSurfaceStyle {
    fun shouldUseDarkSystemBarIcons(backgroundColor: Int): Boolean {
        val red = ((backgroundColor shr 16) and 0xFF) / 255.0
        val green = ((backgroundColor shr 8) and 0xFF) / 255.0
        val blue = (backgroundColor and 0xFF) / 255.0

        val luminance = (0.2126 * linearize(red)) + (0.7152 * linearize(green)) + (0.0722 * linearize(blue))
        return luminance >= 0.5
    }

    private fun linearize(channel: Double): Double {
        return if (channel <= 0.03928) {
            channel / 12.92
        } else {
            Math.pow((channel + 0.055) / 1.055, 2.4)
        }
    }
}

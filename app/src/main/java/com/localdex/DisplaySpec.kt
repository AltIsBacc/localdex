package com.localdex

import java.util.Locale
import kotlin.math.roundToInt

/**
 * What the virtual display should look like. A null part means "choose automatically": the screen's own size and
 * density, and the system's default refresh rate.
 */
data class DisplaySpec(
    val width: Int? = null,
    val height: Int? = null,
    val dpi: Int? = null,
    val hz: Float? = null,
) {
    companion object {
        const val MAX_HZ = 240f

        /** `WIDTHxHEIGHT/DPI` with an optional `@HZ`, e.g. `1920x1080/240` or `1920x1080/240@90`. */
        private val PATTERN = Regex("(\\d{3,5})x(\\d{3,5})/(\\d{2,3})(?:@(\\d{1,3}(?:\\.\\d{1,2})?))?")

        /** Returns an all-automatic spec for blank text and null for text that is not a valid spec. */
        fun parse(text: String): DisplaySpec? {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return DisplaySpec()
            val match = PATTERN.matchEntire(trimmed) ?: return null
            val hz = match.groupValues[4].takeIf { it.isNotEmpty() }?.toFloat()
            if (hz != null && hz !in 1f..MAX_HZ) return null
            return DisplaySpec(
                width = match.groupValues[1].toInt(),
                height = match.groupValues[2].toInt(),
                dpi = match.groupValues[3].toInt(),
                hz = hz,
            )
        }

        /** How wide, in dp, Automatic makes the layout: a laptop-sized desktop rather than a phone layout. */
        const val DESKTOP_DP_WIDTH = 1600

        /** The density for [width] pixels that gives a [DESKTOP_DP_WIDTH] dp wide layout: 1920 px is 192 dpi. */
        fun autoDpi(width: Int): Int = (width * 160f / DESKTOP_DP_WIDTH).roundToInt().coerceIn(72, 800)

        fun formatHz(hz: Float): String =
            if (hz % 1f == 0f) hz.toInt().toString() else String.format(Locale.ROOT, "%.1f", hz)
    }
}

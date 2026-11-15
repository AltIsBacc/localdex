package com.localdex

import java.util.Locale
import kotlin.math.roundToInt

// A null part means automatic
data class DisplaySpec(
    val width: Int? = null,
    val height: Int? = null,
    val dpi: Int? = null,
    val hz: Float? = null,
) {
    companion object {
        const val MAX_HZ = 240f

        // WIDTHxHEIGHT/DPI with an optional @HZ
        private val PATTERN = Regex("(\\d{3,5})x(\\d{3,5})/(\\d{2,3})(?:@(\\d{1,3}(?:\\.\\d{1,2})?))?")

        // Blank text is an all-automatic spec; text that is not a valid spec is null
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

        const val DESKTOP_DP_WIDTH = 1600

        fun autoDpi(width: Int): Int = (width * 160f / DESKTOP_DP_WIDTH).roundToInt().coerceIn(72, 800)

        fun formatHz(hz: Float): String =
            if (hz % 1f == 0f) hz.toInt().toString() else String.format(Locale.ROOT, "%.1f", hz)
    }
}

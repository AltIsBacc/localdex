package com.localdex

import kotlin.math.floor
import kotlin.math.roundToInt

object Presets {

    data class Resolution(val label: String, val width: Int, val height: Int)

    val resolutions = listOf(
        Resolution("1280×720 (HD)", 1280, 720),
        Resolution("1600×900 (HD+)", 1600, 900),
        Resolution("1920×1080 (Full HD)", 1920, 1080),
        Resolution("1920×1200 (16:10)", 1920, 1200),
        Resolution("2560×1080 (21:9 ultrawide)", 2560, 1080),
        Resolution("2560×1440 (QHD)", 2560, 1440),
        Resolution("2560×1600 (16:10 QHD)", 2560, 1600),
        Resolution("3840×2160 (4K, heavy on the GPU)", 3840, 2160),
    )

    val dpis = listOf(120, 140, 160, 180, 200, 213, 240, 280, 320, 400, 480)

    // The system rounds a request up to a divisor of the screen's rate, so only divisors are exact
    fun refreshRates(screenHz: Float): List<Float> {
        val screen = screenHz.roundToInt().coerceAtLeast(1)
        return (1..4).map { screen.toFloat() / it }.filter { it >= 24f }
    }

    fun expectedRefreshRate(screenHz: Float, requested: Float): Float {
        val screen = screenHz.roundToInt().coerceAtLeast(1).toFloat()
        if (requested >= screen) return screen
        val divisor = floor(screen / requested + 0.001f).toInt().coerceAtLeast(1)
        return screen / divisor
    }
}

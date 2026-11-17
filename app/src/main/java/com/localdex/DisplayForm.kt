package com.localdex

import com.localdex.ui.ChoiceState
import kotlin.math.abs
import kotlin.math.roundToInt

class DisplayForm(
    private val screenWidth: Int,
    private val screenHeight: Int,
    private val screenHz: Float,
    initial: DisplaySpec,
) {
    data class Validation(
        val spec: DisplaySpec?,
        val error: String?,
        val resolutionError: String?,
        val dpiError: String?,
        val hzError: String?,
    )

    private val refreshRates = Presets.refreshRates(screenHz)

    val resolution = ChoiceState(
        automatic = "This screen ($screenWidth×$screenHeight)",
        presets = Presets.resolutions.map { it.label },
    )
    val dpi = ChoiceState(
        automatic = "Automatic (desktop-sized)",
        presets = Presets.dpis.map { "$it dpi" },
    )
    val hz = ChoiceState(
        automatic = "System default",
        presets = refreshRates.mapIndexed { index, rate ->
            "${DisplaySpec.formatHz(rate)} Hz" + if (index == 0) " (this screen)" else ""
        },
    )

    init {
        restore(initial)
    }

    fun reset() = listOf(resolution, dpi, hz).forEach { it.reset() }

    fun validate(): Validation {
        val resolutionResult = readResolution()
        val dpiResult = readDpi()
        val hzResult = readHz()
        val error = listOf(resolutionResult, dpiResult, hzResult).firstNotNullOfOrNull { it.exceptionOrNull() }
        val spec = if (error == null) {
            DisplaySpec(
                resolutionResult.getOrNull()?.first,
                resolutionResult.getOrNull()?.second,
                dpiResult.getOrNull(),
                hzResult.getOrNull(),
            )
        } else {
            null
        }
        return Validation(
            spec = spec,
            error = error?.message,
            resolutionError = fieldError(resolution, resolutionResult.exceptionOrNull()),
            dpiError = fieldError(dpi, dpiResult.exceptionOrNull()),
            hzError = fieldError(hz, hzResult.exceptionOrNull()),
        )
    }

    fun summary(validation: Validation, running: Boolean): String {
        val spec = validation.spec ?: return validation.error.orEmpty()
        return if (running) {
            describe(spec) + "\nA display is already running. Close it to apply new settings."
        } else {
            describe(spec)
        }
    }

    private fun fieldError(choice: ChoiceState, error: Throwable?): String? =
        error?.message?.takeIf { choice.isCustom && choice.customText.isNotEmpty() }

    private fun restore(spec: DisplaySpec) {
        val width = spec.width
        val height = spec.height
        if (width != null && height != null) {
            val index = Presets.resolutions.indexOfFirst { it.width == width && it.height == height }
            if (index >= 0) resolution.selectPreset(index) else resolution.selectCustom("${width}x$height")
        }
        val dpiValue = spec.dpi
        if (dpiValue != null) {
            val index = Presets.dpis.indexOf(dpiValue)
            if (index >= 0) dpi.selectPreset(index) else dpi.selectCustom(dpiValue.toString())
        }
        val hzValue = spec.hz
        if (hzValue != null) {
            val index = refreshRates.indexOfFirst { abs(it - hzValue) < 0.01f }
            if (index >= 0) hz.selectPreset(index) else hz.selectCustom(DisplaySpec.formatHz(hzValue))
        }
    }

    private fun readResolution(): Result<Pair<Int, Int>?> {
        val preset = resolution.presetIndex
        if (preset != null) return Result.success(Presets.resolutions[preset].let { it.width to it.height })
        if (!resolution.isCustom) return Result.success(null)
        val match = RESOLUTION.matchEntire(resolution.customText.trim())
        val width = match?.groupValues?.get(1)?.toInt()
        val height = match?.groupValues?.get(2)?.toInt()
        if (width == null || height == null || width !in MIN_SIZE..MAX_SIZE || height !in MIN_SIZE..MAX_SIZE) {
            return Result.failure(
                IllegalArgumentException("Resolution: use WIDTHxHEIGHT, $MIN_SIZE to $MAX_SIZE pixels, e.g. 1920x1080")
            )
        }
        return Result.success(width to height)
    }

    private fun readDpi(): Result<Int?> {
        val preset = dpi.presetIndex
        if (preset != null) return Result.success(Presets.dpis[preset])
        if (!dpi.isCustom) return Result.success(null)
        val value = dpi.customText.trim().toIntOrNull()
        if (value == null || value !in MIN_DPI..MAX_DPI) {
            return Result.failure(IllegalArgumentException("Density: use a number from $MIN_DPI to $MAX_DPI"))
        }
        return Result.success(value)
    }

    private fun readHz(): Result<Float?> {
        val preset = hz.presetIndex
        if (preset != null) return Result.success(refreshRates[preset])
        if (!hz.isCustom) return Result.success(null)
        val value = hz.customText.trim().toFloatOrNull()
        if (value == null || value !in 1f..DisplaySpec.MAX_HZ) {
            return Result.failure(
                IllegalArgumentException("Refresh rate: use a number from 1 to ${DisplaySpec.MAX_HZ.toInt()}")
            )
        }
        return Result.success(value)
    }

    private fun describe(spec: DisplaySpec): String {
        val width = spec.width ?: screenWidth
        val height = spec.height ?: screenHeight
        val density = spec.dpi ?: DisplaySpec.autoDpi(width)
        val dpWidth = (width * 160f / density).roundToInt()
        val dpHeight = (height * 160f / density).roundToInt()
        return "$width×$height at $density dpi\nLayout about $dpWidth×$dpHeight dp\n${describeRefreshRate(spec.hz)}"
    }

    private fun describeRefreshRate(requested: Float?): String {
        if (requested == null) return "Refresh rate: system default"
        val expected = Presets.expectedRefreshRate(screenHz, requested)
        val text = "Refresh rate: ${DisplaySpec.formatHz(requested)} Hz"
        return if (abs(expected - requested) < 0.01f) {
            text
        } else {
            "$text, which the system will round to about ${DisplaySpec.formatHz(expected)} Hz"
        }
    }

    private companion object {
        val RESOLUTION = Regex("(\\d{3,4})\\s*[x×]\\s*(\\d{3,4})")
        const val MIN_SIZE = 200
        const val MAX_SIZE = 8192
        const val MIN_DPI = 72
        const val MAX_DPI = 800
    }
}

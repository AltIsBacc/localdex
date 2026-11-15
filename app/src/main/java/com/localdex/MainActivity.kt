package com.localdex

import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var refreshButton: Button
    private lateinit var configGroup: View
    private lateinit var summaryText: TextView
    private lateinit var resetButton: Button
    private lateinit var viewerButton: Button

    private lateinit var resolutionChoice: Choice
    private lateinit var dpiChoice: Choice
    private lateinit var hzChoice: Choice

    private var screenHz = 60f
    private lateinit var refreshRates: List<Float>

    /** The spec the pickers currently describe, or null while one of them holds an invalid custom value. */
    private var currentSpec: DisplaySpec? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        refreshButton = findViewById(R.id.refreshButton)
        configGroup = findViewById(R.id.configGroup)
        summaryText = findViewById(R.id.summaryText)
        resetButton = findViewById(R.id.resetButton)
        viewerButton = findViewById(R.id.viewerButton)

        screenHz = screenRefreshRate()
        refreshRates = Presets.refreshRates(screenHz)
        val (screenWidth, screenHeight) = screenSize()
        resolutionChoice = Choice(
            findViewById(R.id.resolutionSpinner),
            findViewById(R.id.customResolutionField),
            automatic = "This screen ($screenWidth×$screenHeight)",
            presets = Presets.resolutions.map { it.label },
            onChange = ::refreshSummary,
        )
        dpiChoice = Choice(
            findViewById(R.id.dpiSpinner),
            findViewById(R.id.customDpiField),
            automatic = "Automatic (desktop-sized)",
            presets = Presets.dpis.map { "$it dpi" },
            onChange = ::refreshSummary,
        )
        hzChoice = Choice(
            findViewById(R.id.hzSpinner),
            findViewById(R.id.customHzField),
            automatic = "System default",
            presets = refreshRates.mapIndexed { index, hz ->
                "${DisplaySpec.formatHz(hz)} Hz" + if (index == 0) " (this screen)" else ""
            },
            onChange = ::refreshSummary,
        )
        restore(Prefs.getSpec(this))
        refreshSummary()

        refreshButton.setOnClickListener { refresh() }
        resetButton.setOnClickListener { listOf(resolutionChoice, dpiChoice, hzChoice).forEach { it.reset() } }
        viewerButton.setOnClickListener { openViewer() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        statusText.text = "Checking root access…"
        lifecycleScope.launch {
            if (Root.isAvailable()) showReady() else showNoRoot()
        }
    }

    private fun showNoRoot() {
        statusText.text = "❌ Root access was not granted.\n\n" +
            "Allow LocalDex in your root manager (Magisk / KernelSU), then refresh."
        render(ready = false)
    }

    private fun showReady() {
        statusText.text = "✅ Root granted. Choose how the virtual display should look, then open it."
        render(ready = true)
    }

    private fun render(ready: Boolean) {
        val visibility = if (ready) View.VISIBLE else View.GONE
        configGroup.visibility = visibility
        viewerButton.visibility = visibility
    }

    /** The highest refresh rate the built-in screen supports, which the virtual display's rate divides. */
    private fun screenRefreshRate(): Float {
        val display = getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY) ?: return 60f
        return display.supportedModes.maxOfOrNull { it.refreshRate } ?: display.refreshRate
    }

    /** The built-in screen's size as the virtual display sees it: landscape, whatever this activity's orientation. */
    private fun screenSize(): Pair<Int, Int> {
        val bounds = windowManager.maximumWindowMetrics.bounds
        return maxOf(bounds.width(), bounds.height()) to minOf(bounds.width(), bounds.height())
    }

    private fun restore(spec: DisplaySpec) {
        val width = spec.width
        val height = spec.height
        if (width != null && height != null) {
            val index = Presets.resolutions.indexOfFirst { it.width == width && it.height == height }
            if (index >= 0) resolutionChoice.selectPreset(index) else resolutionChoice.selectCustom("${width}x$height")
        }
        val dpi = spec.dpi
        if (dpi != null) {
            val index = Presets.dpis.indexOf(dpi)
            if (index >= 0) dpiChoice.selectPreset(index) else dpiChoice.selectCustom(dpi.toString())
        }
        val hz = spec.hz
        if (hz != null) {
            val index = refreshRates.indexOfFirst { abs(it - hz) < 0.01f }
            if (index >= 0) hzChoice.selectPreset(index) else hzChoice.selectCustom(DisplaySpec.formatHz(hz))
        }
    }

    private fun readResolution(): Result<Pair<Int, Int>?> {
        val preset = resolutionChoice.presetIndex
        if (preset != null) return Result.success(Presets.resolutions[preset].let { it.width to it.height })
        if (!resolutionChoice.isCustom) return Result.success(null)
        val match = RESOLUTION.matchEntire(resolutionChoice.field.text.toString().trim())
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
        val preset = dpiChoice.presetIndex
        if (preset != null) return Result.success(Presets.dpis[preset])
        if (!dpiChoice.isCustom) return Result.success(null)
        val dpi = dpiChoice.field.text.toString().trim().toIntOrNull()
        if (dpi == null || dpi !in MIN_DPI..MAX_DPI) {
            return Result.failure(IllegalArgumentException("Density: use a number from $MIN_DPI to $MAX_DPI"))
        }
        return Result.success(dpi)
    }

    private fun readHz(): Result<Float?> {
        val preset = hzChoice.presetIndex
        if (preset != null) return Result.success(refreshRates[preset])
        if (!hzChoice.isCustom) return Result.success(null)
        val hz = hzChoice.field.text.toString().trim().toFloatOrNull()
        if (hz == null || hz !in 1f..DisplaySpec.MAX_HZ) {
            return Result.failure(
                IllegalArgumentException("Refresh rate: use a number from 1 to ${DisplaySpec.MAX_HZ.toInt()}")
            )
        }
        return Result.success(hz)
    }

    /** Re-reads the pickers, flags invalid custom values inline and shows what the display would end up as. */
    private fun refreshSummary() {
        val resolution = readResolution()
        val dpi = readDpi()
        val hz = readHz()
        resolutionChoice.showError(resolution.exceptionOrNull()?.message)
        dpiChoice.showError(dpi.exceptionOrNull()?.message)
        hzChoice.showError(hz.exceptionOrNull()?.message)

        val error = listOf(resolution, dpi, hz).firstNotNullOfOrNull { it.exceptionOrNull() }
        val spec = if (error == null) {
            DisplaySpec(resolution.getOrNull()?.first, resolution.getOrNull()?.second, dpi.getOrNull(), hz.getOrNull())
        } else {
            null
        }
        currentSpec = spec
        viewerButton.isEnabled = spec != null
        summaryText.text = if (spec != null) describe(spec) else error?.message.orEmpty()
    }

    private fun describe(spec: DisplaySpec): String {
        val (screenWidth, screenHeight) = screenSize()
        val width = spec.width ?: screenWidth
        val height = spec.height ?: screenHeight
        val dpi = spec.dpi ?: DisplaySpec.autoDpi(width)
        val dpWidth = (width * 160f / dpi).roundToInt()
        val dpHeight = (height * 160f / dpi).roundToInt()
        return "$width×$height at $dpi dpi\nLayout about $dpWidth×$dpHeight dp\n${describeRefreshRate(spec.hz)}"
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

    private fun openViewer() {
        val spec = currentSpec ?: return
        Prefs.setSpec(this, spec)
        startActivity(Intent(this, DisplayActivity::class.java))
    }

    /**
     * A spinner with an "automatic" entry first, the [presets], and a "Custom…" entry last. The [field] is shown only
     * while Custom is selected. [onChange] runs when the selection or the field's text changes.
     */
    private class Choice(
        val spinner: Spinner,
        val field: EditText,
        automatic: String,
        presets: List<String>,
        onChange: () -> Unit,
    ) {
        private val customIndex = presets.size + 1

        init {
            val labels = listOf(automatic) + presets + "Custom…"
            spinner.adapter = ArrayAdapter(spinner.context, android.R.layout.simple_spinner_item, labels).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    field.visibility = if (position == customIndex) View.VISIBLE else View.GONE
                    onChange()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
            field.doAfterTextChanged { onChange() }
        }

        val isCustom: Boolean get() = spinner.selectedItemPosition == customIndex

        /** Index into the presets, or null while automatic or Custom is selected. */
        val presetIndex: Int?
            get() = (spinner.selectedItemPosition - 1).takeIf { it in 0 until customIndex - 1 }

        fun selectPreset(index: Int) = spinner.setSelection(index + 1)

        fun selectCustom(text: String) {
            spinner.setSelection(customIndex)
            field.setText(text)
            field.visibility = View.VISIBLE
        }

        fun reset() {
            field.setText("")
            spinner.setSelection(0)
        }

        /** Marks the field only once something has been typed, so a freshly opened Custom field is not red. */
        fun showError(message: String?) {
            val shown = if (isCustom && field.text.isNotEmpty()) message else null
            if (field.error?.toString() != shown) field.error = shown
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

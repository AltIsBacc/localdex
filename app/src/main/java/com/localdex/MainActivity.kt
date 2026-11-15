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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var refreshButton: Button
    private lateinit var configGroup: View
    private lateinit var viewerButton: Button

    private lateinit var resolutionChoice: Choice
    private lateinit var dpiChoice: Choice
    private lateinit var hzChoice: Choice
    private lateinit var refreshRates: List<Float>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        refreshButton = findViewById(R.id.refreshButton)
        configGroup = findViewById(R.id.configGroup)
        viewerButton = findViewById(R.id.viewerButton)

        refreshRates = Presets.refreshRates(screenRefreshRate())
        resolutionChoice = Choice(
            findViewById(R.id.resolutionSpinner),
            findViewById(R.id.customResolutionField),
            automatic = "This screen (automatic)",
            presets = Presets.resolutions.map { it.label },
        )
        dpiChoice = Choice(
            findViewById(R.id.dpiSpinner),
            findViewById(R.id.customDpiField),
            automatic = "Automatic",
            presets = Presets.dpis.map { "$it dpi" },
        )
        hzChoice = Choice(
            findViewById(R.id.hzSpinner),
            findViewById(R.id.customHzField),
            automatic = "System default",
            presets = refreshRates.mapIndexed { index, hz ->
                "${DisplaySpec.formatHz(hz)} Hz" + if (index == 0) " (this screen)" else ""
            },
        )
        restore(Prefs.getSpec(this))

        refreshButton.setOnClickListener { refresh() }
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
        statusText.text = "✅ Root granted.\n\n" +
            "Open the virtual display to run a separate desktop inside this app. Pick a " +
            "resolution, density and refresh rate, or choose Custom to type your own. " +
            "Automatic uses this screen's size and the system's default refresh rate."
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

    /** Returns null after telling the user what is wrong with a custom value. */
    private fun readSpec(): DisplaySpec? {
        var width: Int? = null
        var height: Int? = null
        val resolutionPreset = resolutionChoice.presetIndex
        if (resolutionPreset != null) {
            width = Presets.resolutions[resolutionPreset].width
            height = Presets.resolutions[resolutionPreset].height
        } else if (resolutionChoice.isCustom) {
            val match = RESOLUTION.matchEntire(resolutionChoice.field.text.toString().trim())
            val customWidth = match?.groupValues?.get(1)?.toInt()
            val customHeight = match?.groupValues?.get(2)?.toInt()
            if (customWidth == null || customHeight == null ||
                customWidth !in MIN_SIZE..MAX_SIZE || customHeight !in MIN_SIZE..MAX_SIZE
            ) {
                return invalid("Resolution must look like 1920x1080 ($MIN_SIZE to $MAX_SIZE pixels)")
            }
            width = customWidth
            height = customHeight
        }

        var dpi: Int? = null
        val dpiPreset = dpiChoice.presetIndex
        if (dpiPreset != null) {
            dpi = Presets.dpis[dpiPreset]
        } else if (dpiChoice.isCustom) {
            dpi = dpiChoice.field.text.toString().trim().toIntOrNull()?.takeIf { it in MIN_DPI..MAX_DPI }
                ?: return invalid("DPI must be a number from $MIN_DPI to $MAX_DPI")
        }

        var hz: Float? = null
        val hzPreset = hzChoice.presetIndex
        if (hzPreset != null) {
            hz = refreshRates[hzPreset]
        } else if (hzChoice.isCustom) {
            hz = hzChoice.field.text.toString().trim().toFloatOrNull()?.takeIf { it in 1f..DisplaySpec.MAX_HZ }
                ?: return invalid("Refresh rate must be a number from 1 to ${DisplaySpec.MAX_HZ.toInt()}")
        }

        return DisplaySpec(width, height, dpi, hz)
    }

    private fun invalid(message: String): DisplaySpec? {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        return null
    }

    private fun openViewer() {
        val spec = readSpec() ?: return
        Prefs.setSpec(this, spec)
        startActivity(Intent(this, DisplayActivity::class.java))
    }

    /**
     * A spinner with an "automatic" entry first, the [presets], and a "Custom…" entry last. The [field] is shown only
     * while Custom is selected.
     */
    private class Choice(val spinner: Spinner, val field: EditText, automatic: String, presets: List<String>) {
        private val customIndex = presets.size + 1

        init {
            val labels = listOf(automatic) + presets + "Custom…"
            spinner.adapter = ArrayAdapter(spinner.context, android.R.layout.simple_spinner_item, labels).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    field.visibility = if (position == customIndex) View.VISIBLE else View.GONE
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
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
    }

    private companion object {
        val RESOLUTION = Regex("(\\d{3,4})\\s*[x×]\\s*(\\d{3,4})")
        const val MIN_SIZE = 200
        const val MAX_SIZE = 8192
        const val MIN_DPI = 72
        const val MAX_DPI = 800
    }
}

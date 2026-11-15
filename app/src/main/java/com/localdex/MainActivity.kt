package com.localdex

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var refreshButton: Button
    private lateinit var configGroup: View
    private lateinit var displaySpecField: EditText
    private lateinit var viewerButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        refreshButton = findViewById(R.id.refreshButton)
        configGroup = findViewById(R.id.configGroup)
        displaySpecField = findViewById(R.id.displaySpecField)
        viewerButton = findViewById(R.id.viewerButton)

        displaySpecField.setText(Prefs.getDisplaySpec(this))

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
            "Open the virtual display to run a separate desktop inside this app. Leave the " +
            "display spec blank to use the size and density of this screen, or enter " +
            "WIDTHxHEIGHT/DPI."
        render(ready = true)
    }

    private fun render(ready: Boolean) {
        val visibility = if (ready) View.VISIBLE else View.GONE
        configGroup.visibility = visibility
        viewerButton.visibility = visibility
    }

    private fun openViewer() {
        val spec = displaySpecField.text.toString().trim()
        if (spec.isNotEmpty() && !DisplaySpec.PATTERN.matches(spec)) {
            Toast.makeText(this, "Display spec must look like 1920x1080/240", Toast.LENGTH_LONG).show()
            return
        }
        Prefs.setDisplaySpec(this, spec)
        startActivity(Intent(this, DisplayActivity::class.java))
    }
}

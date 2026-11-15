package com.localdex

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var actionButton: Button
    private lateinit var refreshButton: Button
    private lateinit var configGroup: View
    private lateinit var displaySpecField: EditText
    private lateinit var enableButton: Button
    private lateinit var viewerButton: Button
    private lateinit var switchBackButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        actionButton = findViewById(R.id.actionButton)
        refreshButton = findViewById(R.id.refreshButton)
        configGroup = findViewById(R.id.configGroup)
        displaySpecField = findViewById(R.id.displaySpecField)
        enableButton = findViewById(R.id.enableButton)
        viewerButton = findViewById(R.id.viewerButton)
        switchBackButton = findViewById(R.id.switchBackButton)

        displaySpecField.setText(Prefs.getDisplaySpec(this))

        refreshButton.setOnClickListener { refresh() }
        enableButton.setOnClickListener { enableDex() }
        viewerButton.setOnClickListener { openViewer() }
        switchBackButton.setOnClickListener {
            DexService.switchBack(this)
            statusText.postDelayed({ refresh() }, REFRESH_DELAY_MS)
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        if (DexMode.isActive(this)) {
            showActive()
            return
        }
        statusText.text = "Checking root access…"
        lifecycleScope.launch {
            when {
                !Root.isAvailable() -> showNoRoot()
                !NotificationManagerCompat.from(this@MainActivity).areNotificationsEnabled() ->
                    showNotificationsOff()
                else -> showReady()
            }
        }
    }

    private fun showNoRoot() {
        statusText.text = "❌ Root access was not granted.\n\n" +
            "Allow LocalDex in your root manager (Magisk / KernelSU), then refresh."
        render(action = false, config = false, enable = false, viewer = false, switchBack = false)
    }

    private fun showNotificationsOff() {
        statusText.text = "Notifications are off.\n\n" +
            "LocalDex needs its notification to let you switch back from DeX."
        actionButton.text = "Enable Notifications"
        actionButton.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            )
        }
        render(action = true, config = false, enable = false, viewer = false, switchBack = false)
    }

    private fun showReady() {
        statusText.text = "✅ Root granted.\n\n" +
            "Enabling DeX turns the main display into the DeX desktop. Leave the " +
            "display spec blank to keep the native resolution, or enter " +
            "WIDTHxHEIGHT/DPI. Switch back anytime from the notification."
        render(action = false, config = true, enable = true, viewer = true, switchBack = false)
    }

    private fun showActive() {
        statusText.text = "🖥️ DeX is active on the main display.\n\n" +
            "Switch back here or from the LocalDex notification."
        render(action = false, config = false, enable = false, viewer = false, switchBack = true)
    }

    private fun render(action: Boolean, config: Boolean, enable: Boolean, viewer: Boolean, switchBack: Boolean) {
        actionButton.visibility = visibility(action)
        configGroup.visibility = visibility(config)
        enableButton.visibility = visibility(enable)
        viewerButton.visibility = visibility(viewer)
        switchBackButton.visibility = visibility(switchBack)
        refreshButton.visibility = visibility(!switchBack)
    }

    private fun visibility(visible: Boolean) = if (visible) View.VISIBLE else View.GONE

    private fun enableDex() {
        val spec = displaySpecField.text.toString().trim()
        if (spec.isNotEmpty() && !DisplaySpec.PATTERN.matches(spec)) {
            Toast.makeText(this, "Display spec must look like 1920x1080/240", Toast.LENGTH_LONG).show()
            return
        }
        Prefs.setDisplaySpec(this, spec)
        DexService.enable(this, spec)
        statusText.postDelayed({ refresh() }, REFRESH_DELAY_MS)
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

    companion object {
        private const val REFRESH_DELAY_MS = 2000L
    }
}

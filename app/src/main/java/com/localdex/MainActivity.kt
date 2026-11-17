package com.localdex

import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.localdex.ui.LocalDexTheme
import com.localdex.ui.MainScreen
import com.localdex.ui.RootStatus
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var rootStatus by mutableStateOf(RootStatus.Checking)
    private lateinit var form: DisplayForm

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val (screenWidth, screenHeight) = screenSize()
        form = DisplayForm(screenWidth, screenHeight, screenRefreshRate(), Prefs.getSpec(this))

        setContent {
            LocalDexTheme {
                val config by DisplaySession.config.collectAsStateWithLifecycle()
                MainScreen(
                    status = rootStatus,
                    form = form,
                    running = config != null,
                    onRefresh = ::refresh,
                    onOpenViewer = ::openViewer,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        rootStatus = RootStatus.Checking
        lifecycleScope.launch {
            rootStatus = if (Root.isAvailable()) RootStatus.Granted else RootStatus.Denied
        }
    }

    private fun screenRefreshRate(): Float {
        val display = getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY) ?: return 60f
        return display.supportedModes.maxOfOrNull { it.refreshRate } ?: display.refreshRate
    }

    private fun screenSize(): Pair<Int, Int> {
        val bounds = windowManager.maximumWindowMetrics.bounds
        return maxOf(bounds.width(), bounds.height()) to minOf(bounds.width(), bounds.height())
    }

    private fun openViewer() {
        if (DisplaySession.config.value == null) {
            val spec = form.validate().spec ?: return
            Prefs.setSpec(this, spec)
            DisplayService.open(this)
        }
        startActivity(Intent(this, DisplayActivity::class.java))
    }
}

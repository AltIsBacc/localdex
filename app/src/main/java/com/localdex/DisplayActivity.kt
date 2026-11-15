package com.localdex

import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.localdex.root.RootManager
import com.localdex.root.VirtualDisplayFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Hosts a virtual display in a SurfaceView. The SurfaceView's Surface is handed to the root process, which creates
 * the virtual display directly on it ([VirtualDisplayFlow]), so there is no video encoding or decoding anywhere.
 *
 * The display lives exactly as long as the Surface: it is released when the Surface is destroyed.
 */
class DisplayActivity : AppCompatActivity(), SurfaceHolder.Callback {

    private var session: Job? = null

    /** Width, height and dpi of the virtual display, fixed after the first surfaceChanged. */
    private var size: Triple<Int, Int, Int>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_display)
        findViewById<SurfaceView>(R.id.surfaceView).holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) = Unit

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        val (displayWidth, displayHeight, dpi) = size ?: run {
            val spec = DisplaySpec.PATTERN.matchEntire(Prefs.getDisplaySpec(this))
            Triple(
                spec?.groupValues?.get(1)?.toInt() ?: width,
                spec?.groupValues?.get(2)?.toInt() ?: height,
                spec?.groupValues?.get(3)?.toInt() ?: resources.displayMetrics.densityDpi,
            )
        }.also { size = it }
        // The virtual display renders into buffers of exactly this size; wait for the resized surface.
        if (width != displayWidth || height != displayHeight) {
            holder.setFixedSize(displayWidth, displayHeight)
            return
        }
        if (session?.isActive == true) return
        val surface = holder.surface
        session = lifecycleScope.launch {
            try {
                RootManager.use { server ->
                    server.flow(VirtualDisplayFlow(surface, displayWidth, displayHeight, dpi)).collect { ready ->
                        // Virtual displays default to fullscreen windowing on recent One UI; freeform gives windows.
                        Root.exec("settings put global enable_freeform_support 1")
                        Root.exec("wm set-display-windowing-mode -d ${ready.displayId} 5")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val cause = generateSequence<Throwable>(e) { it.cause }.last()
                Toast.makeText(this@DisplayActivity, "Virtual display failed: $cause", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        session?.cancel()
        session = null
    }
}

package com.localdex

import android.content.Context
import android.graphics.PixelFormat
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import android.widget.Toast
import com.localdex.root.DisplayReady
import com.localdex.root.RootManager
import com.localdex.root.VirtualDisplayFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

object DisplaySession {

    data class Config(val width: Int, val height: Int, val dpi: Int, val hz: Float?)

    private const val TAG = "LocalDex"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val configState = MutableStateFlow<Config?>(null)
    private val controlState = MutableStateFlow<DexControl?>(null)

    private var job: Job? = null
    private var sink: Surface? = null
    private var owner: Any? = null
    private var attached: Surface? = null

    val config: StateFlow<Config?> = configState
    val control: StateFlow<DexControl?> = controlState

    fun start(context: Context) {
        if (configState.value != null) return
        val app = context.applicationContext
        val spec = Prefs.getSpec(app)
        val bounds = app.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        val width = spec.width ?: maxOf(bounds.width(), bounds.height())
        val height = spec.height ?: minOf(bounds.width(), bounds.height())
        val config = Config(width, height, spec.dpi ?: DisplaySpec.autoDpi(width), spec.hz)

        val thread = HandlerThread("LocalDexSink").apply { start() }
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2).apply {
            setOnImageAvailableListener({ it.acquireLatestImage()?.close() }, Handler(thread.looper))
        }
        sink = reader.surface
        configState.value = config

        job = scope.launch {
            try {
                RootManager.use { server ->
                    server.flow(VirtualDisplayFlow(reader.surface, width, height, config.dpi, config.hz ?: 0f))
                        .collect { onReady(it, config) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val cause = generateSequence<Throwable>(e) { it.cause }.last()
                Toast.makeText(app, "Virtual display failed: $cause", Toast.LENGTH_LONG).show()
                reset()
            } finally {
                reader.close()
                thread.quitSafely()
            }
        }
    }

    fun stop() {
        job?.cancel()
        reset()
    }

    fun attach(owner: Any, surface: Surface) {
        this.owner = owner
        attached = surface
        controlState.value?.setSurface(surface)
    }

    fun detach(owner: Any) {
        if (this.owner !== owner) return
        this.owner = null
        attached = null
        val sink = sink ?: return
        controlState.value?.setSurface(sink)
    }

    private fun reset() {
        job = null
        sink = null
        owner = null
        attached = null
        controlState.value = null
        configState.value = null
    }

    private suspend fun onReady(ready: DisplayReady, config: Config) {
        val control = DexControl(ready.control.value)
        attached?.let { control.setSurface(it) }
        controlState.value = control

        val app = App.app
        if (!ready.trusted) {
            Toast.makeText(
                app,
                "The system did not mark the display as trusted, so no desktop will appear",
                Toast.LENGTH_LONG
            ).show()
        }
        config.hz?.let { requested ->
            if (abs(ready.refreshRate - requested) > 1f) {
                Toast.makeText(
                    app,
                    "Asked for ${DisplaySpec.formatHz(requested)} Hz, the system gave " +
                        "${DisplaySpec.formatHz(ready.refreshRate)} Hz",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        val commands = listOf(
            "settings put global enable_freeform_support 1",
            // Without this, activities that are not resizable are refused on a secondary display and open on the main one.
            "settings put global force_resizable_activities 1",
            "wm set-display-windowing-mode -d ${ready.displayId} 5",
        )
        val failed = commands.filterNot { command ->
            val result = Root.exec(command)
            if (!result.ok) Log.w(TAG, "'$command' failed (${result.code}): ${result.output}")
            result.ok
        }
        if (failed.isNotEmpty()) {
            Toast.makeText(
                app,
                "${failed.size} display setup command(s) failed; see logcat (tag $TAG)",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

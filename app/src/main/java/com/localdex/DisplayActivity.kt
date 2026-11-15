package com.localdex

import android.annotation.SuppressLint
import android.hardware.input.InputManager
import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.localdex.root.DisplayReady
import com.localdex.root.RootManager
import com.localdex.root.VirtualDisplayFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class DisplayActivity : AppCompatActivity(), SurfaceHolder.Callback {

    private lateinit var root: FrameLayout
    private lateinit var surfaceView: DexSurfaceView
    private lateinit var controls: List<View>

    private val forwarder = InputForwarder()
    private val deviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = updateControls()
        override fun onInputDeviceRemoved(deviceId: Int) = updateControls()
        override fun onInputDeviceChanged(deviceId: Int) = updateControls()
    }
    private var session: Job? = null
    private var size: Triple<Int, Int, Int>? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.attributes = window.attributes.also {
            it.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        setContentView(R.layout.activity_display)

        root = findViewById(R.id.displayRoot)
        surfaceView = findViewById(R.id.surfaceView)
        surfaceView.forwarder = forwarder
        surfaceView.holder.addCallback(this)
        surfaceView.setOnTouchListener { view, event -> forwarder.onTouch(view, event) }
        surfaceView.setOnGenericMotionListener { view, event -> forwarder.onGenericMotion(view, event) }

        val closeButton = findViewById<View>(R.id.closeButton)
        val keyboardButton = findViewById<View>(R.id.keyboardButton)
        controls = listOf(closeButton, keyboardButton)
        makeDraggable(closeButton) { finish() }
        makeDraggable(keyboardButton) { toggleKeyboard() }

        root.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or, ob ->
            if (r - l != or - ol || b - t != ob - ot) fitSurface()
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            view.updatePadding(bottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom)
            fitSurface()
            insets
        }

        enterImmersive()
    }

    override fun onStart() {
        super.onStart()
        getSystemService(InputManager::class.java).registerInputDeviceListener(deviceListener, null)
        updateControls()
    }

    override fun onStop() {
        super.onStop()
        getSystemService(InputManager::class.java).unregisterInputDeviceListener(deviceListener)
    }

    private fun updateControls() {
        val devices = InputDevice.getDeviceIds().mapNotNull { InputDevice.getDevice(it) }.filter { !it.isVirtual }
        val hasKeyboard = devices.any {
            it.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC && it.supportsSource(InputDevice.SOURCE_KEYBOARD)
        }
        val hasMouse = devices.any { it.supportsSource(InputDevice.SOURCE_MOUSE) }
        val visibility = if (hasKeyboard && hasMouse) View.GONE else View.VISIBLE
        controls.forEach { it.visibility = visibility }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersive()
    }

    private fun enterImmersive() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun surfaceCreated(holder: SurfaceHolder) = Unit

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        val (displayWidth, displayHeight, dpi) = size ?: run {
            val spec = DisplaySpec.PATTERN.matchEntire(Prefs.getDisplaySpec(this))
            val screen = windowManager.maximumWindowMetrics.bounds
            Triple(
                spec?.groupValues?.get(1)?.toInt() ?: screen.width(),
                spec?.groupValues?.get(2)?.toInt() ?: screen.height(),
                spec?.groupValues?.get(3)?.toInt() ?: resources.displayMetrics.densityDpi,
            )
        }.also { size = it }
        forwarder.displayWidth = displayWidth
        forwarder.displayHeight = displayHeight
        fitSurface()

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
                    server.flow(VirtualDisplayFlow(surface, displayWidth, displayHeight, dpi)).collect { onReady(it) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val cause = generateSequence<Throwable>(e) { it.cause }.last()
                Toast.makeText(this@DisplayActivity, "Virtual display failed: $cause", Toast.LENGTH_LONG).show()
                finish()
            } finally {
                forwarder.control = null
            }
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        forwarder.control = null
        session?.cancel()
        session = null
    }

    private suspend fun onReady(ready: DisplayReady) {
        forwarder.control = DexControl(ready.control.value)
        if (!ready.trusted) {
            Toast.makeText(
                this,
                "The system did not mark the display as trusted, so no desktop will appear",
                Toast.LENGTH_LONG
            ).show()
        }
        Root.exec("settings put global enable_freeform_support 1")
        Root.exec("wm set-display-windowing-mode -d ${ready.displayId} 5")
    }

    private fun fitSurface() {
        val (displayWidth, displayHeight, _) = size ?: return
        root.post {
            val containerWidth = root.width - root.paddingLeft - root.paddingRight
            val containerHeight = root.height - root.paddingTop - root.paddingBottom
            if (containerWidth <= 0 || containerHeight <= 0) return@post

            val scale = minOf(
                containerWidth.toFloat() / displayWidth,
                containerHeight.toFloat() / displayHeight
            )
            val width = (displayWidth * scale).toInt()
            val height = (displayHeight * scale).toInt()
            val params = surfaceView.layoutParams as FrameLayout.LayoutParams
            if (params.width == width && params.height == height) return@post
            params.width = width
            params.height = height
            params.gravity = Gravity.CENTER
            surfaceView.layoutParams = params
        }
    }

    private fun toggleKeyboard() {
        val controller = WindowCompat.getInsetsController(window, surfaceView)
        surfaceView.requestFocus()
        val visible = ViewCompat.getRootWindowInsets(root)?.isVisible(WindowInsetsCompat.Type.ime()) == true
        if (visible) {
            controller.hide(WindowInsetsCompat.Type.ime())
        } else {
            controller.show(WindowInsetsCompat.Type.ime())
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (forwarder.onKey(event)) return true
        return super.dispatchKeyEvent(event)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun makeDraggable(button: View, onTap: () -> Unit) {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f
        var startTx = 0f
        var startTy = 0f
        var dragging = false
        val startDrag = Runnable {
            dragging = true
            button.alpha = 0.9f
            button.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }

        button.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startTx = view.translationX
                    startTy = view.translationY
                    dragging = false
                    view.postDelayed(startDrag, DRAG_HOLD_MS)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (dragging) {
                        view.translationX = startTx + dx
                        view.translationY = startTy + dy
                    } else if (dx * dx + dy * dy > slop * slop) {
                        view.removeCallbacks(startDrag)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    view.removeCallbacks(startDrag)
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (dragging) {
                        dragging = false
                        view.alpha = 0.5f
                    } else if (dx * dx + dy * dy <= slop * slop) {
                        onTap()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    view.removeCallbacks(startDrag)
                    if (dragging) {
                        dragging = false
                        view.alpha = 0.5f
                    }
                    true
                }
                else -> false
            }
        }
    }

    private companion object {
        const val DRAG_HOLD_MS = 350L
    }
}

package com.localdex

import android.annotation.SuppressLint
import android.hardware.input.InputManager
import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
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
    private var config: DisplaySession.Config? = null
    private var attached = false
    private var inputMode = InputMode.AUTO
    private var touchApps = emptySet<String>()
    private lateinit var touchButton: TextView

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
        makeDraggable(closeButton) { DisplaySession.stop() }
        makeDraggable(keyboardButton) { toggleKeyboard() }
        touchButton = findViewById(R.id.touchButton)
        inputMode = Prefs.getInputMode(this)
        touchApps = Prefs.getTouchApps(this)
        makeDraggable(touchButton) { cycleInputMode() }
        applyInputMode()

        lifecycleScope.launch {
            DisplaySession.config.collect { if (it == null) finish() else applyConfig(it) }
        }
        lifecycleScope.launch {
            DisplaySession.focus.collect { applyInputMode() }
        }
        lifecycleScope.launch {
            DisplaySession.control.collect { forwarder.control = it }
        }

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
        val devices = InputDevice.getDeviceIds().toList().mapNotNull { InputDevice.getDevice(it) }.filter { !it.isVirtual }
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
        val config = config ?: return
        if (width != config.width || height != config.height) {
            holder.setFixedSize(config.width, config.height)
            return
        }
        config.hz?.let { holder.surface.setFrameRate(it, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT) }
        if (attached) return
        attached = true
        DisplaySession.attach(this, holder.surface)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        attached = false
        DisplaySession.detach(this)
    }

    override fun onDestroy() {
        DisplaySession.detach(this)
        super.onDestroy()
    }

    private fun applyConfig(config: DisplaySession.Config) {
        this.config = config
        forwarder.displayWidth = config.width
        forwarder.displayHeight = config.height
        surfaceView.holder.setFixedSize(config.width, config.height)
        fitSurface()
    }

    private fun fitSurface() {
        val config = config ?: return
        val displayWidth = config.width
        val displayHeight = config.height
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

    private fun applyInputMode() {
        val focus = DisplaySession.focus.value
        forwarder.touchMode = when (inputMode) {
            InputMode.TOUCH -> true
            InputMode.MOUSE -> false
            InputMode.AUTO -> focus != null && (focus.isGame || focus.packageName in touchApps)
        }
        touchButton.text = when (inputMode) {
            InputMode.AUTO -> "A"
            InputMode.TOUCH -> "👆"
            InputMode.MOUSE -> "🖱"
        }
    }

    private fun cycleInputMode() {
        inputMode = when (inputMode) {
            InputMode.AUTO -> InputMode.TOUCH
            InputMode.TOUCH -> InputMode.MOUSE
            InputMode.MOUSE -> InputMode.AUTO
        }
        Prefs.setInputMode(this, inputMode)
        var message = when (inputMode) {
            InputMode.AUTO -> "Auto: touch for games, mouse for everything else"
            InputMode.TOUCH -> "Touch: fingers are always sent as touch"
            InputMode.MOUSE -> "Mouse: one finger is always a mouse"
        }
        val focus = DisplaySession.focus.value
        if (focus != null && inputMode != InputMode.AUTO) {
            val touch = inputMode == InputMode.TOUCH
            Prefs.setTouchApp(this, focus.packageName, touch)
            touchApps = Prefs.getTouchApps(this)
            message += "\n${focus.packageName} " + if (touch) "added to the touch list" else "removed from the touch list"
        }
        applyInputMode()
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
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

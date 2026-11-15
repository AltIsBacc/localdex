package com.localdex.root

import android.content.Context
import android.hardware.input.InputManager
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import be.mygod.librootkotlinx.systemContext
import java.lang.reflect.Method

internal class InputInjector(private val displayId: Int) {

    private val manager = systemContext.getSystemService(Context.INPUT_SERVICE) as InputManager
    private val injectInputEvent: Method = InputManager::class.java
        .getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)
    private val setDisplayId: Method = InputEvent::class.java
        .getMethod("setDisplayId", Int::class.javaPrimitiveType)
    private val setActionButton: Method = MotionEvent::class.java
        .getMethod("setActionButton", Int::class.javaPrimitiveType)

    private val properties = arrayOf(
        MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_MOUSE
        }
    )
    private val coords = arrayOf(MotionEvent.PointerCoords())
    private val touchProperties = Array(MAX_TOUCH_POINTERS) { MotionEvent.PointerProperties() }
    private val touchCoords = Array(MAX_TOUCH_POINTERS) { MotionEvent.PointerCoords() }
    private var touchDownTime = SystemClock.uptimeMillis()

    private var downTime = SystemClock.uptimeMillis()
    private var lastErrorLog = 0L

    @Synchronized
    fun pointer(kind: Int, x: Float, y: Float, buttons: Int, actionButton: Int) {
        when (kind) {
            Protocol.POINTER_HOVER -> motion(MotionEvent.ACTION_HOVER_MOVE, x, y, buttons)
            Protocol.POINTER_MOVE -> motion(MotionEvent.ACTION_MOVE, x, y, buttons)
            Protocol.POINTER_PRESS -> {
                if (actionButton == buttons) {
                    downTime = SystemClock.uptimeMillis()
                    motion(MotionEvent.ACTION_DOWN, x, y, buttons)
                }
                motion(MotionEvent.ACTION_BUTTON_PRESS, x, y, buttons, actionButton)
            }
            Protocol.POINTER_RELEASE -> {
                motion(MotionEvent.ACTION_BUTTON_RELEASE, x, y, buttons, actionButton)
                if (buttons == 0) motion(MotionEvent.ACTION_UP, x, y, 0)
            }
        }
    }

    @Synchronized
    fun scroll(x: Float, y: Float, hScroll: Float, vScroll: Float, buttons: Int) {
        motion(MotionEvent.ACTION_SCROLL, x, y, buttons, 0, hScroll, vScroll)
    }

    @Synchronized
    fun touch(action: Int, count: Int, ids: IntArray, xs: FloatArray, ys: FloatArray) {
        val pointers = minOf(count, MAX_TOUCH_POINTERS)
        val now = SystemClock.uptimeMillis()
        if ((action and MotionEvent.ACTION_MASK) == MotionEvent.ACTION_DOWN) touchDownTime = now
        for (i in 0 until pointers) {
            touchProperties[i].id = ids[i]
            touchProperties[i].toolType = MotionEvent.TOOL_TYPE_FINGER
            touchCoords[i].clear()
            touchCoords[i].x = xs[i]
            touchCoords[i].y = ys[i]
            touchCoords[i].pressure = 1f
            touchCoords[i].size = 1f
        }
        val event = MotionEvent.obtain(
            touchDownTime, now, action, pointers, touchProperties, touchCoords, 0, 0, 1f, 1f,
            0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0
        )
        try {
            inject(event)
        } finally {
            event.recycle()
        }
    }

    @Synchronized
    fun key(action: Int, keyCode: Int, repeat: Int, metaState: Int, scanCode: Int) {
        val now = SystemClock.uptimeMillis()
        inject(
            KeyEvent(
                now, now, action, keyCode, repeat, metaState,
                KeyCharacterMap.VIRTUAL_KEYBOARD, scanCode, 0, InputDevice.SOURCE_KEYBOARD
            )
        )
    }

    private fun motion(
        action: Int,
        x: Float,
        y: Float,
        buttons: Int,
        actionButton: Int = 0,
        hScroll: Float = 0f,
        vScroll: Float = 0f,
    ) {
        val now = SystemClock.uptimeMillis()
        val pointer = coords[0]
        pointer.clear()
        pointer.x = x
        pointer.y = y
        pointer.pressure = if (buttons != 0) 1f else 0f
        pointer.size = 1f
        if (action == MotionEvent.ACTION_SCROLL) {
            pointer.setAxisValue(MotionEvent.AXIS_HSCROLL, hScroll)
            pointer.setAxisValue(MotionEvent.AXIS_VSCROLL, vScroll)
        }

        val event = MotionEvent.obtain(
            downTime, now, action, 1, properties, coords, 0, buttons, 1f, 1f,
            0, 0, InputDevice.SOURCE_MOUSE, 0
        )
        try {
            if (actionButton != 0) setActionButton.invoke(event, actionButton)
            inject(event)
        } catch (e: ReflectiveOperationException) {
            logFailure(e)
        } finally {
            event.recycle()
        }
    }

    private fun inject(event: InputEvent) {
        try {
            if (displayId != 0) setDisplayId.invoke(event, displayId)
            injectInputEvent.invoke(manager, event, INJECT_MODE_ASYNC)
        } catch (e: ReflectiveOperationException) {
            logFailure(e)
        }
    }

    private fun logFailure(e: Exception) {
        val now = SystemClock.uptimeMillis()
        if (now - lastErrorLog > 3000) {
            lastErrorLog = now
            Log.w(TAG, "Input injection failed", e)
        }
    }

    private companion object {
        const val TAG = "LocalDex"
        const val INJECT_MODE_ASYNC = 0
        const val MAX_TOUCH_POINTERS = 10
    }
}

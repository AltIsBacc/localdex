package com.localdex

import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import com.localdex.root.Protocol

class InputForwarder {

    var control: DexControl? = null
    var displayWidth = 0
    var displayHeight = 0

    private enum class Gesture { NONE, POINTER, SCROLL, DONE }

    private var gesture = Gesture.NONE
    private var lastX = 0f
    private var lastY = 0f
    private var mouseButtons = 0
    private var navButtons = 0

    fun onTouch(view: View, event: MotionEvent): Boolean {
        val control = control ?: return false
        if (!ready(view)) return false
        if (event.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE) {
            mouseTouch(view, event, control)
        } else {
            fingerTouch(view, event, control)
        }
        return true
    }

    fun onGenericMotion(view: View, event: MotionEvent): Boolean {
        val control = control ?: return false
        if (!ready(view) || event.getToolType(0) != MotionEvent.TOOL_TYPE_MOUSE) return false
        val x = mapX(view, event.x)
        val y = mapY(view, event.y)
        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> {
                syncButtons(control, x, y, event.buttonState)
                control.pointer(Protocol.POINTER_HOVER, x, y, mouseButtons, 0)
            }
            MotionEvent.ACTION_BUTTON_PRESS, MotionEvent.ACTION_BUTTON_RELEASE ->
                syncButtons(control, x, y, event.buttonState)
            MotionEvent.ACTION_SCROLL -> control.scroll(
                x, y,
                event.getAxisValue(MotionEvent.AXIS_HSCROLL),
                event.getAxisValue(MotionEvent.AXIS_VSCROLL),
                mouseButtons
            )
            else -> return false
        }
        return true
    }

    fun onKey(event: KeyEvent): Boolean {
        val control = control ?: return false
        if (event.keyCode in SYSTEM_KEYS || event.action == KeyEvent.ACTION_MULTIPLE) return false
        control.key(event.action, event.keyCode, event.repeatCount, event.metaState, event.scanCode)
        return true
    }

    fun pressKey(keyCode: Int) {
        val control = control ?: return
        control.key(KeyEvent.ACTION_DOWN, keyCode, 0, 0, 0)
        control.key(KeyEvent.ACTION_UP, keyCode, 0, 0, 0)
    }

    fun typeText(text: CharSequence) {
        val control = control ?: return
        val map = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
        for (char in text) {
            val events = map.getEvents(charArrayOf(char)) ?: continue
            for (event in events) {
                control.key(event.action, event.keyCode, 0, event.metaState, 0)
            }
        }
    }

    private fun ready(view: View) = view.width > 0 && view.height > 0 && displayWidth > 0 && displayHeight > 0

    private fun mapX(view: View, x: Float) = (x * displayWidth / view.width).coerceIn(0f, displayWidth - 1f)

    private fun mapY(view: View, y: Float) = (y * displayHeight / view.height).coerceIn(0f, displayHeight - 1f)

    private fun mouseTouch(view: View, event: MotionEvent, control: DexControl) {
        val x = mapX(view, event.x)
        val y = mapY(view, event.y)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                control.pointer(Protocol.POINTER_HOVER, x, y, 0, 0)
                val state = if (event.buttonState == 0) MotionEvent.BUTTON_PRIMARY else event.buttonState
                syncButtons(control, x, y, state)
            }
            MotionEvent.ACTION_MOVE -> {
                syncButtons(control, x, y, event.buttonState)
                val kind = if (mouseButtons != 0) Protocol.POINTER_MOVE else Protocol.POINTER_HOVER
                control.pointer(kind, x, y, mouseButtons, 0)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> syncButtons(control, x, y, 0)
        }
    }

    private fun syncButtons(control: DexControl, x: Float, y: Float, buttonState: Int) {
        val state = buttonState and MOUSE_BUTTONS
        val pressed = state and mouseButtons.inv()
        val released = mouseButtons and state.inv()
        var cumulative = mouseButtons
        for (bit in BUTTON_BITS) {
            if ((pressed and bit) != 0) {
                cumulative = cumulative or bit
                control.pointer(Protocol.POINTER_PRESS, x, y, cumulative, bit)
            }
        }
        for (bit in BUTTON_BITS) {
            if ((released and bit) != 0) {
                cumulative = cumulative and bit.inv()
                control.pointer(Protocol.POINTER_RELEASE, x, y, cumulative, bit)
            }
        }
        mouseButtons = state
        syncNavigationButtons(control, buttonState)
    }

    private fun syncNavigationButtons(control: DexControl, buttonState: Int) {
        val nav = buttonState and (MotionEvent.BUTTON_BACK or MotionEvent.BUTTON_FORWARD)
        val changed = nav xor navButtons
        if (changed == 0) return
        for ((button, keyCode) in NAVIGATION_KEYS) {
            if ((changed and button) != 0) {
                val action = if ((nav and button) != 0) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP
                control.key(action, keyCode, 0, 0, 0)
            }
        }
        navButtons = nav
    }

    private fun fingerTouch(view: View, event: MotionEvent, control: DexControl) {
        fun x(index: Int) = mapX(view, event.getX(index))
        fun y(index: Int) = mapY(view, event.getY(index))
        fun centroidX() = (0 until event.pointerCount).sumOf { x(it).toDouble() }.toFloat() / event.pointerCount
        fun centroidY() = (0 until event.pointerCount).sumOf { y(it).toDouble() }.toFloat() / event.pointerCount

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gesture = Gesture.POINTER
                lastX = x(0)
                lastY = y(0)
                control.pointer(Protocol.POINTER_HOVER, lastX, lastY, 0, 0)
                control.pointer(Protocol.POINTER_PRESS, lastX, lastY, MotionEvent.BUTTON_PRIMARY, MotionEvent.BUTTON_PRIMARY)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (gesture == Gesture.POINTER) {
                    control.pointer(Protocol.POINTER_RELEASE, lastX, lastY, 0, MotionEvent.BUTTON_PRIMARY)
                    gesture = Gesture.SCROLL
                    lastX = centroidX()
                    lastY = centroidY()
                }
            }
            MotionEvent.ACTION_MOVE -> when (gesture) {
                Gesture.POINTER -> {
                    lastX = x(0)
                    lastY = y(0)
                    control.pointer(Protocol.POINTER_MOVE, lastX, lastY, MotionEvent.BUTTON_PRIMARY, 0)
                }
                Gesture.SCROLL -> {
                    val cx = centroidX()
                    val cy = centroidY()
                    val dx = cx - lastX
                    val dy = cy - lastY
                    if (dx != 0f || dy != 0f) {
                        control.scroll(cx, cy, dx / SCROLL_PX_PER_TICK, dy / SCROLL_PX_PER_TICK, 0)
                        lastX = cx
                        lastY = cy
                    }
                }
                else -> Unit
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (gesture == Gesture.SCROLL && event.pointerCount <= 2) gesture = Gesture.DONE
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (gesture == Gesture.POINTER) {
                    control.pointer(Protocol.POINTER_RELEASE, x(0), y(0), 0, MotionEvent.BUTTON_PRIMARY)
                }
                gesture = Gesture.NONE
            }
        }
    }

    private companion object {
        const val SCROLL_PX_PER_TICK = 64f

        const val MOUSE_BUTTONS = MotionEvent.BUTTON_PRIMARY or
            MotionEvent.BUTTON_SECONDARY or
            MotionEvent.BUTTON_TERTIARY

        val BUTTON_BITS = intArrayOf(
            MotionEvent.BUTTON_PRIMARY,
            MotionEvent.BUTTON_SECONDARY,
            MotionEvent.BUTTON_TERTIARY,
        )

        val NAVIGATION_KEYS = listOf(
            MotionEvent.BUTTON_BACK to KeyEvent.KEYCODE_BACK,
            MotionEvent.BUTTON_FORWARD to KeyEvent.KEYCODE_FORWARD,
        )

        val SYSTEM_KEYS = setOf(
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE,
            KeyEvent.KEYCODE_POWER,
            KeyEvent.KEYCODE_SLEEP,
            KeyEvent.KEYCODE_WAKEUP,
            KeyEvent.KEYCODE_BRIGHTNESS_UP,
            KeyEvent.KEYCODE_BRIGHTNESS_DOWN,
        )
    }
}

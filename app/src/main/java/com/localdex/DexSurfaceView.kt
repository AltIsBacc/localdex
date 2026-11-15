package com.localdex

import android.content.Context
import android.text.InputType
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.SurfaceView
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection

class DexSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : SurfaceView(context, attrs) {

    var forwarder: InputForwarder? = null

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onCheckIsTextEditor() = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_NULL
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_FLAG_NO_EXTRACT_UI

        return object : BaseInputConnection(this@DexSurfaceView, false) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                if (text != null) forwarder?.typeText(text)
                return true
            }

            override fun setComposingText(text: CharSequence?, newCursorPosition: Int) = true

            override fun sendKeyEvent(event: KeyEvent?): Boolean {
                if (event != null) forwarder?.onKey(event)
                return true
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                repeat(beforeLength) { forwarder?.pressKey(KeyEvent.KEYCODE_DEL) }
                repeat(afterLength) { forwarder?.pressKey(KeyEvent.KEYCODE_FORWARD_DEL) }
                return true
            }

            override fun performEditorAction(editorAction: Int): Boolean {
                forwarder?.pressKey(KeyEvent.KEYCODE_ENTER)
                return true
            }
        }
    }
}

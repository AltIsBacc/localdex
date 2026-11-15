package com.localdex.root

import android.os.Binder
import android.os.Parcel
import android.util.Log
import android.view.Surface

internal class ControlBinder(
    private val injector: InputInjector,
    private val onSurface: (Surface) -> Unit,
) : Binder() {

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        try {
            when (code) {
                Protocol.TX_POINTER -> {
                    val kind = data.readInt()
                    val x = data.readFloat()
                    val y = data.readFloat()
                    val buttons = data.readInt()
                    val actionButton = data.readInt()
                    injector.pointer(kind, x, y, buttons, actionButton)
                }
                Protocol.TX_SCROLL -> {
                    val x = data.readFloat()
                    val y = data.readFloat()
                    val hScroll = data.readFloat()
                    val vScroll = data.readFloat()
                    val buttons = data.readInt()
                    injector.scroll(x, y, hScroll, vScroll, buttons)
                }
                Protocol.TX_KEY -> {
                    val action = data.readInt()
                    val keyCode = data.readInt()
                    val repeat = data.readInt()
                    val metaState = data.readInt()
                    val scanCode = data.readInt()
                    injector.key(action, keyCode, repeat, metaState, scanCode)
                }
                Protocol.TX_SURFACE -> {
                    onSurface(Surface.CREATOR.createFromParcel(data))
                    reply?.writeInt(1)
                }
                else -> return super.onTransact(code, data, reply, flags)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Transaction $code failed", e)
        }
        return true
    }

    private companion object {
        const val TAG = "LocalDex"
    }
}

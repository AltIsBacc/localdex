package com.localdex

import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import android.view.Surface
import com.localdex.root.Protocol

class DexControl(private val binder: IBinder) {

    fun pointer(kind: Int, x: Float, y: Float, buttons: Int, actionButton: Int) =
        send(Protocol.TX_POINTER) {
            it.writeInt(kind)
            it.writeFloat(x)
            it.writeFloat(y)
            it.writeInt(buttons)
            it.writeInt(actionButton)
        }

    fun scroll(x: Float, y: Float, hScroll: Float, vScroll: Float, buttons: Int) =
        send(Protocol.TX_SCROLL) {
            it.writeFloat(x)
            it.writeFloat(y)
            it.writeFloat(hScroll)
            it.writeFloat(vScroll)
            it.writeInt(buttons)
        }

    fun key(action: Int, keyCode: Int, repeat: Int, metaState: Int, scanCode: Int) =
        send(Protocol.TX_KEY) {
            it.writeInt(action)
            it.writeInt(keyCode)
            it.writeInt(repeat)
            it.writeInt(metaState)
            it.writeInt(scanCode)
        }

    fun touch(action: Int, count: Int, ids: IntArray, xs: FloatArray, ys: FloatArray) =
        send(Protocol.TX_TOUCH) {
            it.writeInt(action)
            it.writeInt(count)
            for (i in 0 until count) {
                it.writeInt(ids[i])
                it.writeFloat(xs[i])
                it.writeFloat(ys[i])
            }
        }

    fun setSurface(surface: Surface): Boolean {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            surface.writeToParcel(data, 0)
            binder.transact(Protocol.TX_SURFACE, data, reply, 0)
            reply.readInt() == 1
        } catch (e: RemoteException) {
            false
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private inline fun send(code: Int, write: (Parcel) -> Unit) {
        val data = Parcel.obtain()
        try {
            write(data)
            binder.transact(code, data, null, IBinder.FLAG_ONEWAY)
        } catch (e: RemoteException) {
            return
        } finally {
            data.recycle()
        }
    }
}

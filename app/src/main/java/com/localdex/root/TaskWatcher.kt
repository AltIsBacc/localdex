package com.localdex.root

import android.app.ActivityManager
import android.content.pm.ApplicationInfo
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import android.util.Log
import be.mygod.librootkotlinx.systemContext
import java.lang.reflect.Field

internal class TaskWatcher(private val displayId: Int) {

    private val packageManager = systemContext.packageManager
    private val displayIdField: Field? = runCatching {
        ActivityManager.RunningTaskInfo::class.java.getField("displayId")
    }.getOrNull()
    private val movedToFront = stubCode(LISTENER_STUB, "TRANSACTION_onTaskMovedToFront")

    private var registered = false

    @Volatile
    private var listener: IBinder? = null

    private val receiver = object : Binder() {
        init {
            attachInterface(null, LISTENER_DESCRIPTOR)
        }

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == movedToFront) {
                try {
                    onMovedToFront(data)
                } catch (e: Throwable) {
                    Log.w(TAG, "Could not read the task that moved to front", e)
                }
                return true
            }
            if (code in IBinder.FIRST_CALL_TRANSACTION..IBinder.LAST_CALL_TRANSACTION) return true
            return super.onTransact(code, data, reply, flags)
        }
    }

    @Synchronized
    fun listen(binder: IBinder) {
        listener = binder
        if (!registered) registered = callTaskManager("registerTaskStackListener")
    }

    @Synchronized
    fun stop() {
        listener = null
        if (registered) callTaskManager("unregisterTaskStackListener")
        registered = false
    }

    private fun onMovedToFront(data: Parcel) {
        data.enforceInterface(LISTENER_DESCRIPTOR)
        if (data.readInt() == 0) return
        val task = ActivityManager.RunningTaskInfo.CREATOR.createFromParcel(data)
        if (displayIdField?.getInt(task) != displayId) return
        val packageName = (task.topActivity ?: task.baseActivity)?.packageName ?: return
        val target = listener ?: return
        val out = Parcel.obtain()
        try {
            out.writeString(packageName)
            out.writeInt(if (isGame(packageName)) 1 else 0)
            target.transact(Protocol.CB_FOCUS, out, null, IBinder.FLAG_ONEWAY)
        } catch (e: RemoteException) {
            listener = null
        } finally {
            out.recycle()
        }
    }

    @Suppress("DEPRECATION")
    private fun isGame(packageName: String): Boolean = try {
        val info = packageManager.getApplicationInfo(packageName, 0)
        info.category == ApplicationInfo.CATEGORY_GAME || (info.flags and ApplicationInfo.FLAG_IS_GAME) != 0
    } catch (e: Exception) {
        false
    }

    private fun callTaskManager(method: String): Boolean {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            val service = Class.forName("android.os.ServiceManager")
                .getMethod("getService", String::class.java)
                .invoke(null, "activity_task") as IBinder
            data.writeInterfaceToken(TASK_MANAGER_DESCRIPTOR)
            data.writeStrongBinder(receiver)
            service.transact(stubCode(TASK_MANAGER_STUB, "TRANSACTION_$method"), data, reply, 0)
            reply.readException()
            true
        } catch (e: Exception) {
            Log.w(TAG, "$method failed, apps on the display will not be detected", e)
            false
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun stubCode(className: String, field: String): Int = try {
        Class.forName(className).getDeclaredField(field).apply { isAccessible = true }.getInt(null)
    } catch (e: Exception) {
        Log.w(TAG, "Could not find $field", e)
        -1
    }

    private companion object {
        const val TAG = "LocalDex"
        const val LISTENER_DESCRIPTOR = "android.app.ITaskStackListener"
        const val LISTENER_STUB = "android.app.ITaskStackListener\$Stub"
        const val TASK_MANAGER_DESCRIPTOR = "android.app.IActivityTaskManager"
        const val TASK_MANAGER_STUB = "android.app.IActivityTaskManager\$Stub"
    }
}

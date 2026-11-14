package com.localdex.xposed

import com.localdex.DexMode
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.reflect.Method

class DexHook : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        when (lpparam.packageName) {
            "android" -> {
                hookWindowingMode(lpparam.classLoader)
                hookEligibility(lpparam.classLoader, SYSTEM_SERVER_TARGETS)
            }
            "com.android.systemui" -> hookEligibility(lpparam.classLoader, SYSTEMUI_TARGETS)
        }
    }

    private fun hookWindowingMode(loader: ClassLoader) {
        val clazz = XposedHelpers.findClassIfExists(DISPLAY_WINDOW_SETTINGS, loader)
        if (clazz == null) {
            XposedBridge.log("$TAG: $DISPLAY_WINDOW_SETTINGS not found")
            return
        }
        val hooks = XposedBridge.hookAllMethods(clazz, "getWindowingModeLocked", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (!isActive()) return
                val display = param.args.firstOrNull { it?.javaClass?.name == DISPLAY_CONTENT } ?: return
                val displayId = XposedHelpers.callMethod(display, "getDisplayId") as? Int
                if (displayId == DEFAULT_DISPLAY) param.result = WINDOWING_MODE_FREEFORM
            }
        })
        XposedBridge.log("$TAG: hooked ${hooks.size} getWindowingModeLocked overload(s)")
    }

    private fun hookEligibility(loader: ClassLoader, targets: Map<String, List<String>>) {
        for ((className, methodNames) in targets) {
            val clazz = XposedHelpers.findClassIfExists(className, loader)
            if (clazz == null) {
                XposedBridge.log("$TAG: $className not found")
                continue
            }
            for (name in methodNames) {
                val hooks = XposedBridge.hookAllMethods(clazz, name, forceTrue)
                XposedBridge.log("$TAG: hooked ${hooks.size} x $className.$name")
            }
        }
    }

    private val forceTrue = object : XC_MethodHook() {
        override fun beforeHookedMethod(param: MethodHookParam) {
            val method = param.method as? Method ?: return
            if (method.returnType == Boolean::class.javaPrimitiveType && isActive()) {
                param.result = true
            }
        }
    }

    private val getProperty: Method? by lazy {
        runCatching {
            Class.forName("android.os.SystemProperties")
                .getMethod("get", String::class.java, String::class.java)
        }.getOrNull()
    }

    private fun isActive(): Boolean =
        runCatching { getProperty?.invoke(null, DexMode.PROP, "0") == "1" }.getOrDefault(false)

    private companion object {
        const val TAG = "LocalDex"
        const val DEFAULT_DISPLAY = 0
        const val WINDOWING_MODE_FREEFORM = 5
        const val DISPLAY_WINDOW_SETTINGS = "com.android.server.wm.DisplayWindowSettings"
        const val DISPLAY_CONTENT = "com.android.server.wm.DisplayContent"

        val ELIGIBILITY_METHODS = listOf(
            "canEnterDesktopMode",
            "isDeviceEligibleForDesktopMode",
            "canInternalDisplayHostDesktops",
        )

        val SYSTEM_SERVER_TARGETS = mapOf(
            "com.android.server.wm.DesktopModeHelper" to ELIGIBILITY_METHODS,
        )

        val SYSTEMUI_TARGETS = mapOf(
            "com.android.wm.shell.shared.desktopmode.DesktopModeStatus" to ELIGIBILITY_METHODS,
            "com.android.wm.shell.desktopmode.DesktopModeStatus" to ELIGIBILITY_METHODS,
        )
    }
}

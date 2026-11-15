package com.localdex

import android.content.Context
import org.json.JSONObject
import java.io.IOException

object DexMode {

    const val PROP = "debug.localdex.active"

    private const val DISPLAY_ID = 0
    private const val FREEFORM = 5
    private const val UNDEFINED = 0
    private const val FULLSCREEN = 1
    private const val FREEFORM_SETTING = "enable_freeform_support"

    private val SIZE_PATTERN = Regex("Override size:\\s*(\\d+x\\d+)")
    private val DENSITY_PATTERN = Regex("Override density:\\s*(\\d+)")
    private val NUMERIC = Regex("\\d+")

    fun isActive(context: Context): Boolean = Prefs.isActive(context)

    suspend fun enable(context: Context, spec: String): Result<Unit> {
        if (Prefs.isActive(context)) return Result.success(Unit)

        val match = DisplaySpec.PATTERN.matchEntire(spec)
        if (spec.isNotEmpty() && match == null) {
            return Result.failure(IllegalArgumentException("Display spec must look like 1920x1080/240"))
        }

        Prefs.setSnapshot(context, takeSnapshot(changesDisplay = match != null).toString())
        Prefs.setActive(context, true)

        val steps = buildList {
            add("settings put global $FREEFORM_SETTING 1")
            add("setprop $PROP 1")
            if (match != null) {
                add("wm size ${match.groupValues[1]}x${match.groupValues[2]}")
                add("wm density ${match.groupValues[3]}")
            }
            add("wm set-display-windowing-mode -d $DISPLAY_ID $FREEFORM")
        }

        for (step in steps) {
            val result = Root.exec(step)
            if (!result.ok) {
                disable(context)
                return Result.failure(IOException("\"$step\" failed: ${result.output}"))
            }
        }
        return Result.success(Unit)
    }

    suspend fun disable(context: Context): Result<Unit> {
        val snapshot = Prefs.getSnapshot(context)?.let { runCatching { JSONObject(it) }.getOrNull() }
        var failure: String? = null

        suspend fun run(step: String): Boolean {
            val result = Root.exec(step)
            if (!result.ok && failure == null) failure = "\"$step\" failed: ${result.output}"
            return result.ok
        }

        if (!run("wm set-display-windowing-mode -d $DISPLAY_ID $UNDEFINED")) {
            failure = null
            run("wm set-display-windowing-mode -d $DISPLAY_ID $FULLSCREEN")
        }

        if (snapshot != null) {
            if (snapshot.optBoolean("display")) {
                run(if (snapshot.isNull("size")) "wm size reset" else "wm size ${snapshot.getString("size")}")
                run(
                    if (snapshot.isNull("density")) "wm density reset"
                    else "wm density ${snapshot.getString("density")}"
                )
            }
            val freeform = if (snapshot.isNull("freeform")) null else snapshot.getString("freeform")
            run(
                if (freeform != null && NUMERIC.matches(freeform)) "settings put global $FREEFORM_SETTING $freeform"
                else "settings delete global $FREEFORM_SETTING"
            )
        }

        run("setprop $PROP 0")

        failure?.let { return Result.failure(IOException(it)) }

        Prefs.setActive(context, false)
        Prefs.setSnapshot(context, null)
        return Result.success(Unit)
    }

    suspend fun reassert(context: Context) {
        if (Prefs.isActive(context)) Root.exec("setprop $PROP 1")
    }

    private suspend fun takeSnapshot(changesDisplay: Boolean): JSONObject {
        val freeform = Root.exec("settings get global $FREEFORM_SETTING")
        val size = SIZE_PATTERN.find(Root.exec("wm size").output)?.groupValues?.get(1)
        val density = DENSITY_PATTERN.find(Root.exec("wm density").output)?.groupValues?.get(1)
        val freeformValue = freeform.output.takeIf { freeform.ok && it != "null" && it.isNotEmpty() }
        return JSONObject()
            .put("freeform", freeformValue ?: JSONObject.NULL)
            .put("size", size ?: JSONObject.NULL)
            .put("density", density ?: JSONObject.NULL)
            .put("display", changesDisplay)
    }
}

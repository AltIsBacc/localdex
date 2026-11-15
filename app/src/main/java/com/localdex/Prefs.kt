package com.localdex

import android.content.Context

object Prefs {
    private const val PREFS_NAME = "localdex_prefs"
    private const val KEY_LEGACY_SPEC = "display_spec"
    private const val KEY_WIDTH = "display_width"
    private const val KEY_HEIGHT = "display_height"
    private const val KEY_DPI = "display_dpi"
    private const val KEY_HZ = "display_hz"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Zero means automatic
    fun getSpec(context: Context): DisplaySpec {
        val prefs = prefs(context)
        if (!prefs.contains(KEY_WIDTH) && !prefs.contains(KEY_DPI) && !prefs.contains(KEY_HZ)) {
            prefs.getString(KEY_LEGACY_SPEC, null)?.let { DisplaySpec.parse(it) }?.let { return it }
        }
        return DisplaySpec(
            width = prefs.getInt(KEY_WIDTH, 0).takeIf { it > 0 },
            height = prefs.getInt(KEY_HEIGHT, 0).takeIf { it > 0 },
            dpi = prefs.getInt(KEY_DPI, 0).takeIf { it > 0 },
            hz = prefs.getFloat(KEY_HZ, 0f).takeIf { it > 0f },
        )
    }

    fun setSpec(context: Context, spec: DisplaySpec) {
        prefs(context).edit()
            .putInt(KEY_WIDTH, spec.width ?: 0)
            .putInt(KEY_HEIGHT, spec.height ?: 0)
            .putInt(KEY_DPI, spec.dpi ?: 0)
            .putFloat(KEY_HZ, spec.hz ?: 0f)
            .remove(KEY_LEGACY_SPEC)
            .apply()
    }
}

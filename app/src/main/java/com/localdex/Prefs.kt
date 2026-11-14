package com.localdex

import android.content.Context

object Prefs {
    private const val PREFS_NAME = "localdex_prefs"
    private const val KEY_DISPLAY_SPEC = "display_spec"
    private const val KEY_ACTIVE = "dex_active"
    private const val KEY_SNAPSHOT = "dex_snapshot"

    const val DEFAULT_DISPLAY_SPEC = ""

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getDisplaySpec(context: Context): String =
        prefs(context).getString(KEY_DISPLAY_SPEC, DEFAULT_DISPLAY_SPEC) ?: DEFAULT_DISPLAY_SPEC

    fun setDisplaySpec(context: Context, spec: String) {
        prefs(context).edit().putString(KEY_DISPLAY_SPEC, spec).apply()
    }

    fun isActive(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ACTIVE, false)

    fun setActive(context: Context, active: Boolean) {
        prefs(context).edit().putBoolean(KEY_ACTIVE, active).commit()
    }

    fun getSnapshot(context: Context): String? =
        prefs(context).getString(KEY_SNAPSHOT, null)

    fun setSnapshot(context: Context, snapshot: String?) {
        prefs(context).edit().putString(KEY_SNAPSHOT, snapshot).commit()
    }
}

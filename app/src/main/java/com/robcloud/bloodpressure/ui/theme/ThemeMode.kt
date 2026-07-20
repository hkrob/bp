package com.robcloud.bloodpressure.ui.theme

import android.content.Context

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
    CONSOLE("Console")
}

class ThemeStore(context: Context) {
    private val prefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)

    fun get(): ThemeMode =
        prefs.getString("mode", null)?.let { stored ->
            ThemeMode.entries.firstOrNull { it.name == stored }
        } ?: ThemeMode.SYSTEM

    fun set(mode: ThemeMode) {
        prefs.edit().putString("mode", mode.name).apply()
    }
}

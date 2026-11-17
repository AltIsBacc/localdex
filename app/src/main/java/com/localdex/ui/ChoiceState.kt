package com.localdex.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

@Stable
class ChoiceState(automatic: String, presets: List<String>) {
    val labels = listOf(automatic) + presets + "Custom…"

    private val customIndex = labels.lastIndex

    var selected by mutableIntStateOf(0)
    var customText by mutableStateOf("")

    val isCustom: Boolean get() = selected == customIndex

    val presetIndex: Int? get() = (selected - 1).takeIf { it in 0 until customIndex - 1 }

    fun selectPreset(index: Int) {
        selected = index + 1
    }

    fun selectCustom(text: String) {
        selected = customIndex
        customText = text
    }

    fun reset() {
        customText = ""
        selected = 0
    }
}

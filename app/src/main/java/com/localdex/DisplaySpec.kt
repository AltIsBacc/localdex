package com.localdex

object DisplaySpec {
    /** `WIDTHxHEIGHT/DPI`, e.g. `1920x1080/240`. */
    val PATTERN = Regex("(\\d{3,5})x(\\d{3,5})/(\\d{2,3})")
}

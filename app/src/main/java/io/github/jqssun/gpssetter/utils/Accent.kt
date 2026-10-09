package io.github.jqssun.gpssetter.utils

import android.app.Activity
import androidx.annotation.StyleRes
import io.github.jqssun.gpssetter.R

// Accent colours the user can pick (Settings). Each maps to a Material theme overlay applied at
// activity creation, so Material components (buttons, switches, FABs) pick up colorPrimary.
enum class Accent(val label: String, val color: Int, @StyleRes val overlay: Int) {
    BLUE("Blue", 0xFF3B82F6.toInt(), R.style.ThemeOverlay_GPSSetter_Accent_Blue),
    GREEN("Green", 0xFF22C55E.toInt(), R.style.ThemeOverlay_GPSSetter_Accent_Green),
    VIOLET("Violet", 0xFF8B5CF6.toInt(), R.style.ThemeOverlay_GPSSetter_Accent_Violet),
    AMBER("Amber", 0xFFF6B73C.toInt(), R.style.ThemeOverlay_GPSSetter_Accent_Amber),
    PINK("Pink", 0xFFEC4899.toInt(), R.style.ThemeOverlay_GPSSetter_Accent_Pink);

    companion object {
        fun current(): Accent = entries.getOrElse(PrefManager.accentIndex) { BLUE }
    }
}

// Apply before setContentView() in each Activity.
fun Activity.applyAccent() {
    theme.applyStyle(Accent.current().overlay, true)
}

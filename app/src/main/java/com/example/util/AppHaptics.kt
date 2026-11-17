package com.example.util

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.view.HapticFeedbackConstantsCompat

/**
 * Tactile haptic feedback helper mapped to semantic business actions
 * without requiring extra manifest permissions.
 */
class HapticController(private val view: View) {
    /** Confirmation pulse for saving bills, updating stock, or committing records. */
    fun confirm() {
        view.performHapticFeedback(HapticFeedbackConstantsCompat.CONFIRM)
    }

    /** Warning / removal pulse for deleting entries, clearing data, or validation errors. */
    fun reject() {
        view.performHapticFeedback(HapticFeedbackConstantsCompat.REJECT)
    }

    /** Light mechanical click for -/+ steppers, toggles, tabs, and keypad keys. */
    fun tick() {
        view.performHapticFeedback(HapticFeedbackConstantsCompat.CLOCK_TICK)
    }

    /** Distinct detent for long-press multi-select gestures. */
    fun longPress() {
        view.performHapticFeedback(HapticFeedbackConstantsCompat.LONG_PRESS)
    }
}

@Composable
fun rememberAppHaptics(): HapticController {
    val view = LocalView.current
    return remember(view) { HapticController(view) }
}

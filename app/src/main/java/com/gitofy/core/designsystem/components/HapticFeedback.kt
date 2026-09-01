package com.gitofy.core.designsystem.components

import android.view.HapticFeedbackConstants
import android.view.View

/** Lightweight system haptics used by interactive GITOFY components. */
internal object GITOFYHaptics {
    fun click(view: View) {
        view.performHapticFeedback(
            HapticFeedbackConstants.VIRTUAL_KEY
        )
    }

    fun toggle(view: View) {
        view.performHapticFeedback(
            HapticFeedbackConstants.CLOCK_TICK
        )
    }

    fun success(view: View) {
        view.performHapticFeedback(
            HapticFeedbackConstants.CONFIRM
        )
    }
}

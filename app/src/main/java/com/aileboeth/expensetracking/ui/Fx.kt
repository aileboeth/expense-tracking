package com.aileboeth.expensetracking.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.aileboeth.expensetracking.data.Prefs

/** The fun & motion switches, as state so screens follow a change at once. */
data class FxFlags(
    val mascot: Boolean = true,
    val haptics: Boolean = true,
    val motion: Boolean = true,
    val funRow: Boolean = true,
    val recapBanner: Boolean = true,
    val badgePopup: Boolean = true,
)

object Fx {
    val state = mutableStateOf(FxFlags())
    val flags: FxFlags get() = state.value

    fun load(ctx: Context) {
        val p = Prefs(ctx)
        state.value = FxFlags(p.fxMascot, p.fxHaptics, p.fxMotion, p.fxFunRow, p.fxRecapBanner, p.fxBadgePopup)
    }

    fun update(ctx: Context, change: (FxFlags) -> FxFlags) {
        val f = change(state.value)
        val p = Prefs(ctx)
        p.fxMascot = f.mascot; p.fxHaptics = f.haptics; p.fxMotion = f.motion
        p.fxFunRow = f.funRow; p.fxRecapBanner = f.recapBanner; p.fxBadgePopup = f.badgePopup
        state.value = f
    }
}

private object NoHaptics : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {}
}

/** Swaps in a silent haptics object when vibration is switched off, so every screen follows. */
@Composable
fun FxProvider(content: @Composable () -> Unit) {
    val real = LocalHapticFeedback.current
    CompositionLocalProvider(LocalHapticFeedback provides if (Fx.state.value.haptics) real else NoHaptics, content = content)
}

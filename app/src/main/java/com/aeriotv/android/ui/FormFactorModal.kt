package com.aeriotv.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import com.aeriotv.android.ui.scale.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.only
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aeriotv.android.ui.scale.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * The app's standard modal container, split by form factor:
 *
 *  - Phone / tablet: a Material3 ModalBottomSheet (drag handle, swipe to
 *    dismiss) -- the natural touch idiom.
 *  - Android TV: a centered Dialog panel. A bottom sheet is wrong on a
 *    remote: its drag handle is a dead control that even GRABS D-pad focus
 *    (showing a "Drag handle" tooltip), and the sheet hugs the screen
 *    bottom. The dialog has no gesture chrome; BACK dismisses.
 *
 * Every modal in the app should use this (or hand-roll the same split, as
 * AddToMultiviewSheet / UpdatePromptSheet / WhatsNewSheet do). The content
 * lambda is identical across both containers; callers keep their own inner
 * padding and scrolling.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormFactorModal(
    onDismiss: () -> Unit,
    tvWidthFraction: Float = 0.55f,
    tvMaxHeight: Dp = 480.dp,
    /**
     * Optional width cap for the TOUCH bottom sheet (plan B5).
     *
     * A ModalBottomSheet spans the full window, which on a 1280dp tablet
     * stretches a list of short group names across the whole display. Callers
     * that want the sheet to read as a panel rather than a full-bleed drawer
     * pass a cap here; the sheet then centres within the window.
     *
     * Opt-in per caller rather than a blanket default: every modal in the app
     * shares this container, and silently narrowing all of them (the player
     * sheets, Record from Now, What's New) would be a much wider visual change
     * than the plan asked for. Null keeps today's full-width behaviour.
     *
     * Ignored on TV, which uses the centered dialog and already sizes itself
     * with [tvWidthFraction].
     */
    sheetMaxWidth: Dp? = null,
    /**
     * Touch sheets only: false removes the sheet's own drag and nested
     * scroll handling, so a scrolling body that reaches its end stretches
     * (Compose overscroll) instead of handing the leftover drag to the
     * sheet, which moved the sheet and snapped it back (Record from Now,
     * Logan 2026-10-02). Cancel and the scrim still dismiss.
     */
    sheetGesturesEnabled: Boolean = true,
    /**
     * Touch sheets only (Logan 2026-09-14): open at the partial height and let
     * the user drag the sheet to full screen. The content column is then given
     * a FIXED height rather than a max, so a list inside it scrolls and the
     * expanded anchor never re-measures with the content (45483dd5: the What's
     * New sheet bounced at the end of its list for exactly that reason).
     */
    sheetExpandable: Boolean = false,
    /** Name for the TV focus trace ([SETTINGS] sheet open / close). */
    traceName: String = "modal",
    /**
     * Touch sheets only: overrides the sheet's container color. The
     * remote-session sheets pass black to match iOS (Logan 2026-10-08).
     * Unspecified keeps the theme background. Ignored on TV.
     */
    sheetContainerColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    content: @Composable ColumnScope.() -> Unit,
) {
    androidx.compose.runtime.DisposableEffect(Unit) {
        com.aeriotv.android.ui.tv.TvFocusTrace.settingsLifecycle("sheet open", traceName)
        onDispose {
            com.aeriotv.android.ui.tv.TvFocusTrace.settingsLifecycle("sheet close", traceName)
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(tvWidthFraction)
                .heightIn(max = tvMaxHeight),
            shape = RoundedCornerShape(16.dp),
            color = com.aeriotv.android.ui.tv.TvChrome.dialogSurface(),
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                com.aeriotv.android.ui.settings.LocalSettingsTracePage provides "sheet:$traceName",
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 12.dp),
                    content = content,
                )
            }
        }
    }
}


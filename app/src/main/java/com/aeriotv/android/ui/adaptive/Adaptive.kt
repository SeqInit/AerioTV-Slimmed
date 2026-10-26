package com.aeriotv.android.ui.adaptive

import androidx.compose.ui.unit.dp

/**
 * Layout widths for the 10-foot canvas. Android TV reports a fixed ~960x540dp
 * window (1920x1080 at density 320, 1280x720 at tvdpi), so these are
 * constants rather than breakpoints.
 */
object TvLayout {
    /** Horizontal padding for full-bleed screens that center constrained content. */
    val gutter = 40.dp

    /** Max content width for form-style screens. 700dp on the Streamer's 960dp
     *  leaves ~130dp gutters each side, the tvOS EditServerPage proportions. */
    val formMaxWidth = 700.dp

    /** Tighter cap for the onboarding flow (Welcome / Choose Source Type /
     *  Configure): a narrower centered column, closer to tvOS. */
    val onboardingMaxWidth = 560.dp
}

/**
 * Bottom padding a scrolling page reserves at the end of its content. Never
 * provided: TV pages use the default. (Phones overrode it to clear their
 * floating tab bar.)
 */
val LocalTabBarBottomInset = androidx.compose.runtime.compositionLocalOf { 104.dp }

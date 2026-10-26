package com.aeriotv.android.feature.player

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.composed
import androidx.compose.ui.unit.dp

/**
 * Phone / tablet in-app mini player (Logan 2026-09-14): a PiP-style floating
 * video window over every tab, the touch counterpart of the TV corner mini.
 * The video is still the ONE activity-lifetime PlayerView in
 * [PersistentExoWindow]; this file only supplies the phone geometry, the
 * window's own touch controls (drag to a corner, tap to expand, X to stop) and
 * the top-strip swipe-down that minimizes the fullscreen player.
 *
 * TV never reaches any of this (PersistentExoWindow keeps its tvOS geometry).
 * Tablets (sw >= 600 dp) share these controls but dock like the iPad corner
 * mini: fixed top-trailing, 400x225 dp, 24 dp in, no drag, and stashed at the
 * trailing edge while Settings is selected.
 */
object PhoneMiniChrome {
    /** Live finger travel (px, downward) of a top-strip minimize drag on the
     *  fullscreen LIVE player; the window follows it. 0 when idle. */
    val dragPx = mutableFloatStateOf(0f)

    /** True while that drag is in progress (geometry snaps instead of springs). */
    val dragging = mutableStateOf(false)

    /** Corner the mini rests in; kept for the session so a re-minimize lands
     *  where the user last left it. Default bottom-trailing. */
    val corner = mutableStateOf(Corner.BottomEnd)

    /** Top edge of MainScaffold's bottom overlay (cast card, companion button,
     *  tab bar) in root px, 0 while the tab shell is not composed. The mini's
     *  bottom corners sit above it so they never cover the bar or the card. */
    val bottomChromeTopPx = mutableFloatStateOf(0f)

    /** Bottom edge of the tablet TOP tab bar in root px, 0 when there is none,
     *  so the top corners never cover it either. */
    val topChromeBottomPx = mutableFloatStateOf(0f)

    enum class Corner { TopStart, TopEnd, BottomStart, BottomEnd }

    /** Tablet only: the user tapped the stashed sliver on Settings to bring
     *  the mini back out (iPad settingsMiniPeek, HomeView.swift). Reset by
     *  MainScaffold on every tab change. */
    val settingsPeek = mutableStateOf(false)

    /** Tablet only: trailing width (dp) the docked corner mini covers at the
     *  top of the window (inset + mini width), 0 when the mini is not docked
     *  there (hidden, fullscreen, stashed on Settings, phones). The Live TV
     *  header pads its trailing actions by this so search, sort and the
     *  List / Guide toggle slide out from under the mini, as on the iPad. */
    val tabletHeaderReserveDp = mutableFloatStateOf(0f)

    /** The spring the mini frame animates with; the header shift reuses it
     *  so both move together. */
    fun <T> frameSpring() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
}


package com.aeriotv.android.feature.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aeriotv.android.ui.tv.tvFocusScale

/**
 * ANDROID (no Apple counterpart): the Sidebar-style TV interface's main
 * menu, replacing TvTopTabBar in that layout. A full-height column on the
 * left: the app name, Search and the tabs, then Refresh (and the Keep Recent
 * Channels Live indicator while it has channels) and Settings at the foot,
 * pinned at the foot. It does NOT dim or cover the screen: the
 * host pushes the content right by [TvSideMenuWidth] (LocalTvSideMenuWidth),
 * and on the guide the groups column stays open beside it.
 *
 * Opened by Back from a tab root, Back or Left from the guide's groups
 * column. Right closes it (back to the groups column when that is open).
 * Back while it is open is [onBack] (Back from the main menu is the
 * way out of the app, so the host raises the Exit AerioTV? prompt). The
 * BackHandler lives in here, composed only while the menu is open, so it is
 * always the newest registration and outranks every tab's own handler.
 *
 * Selection does NOT follow focus here, unlike the top bar: walking the menu
 * must never cold-build each tab it passes (the E-5 perf hazard the bar
 * needed a settle window for). OK commits.
 */
@Composable
internal fun TvSideMenu(
    tabs: List<AppTab>,
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
    onRefresh: () -> Unit,
    retainedCount: Int,
    onRetainedClick: () -> Unit,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
) {
    androidx.activity.compose.BackHandler(onBack = onBack)
    val items = remember(tabs) { listOf(AppTab.Search) + tabs.filter { it != AppTab.Settings } }
    val hasSettings = AppTab.Settings in tabs
    val requesters = remember(tabs) { (items + AppTab.Settings).associateWith { FocusRequester() } }
    var menuHasFocus by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // Keep asking until the menu has HELD focus for a few frames: the
        // content behind can still be settling its own focus (a closing
        // groups panel, a tab entry) on the frames right after opening.
        val target = requesters[selected] ?: requesters.getValue(items.first())
        var held = 0
        var attempts = 0
        while (held < 3 && attempts < 60) {
            androidx.compose.runtime.withFrameNanos { }
            if (menuHasFocus) held++ else { held = 0; runCatching { target.requestFocus() } }
            attempts++
        }
        com.aeriotv.android.ui.tv.TvFocusTrace.guide("tv-side-menu focus held=$menuHasFocus attempts=$attempts")
    }
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(TvSideMenuWidth)
            .background(cs.surface)
            .padding(horizontal = 12.dp, vertical = 20.dp)
            // Focus stays in the menu until it closes: a stray Up / Down past
            // the ends must not land on content behind it.
            .onFocusChanged { menuHasFocus = it.hasFocus }
            .focusGroup()
            .focusProperties {
                @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
                run { exit = { FocusRequester.Cancel } }
            }
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionRight) {
                    onDismiss(); true
                } else {
                    false
                }
            },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "AerioTV",
            fontSize = 26.sp, fontWeight = FontWeight.Medium,
            color = cs.primary,
            modifier = Modifier.padding(start = 12.dp, bottom = 28.dp),
        )
        Spacer(Modifier.weight(0.6f))
        items.forEach { tab ->
            com.aeriotv.android.ui.tv.TvPanelRow(
                label = tab.label,
                icon = if (tab == selected) tab.iconSelected else tab.iconUnselected,
                selected = tab == selected,
                onClick = { onSelect(tab) },
                modifier = Modifier.focusRequester(requesters.getValue(tab)),
            )
        }
        Spacer(Modifier.weight(1f))
        if (retainedCount > 0) {
            com.aeriotv.android.ui.tv.TvPanelRow(
                label = "Live Channels ($retainedCount)",
                icon = Icons.Filled.Refresh,
                onClick = onRetainedClick,
            )
        }
        com.aeriotv.android.ui.tv.TvPanelRow(
            label = "Refresh",
            icon = Icons.Filled.Refresh,
            onClick = { onRefresh(); onDismiss() },
        )
        if (hasSettings) {
            com.aeriotv.android.ui.tv.TvPanelRow(
                label = AppTab.Settings.label,
                icon = AppTab.Settings.iconSelected,
                selected = selected == AppTab.Settings,
                onClick = { onSelect(AppTab.Settings) },
                modifier = Modifier.focusRequester(requesters.getValue(AppTab.Settings)),
            )
        }
    }
}

/** Width of the Sidebar-style side menu; the host pushes content right by this. */
internal val TvSideMenuWidth = 210.dp

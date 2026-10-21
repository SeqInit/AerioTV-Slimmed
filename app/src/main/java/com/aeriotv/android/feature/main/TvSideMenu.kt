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
 * ANDROID (no Apple counterpart): the TiviMate-style TV interface's main
 * menu, replacing TvTopTabBar in that layout. A full-height panel on the
 * left over a dimmed screen: Search, every tab, then Refresh (and the Keep
 * Recent Channels Live indicator while it has channels). It is hidden until
 * opened (Back from a tab root, or Back from the guide's groups panel) and
 * closes on selection or Right. Back while it is open is [onBack] (TiviMate:
 * Back from the main menu is the way out of the app, so the host raises the
 * Exit AerioTV? prompt). The BackHandler lives in here, composed only while
 * the menu is open, so it is always the newest registration and outranks
 * every tab's own handler.
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
    val items = remember(tabs) { listOf(AppTab.Search) + tabs }
    val requesters = remember(items) { items.associateWith { FocusRequester() } }
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(TV_SIDE_MENU_WIDTH)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp, vertical = 24.dp)
                // Focus stays in the menu until it closes: the screen behind
                // is dimmed, and a stray Up / Down past the ends must not
                // land on content the user cannot see properly.
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
                fontSize = 18.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 12.dp, bottom = 16.dp),
            )
            items.forEach { tab ->
                TvSideMenuRow(
                    label = tab.label,
                    icon = if (tab == selected) tab.iconSelected else tab.iconUnselected,
                    selected = tab == selected,
                    onClick = { onSelect(tab) },
                    modifier = Modifier.focusRequester(requesters.getValue(tab)),
                )
            }
            Spacer(Modifier.weight(1f))
            if (retainedCount > 0) {
                TvSideMenuRow(
                    label = "Live Channels ($retainedCount)",
                    icon = Icons.Filled.Refresh,
                    selected = false,
                    onClick = onRetainedClick,
                )
            }
            TvSideMenuRow(
                label = "Refresh",
                icon = Icons.Filled.Refresh,
                selected = false,
                onClick = { onRefresh(); onDismiss() },
            )
        }
    }
}

@Composable
private fun TvSideMenuRow(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    val ink = when {
        focused -> colors.onPrimary
        selected -> colors.primary
        else -> colors.onSurfaceVariant
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .tvFocusScale(focused, focusedScale = 1.03f)
            .clip(shape)
            .background(if (focused) colors.primary else Color.Transparent)
            .border(1.dp, if (selected && !focused) colors.primary.copy(alpha = 0.5f) else Color.Transparent, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = ink, modifier = Modifier.size(20.dp))
        Text(
            label, fontSize = 14.sp,
            fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Medium,
            color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

private val TV_SIDE_MENU_WIDTH = 240.dp

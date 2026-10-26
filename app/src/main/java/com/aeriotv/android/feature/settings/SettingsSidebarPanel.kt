package com.aeriotv.android.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aeriotv.android.ui.settings.LocalSettingsFlatStyle
import com.aeriotv.android.ui.tv.TvPanelRow

/**
 * ANDROID (Sidebar-style TV interface): Settings as a
 * panel on the RIGHT over the dimmed screen instead of a full-screen tab.
 * A header band carries the page title ("Settings", then the open page's
 * name); under it the category list, and once a category is picked that
 * page's content in the same panel.
 *
 * It is a drop-in for SettingsTvRailHost: same selection / push contract,
 * same [detail] renderer, so every existing settings page (and every push
 * a page makes) renders here unchanged, flattened by [LocalSettingsFlatStyle].
 * Takeovers (Edit / Add Playlist, the log viewer, licences, the category
 * editor) still take the whole screen for their keyboard and IME plumbing.
 *
 * Back inside a page pops the page's own pushes (SettingsTabContent's nav
 * handler), then returns to the category list (here); Back on the list is
 * the host's (it closes the panel).
 */
@Composable
fun SettingsSidebarPanelHost(
    selection: SettingsRoute,
    onSelect: (SettingsRoute) -> Unit,
    pushed: SettingsRoute?,
    sections: List<SettingsSectionGroupSpec>,
    activePlaylistName: String?,
    takeover: (SettingsRoute) -> Boolean,
    detail: @Composable (SettingsRoute) -> Unit,
) {
    if (pushed != null && takeover(pushed)) {
        detail(pushed)
        return
    }
    val role by com.aeriotv.android.core.profiles.ProfileRoles.active.collectAsState()
    val child = role == com.aeriotv.android.core.profiles.ProfileRole.Child
    val rows = remember(sections, activePlaylistName, child) {
        buildList {
            if (!child) add(SettingsRoute.Playlists as SettingsRoute to "Playlists")
            sections.forEach { g -> g.sections.forEach { add(SettingsRoute.Section(it) to it.title) } }
            if (child) add(SettingsRoute.ManageProfiles as SettingsRoute to "Manage Profiles")
        }
    }
    val atList = selection == SettingsRoute.Root && pushed == null
    val shown = pushed ?: selection
    val title = when (shown) {
        SettingsRoute.Root -> "Settings"
        is SettingsRoute.Section -> shown.section.title
        SettingsRoute.Playlists, is SettingsRoute.PlaylistDetail -> "Playlists"
        SettingsRoute.About -> "About"
        SettingsRoute.SyncCategories -> "Sync"
        SettingsRoute.ManageProfiles -> "Manage Profiles"
        else -> "Settings"
    }
    // Back from a page (nothing pushed above it) returns to the list, on the
    // row the page was opened from. Composed before the page, so a page's
    // own handlers (registered later) still win while they are enabled.
    var lastOpened by remember { androidx.compose.runtime.mutableStateOf<SettingsRoute?>(null) }
    androidx.activity.compose.BackHandler(enabled = !atList && pushed == null) {
        lastOpened = selection
        onSelect(SettingsRoute.Root)
    }
    val rowFocus = remember(rows) { rows.associate { it.first to FocusRequester() } }
    val pageFocus = remember { FocusRequester() }
    LaunchedEffect(atList, shown) {
        // Land focus on the list's row for the page just
        // left (else the first), or into the page that just opened.
        repeat(10) {
            androidx.compose.runtime.withFrameNanos { }
            val ok = if (atList) {
                val target = lastOpened?.let { rowFocus[it] } ?: rowFocus[rows.firstOrNull()?.first]
                target != null && runCatching { target.requestFocus() }.getOrDefault(false)
            } else {
                runCatching { pageFocus.requestFocus() }.getOrDefault(false)
            }
            if (ok) return@LaunchedEffect
        }
    }

    val cs = MaterialTheme.colorScheme
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                // The panel: a bit over a third of the screen.
                .fillMaxWidth(0.38f)
                .background(cs.surface)
                // Focus stays in the panel while it is open.
                .focusGroup()
                .focusProperties {
                    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
                    run { exit = { FocusRequester.Cancel } }
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(cs.onSurface.copy(alpha = 0.06f))
                    .padding(horizontal = 24.dp, vertical = 22.dp),
            ) {
                Text(title, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = cs.onSurface)
            }
            if (atList) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    itemsIndexed(rows, key = { _, r -> encodeSettingsRoute(r.first) }) { _, (route, label) ->
                        TvPanelRow(
                            label = label,
                            onClick = {
                                lastOpened = route
                                onSelect(route)
                            },
                            modifier = Modifier.focusRequester(rowFocus.getValue(route)),
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(pageFocus)
                        .focusGroup(),
                ) {
                    CompositionLocalProvider(LocalSettingsFlatStyle provides true) {
                        detail(shown)
                    }
                }
            }
        }
    }
}

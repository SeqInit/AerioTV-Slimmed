package com.aeriotv.android.feature.settings

import com.aeriotv.android.ui.scale.subtext
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aeriotv.android.core.sync.SyncCategory
import com.aeriotv.android.ui.adaptive.LocalTabBarBottomInset
import com.aeriotv.android.ui.settings.SettingsDetailTopBar
import com.aeriotv.android.ui.settings.SettingsRowContainer
import com.aeriotv.android.ui.settings.SettingsSection
import com.aeriotv.android.ui.settings.SettingsToggleAffordance
import com.aeriotv.android.ui.settings.rememberIsTvDevice
import com.aeriotv.android.ui.settings.settingsFootnoteStyle
import com.aeriotv.android.ui.settings.settingsFormWidth
import com.aeriotv.android.ui.settings.settingsRowTitleStyle

/**
 * Settings > Sync: Dispatcharr Sync, the only sync this build has. One switch
 * turns it on or off (offered only on a Dispatcharr Direct Connect playlist,
 * or while it is on so it can be turned off), then Sync Now and the pushed
 * [SyncCategoriesScreen].
 */
@Composable
fun SyncSettingsScreen(
    onBack: () -> Unit,
    /** Pushes Sync Categories (Apple's NavigationLink). */
    onOpenSyncCategories: () -> Unit = {},
    viewModel: SyncSettingsViewModel = hiltViewModel(),
) {
    val enabled by viewModel.dispatcharrEnabled.collectAsStateWithLifecycle(initialValue = false)
    val available by viewModel.dispatcharrAvailable.collectAsStateWithLifecycle(initialValue = false)
    val dsyncUi by viewModel.dispatcharrUi.collectAsState()
    val tvRows = rememberIsTvDevice()

    Column(modifier = Modifier.fillMaxSize()) {
        SettingsDetailTopBar(title = "Sync", onBack = onBack)
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                // fillMaxHeight bounds the LazyColumn so its inner viewport can
                // scroll past the first screen of content.
                modifier = Modifier.settingsFormWidth().fillMaxHeight(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = LocalTabBarBottomInset.current,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    SettingsSection(
                        header = "",
                        footer = if (tvRows) null else "Dispatcharr keeps your favorites, progress, reminders, and settings on your Dispatcharr server, separately for each profile. Playlists and sign-in details never sync. Admins on your Dispatcharr server can view synced data.",
                    ) {
                        SyncTileToggleRow(
                            icon = Icons.Filled.CloudSync,
                            title = "Dispatcharr Sync",
                            subtitle = if (available || enabled) "Sync this profile through your Dispatcharr server" else "Needs a Dispatcharr playlist",
                            checked = enabled,
                            onCheckedChange = { viewModel.setDispatcharrEnabled(it) },
                            enabled = available || enabled,
                        )
                        if (enabled) {
                            SyncTileActionRow(
                                icon = Icons.Filled.CloudSync,
                                title = "Sync Now",
                                subtitle = when {
                                    !available -> "Needs a Dispatcharr playlist"
                                    dsyncUi.message != null -> dsyncUi.message
                                    dsyncUi.running -> "Syncing"
                                    dsyncUi.lastSyncAt > 0L -> "Last synced ${lastSyncedAgo(dsyncUi.lastSyncAt)}"
                                    else -> "Not synced yet"
                                },
                                running = dsyncUi.running,
                                onClick = { if (available && !dsyncUi.running) viewModel.dispatcharrSyncNow() },
                            )
                            SyncTileActionRow(
                                icon = Icons.Filled.Tune,
                                title = "Sync Categories",
                                subtitle = "Choose what syncs across your devices",
                                chevron = !tvRows,
                                onClick = onOpenSyncCategories,
                            )
                        }
                    }
                }
            }
        }
    }
}

// MARK: - Apple-style tiled rows
//
// Apple's Sync rows are SettingsRow: a 32pt rounded tile in the icon color at
// 20% with the glyph in full color. The shared Android rows draw a bare
// glyph, so this page builds on SettingsRowContainer instead of changing
// every other page's rows.

@Composable
private fun SyncIconTile(icon: ImageVector, color: Color) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(color.copy(alpha = 0.20f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun SyncRowText(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = settingsRowTitleStyle(),
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Medium,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = settingsFootnoteStyle().subtext(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SyncTileToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    SettingsRowContainer(
        onClick = { if (enabled) onCheckedChange(!checked) },
        // Apple dims disabled category rows to 50% rather than hiding them.
        modifier = Modifier.alpha(if (enabled) 1f else 0.5f),
    ) {
        SyncIconTile(icon, MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        SyncRowText(title = title, subtitle = subtitle, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        SettingsToggleAffordance(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

/**
 * Tiled action row. While [running] the click is swallowed rather than the
 * row disabled, so it stays in D-pad traversal (SettingsActionRow's rule).
 */
@Composable
private fun SyncTileActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    running: Boolean = false,
    chevron: Boolean = false,
) {
    SettingsRowContainer(onClick = { if (!running) onClick() }) {
        SyncIconTile(icon, MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        SyncRowText(title = title, subtitle = subtitle, modifier = Modifier.weight(1f))
        if (running) {
            Spacer(Modifier.width(12.dp))
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        }
        if (chevron) {
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Apple's lastSyncedString: "just now", "5m ago", "3h ago", "2d ago". */
private fun lastSyncedAgo(millis: Long): String {
    val secs = (System.currentTimeMillis() - millis) / 1000
    return when {
        secs < 60 -> "just now"
        secs < 3600 -> "${secs / 60}m ago"
        secs < 86400 -> "${secs / 3600}h ago"
        else -> "${secs / 86400}d ago"
    }
}

// MARK: - Sync Categories copy
//
// Settings-page titles and subtitles, kept here rather than on SyncCategory
// (Apple splits the two the same way: subtitle vs briefSubtitle).

private fun settingsCategoryTitle(category: SyncCategory): String = when (category) {
    SyncCategory.WatchProgress -> "VOD Watch Progress"
    else -> category.displayName
}

private fun settingsCategorySubtitle(category: SyncCategory): String = when (category) {
    SyncCategory.WatchProgress -> "Resume points and last-watched timestamps for movies and TV episodes."
    SyncCategory.Reminders -> "Upcoming-program reminders you scheduled from the EPG."
    SyncCategory.Favorites -> "Favorite channels and your manual order."
    SyncCategory.Watchlist -> "Movies and TV shows you saved for later, and titles you hid."
    SyncCategory.Preferences -> "Theme, appearance mode, accent color, default tab, hidden groups, and palette overrides."
}

private fun settingsCategoryIcon(category: SyncCategory): ImageVector = when (category) {
    SyncCategory.WatchProgress -> Icons.Filled.PlayCircle
    SyncCategory.Reminders -> Icons.Filled.NotificationsActive
    SyncCategory.Favorites -> Icons.Filled.Star
    SyncCategory.Watchlist -> Icons.Filled.Bookmark
    SyncCategory.Preferences -> Icons.Filled.Settings
}

/**
 * Settings > Sync > Sync Categories. Apple SyncCategoriesSettingsView: one
 * toggle per category Dispatcharr Sync carries.
 */
@Composable
fun SyncCategoriesScreen(
    onBack: () -> Unit,
    viewModel: SyncSettingsViewModel = hiltViewModel(),
) {
    val isTv = rememberIsTvDevice()
    val footer = "Each toggle controls whether this device sends and receives that category through your Dispatcharr server. The toggles apply to this device only."

    Column(modifier = Modifier.fillMaxSize()) {
        SettingsDetailTopBar(title = "Sync Categories", onBack = onBack)
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.settingsFormWidth().fillMaxHeight(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = LocalTabBarBottomInset.current,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    SettingsSection(header = "Categories", footer = if (isTv) null else footer) {
                        SyncCategory.entries.forEach { category ->
                            val enabled by viewModel.categoryEnabled(category)
                                .collectAsStateWithLifecycle(initialValue = true)
                            SyncTileToggleRow(
                                icon = settingsCategoryIcon(category),
                                title = settingsCategoryTitle(category),
                                subtitle = settingsCategorySubtitle(category),
                                checked = enabled,
                                onCheckedChange = { viewModel.setCategoryEnabled(category, it) },
                            )
                            // Slices of App Preferences rather than categories of
                            // their own. Sit under their parent so the nesting reads.
                            if (category == SyncCategory.Preferences) {
                                val shareRemoteMap by viewModel.syncRemoteControlMap
                                    .collectAsStateWithLifecycle(initialValue = true)
                                SyncTileToggleRow(
                                    icon = Icons.Filled.SettingsRemote,
                                    title = "Remote Button Map",
                                    subtitle = "Your customized remote button assignments. Turn this off on a TV whose remote is a different model from your others.",
                                    checked = shareRemoteMap,
                                    onCheckedChange = { viewModel.setSyncRemoteControlMap(it) },
                                )
                                val shareRadioDefault by viewModel.syncRadioModeByDefault
                                    .collectAsStateWithLifecycle(initialValue = false)
                                SyncTileToggleRow(
                                    icon = Icons.Filled.Radio,
                                    title = "Radio Mode by Default",
                                    subtitle = "Whether every channel opens in Radio Mode. Off by default, so each device keeps its own choice.",
                                    checked = shareRadioDefault,
                                    onCheckedChange = { viewModel.setSyncRadioModeByDefault(it) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

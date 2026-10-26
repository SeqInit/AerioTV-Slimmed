package com.aeriotv.android.feature.livetv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aeriotv.android.core.data.M3UChannel
import com.aeriotv.android.core.ui.LocalShowEpgBadges
import com.aeriotv.android.feature.livetv.grid.GuideScreen
import com.aeriotv.android.feature.playlist.PlaylistViewModel
import com.aeriotv.android.feature.settings.SettingsViewModel

/**
 * Entry point for the Live TV tab: the Guide grid, with the EPG display
 * preferences provided to it. (The phone List view and the List / Guide
 * toggle went with the phone builds; a stored "Default Live TV View" synced
 * from another device is left untouched.)
 */
@Composable
fun LiveTVTabContent(
    onChannelClick: (M3UChannel) -> Unit,
    modifier: Modifier = Modifier,
    onOpenSearch: () -> Unit = {},
    /** Catch-up (task #133/#136): play a resolved timeshift URL in the
     *  recording player, carrying programme window + panel timezone for
     *  scrub-seek URL rebuilds. */
    onPlayCatchup: (String, String, String, Long, Long, String, String) -> Unit = { _, _, _, _, _, _, _ -> },
    // REQUIRED, no hiltViewModel() default (2026-07-12 field report): this
    // composable lives under the MAIN nav entry, so a bare default resolved
    // against MAIN's store and minted a SECOND PlaylistViewModel besides the
    // PLAYLIST_GRAPH-scoped one. Its init re-ran the whole channel+EPG
    // bootstrap on every cold launch (doubled Room reads, two in-memory EPG
    // maps, a GC storm + first-minute jank on the Streamer) and the guide ran
    // on a different state timeline than the scaffold. Callers must thread
    // the graph-scoped instance down.
    viewModel: PlaylistViewModel,
    settingsVm: SettingsViewModel = hiltViewModel(),
) {
    // Shared settle signal for the quiet background refreshes (EPG, VOD, DVR):
    // the guide has had its first composition.
    val settleGate = com.aeriotv.android.core.app.rememberAppSettleGate()
    androidx.compose.runtime.LaunchedEffect(Unit) { settleGate.noteGuideRendered() }
    // EPG badge visibility, provided to the guide / list / info
    // sheet below so they hide the badges when the user turns them off.
    val showEpgBadges by settingsVm.showEpgBadges
        .collectAsStateWithLifecycle(initialValue = true)
    val showProgramSubtitles by settingsVm.showProgramSubtitles.collectAsStateWithLifecycle(initialValue = true)
    val showChannelLogos by settingsVm.showChannelLogos.collectAsStateWithLifecycle(initialValue = true)
    val channelLogoSize by settingsVm.channelLogoSize.collectAsStateWithLifecycle(initialValue = 100)
    val showChannelNumbers by settingsVm.showChannelNumbers.collectAsStateWithLifecycle(initialValue = true)
    val showChannelNames by settingsVm.showChannelNames.collectAsStateWithLifecycle(initialValue = true)
    val hiddenEpgBadges by settingsVm.hiddenEpgBadges
        .collectAsStateWithLifecycle(initialValue = emptySet())

    // Android#131: a trim that shed the in-memory guide is repainted from the
    // Room cache the moment the Live TV tab (guide or list) is shown.
    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.repaintGuideIfShed("guide-open") }

    CompositionLocalProvider(
        LocalShowEpgBadges provides showEpgBadges,
        com.aeriotv.android.core.ui.LocalShowProgramSubtitles provides showProgramSubtitles,
        com.aeriotv.android.core.ui.LocalGuideRailPrefs provides
            com.aeriotv.android.core.ui.GuideRailPrefs(
                showChannelLogos, showChannelNumbers, showChannelNames,
                logoScale = com.aeriotv.android.core.ui.liveTvLogoScale(channelLogoSize),
            ),
        com.aeriotv.android.core.ui.LocalHiddenEpgBadges provides hiddenEpgBadges,
    ) {
        GuideScreen(
            onChannelClick = onChannelClick,
            viewModel = viewModel,
            modifier = modifier,
            onOpenSearch = onOpenSearch,
            onPlayCatchup = onPlayCatchup,
        )
    }
}


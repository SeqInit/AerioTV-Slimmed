package com.aeriotv.android.feature.livetv

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aeriotv.android.core.timeshift.TimeshiftController
import com.aeriotv.android.core.tv.TvActionMenuDialog
import com.aeriotv.android.core.tv.TvMenuAction
import com.aeriotv.android.core.tv.rememberTvMenuGuard
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Keep Recent Channels Live (iOS parity): thin surface over the
 * controller's retained-session list for the Live TV header indicator.
 */
@HiltViewModel
class RetainedChannelsViewModel @Inject constructor(
    private val timeshift: TimeshiftController,
) : ViewModel() {
    val retained = timeshift.retainedChannels
    fun stop(channelId: String) = timeshift.stopRetainedChannel(channelId)
    fun stopAll() = timeshift.stopAllRetainedByUser()
}

/**
 * Kept Live list. TV: the action-menu dialog opened from the floating circle
 * in TvTopTabBar. Phones and tablets: the same contents in a bottom sheet
 * (FormFactorModal), opened by the multi-channel text of
 * [RetainedChannelsPill] (iPhone parity): a Stop All header, then each kept
 * channel with Watch and Stop. Closes on Watch, on Stop All, or when nothing
 * is kept any more.
 */
@Composable
fun RetainedChannelsDialog(
    viewModel: RetainedChannelsViewModel,
    onJumpToChannel: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val retained by viewModel.retained.collectAsStateWithLifecycle()
    val guard = rememberTvMenuGuard()
    val actions = buildList {
        retained.asReversed().forEach { ch ->
            add(
                TvMenuAction(
                    label = "Watch ${ch.channelName}",
                    icon = Icons.Filled.PlayArrow,
                ) { onJumpToChannel(ch.channelId) },
            )
            add(
                TvMenuAction(
                    label = "Stop ${ch.channelName}",
                    icon = Icons.Filled.Stop,
                    destructive = true,
                ) { viewModel.stop(ch.channelId) },
            )
        }
        if (retained.size > 1) {
            add(
                TvMenuAction(
                    label = "Stop All",
                    icon = Icons.Filled.Stop,
                    destructive = true,
                ) { viewModel.stopAll() },
            )
        }
    }
    TvActionMenuDialog(
        title = "Kept Live",
        actions = actions,
        guard = guard,
        onDismiss = onDismiss,
    )
}

/**
 * Ids of the channels currently kept live, provided at the scaffold level so
 * the guide rail and the channel list rows can draw their KEPT badge. Dynamic
 * (not static) for the same reason as LocalShowEpgBadges: the rows live in a
 * LazyColumn subcomposition and must recompose on their own when the set
 * changes.
 */
val LocalRetainedChannelIds = androidx.compose.runtime.compositionLocalOf { emptySet<String>() }

/** Kept Live card vertical padding, the cast card's 8 dp. */
val RetainedCardVerticalPadding = 8.dp
/** Kept Live card trailing control height, the cast card's 48 dp. */
val RetainedCardControlHeight = 48.dp
/**
 * Height of the Kept Live card (64 dp): the Control a TV button beside it on
 * phones takes this diameter so the row reads as one shape (Logan 2026-10-05).
 */
val RetainedCardHeight = RetainedCardVerticalPadding * 2 + RetainedCardControlHeight


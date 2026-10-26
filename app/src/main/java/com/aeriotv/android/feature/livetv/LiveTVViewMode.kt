package com.aeriotv.android.feature.livetv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable

/**
 * Whether Live TV renders the dense channel List or the time-x-channels Guide grid.
 * Mirrors iOS `defaultLiveTVView` @AppStorage. Phase 5 keeps the value
 * rememberSaveable; Phase 8 (Settings sub-screens) will move it to DataStore so
 * the user choice survives cold starts.
 */
enum class LiveTVViewMode { List, Guide }

/**
 * Live TV view availability. The List view is gated on
 * [com.aeriotv.android.core.ui.TvListView.ENABLED] (off: the Guide is the only
 * presentation on TV), and with it the List / Guide toggle.
 */
@Immutable
object LiveTvFormFactor {
    /** Whether the List view exists at all. */
    val listAvailable: Boolean
        get() = com.aeriotv.android.core.ui.TvListView.ENABLED

    /** Whether the user can flip between List and Guide. */
    val supportsToggle: Boolean get() = listAvailable

    /** Default view when no user override exists. */
    val defaultMode: LiveTVViewMode
        get() = LiveTVViewMode.Guide
}

fun rememberLiveTvFormFactor(): LiveTvFormFactor = LiveTvFormFactor

@Composable
fun rememberLiveTvViewModeState(formFactor: LiveTvFormFactor): MutableState<LiveTVViewMode> {
    return rememberSaveable(formFactor.defaultMode) {
        mutableStateOf(formFactor.defaultMode)
    }
}

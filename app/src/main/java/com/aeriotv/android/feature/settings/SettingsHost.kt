// SettingsHost.kt
//
// Settings redesign Phase B3 (SettingsUIRedesign.md B3): the tablet list-detail
// host. A 320dp touch sidebar of selected-tonal rows on the left, the selected
// pane on the right, and a hairline between them.
//
// Hand-rolled rather than material3-adaptive, per the plan's decision: the
// library's NavigableListDetailPaneScaffold owns its own back stack, which
// would fight the one B2 just built, and it buys nothing this layout needs.
//
// Rev 2, SELECTION IS NOT THE STACK. The sidebar mutates `selection` and never
// pushes, so Back can never replay sidebar browsing. `SettingsNavState` holds
// only what was pushed ABOVE the selected pane. The posture mapping in
// [rememberSettingsPaneSelection] translates between the two when the window
// crosses the two-pane threshold (rotation, fold, resizable-window drag).

package com.aeriotv.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.launch
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aeriotv.android.ui.settings.LocalSettingsInPane
import com.aeriotv.android.ui.settings.SettingsNavRow
import com.aeriotv.android.ui.settings.TvSettingsMetrics

/**
 * Pane-swap crossfade (plan B5: "about 150ms crossfade on pane content swaps;
 * no shared-element transitions").
 *
 * Short on purpose. The rail commits a selection through its own debounce, so
 * a longer fade would still be running when a fast scroll lands on the next
 * row and the pane would visibly lag the highlight.
 */
private const val SettingsPaneCrossfadeMs = 150

/**
 * The rail's pane selection, kept across process death. It starts with
 * NOTHING selected (Logan 2026-09-03: landing on the Settings pill must not
 * open Playlists).
 */
@Composable
fun rememberSettingsPaneSelection(): MutableState<SettingsRoute> =
    rememberSaveable(stateSaver = SettingsRouteSaver) { mutableStateOf<SettingsRoute>(SettingsRoute.Root) }

// MARK: - TV rail (plan B2/B4)

/**
 * Rail width and overscan-safe start inset for the 10-foot host.
 *
 * Sized as a FRACTION of the canvas, not by copying tvOS's point value.
 * A 1080p panel gives Android 960x540dp (1920x1080 at density 320) where
 * tvOS gets 1920x1080pt, so an identical number is twice the physical size
 * here. tvOS's rail is 430pt of 1920 = 22.4%; 220dp of 960 = 22.9%, which
 * lands the two rails at the same size on the same TV. The plan's "~300dp"
 * predated this measurement and read as 36% of the screen on the Streamer.
 *
 * The type ladder already scales correctly by the same logic (tvOS 30pt of
 * 1920 = 1.56%; bodyLarge 16sp at the 0.9 TV scale = 14.4dp of 960 = 1.5%),
 * so the narrower rail fits its labels exactly as tvOS's does.
 */
private val TvRailWidth = 220.dp

/**
 * The rail's left inset. Aliases the shared metric so full-screen takeovers
 * (which have no rail to align against) start at the exact same edge.
 */
private val TvOverscanStart = TvSettingsMetrics.overscanStart

/**
 * Debounce before a focused rail row becomes the SELECTED one.
 *
 * Focus-follows-selection means holding DOWN through the rail would
 * otherwise rebuild the detail pane once per row. The same 150ms as the
 * tvOS rail (plan A3), so the two TV apps feel identical. DPAD_RIGHT
 * FLUSHES a pending selection before moving focus, so entering the pane
 * never lands on the previous row's content.
 */
private const val RailSelectDebounceMs = 150L

/**
 * Android TV Settings: a persistent rail of categories on the left, the
 * selected pane on the right.
 *
 * Focus contract, mirroring `GroupSidebarPanel` (the proven in-app TV rail):
 *  - focusing a rail row selects it after [RailSelectDebounceMs]
 *  - DPAD_RIGHT flushes that pending selection, then moves focus into the pane
 *  - Back with focus in the pane returns it to the rail rather than exiting
 *  - Back with focus on the rail falls through to the tab bar, unchanged
 */
@Composable
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun SettingsTvRailHost(
    /**
     * Bumped by the screenshot deep link when it changes [selection] without a
     * push. The rail's own focus effect only fires on a push, so without this
     * a deep-linked pane would render with focus still on the tab pill.
     * 0 means "never", and is ignored.
     */
    focusPaneSignal: Int = 0,
    selection: SettingsRoute,
    onSelect: (SettingsRoute) -> Unit,
    pushed: SettingsRoute?,
    sections: List<SettingsSectionGroupSpec>,
    activePlaylistName: String?,
    /** Drives the Sync row's On / Off subtitle; see settingsSectionSubtitle. */
    syncEnabled: String,
    takeover: (SettingsRoute) -> Boolean,
    detail: @Composable (SettingsRoute) -> Unit,
) {
    // Retains each pane's own UI state (crucially its LazyColumn scroll
    // offset) while that pane is out of composition. The takeover below
    // REMOVES the whole rail+pane subtree, so without this the pane is rebuilt
    // from scratch on the way back and lands at the top: open Add More
    // Categories from the bottom of Appearance, press Back, and you are
    // returned to the Theme list having lost your place. Declared before the
    // early return so the holder itself survives the takeover.
    val paneStateHolder = rememberSaveableStateHolder()

    if (pushed != null && takeover(pushed)) {
        // Edit / Add-playlist / log viewer / category editors replace the whole
        // host, exactly as they do today, so all the TV keyboard and IME
        // plumbing on those screens is untouched.
        detail(pushed)
        return
    }

    val railFocus = remember { FocusRequester() }
    // Profiles: Manage Profiles -> Admin moves the rail to Playlists; once the
    // switch scrim drops, focus lands on that rail row (never a tab pill).
    val profileScrimReleased by com.aeriotv.android.core.profiles.ProfileSwitchFocus.released.collectAsState()
    androidx.compose.runtime.LaunchedEffect(profileScrimReleased) {
        if (!com.aeriotv.android.core.profiles.ProfileSwitchFocus.consume(com.aeriotv.android.core.profiles.ProfileSwitchFocus.Target.Rail)) return@LaunchedEffect
        kotlinx.coroutines.delay(150L)
        repeat(8) {
            runCatching { railFocus.requestFocus() }
            kotlinx.coroutines.delay(100L)
        }
        android.util.Log.i("AerioProfiles", "[PROFILE] focus restored to settings rail row ${encodeSettingsRoute(selection)}")
    }
    val detailFocus = remember { FocusRequester() }
    var railHasFocus by remember { mutableStateOf(false) }
    var detailHasFocus by remember { mutableStateOf(false) }
    val railRightScope = androidx.compose.runtime.rememberCoroutineScope()
    var hostHasFocus by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<SettingsRoute?>(null) }
    // Bumped on EVERY rail focus. Keying the commit on the route alone missed
    // re-focusing the same route after the selection moved elsewhere without
    // touching `pending` (deep link, posture remap): the effect never
    // restarted, so the row read as selected while the pane kept the old page
    // (Player highlighted over the Live TV pane).
    var pendingSeq by remember { mutableStateOf(0) }
    // The effect outlives the composition it launched in; read the CURRENT
    // selection and callback when the debounce fires, not the launch-time ones.
    val currentSelection by androidx.compose.runtime.rememberUpdatedState(selection)
    val currentOnSelect by androidx.compose.runtime.rememberUpdatedState(onSelect)

    // Debounced commit. Each new focus restarts the timer and a fast scroll
    // through the rail commits only where it stops.
    androidx.compose.runtime.LaunchedEffect(pendingSeq) {
        val next = pending ?: return@LaunchedEffect
        kotlinx.coroutines.delay(RailSelectDebounceMs)
        // `pending` is cleared when the selection moves from outside the rail
        // (deep link); a commit launched before that must not undo it.
        if (pending == next && next != currentSelection) currentOnSelect(next)
    }
    // A selection that changed without the rail (deep link, posture remap)
    // makes any pending rail focus stale. Left in place, a later flush or
    // debounce re-applied it and the pane landed one page behind the link
    // (aeriotv://settings/livetv showed Playlists).
    androidx.compose.runtime.LaunchedEffect(selection) {
        if (pending != null && pending != selection) pending = null
    }
    val flushPending = {
        pending?.let { if (it != selection) onSelect(it) }
    }

    // A pane-level push (playlist detail, Manage Playlists, a section's own
    // sub-page) swaps the PANE's content while the rail stays on screen. Focus
    // has to follow it in, or Compose falls back to the first focusable in the
    // tree - the rail's top row - and the user is silently back on the rail
    // with a push they can no longer see. The next D-pad move then reads as a
    // rail selection and popToRoot() throws the push away. Takeovers are
    // excluded: they replace the whole host and bring their own focus.
    //
    // POPPING one is the mirror case, and it needs handling here too. In a
    // two-pane host MainScaffold's `subScreenKey` is null for a pane push AND
    // for its pop, so its focus pull never fires on either edge; without this,
    // Back left focus to Compose's fallback, which parked it on the top tab
    // bar's Live TV pill (observed on the Streamer 2026-08-05). Logan's call is
    // that Back returns to the SIDEBAR, so aim at `railFocus` - which is
    // attached to the SELECTED rail row, landing the user exactly where they
    // pushed from. Takeovers are excluded on this edge too: MainScaffold's
    // exiting-to-root branch already focuses them out correctly.
    var prevPushed by remember { mutableStateOf<SettingsRoute?>(null) }
    androidx.compose.runtime.LaunchedEffect(pushed) {
        val previous = prevPushed
        if (pushed != null && pushed != previous) {
            com.aeriotv.android.ui.tv.TvFocusTrace.settingsLifecycle("push", encodeSettingsRoute(pushed))
        }
        if (pushed == null && previous != null) {
            com.aeriotv.android.ui.tv.TvFocusTrace.settingsLifecycle("pop", encodeSettingsRoute(previous))
        }
        if (pushed != null && !takeover(pushed)) {
            com.aeriotv.android.ui.tv.TvFocusTrace.settingsRequest("detailFocus", "push", detailFocus)
        } else if (pushed == null && previous != null && !takeover(previous)) {
            com.aeriotv.android.ui.tv.TvFocusTrace.settingsRequest("railFocus", "pop", railFocus)
        }
        prevPushed = pushed
    }

    androidx.compose.runtime.LaunchedEffect(focusPaneSignal) {
        if (focusPaneSignal == 0) return@LaunchedEffect
        // One frame for the new pane content to compose before aiming at it.
        kotlinx.coroutines.delay(RailSelectDebounceMs)
        com.aeriotv.android.ui.tv.TvFocusTrace.settingsRequest("detailFocus", "deeplink", detailFocus)
    }

    // Back in the pane returns focus to the rail instead of leaving Settings.
    // Disabled while something is pushed so the nav stack's own handler pops
    // first, and while the rail already holds focus so Back reaches the tabs.
    // Two-step Back, the same as DVR / Movies / TV Shows (Logan 2026-10-04):
    // Back anywhere in the Settings page (rail or pane, nothing pushed) parks
    // focus on the Settings pill and the page stays; a second Back on the
    // pill reaches the scaffold's "Back goes to the home tab" rule.
    val requestTabPill = com.aeriotv.android.feature.main.LocalTvRequestCurrentTabPill.current
    androidx.activity.compose.BackHandler(enabled = com.aeriotv.android.feature.main.LocalTabIsActive.current && pushed == null && hostHasFocus && requestTabPill != null) {
        val landed = requestTabPill?.invoke() == true
        com.aeriotv.android.ui.tv.TvFocusTrace.key("Back", landed, "settings-to-tab-pill")
        if (!landed) com.aeriotv.android.ui.tv.TvFocusTrace.settingsRequest("railFocus", "back", railFocus)
    }

    Row(modifier = Modifier.fillMaxSize().onFocusChanged { hostHasFocus = it.hasFocus }) {
        SettingsTvRail(
            selection = selection,
            sections = sections,
            activePlaylistName = activePlaylistName,
            syncEnabled = syncEnabled,
            onFocusRoute = { pending = it; pendingSeq++ },
            onClickRoute = { route ->
                pending = route
                if (route != selection) onSelect(route)
                com.aeriotv.android.ui.tv.TvFocusTrace.settingsRequest("detailFocus", "rail click", detailFocus)
            },
            railFocus = railFocus,
            modifier = Modifier
                .width(TvRailWidth + TvOverscanStart)
                .onFocusChanged { railHasFocus = it.hasFocus }
                .onPreviewKeyEvent { event ->
                    val right = event.key == Key.DirectionRight &&
                        event.type == KeyEventType.KeyDown
                    if (right) {
                        // Apply the pending selection BEFORE crossing the
                        // boundary, or the pane the user just entered would
                        // still be showing the previously committed route.
                        val changed = pending != null && pending != selection
                        flushPending()
                        if (!changed) {
                            com.aeriotv.android.ui.tv.TvFocusTrace.settingsRequest("detailFocus", "rail right", detailFocus)
                        } else {
                            // The pane is about to recompose with the new page:
                            // aiming now would land on the OLD page's row, which
                            // is then disposed and focus falls to the tab pill.
                            // Aim after the new page has composed, and retry
                            // until focus is inside the pane.
                            railRightScope.launch {
                                repeat(10) { i ->
                                    kotlinx.coroutines.delay(if (i == 0) 64L else 50L)
                                    if (detailHasFocus) return@launch
                                    com.aeriotv.android.ui.tv.TvFocusTrace.settingsRequest("detailFocus", "rail right (after select, try ${i + 1})", detailFocus)
                                }
                            }
                        }
                        true
                    } else {
                        false
                    }
                },
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .focusRequester(detailFocus)
                .onFocusChanged { detailHasFocus = it.hasFocus }
                // Left out of any page lands on the SELECTED rail row (the
                // geometric search picked whichever row was level with the
                // focused pane row, one row off).
                .focusProperties {
                    exit = { dir ->
                        if (dir == androidx.compose.ui.focus.FocusDirection.Left) {
                            android.util.Log.i("AerioFocus", "[SETTINGS] pane left -> rail row ${encodeSettingsRoute(selection)}")
                            railFocus
                        } else {
                            FocusRequester.Default
                        }
                    }
                }
                .focusGroup(),
        ) {
            if (pushed != null) {
                CompositionLocalProvider(
                    com.aeriotv.android.ui.settings.LocalSettingsTracePage provides encodeSettingsRoute(pushed),
                ) { detail(pushed) }
            } else if (selection is SettingsRoute.Root) {
                // Nothing picked yet (fresh TV host): a quiet placeholder until
                // the user moves into the rail. Focusing a row selects it.
                SettingsPanePlaceholder()
            } else {
                // Plan B5: a short crossfade on pane swaps, so walking the rail
                // does not hard-cut the whole right half of the screen. Content
                // only; explicitly NOT a shared-element transition.
                Crossfade(
                    targetState = selection,
                    animationSpec = tween(SettingsPaneCrossfadeMs),
                    label = "settingsPane",
                ) { paneRoute ->
                    // Keyed per pane so each section keeps its OWN scroll
                    // offset; switching rail rows still starts at the top.
                    paneStateHolder.SaveableStateProvider(encodeSettingsRoute(paneRoute)) {
                        CompositionLocalProvider(
                            LocalSettingsInPane provides true,
                            com.aeriotv.android.ui.settings.LocalSettingsTracePage provides encodeSettingsRoute(paneRoute),
                        ) {
                            detail(paneRoute)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsTvRail(
    selection: SettingsRoute,
    sections: List<SettingsSectionGroupSpec>,
    activePlaylistName: String?,
    syncEnabled: String,
    onFocusRoute: (SettingsRoute) -> Unit,
    onClickRoute: (SettingsRoute) -> Unit,
    railFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    // Flatten to one list so the selected index (for initial scroll + focus) is
    // a simple lookup rather than a per-section calculation. About arrives from
    // the closing section group like any other row.
    val railRole by com.aeriotv.android.core.profiles.ProfileRoles.active.collectAsState()
    val railChild = railRole == com.aeriotv.android.core.profiles.ProfileRole.Child
    val rows = remember(sections, activePlaylistName, syncEnabled, railChild) {
        buildList {
            // Phase 2b: a Child has no Playlists row; Manage Profiles closes the rail.
            if (!railChild) add(Triple(SettingsRoute.Playlists as SettingsRoute, "Playlists", activePlaylistName))
            sections.forEach { group ->
                group.sections.forEach { section ->
                    add(
                        Triple(
                            SettingsRoute.Section(section),
                            section.title,
                            settingsSectionSubtitle(section, syncEnabled),
                        ),
                    )
                }
            }
            if (railChild) add(Triple(SettingsRoute.ManageProfiles as SettingsRoute, "Manage Profiles", null))
        }
    }
    val selectedIndex = rows.indexOfFirst { it.first == selection }.coerceAtLeast(0)
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    androidx.compose.runtime.LaunchedEffect(Unit) {
        // Keep the selected row in view. Focus is NOT pulled here any more
        // (Logan 2026-09-03): landing on the Settings pill used to yank focus
        // into the rail, which selected Playlists. Down from the pill lands
        // on the rail through the tab-entry contract below.
        runCatching { listState.scrollToItem(selectedIndex) }
    }
    val tabEntry = com.aeriotv.android.feature.main.LocalTvTabEntryFocus.current
    // Published only while Settings is the ACTIVE tab: Settings composes warm
    // after a cold launch, and an unconditional publish replaced the landing
    // tab's entry, so Down from that tab's pill requested the hidden rail
    // (Streamer 2026-10-04, DVR landing: [SETTINGS] rail focus on Down).
    val railTabActive = com.aeriotv.android.feature.main.LocalTabIsActive.current
    androidx.compose.runtime.DisposableEffect(railFocus, railTabActive) {
        if (railTabActive) tabEntry.value = railFocus
        onDispose { if (tabEntry.value === railFocus) tabEntry.value = null }
    }
    // Read through rememberUpdatedState so the focus callback sees the live
    // value: a GMS or other overlay activity takes the window, Compose parks
    // focus on the first rail row, and that gain must not become a selection.
    val windowFocused = androidx.compose.runtime.rememberUpdatedState(
        androidx.compose.ui.platform.LocalWindowInfo.current.isWindowFocused,
    )
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxHeight(),
        // Top inset aligns the first rail row with the DETAIL PANE'S TITLE.
        // At 8dp the rail started ~20dp above it and read as floating higher
        // than the body it belongs to (Logan, on the Streamer). tvOS lines its
        // rail up with the pane content for the same reason.
        //
        // Still budgeted so ALL rail items clear the fold: tvOS fits its 9
        // without scrolling, Android carries 11 (Remote Control is TV-only,
        // App Updates sideload-only), so the pitch stays near 40dp and the
        // inset can only spend what the budget leaves.
        contentPadding = PaddingValues(
            start = TvOverscanStart,
            end = 12.dp,
            top = 30.dp,
            bottom = 12.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        itemsIndexed(rows, key = { _, row -> encodeSettingsRoute(row.first) }) { index, row ->
            val (route, title, subtitle) = row
            // onFocusChanged re-reports the CURRENT state whenever the row's
            // modifier chain changes (the railFocus requester moves to the
            // newly selected row on a deep link). Counting only real focus
            // GAINS keeps that echo from queuing the old row as a selection.
            var wasFocused by remember { mutableStateOf(false) }
            SettingsNavRow(
                title = title,
                subtitle = subtitle,
                icon = settingsRouteIcon(route),
                // tvOS draws DVR as a red record.circle, not an accent glyph.
                iconTint = if (route == SettingsRoute.Section(SettingsSection.DvrSettings)) {
                    TvDvrRecordRed
                } else {
                    null
                },
                onClick = { onClickRoute(route) },
                selected = route == selection,
                trailingChevron = false,
                flat = true,
                modifier = Modifier
                    .onFocusChanged {
                        if (it.isFocused && !wasFocused) {
                            val key = encodeSettingsRoute(route)
                            if (!windowFocused.value) {
                                // An overlay activity (GMS account picker,
                                // consent) took the window and Compose's
                                // fallback parked focus here. Never commit
                                // that as a rail selection.
                                com.aeriotv.android.ui.tv.TvFocusTrace.settings(
                                    "rail focus ignored (window unfocused) row=$key",
                                )
                            } else {
                                com.aeriotv.android.ui.tv.TvFocusTrace.settings("rail focus row=$key")
                                com.aeriotv.android.ui.tv.TvFocusTrace.settingsRailFocused(key)
                                onFocusRoute(route)
                            }
                        }
                        wasFocused = it.isFocused
                    }
                    .then(
                        if (index == selectedIndex) Modifier.focusRequester(railFocus)
                        else Modifier,
                    ),
            )
        }
    }
}

/** The system red Apple uses for the tvOS DVR rail glyph (UIColor.systemRed, dark). */
private val TvDvrRecordRed = androidx.compose.ui.graphics.Color(0xFFFF453A)

/** Rail/sidebar glyph for a route. Sections carry their own. */
private fun settingsRouteIcon(route: SettingsRoute) = when (route) {
    // tvOS record.circle: a ring around a dot. FiberManualRecord (the phone
    // glyph) is only the dot.
    SettingsRoute.Section(SettingsSection.DvrSettings) -> Icons.Outlined.RadioButtonChecked
    is SettingsRoute.Section -> route.section.icon
    is SettingsRoute.About -> Icons.Outlined.Info
    is SettingsRoute.ManageProfiles -> Icons.Outlined.ManageAccounts
    else -> Icons.AutoMirrored.Filled.List
}

/** Right pane before any rail row is picked. */
@Composable
private fun SettingsPanePlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        androidx.compose.material3.Text(
            text = "Select a setting",
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

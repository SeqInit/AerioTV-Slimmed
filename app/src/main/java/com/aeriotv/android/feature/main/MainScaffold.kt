package com.aeriotv.android.feature.main

import kotlinx.coroutines.launch
import com.aeriotv.android.core.data.db.entity.dispatcharrCanViewDvr
import com.aeriotv.android.core.data.db.entity.dispatcharrCanViewVod
import com.aeriotv.android.core.data.db.entity.dispatcharrCanViewSeries
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.dropShadow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import com.aeriotv.android.core.pip.findActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.aeriotv.android.core.data.M3UChannel
import com.aeriotv.android.core.playback.AerioExoPlayerHolder
import com.aeriotv.android.feature.dvr.DvrViewModel
import com.aeriotv.android.feature.favorites.FavoritesTabContent
import com.aeriotv.android.feature.favorites.FavoritesViewModel
import com.aeriotv.android.feature.livetv.LiveTVTabContent
import com.aeriotv.android.feature.miniplayer.MiniPlayerSession
import com.aeriotv.android.feature.miniplayer.MiniPlayerViewModel
import com.aeriotv.android.feature.onboarding.ChooseSourceTypeScreen
import com.aeriotv.android.feature.onboarding.ConfigureSourceScreen
import com.aeriotv.android.feature.ondemand.OnDemandTabContent
import com.aeriotv.android.feature.ondemand.OnDemandViewModel
import com.aeriotv.android.feature.playlist.PlaylistViewModel
import com.aeriotv.android.feature.settings.AddMoreCategoriesScreen
import com.aeriotv.android.feature.settings.AddPlaylistWizardStep
import com.aeriotv.android.feature.settings.AppearanceSettingsScreen
import com.aeriotv.android.feature.settings.DeveloperSettingsScreen
import com.aeriotv.android.feature.settings.DvrSettingsScreen
import com.aeriotv.android.feature.settings.GeneralSettingsScreen
import com.aeriotv.android.feature.settings.LiveTvSettingsScreen
import com.aeriotv.android.feature.settings.MoviesAndTvShowsSettingsScreen
import com.aeriotv.android.feature.settings.PlayerSettingsScreen
import com.aeriotv.android.feature.settings.SettingsRootContent
import com.aeriotv.android.feature.settings.DefaultSettingsPaneSelection
import com.aeriotv.android.feature.settings.SettingsRoute
import com.aeriotv.android.feature.settings.SettingsScreen
import com.aeriotv.android.feature.settings.SettingsSection
import com.aeriotv.android.feature.settings.SettingsViewModel
import com.aeriotv.android.ui.adaptive.rememberViewport
import com.aeriotv.android.feature.settings.SettingsTvRailHost
import com.aeriotv.android.feature.settings.SettingsPaneSplit
import com.aeriotv.android.feature.settings.rememberSettingsNavState
import com.aeriotv.android.feature.settings.rememberSettingsPaneSelection
import com.aeriotv.android.feature.settings.isPaneBaseline
import com.aeriotv.android.feature.settings.settingsDeepLinkPageNeedsPlaylist
import com.aeriotv.android.feature.settings.settingsRouteForDeepLinkPage
import com.aeriotv.android.feature.settings.visibleSettingsSections
import com.aeriotv.android.feature.settings.allowedFor

/** True while the TV side menu or Settings panel is open. Tab content reads
 *  it so a launch-focus grab never yanks focus out of the menu while the user
 *  is still walking it (Logan 2026-09-02: a guide refocus loop stole focus
 *  mid-walk and every further key went into the grid). */
val LocalTvTopNavHasFocus = staticCompositionLocalOf<androidx.compose.runtime.State<Boolean>> {
    androidx.compose.runtime.mutableStateOf(false)
}

/** Where focus should land inside the current tab when the TV side menu
 *  closes onto it. A tab sets it (On Demand: its current sub-tab pill) and
 *  clears it on dispose; null keeps Compose's geometric search. */
val LocalTvTabEntryFocus = staticCompositionLocalOf<androidx.compose.runtime.MutableState<FocusRequester?>> {
    androidx.compose.runtime.mutableStateOf(null)
}

/** Open the TV side menu ("go to the nav"), returning whether it took
 *  focus. Back or Up out of a TV page's content uses it. `null` on the phone
 *  shell. */
val LocalTvRequestCurrentTabPill = staticCompositionLocalOf<(() -> Boolean)?> { null }

/**
 * TV full-screen overlay slot: a tab sets a composable here to draw ABOVE
 * the whole shell (tab bar included) without a Dialog window. The guide's
 * Jump To sheet uses it so its scrim covers the bar (Logan 2026-09-10).
 */
/**
 * ANDROID (Sidebar-style interface): how far the open side menu pushes the
 * content right (0 when closed), animated. The guide reads it to place its
 * groups column beside the menu and push itself further by that column.
 */
val LocalTvSideMenuWidth =
    staticCompositionLocalOf<androidx.compose.runtime.State<androidx.compose.ui.unit.Dp>> {
        androidx.compose.runtime.mutableStateOf(0.dp)
    }

val LocalTvFullScreenOverlay =
    staticCompositionLocalOf<androidx.compose.runtime.MutableState<(@Composable () -> Unit)?>?> { null }

/**
 * Top-level scaffold once a playlist is loaded. Mirrors iOS MainTabView with the
 * caveat that tabs are CONDITIONAL on content (see [visibleTabs]) - matching
 * iOS, the test-server screenshots show only 4 tabs not 5.
 */
@Composable
fun MainScaffold(
    onChannelClick: (M3UChannel) -> Unit,
    /** Expanding the mini: plays the channel, skipping the Radio Mode pick. */
    onChannelResume: (M3UChannel) -> Unit = onChannelClick,
    onMovieClick: (String) -> Unit = {},
    onSeriesClick: (Int) -> Unit = {},
    onEpisodeResume: (String) -> Unit = {},
    onResumeMovie: (String) -> Unit = {},
    /** Media center hero: play a movie directly (resumes from saved progress). */
    onPlayMovie: (String) -> Unit = {},
    /** Media center hero "Play from Beginning": start at 0 and KEEP the
     *  Continue Watching row (tvOS MoviesView:1507-1511). */
    onPlayMovieFromStart: (String) -> Unit = onPlayMovie,
    onEpisodeResumeFromStart: (String) -> Unit = onEpisodeResume,
    /** (playbackUrl, title, dispatcharrRecordingId or -1). */
    onPlayRecording: (String, String, Int) -> Unit = { _, _, _ -> },
    /** Catch-up (task #136): url, title, progStartMillis, progEndMillis, panelTz. */
    onPlayCatchup: (String, String, String, Long, Long, String, String) -> Unit = { _, _, _, _, _, _, _ -> },
    onWatchLive: (String, String, Boolean, Long, Int?) -> Unit = { _, _, _, _, _ -> },
    onWatchFromBeginning: (String, String, Boolean, Long, Int?, Boolean) -> Unit = { _, _, _, _, _, _ -> },
    onOpenSearch: () -> Unit = {},
    viewModel: PlaylistViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Keep Recent Channels Live: app-bar indicator + dialog live HERE (not
    // in GuideScreen) so the TV circle is part of the nav bar's existing
    // focus row and works from every content tab.
    val retainedVm: com.aeriotv.android.feature.livetv.RetainedChannelsViewModel = hiltViewModel()
    val retainedList by retainedVm.retained.collectAsStateWithLifecycle()
    // KEPT badges (guide rail, channel list) read this set.
    val retainedIds = remember(retainedList) { retainedList.mapTo(HashSet()) { it.channelId } }
    val tuneRetained: (String) -> Unit = { id ->
        state.channels.firstOrNull { it.id == id }?.let(onChannelClick)
    }
    var showRetainedDialog by remember { mutableStateOf(false) }
    if (showRetainedDialog) {
        com.aeriotv.android.feature.livetv.RetainedChannelsDialog(
            viewModel = retainedVm,
            onJumpToChannel = { id ->
                state.channels.firstOrNull { it.id == id }?.let(onChannelClick)
            },
            onDismiss = { showRetainedDialog = false },
        )
    }
    val favoritesVm: FavoritesViewModel = hiltViewModel()
    // null until the DB emits: on the phone, backing out of the player
    // recomposes this scaffold from scratch and an empty INITIAL list read as
    // "no favorites" for a frame, the Favorites tab vanished, and the tabs
    // fallback below bounced a Favorites-first user to Live TV (GH #81
    // follow-up, PsykoRider 2026-09-02).
    val favoritesOrNull by favoritesVm.all.collectAsStateWithLifecycle()
    val favorites = favoritesOrNull ?: emptyList()
    // Show the Favorites tab only when the user has at least one favorite
    // that ALSO exists in the active playlist. The raw DB count would keep
    // the tab pinned to the bottom bar after a playlist switch left stale
    // orphan rows pointing at channel ids that no longer exist - the user
    // would see the tab, tap it, and find an empty "No Favorites" body
    // even though the DB count was non-zero.
    // GH #81: an EMPTY channel list is "still loading" (playlist refresh,
    // return from the player, sync apply), not "no favorites". Dropping the
    // flag there removed the Favorites tab for a frame, and the tabs
    // fallback below bounced the user to Live TV every time. Keep the last
    // verdict until channels are back; an empty favorites list is a real
    // DB emission and still retires the tab.
    var lastRenderableFavorites by rememberSaveable { mutableStateOf(false) }
    val hasRenderableFavorites = remember(favoritesOrNull, state.channels) {
        if (favoritesOrNull == null) return@remember lastRenderableFavorites
        if (favorites.isEmpty()) return@remember false
        if (state.channels.isEmpty()) return@remember lastRenderableFavorites
        val visibleIds = state.channels.asSequence().map { it.id }.toHashSet()
        favorites.any { it.channelId in visibleIds }.also { lastRenderableFavorites = it }
    }
    // Dynamic On Demand + DVR tabs (iOS MainTabView.hasVOD / hasRecordings
    // parity). Both ViewModels are hoisted here so the tabs can appear / vanish
    // based on actual content, NOT just the source type. hiltViewModel() resolves
    // to the SAME instance the tab body uses (shared ViewModelStoreOwner), so this
    // adds no duplicate fetch; it just makes the eager load drive tab visibility.
    val onDemandVm: OnDemandViewModel = hiltViewModel()
    // Collect only the bits the scaffold needs, de-duplicated: the catalog
    // walk emits a new state per page (~100 pages after launch) and collecting
    // the whole state here recomposed the entire scaffold, every tab included,
    // on each one (Streamer 2026-09-03: "every tab redraws" for the first
    // 30-45 s).
    val onDemandState by remember(onDemandVm) {
        onDemandVm.state
            .map { VodPresence.from(it) }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = VodPresence.from(onDemandVm.state.value))
    // Per-playlist VOD opt-in gate (iOS HomeView.swift:317 servers.filter
    // { $0.supportsVOD && $0.vodEnabled }). Read directly off the active
    // playlist so the tab hides immediately when the user toggles "Fetch On
    // Demand from this playlist" OFF at Add / Edit time, without waiting for
    // the next OnDemandViewModel refresh to flip unsupportedSource. If the
    // playlist hasn't loaded yet the default is true so we don't suppress
    // the tab on a slow cold launch.
    // LIVE active playlist row, not `state.playlist`. The latter is a snapshot
    // frozen when the state was last written, so the AerioCaps capability probe
    // that lands after launch was invisible to every gate below: the Streamer
    // restored 40050 movies, ran the forced sweep, and STILL hid the Movies tab
    // because `moviesDenied` was computed from the pre-probe row and retired the
    // pill on every recomposition (2026-09-15). Falls back to the snapshot only
    // until the first DB emission.
    val livePlaylist by viewModel.activePlaylistLive
        .collectAsStateWithLifecycle(initialValue = null)
    val capsPlaylist = livePlaylist ?: state.playlist
    val activePlaylistVodEnabled = capsPlaylist?.vodEnabled ?: true
    // hasVOD: any movie/series loaded, OR still loading its library. The loading
    // bridge keeps the tab from flickering "absent -> present" on cold launch /
    // source switch; a source that finishes with zero VOD hides the tab entirely.
    val hasVodContent = activePlaylistVodEnabled && !onDemandState.unsupportedSource && (
        onDemandState.hasMovies ||
            onDemandState.hasSeries ||
            onDemandState.isLoading ||
            onDemandState.isLoadingSeries ||
            // XC: the cheap probe found categories but deferred the heavy walk
            // until the tab is opened -- show the tab on the probe result alone.
            onDemandState.hasDeferredXtreamContent
        )
    val dvrVm: DvrViewModel = hiltViewModel()
    val dvrState by dvrVm.state.collectAsStateWithLifecycle()
    // hasRecordings: at least one recording (scheduled / recording / completed,
    // server or local) for the active source. Scheduling from the guide makes the
    // tab appear; deleting the last recording makes it disappear.
    // hasRecordingsHint: persisted last-session verdict so the tab shows from
    // the first frame after launch / playlist switch instead of popping in when
    // the server list arrives; retires as soon as the real list loads.
    // Dispatcharr 0.30 dvr_access "none": server recordings are not
    // listable; only a local recording in progress shows the tab.
    // Capability.CanViewDvr. Denied (dvr_access "none" / level < 1) hides the
    // server list; Unknown keeps it, so an unprobed account is never downgraded.
    // DENIED is a real, user-visible state change (like favorites emptied), so
    // it must also retire an already-shown tab below; the sticky rule must not
    // pin it. Note state.playlist is NULL for the first frames after launch:
    // that reads as Unknown (tab allowed), and the persisted hint would show
    // the tab, so the retire-on-Denied pass below is what actually hides it
    // once the active playlist and its capability snapshot land.
    val dvrDenied = capsPlaylist?.dispatcharrCanViewDvr() == false
    val dvrListable = !dvrDenied
    // Local recordings belong to the DEVICE, not the server: an in-progress
    // capture or any saved local row keeps the tab regardless of dvr_access.
    val hasLocalDvrContent = dvrState.isLocalRecordingActive ||
        dvrState.recordings.any { it.source == DvrViewModel.Source.Local }
    val hasRecordings = (dvrListable && (dvrState.recordings.isNotEmpty() || dvrState.hasRecordingsHint)) ||
        hasLocalDvrContent
    // Sticky tabs (Streamer 2026-09-03, "every tab flashes"): the loaders
    // behind DVR and On Demand briefly report "nothing" while they transition
    // (On Demand dropped out for 0.8 s between its movie and series passes;
    // DVR flickers on its list refresh). When that happens while the user is
    // on that pill, the focused pill is removed, Compose drops focus to the
    // leftmost control, selection follows to Live TV and the tab flashes. A
    // tab that has been shown stays for the rest of the playlist session;
    // only a real user action retires it (favorites emptied, On Demand
    // switched off for the playlist, or an unsupported source).
    // Media center (phone/tablet): Movies and TV Shows tabs, each shown while
    // its library has content or is still loading. TV keeps On Demand.
    // Every form factor now (Android TV joined 2026-09-10 with TvMediaPage).
    val splitVod = true
    // Same three-state gate as DVR: Denied retires the tab even once shown,
    // Unknown (including the null playlist on the first frames) keeps it.
    val moviesDenied = capsPlaylist?.dispatcharrCanViewVod() == false
    val seriesDenied = capsPlaylist?.dispatcharrCanViewSeries() == false
    val vodSourceOk = activePlaylistVodEnabled && !onDemandState.unsupportedSource
    val hasMoviesContent = vodSourceOk && !moviesDenied &&
        (onDemandState.hasMovies || onDemandState.isLoading || onDemandState.hasDeferredXtreamContent)
    val hasSeriesContent = vodSourceOk && !seriesDenied &&
        (onDemandState.hasSeries || onDemandState.isLoadingSeries || onDemandState.hasDeferredXtreamContent)
    val stickyTabs = remember(capsPlaylist?.id ?: state.playlist?.id) { mutableSetOf<AppTab>() }
    val tabs = run {
        val live = visibleTabs(
            // Phone/tablet: Favorites is a pinned Live TV group, not a tab (Apple parity).
            // Favorites is the pinned Live TV pill on every form factor (tvOS dropped the tab 2026-09-05).
            hasFavorites = false,
            hasVod = hasVodContent,
            hasRecordings = hasRecordings,
            splitVod = splitVod,
            hasMovies = hasMoviesContent,
            hasSeries = hasSeriesContent,
        )
        stickyTabs += live
        stickyTabs -= AppTab.Favorites
        if (!vodSourceOk) { stickyTabs -= AppTab.OnDemand; stickyTabs -= AppTab.Movies; stickyTabs -= AppTab.TVShows }
        // A DENIED capability verdict is a real state change, not the transient
        // "empty list / still loading" the sticky rule exists to absorb, so it
        // retires the tab even after it has been shown. This is the dvr_access
        // "none" case: the null playlist on the first frames reads as Unknown,
        // the persisted hint paints the DVR pill, and without this the tab was
        // pinned for the session once the verdict arrived. Local recordings
        // still win over a server denial.
        if (dvrDenied && !hasLocalDvrContent) stickyTabs -= AppTab.DVR
        if (moviesDenied) stickyTabs -= AppTab.Movies
        if (seriesDenied) stickyTabs -= AppTab.TVShows
        if (moviesDenied && seriesDenied) stickyTabs -= AppTab.OnDemand
        visibleTabs(
            hasFavorites = AppTab.Favorites in stickyTabs,
            hasVod = AppTab.OnDemand in stickyTabs,
            hasRecordings = AppTab.DVR in stickyTabs,
            splitVod = splitVod,
            hasMovies = AppTab.Movies in stickyTabs,
            hasSeries = AppTab.TVShows in stickyTabs,
        )
    }
    // One line per change of the inputs, so "why is the tab hidden" is provable
    // from logcat instead of from the screen.
    androidx.compose.runtime.LaunchedEffect(
        moviesDenied, seriesDenied, vodSourceOk, hasMoviesContent, hasSeriesContent,
        onDemandState, tabs,
    ) {
        android.util.Log.i(
            "MainScaffold",
            "[VOD-TAB] vodEnabled=$activePlaylistVodEnabled unsupported=${onDemandState.unsupportedSource} " +
                "sourceOk=$vodSourceOk | movies: denied=$moviesDenied has=${onDemandState.hasMovies} " +
                "loading=${onDemandState.isLoading} -> show=$hasMoviesContent | " +
                "series: denied=$seriesDenied has=${onDemandState.hasSeries} " +
                "loading=${onDemandState.isLoadingSeries} -> show=$hasSeriesContent | tabs=$tabs",
        )
    }
    val miniPlayerVm: MiniPlayerViewModel = hiltViewModel()
    val miniPlayerState by miniPlayerVm.state.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val exoHolder = remember {
        dagger.hilt.android.EntryPointAccessors.fromApplication(
            context.applicationContext,
            MainScaffoldEntryPoint::class.java,
        ).exoPlayerHolder()
    }
    val exoWindowMode by remember {
        dagger.hilt.android.EntryPointAccessors.fromApplication(
            context.applicationContext,
            MainScaffoldEntryPoint::class.java,
        ).exoWindowState().mode
    }.collectAsStateWithLifecycle()
    // Poll pause state from the held ExoPlayer so the mini-player's
    // Pause/Play icon stays accurate when the notification action /
    // BT button toggles playback elsewhere.
    var miniPaused by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(miniPlayerState) {
        if (miniPlayerState !is com.aeriotv.android.feature.miniplayer.MiniPlayerSession.State.Active) return@LaunchedEffect
        while (true) {
            miniPaused = exoHolder.isPaused()
            kotlinx.coroutines.delay(500L)
        }
    }

    val settingsVm: SettingsViewModel = hiltViewModel()
    val defaultTabPref by settingsVm.defaultTab.collectAsStateWithLifecycle(initialValue = "")

    var selectedTab by rememberSaveable { mutableStateOf(AppTab.LiveTV) }
    var initialTabApplied by rememberSaveable { mutableStateOf(false) }
    // Which tabs are already composed and alive (see MainTabContent). Hoisted
    // here because the TV tab bar needs it too: switching to a tab that is
    // ALREADY built is free, so it must commit on the same frame as the pill
    // highlight, while a first visit still gets the settle window that keeps a
    // pill walk from cold-building every tab it passes through.
    val visitedTabs = rememberSaveable(saver = androidx.compose.runtime.saveable.listSaver(
        save = { it.map { t -> t.name } },
        restore = { names -> names.mapNotNull { n -> AppTab.entries.firstOrNull { it.name == n } }.toMutableStateList() },
    )) { mutableStateListOf<AppTab>() }

    // Honour the saved defaultTab once its tab is actually available. On Demand /
    // DVR now materialise a beat after launch (their content loads async), so we
    // must NOT latch on the empty initial pref or while the target tab is still
    // missing - otherwise a "default = On Demand" preference would be dropped on
    // the floor before the tab appeared. We latch only when the target is applied;
    // a manual tab tap also latches (see onSelect / NavigationBarItem onClick) so
    // the default never overrides a deliberate user choice. Empty pref = Live TV
    // (already the initial selection), so there's nothing to apply.
    LaunchedEffect(defaultTabPref, tabs) {
        if (initialTabApplied || defaultTabPref.isEmpty()) return@LaunchedEffect
        // A default saved as On Demand opens Movies where the tab was split.
        val target = AppTab.entries.firstOrNull { it.name == defaultTabPref }
            ?.let { if (it == AppTab.OnDemand && splitVod) AppTab.Movies else it }
            ?.let { if (it == AppTab.Favorites && splitVod) AppTab.LiveTV else it }
        when {
            target == null -> initialTabApplied = true
            target in tabs -> {
                selectedTab = target
                initialTabApplied = true
            }
            // else: target tab not present yet (content still loading); keep
            // waiting - this effect re-fires when `tabs` changes.
        }
    }

    // If the currently-selected tab disappears (e.g. user clears playlist, sourceType
    // changes), fall back to Live TV.
    LaunchedEffect(tabs) {
        // Search is never IN `tabs` (it's the floating bar button, not a
        // pill) but is a perfectly valid selection - don't bounce it.
        if (selectedTab !in tabs && selectedTab != AppTab.Search) {
            selectedTab = AppTab.LiveTV
        }
    }

    // EPG-search guide jump (iOS: MainTabView switches to .liveTV +
    // ChannelListView sets showGuideView=true). When a Search EPG result is
    // tapped (warm path) or an aeriotv://guide deep link is consumed (cold
    // path re-emits through requestGuideJump), select the Live TV tab.
    // LiveTVTabContent collects the same SharedFlow to force its SESSION view to
    // Guide (so the target cell exists to scroll/focus), and GuideScreen collects
    // it too (replay=1) to do the scroll + focus. This used to write the persisted
    // defaultLiveTVView("guide") here, which both failed to switch when the view
    // was already resolving to Guide and clobbered the Drive-synced default across
    // devices; forcing the session view instead avoids both.
    LaunchedEffect(Unit) {
        viewModel.guideJumpRequests.collect {
            selectedTab = AppTab.LiveTV
            initialTabApplied = true
        }
    }

    // Screenshot deep link (aeriotv://settings/<page>). Select the Settings
    // tab so SettingsTabContent composes; that composable then reads the same
    // pending request and applies the route (it cannot be applied from here --
    // the nav stack and pane selection live inside it). The request is a
    // StateFlow, so it is still readable on the frame after this switch and on
    // a cold launch where Settings had never been composed.
    val pendingSettingsPage by viewModel.settingsPageRequest.collectAsStateWithLifecycle()
    LaunchedEffect(pendingSettingsPage) {
        if (pendingSettingsPage != null) {
            selectedTab = AppTab.Settings
            initialTabApplied = true
        }
    }

    // Back from any secondary tab returns to the HOME tab instead of exiting
    // the app. Home is the user's Default Tab when it is set and present
    // (GH #81: a Favorites-first user expects Back to land on Favorites,
    // not Live TV), else Live TV. On the home tab this handler is disabled
    // so Back falls through to the default (mini-player / exit). The
    // Settings sub-screen BackHandler in SettingsTabContent is composed
    // DEEPER and is enabled only while a sub-screen is open, so it takes
    // priority there; this only fires on a tab root.
    val homeTab = AppTab.entries.firstOrNull { it.name == defaultTabPref }
        ?.let { if (it == AppTab.OnDemand && splitVod) AppTab.Movies else it }
        ?.takeIf { it in tabs && it != AppTab.Search } ?: AppTab.LiveTV
    // TV: the leaving tab's content nodes vanish, and Compose's fallback
    // hands focus to the LEFTMOST pill (Live TV) while the home tab is
    // selected (Logan 2026-09-02 screenshot). Ask for the home pill instead;
    // the bar treats a non-UP focus arrival as a fallback, so selection does
    // not follow it.
    // Focus the home pill BEFORE the tab switches (the pill already exists),
    // so the leaving content's nodes vanish with focus already parked there
    // and no fallback flashes the Live TV pill (Logan: "less than a quarter
    // of a second on Live TV before settling on Favorites").
    var sideMenuOpen by remember { mutableStateOf(false) }
    // TV: Settings opens as a right-hand panel over the current tab rather
    // than as a tab of its own.
    var settingsPanelOpen by remember { mutableStateOf(false) }
    // TV: Back from a tab root opens the side menu.
    androidx.activity.compose.BackHandler(enabled = selectedTab != homeTab && !sideMenuOpen) {
        sideMenuOpen = true
    }

    // Background-activity flag. ORs the content-fetch flags so the activity
    // indicators show while the channel list, EPG/guide, or On Demand library
    // is still loading -- an activity light, NOT a cross-device-sync status.
    // It drives the TV bar's spinning Refresh circle and the phone Live TV
    // header's spinning indicator; the old "Syncing | Tap for Info" pill it
    // used to drive was removed (Logan 2026-09-14: it overlapped the sidebar
    // button on both shells).
    val anyBackgroundWork = state.isLoading || state.isEpgLoading ||
        onDemandState.isLoading || onDemandState.isLoadingSeries

    // Pre-warm (Logan 2026-09-11): a FIRST visit to a tab still cost 419-567 ms
    // key to frame on the Streamer, of which ~250 ms was the settle window and
    // 130-280 ms was the tab's own first composition plus layout. Once Live TV
    // has painted and the app is idle, each present tab is composed into its
    // hidden keep-alive slot, one at a time, cheapest first, so the user's
    // first real visit is a warm one. A pre-warmed tab counts as visited, so
    // the settle window never runs for it either. Never pre-warms a tab that
    // is not present, never while the channel or guide load is saturating the
    // main thread, and TV only (the phone keeps one tab at a time).
    val prewarmTabsNow = androidx.compose.runtime.rememberUpdatedState(tabs)
    // Gate on the CHANNEL and GUIDE load only, not on every sync label. The
    // VOD labels ("Loading Movies" / "Loading Series") stay up for tens of
    // seconds and are exactly the work that makes the Movies and TV Shows tabs
    // appear in the first place, so waiting on them starved the sweep and the
    // media tabs were still cold after 45 s (measured 2026-09-11).
    val prewarmBusyNow = androidx.compose.runtime.rememberUpdatedState(
        state.isLoading || state.isEpgLoading,
    )
    // Memory gate: a laid-out hidden page is worth about 100 MB across the
    // three media tabs plus Settings (Streamer, 4 GB). That is fine there and
    // NOT on a 2 GB Onn or a 3 GB Shield, so low-RAM and sub-3 GB boxes skip
    // the sweep entirely and keep the old behavior: a tab composes on its
    // first visit. They lose nothing else - hidden slots still measure at the
    // real size once a tab has been visited, so there are no slivers, and the
    // hero's own zero-width guard covers the first pass either way.
    val prewarmAllowed = remember(context) {
        val am = context.getSystemService(android.content.Context.ACTIVITY_SERVICE)
            as? android.app.ActivityManager
        val info = android.app.ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }
        val lowRam = am?.isLowRamDevice ?: false
        val totalMem = info.totalMem
        !lowRam && totalMem >= 3L * 1024L * 1024L * 1024L
    }
    // A sweep already under way stops for good the first time the system asks
    // for memory back (TRIM_MEMORY_RUNNING_LOW or worse). The tabs it already
    // built stay: they are the ones the user is most likely to open, and
    // dropping them would put the reload back.
    val prewarmStopped = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    DisposableEffect(context) {
        val cb = object : android.content.ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
                    prewarmStopped.set(true)
                }
            }
            override fun onConfigurationChanged(newConfig: android.content.res.Configuration) = Unit
            @Deprecated("ComponentCallbacks")
            override fun onLowMemory() { prewarmStopped.set(true) }
        }
        context.registerComponentCallbacks(cb)
        onDispose { context.unregisterComponentCallbacks(cb) }
    }
    LaunchedEffect(prewarmAllowed) {
        if (!prewarmAllowed) return@LaunchedEffect
        androidx.compose.runtime.withFrameNanos { }
        kotlinx.coroutines.delay(2_000L)
        // Cheapest first (measured cold key-to-frame: Settings and Favorites
        // are plain columns, DVR/Movies/TV Shows are the LazyVerticalGrid
        // media pages, On Demand is the legacy grid).
        val order = listOf(
            AppTab.Settings, AppTab.Favorites, AppTab.DVR,
            AppTab.Movies, AppTab.TVShows, AppTab.OnDemand,
        )
        // Tabs can still materialize a beat after launch (VOD, recordings), so
        // keep sweeping for a while instead of taking one snapshot.
        val deadline = android.os.SystemClock.uptimeMillis() + 180_000L
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            if (prewarmStopped.get()) return@LaunchedEffect
            val next = order.firstOrNull {
                it in prewarmTabsNow.value && it !in visitedTabs
            }
            if (next == null) { kotlinx.coroutines.delay(2_000L); continue }
            if (prewarmBusyNow.value) { kotlinx.coroutines.delay(500L); continue }
            visitedTabs.add(next)
            kotlinx.coroutines.delay(500L)
        }
    }

    // iOS Issue #24: when the app returns to the foreground, refresh the guide
    // if it has gone stale (>30min). Skip the first ON_START (cold launch
    // already loads the EPG) so a normal launch never double-fetches.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var sawFirstStart by remember { mutableStateOf(false) }
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START) {
                // EpgSweepGate.appInForeground follows the process lifecycle
                // (Android#131): this destination's lifecycle reads STOPPED
                // while the full-screen player route is on top. A return here
                // re-checks the sources gate (rate limited in the repository)
                // and repaints a guide a background trim shed.
                viewModel.repaintGuideIfShed("main-start")
                if (sawFirstStart) {
                    viewModel.refreshEpgIfStale()
                    viewModel.checkEpgSourcesForChanges()
                } else {
                    sawFirstStart = true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Periodic foreground gate re-check: every 15 minutes, ask the server
    // whether its EPG sources moved and let the repository sweep the grid in
    // the background if they did. repeatOnLifecycle(STARTED) so the tick does
    // not fire while the app is backgrounded.
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(
            androidx.lifecycle.Lifecycle.State.STARTED,
        ) {
            while (true) {
                kotlinx.coroutines.delay(15L * 60L * 1000L)
                viewModel.checkEpgSourcesForChanges()
            }
        }
    }

    // Android TV / Google TV: the sidebar shell. TvSideMenu is the tab
    // navigation, Settings opens as a right-hand panel, and the tab content
    // fills the screen. Phone / tablet / fold keep the bottom nav below.
    // "Go to the nav" (Back out of a TV page's content) opens the menu.
    val requestCurrentTabPill: () -> Boolean = remember {
        {
            sideMenuOpen = true
            true
        }
    }
    val fullScreenOverlay = remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
    // The menu pushes the content instead of covering it.
    val sideMenuShift = androidx.compose.animation.core.animateDpAsState(
        targetValue = if (sideMenuOpen) TvSideMenuWidth else 0.dp,
        label = "tvSideMenuShift",
    )
    val topNavHasFocusState: androidx.compose.runtime.MutableState<Boolean> = remember { mutableStateOf(false) }
    val tabEntryFocus: androidx.compose.runtime.MutableState<FocusRequester?> = remember { mutableStateOf(null) }
    // Side menu close: give focus back to the content. Same tab = the
    // restorer's last-focused node; a new tab = its entry target (it may
    // need a frame or two to compose).
    val sidebarContentFocus = remember { FocusRequester() }
    // The open menu stands in for a nav bar in LocalTvTopNavHasFocus: the
    // guide's refocus loops (launch, sidebar close, tab entry) back off
    // while the nav holds focus, and without this they pulled focus
    // straight back to the grid the moment the menu opened.
    // Keyed, not a SideEffect: nothing in this scope reads sideMenuOpen
    // during composition, so a SideEffect never re-ran on open.
    androidx.compose.runtime.LaunchedEffect(sideMenuOpen, settingsPanelOpen) {
        topNavHasFocusState.value = sideMenuOpen || settingsPanelOpen
    }
    val sideMenuScope = androidx.compose.runtime.rememberCoroutineScope()
    val closeSideMenu: (Boolean) -> Unit = { switched ->
        sideMenuOpen = false
        sideMenuScope.launch {
            androidx.compose.runtime.withFrameNanos { }
            androidx.compose.runtime.withFrameNanos { }
            val restored = !switched && runCatching { sidebarContentFocus.requestFocus() }.getOrDefault(false)
            val landed = restored ||
                runCatching { tabEntryFocus.value?.requestFocus() }.getOrNull() == true ||
                runCatching { sidebarContentFocus.requestFocus() }.getOrDefault(false)
            com.aeriotv.android.ui.tv.TvFocusTrace.guide("tv-side-menu close switched=$switched landed=$landed")
        }
    }
    CompositionLocalProvider(
        LocalTvTopNavHasFocus provides topNavHasFocusState,
        LocalTvTabEntryFocus provides tabEntryFocus,
        LocalTvRequestCurrentTabPill provides requestCurrentTabPill,
        LocalTvFullScreenOverlay provides fullScreenOverlay,
        LocalTvSideMenuWidth provides sideMenuShift,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
        // The corner mini player is mounted at the ACTIVITY root, outside
        // this composition. With no bar, an undocked corner mini (a tab
        // without the guide header) sits 16 dp from the top.
        androidx.compose.runtime.LaunchedEffect(Unit) {
            com.aeriotv.android.feature.player.MiniPlayerChrome.topInsetDp.value = 16f
        }
        // Stash the corner mini while Settings is the selected tab;
        // cleared when the scaffold leaves composition so a player /
        // search route never inherits a stashed mini.
        // The Settings panel sits on the right, over the corner mini (an
        // undocked one); the docked preview on the left stays clear of it.
        // The side menu pushes the docked preview along with the guide,
        // so only the Settings panel can cover the mini.
        val dockRect by com.aeriotv.android.feature.player.MiniPlayerChrome
            .dockRectPx.collectAsStateWithLifecycle()
        val menuCoversMini = settingsPanelOpen && dockRect.width <= 0f
        androidx.compose.runtime.LaunchedEffect(selectedTab, menuCoversMini) {
            com.aeriotv.android.feature.player.MiniPlayerChrome
                .settingsStashed.value = selectedTab == AppTab.Settings || menuCoversMini
        }
        androidx.compose.runtime.DisposableEffect(Unit) {
            onDispose {
                com.aeriotv.android.feature.player.MiniPlayerChrome
                    .settingsStashed.value = false
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                com.aeriotv.android.feature.livetv.LocalRetainedChannelIds provides retainedIds,
            ) {
            MainTabContent(
                selectedTab = selectedTab,
                tabs = tabs,
                onChannelClick = onChannelClick,
                onMovieClick = onMovieClick,
                onSeriesClick = onSeriesClick,
                onEpisodeResume = onEpisodeResume,
                onResumeMovie = onResumeMovie,
                onPlayMovie = onPlayMovie,
                onPlayMovieFromStart = onPlayMovieFromStart,
                onEpisodeResumeFromStart = onEpisodeResumeFromStart,
                onPlayRecording = onPlayRecording,
                onPlayCatchup = onPlayCatchup,
                onWatchLive = onWatchLive,
                onWatchFromBeginning = onWatchFromBeginning,
                onOpenSearch = onOpenSearch,
                onSelectTab = { selectedTab = it; initialTabApplied = true },
                viewModel = viewModel,
                visited = visitedTabs,
                modifier = Modifier
                    .fillMaxSize()
                    .offset { androidx.compose.ui.unit.IntOffset(sideMenuShift.value.roundToPx(), 0) }
                    // The menu reseats focus here on close, and the
                    // restorer puts it back on whatever had it.
                    .focusRequester(sidebarContentFocus)
                    .focusRestorer()
                    .focusGroup(),
            )
            }
        }
                    fullScreenOverlay.value?.invoke()
            if (sideMenuOpen) {
                val menuActivity = androidx.compose.ui.platform.LocalContext.current
                TvSideMenu(
                    tabs = tabs,
                    selected = selectedTab,
                    onSelect = { tab ->
                        if (tab == AppTab.Settings) {
                            // Settings is a panel over the current tab,
                            // not a tab switch.
                            sideMenuOpen = false
                            settingsPanelOpen = true
                        } else {
                            val switched = tab != selectedTab
                            selectedTab = tab
                            initialTabApplied = true
                            closeSideMenu(switched)
                        }
                    },
                    onRefresh = { viewModel.refreshPlaylist() },
                    retainedCount = retainedList.size,
                    onRetainedClick = { closeSideMenu(false); showRetainedDialog = true },
                    onDismiss = { closeSideMenu(false) },
                    // Back from the main menu is the way out.
                    onBack = {
                        (menuActivity.findActivity() as? com.aeriotv.android.MainActivity)
                            ?.requestExitPrompt("tv-side-menu")
                    },
                )
            }
            if (settingsPanelOpen) {
                // Back on the category list closes the panel. Registered
                // before the panel's own content, so the page / push
                // handlers inside it (registered later) win while enabled.
                androidx.activity.compose.BackHandler {
                    settingsPanelOpen = false
                    closeSideMenu(false)
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(3f)
                        .background(Color.Black.copy(alpha = 0.6f)),
                ) {
                    CompositionLocalProvider(LocalTabIsActive provides true) {
                        SettingsTabContent(playlistViewModel = viewModel, sidebarPanel = true)
                    }
                }
            }
        }
    }
}

/** Floating bottom-nav chrome shared by the tab bar pill and the minimized
 *  pill so they read as one layer over busy content
 *  (guide grid, posters): solid card surface at 92 percent, a 1 dp on-surface
 *  hairline at 12 percent and a soft black drop shadow. Material styling, no
 *  translucent glass (Logan 2026-10-04, same recipe as the iPad). */
@Composable
internal fun Modifier.floatingNavChrome(shape: androidx.compose.ui.graphics.Shape): Modifier {
    val cs = MaterialTheme.colorScheme
    return this
        .dropShadow(
            shape,
            androidx.compose.ui.graphics.shadow.Shadow(
                radius = 18.dp,
                color = Color.Black.copy(alpha = 0.35f),
                offset = androidx.compose.ui.unit.DpOffset(0.dp, 6.dp),
            ),
        )
        .clip(shape)
        .background(cs.surface.copy(alpha = 0.92f))
        .border(1.dp, cs.onSurface.copy(alpha = 0.12f), shape)
}

/**
 * iOS 26 parity: the phone tab bar as a centered, floating, rounded pill
 * (the iPhone app's Liquid-Glass bar) instead of a full-width Material
 * NavigationBar. Wrap-content width, surface fill with the shared accent
 * hairline, selected tab gets a soft accent capsule behind icon + label.
 */

/**
 * Shared body for both the phone (bottom-nav Scaffold) and TV (top-tab) shells.
 * [modifier] carries the per-shell insets: Scaffold content padding on phone,
 * a weight + fill on TV.
 */
@Composable
private fun MainTabContent(
    selectedTab: AppTab,
    /** Tabs currently present; only these are kept alive. */
    tabs: List<AppTab>,
    onChannelClick: (M3UChannel) -> Unit,
    onMovieClick: (String) -> Unit,
    onSeriesClick: (Int) -> Unit,
    onEpisodeResume: (String) -> Unit,
    onResumeMovie: (String) -> Unit,
    onPlayMovie: (String) -> Unit = {},
    /** See MainScaffold: start-at-zero siblings that keep the progress row. */
    onPlayMovieFromStart: (String) -> Unit = onPlayMovie,
    onEpisodeResumeFromStart: (String) -> Unit = onEpisodeResume,
    onPlayRecording: (String, String, Int) -> Unit,
    onPlayCatchup: (String, String, String, Long, Long, String, String) -> Unit,
    onWatchLive: (String, String, Boolean, Long, Int?) -> Unit,
    onWatchFromBeginning: (String, String, Boolean, Long, Int?, Boolean) -> Unit,
    onOpenSearch: () -> Unit = {},
    // Lets a tab's own content change the selected tab (the TV Search tab's
    // Back returns to Live TV). Latches initialTabApplied at the call sites,
    // same as a manual pill press.
    onSelectTab: (AppTab) -> Unit = {},
    // The PLAYLIST_GRAPH-scoped PlaylistViewModel, threaded down so no tab
    // self-resolves a bare hiltViewModel() against the MAIN entry's store.
    // That default silently minted a SECOND PlaylistViewModel whose init ran
    // the whole channel+EPG bootstrap again (2026-07-12 Streamer report:
    // doubled Room reads, two 24k-row EPG maps, GC storm + jank for the
    // first minute of every cold launch, and the guide running on a
    // different state timeline than the scaffold).
    viewModel: PlaylistViewModel,
    /** Hoisted in MainScaffold; the TV tab bar reads it to skip its settle
     *  window for tabs that are already composed. */
    visited: androidx.compose.runtime.snapshots.SnapshotStateList<AppTab>,
    modifier: Modifier = Modifier,
) {
    // Keep visited tabs alive (Logan 2026-09-03: "why does every tab have to
    // reload?"): every tab present in [tabs] that has been shown once stays
    // composed; the inactive ones are measured at zero size and never placed,
    // so they neither draw nor take focus, and their Back handlers and focus
    // pulls are gated on [LocalTabIsActive]. A tab absent from [tabs] (no
    // favorites, VOD or recordings) is not composed at all. Search is the
    // floating screen and is never kept.
    if (selectedTab != AppTab.Search && selectedTab !in visited) visited.add(selectedTab)
    val keepAliveTabs = tabs.filter { it in visited }
    Box(modifier = modifier) {
        val render: @Composable (AppTab) -> Unit = { tab -> when (tab) {
            AppTab.LiveTV -> {
    LiveTVTabContent(
                    onChannelClick = onChannelClick,
                    onOpenSearch = onOpenSearch,
                    // Catch-up (task #133/#136): a resolved timeshift URL plays
                    // through the recording-player route with programme window +
                    // panel tz for the scrubbable timeline.
                    onPlayCatchup = onPlayCatchup,
                    viewModel = viewModel,
                )
            }
            AppTab.Favorites -> {
    FavoritesTabContent(onChannelClick = onChannelClick, onPlayCatchup = onPlayCatchup)
            }
            AppTab.DVR -> {
                // Media center on every form factor (TV joined 2026-09-10).
                com.aeriotv.android.feature.dvr.DvrMediaTabContent(
                    onPlayRecording = onPlayRecording,
                    onWatchLive = onWatchLive,
                    onWatchFromBeginning = onWatchFromBeginning,
                )
            }
            AppTab.Movies -> {
    com.aeriotv.android.feature.movies.MediaTabContent(
                    kind = com.aeriotv.android.feature.movies.MediaKind.Movies,
                    onMovieClick = { uuid -> onMovieClick(uuid) },
                    onSeriesClick = { id -> onSeriesClick(id) },
                    onEpisodeResume = onEpisodeResume,
                    onResumeMovie = onResumeMovie,
                    onPlayMovie = onPlayMovie,
                    onPlayMovieFromStart = onPlayMovieFromStart,
                    onEpisodeResumeFromStart = onEpisodeResumeFromStart,
                )
            }
            AppTab.TVShows -> {
    com.aeriotv.android.feature.movies.MediaTabContent(
                    kind = com.aeriotv.android.feature.movies.MediaKind.TVShows,
                    onMovieClick = { uuid -> onMovieClick(uuid) },
                    onSeriesClick = { id -> onSeriesClick(id) },
                    onEpisodeResume = onEpisodeResume,
                    onResumeMovie = onResumeMovie,
                    onPlayMovie = onPlayMovie,
                    onPlayMovieFromStart = onPlayMovieFromStart,
                    onEpisodeResumeFromStart = onEpisodeResumeFromStart,
                )
            }
            AppTab.OnDemand -> {
    OnDemandTabContent(
                    onMovieClick = { movie -> onMovieClick(movie.uuid) },
                    onSeriesClick = { series -> onSeriesClick(series.id) },
                    onEpisodeResume = onEpisodeResume,
                    onResumeMovie = onResumeMovie,
                    onOpenSearch = onOpenSearch,
                )
            }
            AppTab.Settings -> {
    SettingsTabContent(playlistViewModel = viewModel)
                // TV-only tab (Logan 2026-08-06): the same global Search screen the
                // phone reaches via the app-bar globe, hosted in place. EPG results
                // go through requestGuideJump, whose scaffold collector already
                // switches to Live TV and jumps the guide; movie/series results use
                // the same detail routes as On Demand. No back arrow (the pushed
                // route keeps its own) and no field auto-focus - with
                // selection-follows-focus on the nav bar, a focus grab here would
                // yank the user out of the bar the moment the pill highlights.
            }
            AppTab.Search -> {
    com.aeriotv.android.feature.search.SearchScreen(
                    onBack = { onSelectTab(AppTab.LiveTV) },
                    onEpgResult = { channelKey, startMillis ->
                        viewModel.requestGuideJump(channelKey, startMillis)
                    },
                    onMovieClick = onMovieClick,
                    onSeriesClick = onSeriesClick,
                    showBackButton = false,
                    autoFocusField = false,
                )
            }
            else -> Unit
        } }
        if (selectedTab == AppTab.Search) {
            render(AppTab.Search)
        }
        // Each kept tab lives in ONE slot (keyed by tab, fixed order) and only
        // its modifier flips between hidden and shown: moving a tab between
        // two different parents would dispose and rebuild it, which is the
        // reload this exists to avoid. BackHandlers inside are gated on
        // LocalTabIsActive, so composition order does not matter for Back.
        keepAliveTabs.forEach { tab ->
            val active = tab == selectedTab
            androidx.compose.runtime.key(tab) {
                CompositionLocalProvider(LocalTabIsActive provides active) {
                    Box(if (active) Modifier.fillMaxSize() else Modifier.keepAliveHidden()) { render(tab) }
                }
            }
        }
    }
}

/**
 * Collapses a chrome strip (the On Demand segment pills on their tab) to
 * [visibleFraction] of its measured height, fading the
 * pixels in step. The strip stays MOUNTED throughout: its children are
 * FocusRequester targets and requestFocus() on a detached node throws, so the
 * hide must be geometric, not compositional. Height is floored at 1px so the
 * strip also stays reachable by plain D-pad UP focus search when fully
 * collapsed; focus arriving is the signal the callers use to expand it again.
 *
 * Internal (not private) because the On Demand tab applies it to its
 * Movies/Series pills. Also drives the phone bottom NavigationBar's scroll auto-hide (GH #20),
 * where the height collapse is what lets Scaffold hand the space to content.
 */
internal fun Modifier.collapsibleChrome(visibleFraction: Float): Modifier = this
    .graphicsLayer { alpha = visibleFraction }
    .clipToBounds()
    .layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val collapsedHeight =
            (placeable.height * visibleFraction).toInt().coerceAtLeast(1)
        layout(placeable.width, collapsedHeight) {
            placeable.placeRelative(0, 0)
        }
    }

/**
 * Routes that replace the WHOLE Settings host rather than rendering in a pane
 * (plan B2's TV takeover set). Shared so the focus contract and the rail host
 * cannot disagree about what counts as a takeover.
 */
private fun isSettingsTakeover(route: SettingsRoute): Boolean =
    route is SettingsRoute.LogViewer ||
        route is SettingsRoute.Licenses ||
        route is SettingsRoute.EditPlaylist ||
        route is SettingsRoute.AddPlaylist ||
        route is SettingsRoute.AddMoreCategories

@Composable
private fun SettingsTabContent(
    // The PLAYLIST_GRAPH-scoped instance, threaded from MainTabContent. A
    // bare hiltViewModel() here resolved against the MAIN entry's store and
    // minted a second PlaylistViewModel (full bootstrap re-run) the moment
    // Settings opened - same leak as LiveTVTabContent's old default.
    playlistViewModel: PlaylistViewModel,
    /** ANDROID: Sidebar-style interface, hosted in the right-hand panel
     *  (SettingsSidebarPanelHost) instead of the full-screen TV rail. */
    sidebarPanel: Boolean = false,
) {
    // Phase B2: one saveable back stack replaces the six independent booleans
    // plus `section`. "Innermost" is now literal (stack order) instead of being
    // encoded implicitly in the ORDER of the BackHandler and content `when`
    // branches, which had to be kept in agreement by hand. rememberSaveable
    // also means rotation, fold posture changes, and process death restore the
    // position, which plain `remember` could not.
    val nav = rememberSettingsNavState()
    val route = nav.current
    // Phase B3: the tablet list-detail host. Expanded width alone is not the
    // gate -- a landscape phone is "expanded" but far too short -- and TV keeps
    // the stacked layout until B4 lands the rail with its focus contract.
    val viewport = rememberViewport()
    // Settings phase 2: the gate is MEDIUM width and up, so an unfolded
    // foldable gets the sidebar. `settingsTwoPane` is Settings' own flag; the
    // window-wide `isTwoPaneEligible` (and the tab-bar placement built on it)
    // is deliberately unchanged.
    val paneEligible = viewport.settingsTwoPane
    // Same eligibility, two hosts: touch tablets get the tap sidebar (B3), TV
    // gets the 10-foot rail with its focus contract (B4).
    val tvRail = paneEligible
    val twoPane = tvRail
    var selection by rememberSettingsPaneSelection(nav = nav, twoPane = twoPane, initial = if (tvRail) SettingsRoute.Root else DefaultSettingsPaneSelection)
    // True for the route the pane is BASELINED on; pushes above it render as
    // ordinary screens and keep their own back affordance.
    val inPane = twoPane && route == null
    val updaterEnabled = hiltViewModel<com.aeriotv.android.feature.update.UpdateViewModel>()
        .isEnabled
    // Sidebar / rail Sync row reads On or Off rather than a description.
    val syncEnabled by hiltViewModel<com.aeriotv.android.feature.settings.SettingsViewModel>()
        .syncProviderLabel
        .collectAsStateWithLifecycle(initialValue = "Off")
    val addPlaylistStep: AddPlaylistStep = when (val r = route) {
        is SettingsRoute.AddPlaylist -> when (val st = r.step) {
            is AddPlaylistWizardStep.ChooseType -> AddPlaylistStep.ChooseType
            is AddPlaylistWizardStep.Configure -> AddPlaylistStep.Configure(st.sourceType)
            // Past the connect: the id-flip watcher below must not pop it.
            is AddPlaylistWizardStep.AddProfiles -> AddPlaylistStep.None
        }
        else -> AddPlaylistStep.None
    }
    val playlistVm = playlistViewModel
    val playlistState by playlistVm.state.collectAsStateWithLifecycle()

    // Screenshot deep link (aeriotv://settings/<page>). MainScaffold has already
    // selected the Settings tab, which is what composed us; translate the page
    // into this host's own terms. In a two-pane host a pane-baseline page is the
    // SELECTION and everything else is a push above Playlists, matching what the
    // user would have produced by hand; the stacked phone layout just pushes.
    // The playlist pages wait for an active playlist, which is why the request
    // is a StateFlow and this effect is keyed on the playlist id as well.
    val pendingSettingsPage by playlistVm.settingsPageRequest.collectAsStateWithLifecycle()
    // Bumped when a deep link changes the rail's selection: the rail only pulls
    // focus into the pane on a PUSH, so a plain selection change would leave
    // focus wherever it was (the tab pill on a cold launch).
    var deepLinkPaneFocus by remember { mutableIntStateOf(0) }
    LaunchedEffect(pendingSettingsPage, twoPane, tvRail, playlistState.playlist?.id) {
        val page = pendingSettingsPage?.page ?: return@LaunchedEffect
        val activeId = playlistState.playlist?.id
        // No playlist yet: leave the request pending and retry when one lands.
        if (activeId == null && settingsDeepLinkPageNeedsPlaylist(page)) return@LaunchedEffect
        val target = settingsRouteForDeepLinkPage(page, activeId, com.aeriotv.android.core.profiles.ProfileRoles.active.value)
        nav.popToRoot()
        if (twoPane) {
            when {
                target == null ->
                    selection = if (tvRail) SettingsRoute.Root else DefaultSettingsPaneSelection
                target.isPaneBaseline -> selection = target
                else -> {
                    // Playlist detail / edit are pushes; baseline them on the
                    // Playlists pane so Back walks out the way it normally would.
                    selection = SettingsRoute.Playlists
                    nav.push(target)
                }
            }
            deepLinkPaneFocus++
        } else if (target != null) {
            nav.push(target)
        }
        playlistVm.consumeSettingsPage()
    }
    // Watch for a playlist id flip while we're inside the Add Playlist flow;
    // that means the user's onboarding Save succeeded and the new row was
    // promoted active. Close the embedded flow.
    val startId = remember(addPlaylistStep) {
        if (addPlaylistStep != AddPlaylistStep.None) playlistState.playlist?.id else null
    }
    LaunchedEffect(playlistState.playlist?.id) {
        if (addPlaylistStep != AddPlaylistStep.None &&
            startId != null &&
            playlistState.playlist?.id != startId
        ) {
            val step = addPlaylistStep
            val newId = playlistState.playlist?.id
            if (step is AddPlaylistStep.Configure && newId != null &&
                (step.sourceType == com.aeriotv.android.core.data.SourceType.DispatcharrApiKey ||
                    step.sourceType == com.aeriotv.android.core.data.SourceType.DispatcharrUserPass)
            ) {
                nav.replaceTop(SettingsRoute.AddPlaylist(AddPlaylistWizardStep.AddProfiles(newId)))
            } else {
                nav.pop()
            }
        }
    }
    androidx.activity.compose.BackHandler(enabled = nav.canPop) {
        val cur = nav.current
        com.aeriotv.android.ui.tv.TvFocusTrace.settingsBack("settings-nav pop ${cur?.let { com.aeriotv.android.feature.settings.encodeSettingsRoute(it) }}")
        // The wizard's stages advance IN PLACE rather than nesting, so Back
        // from Configure returns to ChooseType instead of leaving the flow.
        if (cur is SettingsRoute.AddPlaylist && cur.step is AddPlaylistWizardStep.Configure) {
            nav.replaceTop(SettingsRoute.AddPlaylist(AddPlaylistWizardStep.ChooseType))
        } else {
            nav.pop()
        }
    }
    // TV focus retention. The top nav uses selection-follows-focus (focusing
    // the Live TV pill switches to the guide). When the user clicks a settings
    // row, the sub-screen replaces the list and the focused row is removed --
    // Compose then falls focus back to the first focusable in the tree, the
    // nav pill row, whose Live TV pill grabs focus and bounces the user to the
    // guide (the "clicking any setting goes back to the guide" bug). Fix: pull
    // focus INTO the settings content whenever a sub-screen appears so it never
    // lands on the nav. Keyed on the visible sub-screen; the root list (key
    // null) is left alone so the pill -> DOWN -> list traversal is unchanged.
    val settingsContentFocus = remember { FocusRequester() }
    // Plan B2: "fire it only for full-screen takeovers entering or exiting".
    // In a two-pane host a push renders INSIDE the pane with the rail still up,
    // so pulling focus to this outer group would land it on the rail's first
    // row and orphan the push (traced on the Streamer 2026-08-05). The rail
    // host moves focus into the pane itself for those; this stays responsible
    // for the stacked layouts and for takeovers.
    val subScreenKey: String? = when {
        !twoPane -> nav.focusKey
        route != null && isSettingsTakeover(route) -> nav.focusKey
        else -> null
    }
    var prevSubScreenKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(subScreenKey) {
        // Pull focus into the content both when a sub-screen OPENS and when it
        // CLOSES back to the root list, so focus never lingers on a nav pill
        // (where the involuntary fallback parks it). Skip the very first
        // composition (prev == cur == null) so switching INTO Settings still
        // lands on the Settings pill, preserving the pill -> DOWN -> list flow.
        val entering = subScreenKey != null
        val exitingToRoot = subScreenKey == null && prevSubScreenKey != null
        if (entering || exitingToRoot) runCatching { settingsContentFocus.requestFocus() }
        prevSubScreenKey = subScreenKey
    }
    // Settings tab re-tap (TabReselect): pop whatever is pushed back to the
    // Settings root first -- including a picker / sub-toggle page, which lives
    // inside the sub-screen being popped -- and once already at the root,
    // scroll that root list to the top. The two-pane host keeps its selection:
    // only the pushes ABOVE the pane baseline are popped, which is what Back
    // would have done one press at a time.
    val settingsRootListState = androidx.compose.foundation.lazy.rememberLazyListState()
    OnTabReselect(AppTab.Settings) {
        if (nav.canPop) nav.popToRoot() else settingsRootListState.animateScrollToItem(0)
    }
    // Phase B3: one renderer for a route, used by BOTH the stacked phone layout
    // and the tablet host's detail pane, so the two can never diverge.
    // Profiles phase 2b: a Child renders only its allowed routes; anything
    // else (a stale selection, a push, a deep link) falls back to the root.
    val activeRole by com.aeriotv.android.core.profiles.ProfileRoles.active.collectAsStateWithLifecycle()
    LaunchedEffect(activeRole) {
        if (activeRole == com.aeriotv.android.core.profiles.ProfileRole.Child) {
            if (nav.stack.any { !it.allowedFor(activeRole) }) nav.popToRoot()
            if (!selection.allowedFor(activeRole)) {
                selection = SettingsRoute.Section(SettingsSection.LiveTV)
            }
        } else if (selection is SettingsRoute.ManageProfiles) {
            // Left the Child (Manage Profiles -> Admin): that page has no row
            // outside Child mode, so land on the canon default instead.
            nav.popToRoot()
            selection = if (tvRail) SettingsRoute.Playlists else DefaultSettingsPaneSelection
        } else if (nav.stack.any { it is SettingsRoute.ManageProfiles }) {
            nav.popToRoot()
        }
    }
    val renderRoute: @Composable (SettingsRoute?) -> Unit = { r0 ->
    val r = if (r0.allowedFor(activeRole)) r0 else null
    when (r) {
        null -> SettingsScreen(
            listState = settingsRootListState,
            onSectionClick = { nav.push(SettingsRoute.Section(it)) },
            onOpenPlaylistDetail = { id -> nav.push(SettingsRoute.PlaylistDetail(id)) },
            onOpenPlaylists = { nav.push(SettingsRoute.Playlists) },
            onEditPlaylist = { id -> nav.push(SettingsRoute.EditPlaylist(id)) },
            onAddPlaylist = {
                nav.push(SettingsRoute.AddPlaylist(AddPlaylistWizardStep.ChooseType))
            },
            onOpenLicenses = { nav.push(SettingsRoute.Licenses) },
            onOpenManageProfiles = { nav.push(SettingsRoute.ManageProfiles) },
            viewModel = playlistVm,
        )
        is SettingsRoute.About -> SettingsScreen(
            onSectionClick = { nav.push(SettingsRoute.Section(it)) },
            onBack = { nav.pop() },
            onOpenLicenses = { nav.push(SettingsRoute.Licenses) },
            viewModel = playlistVm,
            content = SettingsRootContent.AboutOnly,
        )
        is SettingsRoute.Root -> SettingsScreen(
            listState = settingsRootListState,
            onSectionClick = { nav.push(SettingsRoute.Section(it)) },
            onOpenPlaylistDetail = { id -> nav.push(SettingsRoute.PlaylistDetail(id)) },
            onOpenPlaylists = { nav.push(SettingsRoute.Playlists) },
            onEditPlaylist = { id -> nav.push(SettingsRoute.EditPlaylist(id)) },
            onAddPlaylist = {
                nav.push(SettingsRoute.AddPlaylist(AddPlaylistWizardStep.ChooseType))
            },
            onOpenLicenses = { nav.push(SettingsRoute.Licenses) },
            onOpenManageProfiles = { nav.push(SettingsRoute.ManageProfiles) },
            viewModel = playlistVm,
        )
        is SettingsRoute.AddPlaylist -> when (val st = r.step) {
            is AddPlaylistWizardStep.Configure -> ConfigureSourceScreen(
                sourceType = st.sourceType,
                onBack = {
                    nav.replaceTop(SettingsRoute.AddPlaylist(AddPlaylistWizardStep.ChooseType))
                },
                viewModel = playlistVm,
            )
            is AddPlaylistWizardStep.AddProfiles ->
                com.aeriotv.android.feature.onboarding.AddProfilesStepScreen(
                    playlistId = st.playlistId,
                    onDone = {
                        if (nav.current == r) nav.pop()
                    },
                )
            is AddPlaylistWizardStep.ChooseType -> ChooseSourceTypeScreen(
                onBack = { nav.pop() },
                onChoose = { type ->
                    // Start a FRESH draft so the add creates a NEW row and can't
                    // carry over the active server's bootstrap-prefilled API key
                    // (which would win over typed user/pass and re-add the active
                    // server's account). Mirrors the onboarding CHOOSE_TYPE path.
                    playlistVm.startNewSource(type)
                    nav.replaceTop(
                        SettingsRoute.AddPlaylist(AddPlaylistWizardStep.Configure(type)),
                    )
                },
            )
        }
        is SettingsRoute.Playlists ->
            if (inPane) {
                // As a PANE this is the sidebar's Playlists item, so it shows
                // the root's playlist block (switch / open / Add / Manage);
                // Manage Playlists then pushes the reorder page above it.
                SettingsScreen(
                    onSectionClick = { nav.push(SettingsRoute.Section(it)) },
                    onOpenPlaylistDetail = { id ->
                        nav.push(SettingsRoute.PlaylistDetail(id))
                    },
                    onOpenPlaylists = { nav.push(SettingsRoute.Playlists) },
                    onEditPlaylist = { id -> nav.push(SettingsRoute.EditPlaylist(id)) },
                    onAddPlaylist = {
                        nav.push(SettingsRoute.AddPlaylist(AddPlaylistWizardStep.ChooseType))
                    },
                    viewModel = playlistVm,
                    content = SettingsRootContent.PlaylistsOnly,
                )
            } else {
                com.aeriotv.android.feature.settings.PlaylistsScreen(
                    onBack = { nav.pop() },
                    onAddPlaylist = {
                        nav.push(SettingsRoute.AddPlaylist(AddPlaylistWizardStep.ChooseType))
                    },
                    onOpenPlaylistDetail = { id ->
                        nav.push(SettingsRoute.PlaylistDetail(id))
                    },
                    viewModel = playlistVm,
                )
            }
        is SettingsRoute.EditPlaylist -> com.aeriotv.android.feature.settings.EditPlaylistScreen(
            onBack = { nav.pop() },
            playlistId = r.playlistId,
            viewModel = playlistVm,
        )
        is SettingsRoute.PlaylistDetail ->
            com.aeriotv.android.feature.settings.PlaylistDetailScreen(
                onBack = { nav.pop() },
                onEdit = { nav.push(SettingsRoute.EditPlaylist(r.playlistId)) },
                playlistId = r.playlistId,
                viewModel = playlistVm,
            )
        is SettingsRoute.AddMoreCategories -> AddMoreCategoriesScreen(onBack = { nav.pop() })
        is SettingsRoute.ManageProfiles ->
            com.aeriotv.android.feature.settings.ManageProfilesPage(onBack = { nav.pop() })
        is SettingsRoute.SyncCategories ->
            com.aeriotv.android.feature.settings.SyncCategoriesScreen(onBack = { nav.pop() })
        is SettingsRoute.LogViewer -> com.aeriotv.android.feature.settings.LogViewerScreen(
            onBack = { nav.pop() },
        )
        is SettingsRoute.Licenses -> com.aeriotv.android.feature.settings.LicensesScreen(
            onBack = { nav.pop() },
        )
        is SettingsRoute.Section -> when (r.section) {
            SettingsSection.LiveTV -> LiveTvSettingsScreen(
                onBack = { nav.pop() },
                onOpenAddMoreCategories = { nav.push(SettingsRoute.AddMoreCategories) },
            )
            SettingsSection.Player -> PlayerSettingsScreen(onBack = { nav.pop() })
            SettingsSection.MoviesAndTvShows ->
                MoviesAndTvShowsSettingsScreen(onBack = { nav.pop() })
            SettingsSection.Appearance -> AppearanceSettingsScreen(onBack = { nav.pop() })
            SettingsSection.General -> GeneralSettingsScreen(onBack = { nav.pop() })
            SettingsSection.RemoteControl ->
                com.aeriotv.android.feature.settings.RemoteControlSettingsScreen(
                    onBack = { nav.pop() },
                )
            SettingsSection.AppUpdates ->
                com.aeriotv.android.feature.settings.AppUpdatesScreen(onBack = { nav.pop() })
            SettingsSection.Sync -> com.aeriotv.android.feature.settings.SyncSettingsScreen(
                onBack = { nav.pop() },
                onOpenSyncCategories = { nav.push(SettingsRoute.SyncCategories) },
            )
            SettingsSection.DvrSettings -> DvrSettingsScreen(onBack = { nav.pop() })
            SettingsSection.Developer -> DeveloperSettingsScreen(
                onBack = { nav.pop() },
                onOpenLogViewer = { nav.push(SettingsRoute.LogViewer) },
            )
            // The About page is the root's About block rendered on its own,
            // so the copy cannot drift from what the pane hosts already show.
            SettingsSection.About -> SettingsScreen(
                onSectionClick = { nav.push(SettingsRoute.Section(it)) },
                onBack = { nav.pop() },
                onOpenLicenses = { nav.push(SettingsRoute.Licenses) },
                viewModel = playlistVm,
                content = SettingsRootContent.AboutOnly,
            )
        }
    }
    }

    // Fold awareness. Every geometry comes from the OS-reported FoldingFeature,
    // and the boundary always lands ON the crease so no pane is bent:
    //
    //  - VERTICAL crease: the window splits left/right, so the existing
    //    side-by-side layout just moves its boundary to the hinge.
    //  - HORIZONTAL crease, ANY state: Settings keeps the full display side by
    //    side (Logan's call), so the split is the ordinary fixed one; only an
    //    OCCLUDING hinge changes anything, by taking the hairline away.
    //
    // The host's Row/Column starts at the window's leading/top edge, which is
    // the same origin the feature bounds use. Recomposing on a new FoldInfo
    // re-lays-out only; `selection` and the nav stack live above this and are
    // untouched, and `twoPane` does not flip, so no posture mapping runs.
    val fold = com.aeriotv.android.ui.adaptive.rememberFoldInfo()
    val settingsSplit = when {
        fold != null &&
            fold.axis == com.aeriotv.android.ui.adaptive.FoldAxis.VERTICAL &&
            fold.start > 0.dp ->
            SettingsPaneSplit(
                sidebarExtent = fold.start,
                gap = fold.gap,
                drawDivider = !fold.needsBlankGap,
            )
        else ->
            SettingsPaneSplit(
                sidebarExtent = viewport.settingsSidebarWidth,
                drawDivider = fold?.isOccluding != true,
            )
    }

    Box(modifier = Modifier.focusRequester(settingsContentFocus).focusGroup()) {
        if (tvRail && sidebarPanel) {
            com.aeriotv.android.feature.settings.SettingsSidebarPanelHost(
                selection = selection,
                onSelect = { picked ->
                    nav.popToRoot()
                    selection = picked
                },
                pushed = route,
                sections = visibleSettingsSections(
                    updaterEnabled = updaterEnabled,
                    role = activeRole,
                ),
                activePlaylistName = playlistState.playlist?.name,
                takeover = ::isSettingsTakeover,
                detail = { renderRoute(it) },
            )
        } else if (tvRail) {
            SettingsTvRailHost(
                focusPaneSignal = deepLinkPaneFocus,
                selection = selection,
                onSelect = { picked ->
                    nav.popToRoot()
                    selection = picked
                },
                pushed = route,
                sections = visibleSettingsSections(
                    updaterEnabled = updaterEnabled,
                    role = activeRole,
                ),
                activePlaylistName = playlistState.playlist?.name,
                syncEnabled = syncEnabled,
                // Plan B2: the TV takeover set. These keep the whole screen so
                // their keyboard / IME plumbing is untouched.
                takeover = ::isSettingsTakeover,
                detail = { renderRoute(it) },
            )
        } else {
            // Dispatch on the stack's top route. Ordering that used to be
            // implicit in branch position (the log viewer had to sit ABOVE
            // Developer to win) is now just push order.
            renderRoute(route)
        }
    }
}

/**
 * Mirror of iOS MainTabView's dynamic tab-visibility rule (HomeView.swift
 * hasFavorites / hasRecordings / hasVOD). Always-on tabs: Live TV, Settings.
 * Conditional tabs appear ONLY when there is real content to show, NOT merely
 * because the source type could in theory serve it:
 *  - Favorites: the user has favorited at least one channel in the active playlist.
 *  - DVR: at least one recording exists (scheduled / recording / completed,
 *    server or local) for the active source.
 *  - On Demand: the active source has advertised any VOD (movies or series), or
 *    is still loading its VOD library (loading bridge prevents cold-start flicker).
 *
 * A bare live-TV M3U, or a Dispatcharr/Xtream source with no VOD and no
 * recordings, surfaces only Live TV + Settings - empty tabs never appear.
 */
internal fun visibleTabs(
    hasFavorites: Boolean = false,
    hasVod: Boolean = false,
    hasRecordings: Boolean = false,
    /** Phone/tablet media center: Movies + TV Shows instead of On Demand. */
    splitVod: Boolean = false,
    hasMovies: Boolean = hasVod,
    hasSeries: Boolean = hasVod,
): List<AppTab> = buildList {
    add(AppTab.LiveTV)
    if (hasFavorites) add(AppTab.Favorites)
    if (hasRecordings) add(AppTab.DVR)
    if (splitVod) {
        if (hasMovies) add(AppTab.Movies)
        if (hasSeries) add(AppTab.TVShows)
    } else if (hasVod) add(AppTab.OnDemand)
    add(AppTab.Settings)
    // AppTab.Search is deliberately NOT a pill: on TV it renders as the
    // floating circle LEFT of Live TV inside TvTopTabBar (Logan 2026-08-06:
    // frequent searchers shouldn't traverse the whole bar), and phones keep
    // their app-bar search entry points instead of a bottom-bar tab.
}

/** EntryPoint accessor so MainScaffold can drive pause/destroy on the held
 * MPV instance without routing through a ViewModel. */
@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface MainScaffoldEntryPoint {
    fun exoPlayerHolder(): AerioExoPlayerHolder
    fun exoWindowState(): com.aeriotv.android.feature.player.ExoWindowState
    fun watchProgressDao(): com.aeriotv.android.core.data.db.dao.WatchProgressDao
    fun timeshiftController(): com.aeriotv.android.core.timeshift.TimeshiftController
}

/** Two-step Add Playlist flow embedded in the Settings tab. None = closed. */
private sealed interface AddPlaylistStep {
    data object None : AddPlaylistStep
    data object ChooseType : AddPlaylistStep
    data class Configure(val sourceType: com.aeriotv.android.core.data.SourceType) : AddPlaylistStep
}

/** Scaffold-level view of On Demand state: presence flags only, so page
 *  emissions from the catalog walk do not recompose the scaffold. */
private data class VodPresence(
    val unsupportedSource: Boolean,
    val hasMovies: Boolean,
    val hasSeries: Boolean,
    val isLoading: Boolean,
    val isLoadingSeries: Boolean,
    val hasDeferredXtreamContent: Boolean,
) {
    companion object {
        fun from(s: com.aeriotv.android.feature.ondemand.OnDemandViewModel.UiState) = VodPresence(
            unsupportedSource = s.unsupportedSource,
            // Stored catalog counts (GH #109: the lists are no longer in memory).
            hasMovies = s.totalCount > 0,
            hasSeries = s.seriesTotalCount > 0,
            isLoading = s.isLoading,
            isLoadingSeries = s.isLoadingSeries,
            hasDeferredXtreamContent = s.hasDeferredXtreamContent,
        )
    }
}

/** True inside the tab the user is on; false inside a tab kept alive but
 *  hidden. Tab content gates focus pulls and BackHandlers on it. */
val LocalTabIsActive = androidx.compose.runtime.compositionLocalOf { true }

/** Measured at the REAL slot size and never placed: no draw, no focus
 *  geometry, no semantics, but the composition (and its state, scroll
 *  positions, loaded data) survives.
 *
 *  It used to measure at ZERO. That made showing a tab a full relayout, and a
 *  pre-warmed tab was laid out at 0 width, so its first placed frame was the
 *  0-width one: the hero carousel (which measures its own width) rendered its
 *  pages as slivers for a beat (Logan 2026-09-11). Measuring at the real size
 *  means the hidden tab is laid out exactly as it will be shown and its first
 *  placed frame is final; the parent still reports 0 x 0 and never places it,
 *  so it draws nothing, and canFocus/invisibleToUser below still keep it out
 *  of focus search and accessibility. */
private fun Modifier.keepAliveHidden(): Modifier = this
    .then(KeepAliveHiddenElement)
    .focusProperties { canFocus = false }
    .semantics { invisibleToUser() }

/* HOLD THE SLOT (perf 2026-10-03). Measuring the hidden tab at full size on
 * EVERY pass meant any change inside it (the TMDB art pass bumps every ~2 s)
 * re-measured its whole poster grid while nobody could see it, 115 to 160 ms
 * a time. The node now measures at the full constraints once (and again only
 * if those constraints change, e.g. rotation), which is all the 2026-09-11
 * fix needs: the content's last layout is the real size, so showing it never
 * starts from a 0-width frame. Later invalidations stay pending and are
 * measured in the frame the tab is shown, before it is placed. */
private object KeepAliveHiddenElement : androidx.compose.ui.node.ModifierNodeElement<KeepAliveHiddenNode>() {
    override fun create() = KeepAliveHiddenNode()
    override fun update(node: KeepAliveHiddenNode) = Unit
    override fun hashCode(): Int = 0x6b41
    override fun equals(other: Any?): Boolean = other === this
}

private class KeepAliveHiddenNode : Modifier.Node(), androidx.compose.ui.node.LayoutModifierNode {
    private var measuredFor: androidx.compose.ui.unit.Constraints? = null

    override fun androidx.compose.ui.layout.MeasureScope.measure(
        measurable: androidx.compose.ui.layout.Measurable,
        constraints: androidx.compose.ui.unit.Constraints,
    ): androidx.compose.ui.layout.MeasureResult {
        // Same constraints the ACTIVE slot's fillMaxSize() resolves to.
        val full = constraints.copy(
            minWidth = constraints.maxWidth.takeIf { it != androidx.compose.ui.unit.Constraints.Infinity } ?: constraints.minWidth,
            minHeight = constraints.maxHeight.takeIf { it != androidx.compose.ui.unit.Constraints.Infinity } ?: constraints.minHeight,
        )
        if (measuredFor != full) {
            measurable.measure(full)
            measuredFor = full
        }
        return layout(0, 0) { /* deliberately not placed */ }
    }
}

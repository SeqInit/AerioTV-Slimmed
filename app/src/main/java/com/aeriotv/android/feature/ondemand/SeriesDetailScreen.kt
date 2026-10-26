package com.aeriotv.android.feature.ondemand

import com.aeriotv.android.ui.scale.subtext
import com.aeriotv.android.ui.theme.textAccent
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.aeriotv.android.ui.TmdbAttribution
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.aeriotv.android.core.network.DispatcharrVODEpisode
import com.aeriotv.android.core.network.TmdbCredits
import com.aeriotv.android.core.network.TmdbDetails
import com.aeriotv.android.core.network.TmdbPerson
import com.aeriotv.android.core.tv.TvActionMenuDialog
import com.aeriotv.android.core.tv.TvMenuAction
import com.aeriotv.android.core.tv.TvQrLink
import com.aeriotv.android.core.tv.TvQrLinkDialog
import com.aeriotv.android.core.tv.rememberTvMenuGuard
import com.aeriotv.android.feature.movies.displayTitle
import com.aeriotv.android.feature.movies.MediaItem
import com.aeriotv.android.feature.movies.tmdbArtKey
import com.aeriotv.android.feature.watchprogress.UpNextEntry
import com.aeriotv.android.feature.watchprogress.WatchProgressViewModel
import com.aeriotv.android.ui.tv.tvFocusScale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Replay
import com.aeriotv.android.core.network.TmdbEpisodeInfo
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Series detail screen. Mirrors iOS VODDetailView
 * (Aerio/Features/VOD/VODDetailView.swift), which renders both form factors
 * from one view:
 *  - TV follows the tvOS layout: the 620 pt (310 dp) hero with no poster, the
 *    action row (Resume S2 E3 / Play S1 E2, Play from Beginning, Version,
 *    Trailer, TMDB), horizontal episode cards, episode-scoped Cast and Crew,
 *    the Details facts block, Available Related Titles, TMDB attribution.
 *  - Phone follows the iPhone layout: the 280 dp hero with the 80 x 120 poster,
 *    the info block, Cast and Crew, vertical episode rows, Related, attribution.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SeriesDetailScreen(
    seriesId: Int,
    onBack: () -> Unit,
    /** fromStart is passed per launch (never held in view state) and zeroes the
     *  start position WITHOUT clearing the WatchProgress row (Apple C3). */
    onEpisodeClick: (DispatcharrVODEpisode, Boolean) -> Unit,
    // Known For tile pushes from the bio dialog: plain navigation pushes so
    // remote BACK returns here. Defaults keep non-nav call sites compiling.
    onOpenMovie: (String) -> Unit = {},
    onOpenSeries: (Int) -> Unit = {},
    viewModel: OnDemandViewModel = hiltViewModel(),
    watchVm: WatchProgressViewModel = hiltViewModel(),
    watchlistVm: com.aeriotv.android.feature.movies.WatchlistViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val series = viewModel.seriesById(seriesId)
    LaunchedEffect(seriesId, series == null) { if (series == null) viewModel.resolveSeries(seriesId) }
    val resolvingSeries = series == null && viewModel.isResolving("s:$seriesId")
    val info = state.seriesProviderInfo[seriesId]
    // Keyed per-series episode rows, never the recent-50 scan: a long-tail
    // series outside the recency window still finds its resume point
    // (Apple's keyed getResumePosition, VODModels.swift:280-286).
    val episodeProgress by watchVm.observeSeriesEpisodes(seriesId.toString())
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val context = LocalContext.current
    // "Related": TMDB recommendations filtered to the local library. Keyed on
    // the library size too so it re-matches as the launch sweep publishes.
    var relatedItems by remember(seriesId) { mutableStateOf<List<MediaItem>>(emptyList()) }
    LaunchedEffect(seriesId, series == null, info, state.seriesTotalCount) {
        val s = series ?: return@LaunchedEffect
        relatedItems = viewModel.relatedTitles(
            tmdbId = info?.tmdbId ?: s.tmdbId,
            title = s.displayName,
            isMovie = false,
            selfKey = "s:${s.id}",
        )
    }
    val watchlistEntries by watchlistVm.entries.collectAsStateWithLifecycle(initialValue = emptyList())
    val watchlistKeys = remember(watchlistEntries) { watchlistEntries.map { it.key }.toSet() }

    LaunchedEffect(seriesId) {
        // loadEpisodes now primes provider-info (Dispatcharr series_info) FIRST so the
        // lazy-scrape populates the episode table before /episodes/ is hit, and caches
        // seriesProviderInfo, so the separate loadSeriesProviderInfo() call is gone.
        viewModel.loadEpisodes(seriesId)
        // Version picker data (Dispatcharr Direct Connect only; the VM caches
        // empty for every other source so the pill never renders).
        viewModel.loadSeriesProviders(seriesId)
    }
    val versionOptions = state.seriesProviders[seriesId].orEmpty()
    val selectedVersion = state.selectedSeriesVersion[seriesId]
    var showVersionPicker by remember(seriesId) { mutableStateOf(false) }

    // TMDB poster fallback (opt-in): only when the server gave no artwork.
    var tmdbPosterUrl by remember(seriesId) { mutableStateOf<String?>(null) }
    // Provenance note inputs (phone): whether the poster lookup has run, and
    // whether the opt-in + key are set, so an art-less title can say "add a
    // key" vs "no match" (iOS tmdbLookupDone / TMDBPosters.apiKey).
    var tmdbLookupDone by remember(seriesId) { mutableStateOf(false) }
    // Persistent art cache: same TMDB-first rule as the movie screen.
    val artVersion by viewModel.artVersion.collectAsStateWithLifecycle(initialValue = 0)
    val artKey = remember(seriesId, series?.displayName) {
        series?.let { tmdbArtKey(displayTitle(it.displayName, it.year), false) }
    }
    val cachedBackdropUrl = remember(artKey, artVersion) { artKey?.let { viewModel.artBackdropUrl(it) } }
    val cachedOverview = remember(artKey, artVersion) { artKey?.let { viewModel.artOverview(it) } }
    var tmdbConfigured by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { tmdbConfigured = viewModel.isTmdbConfigured() }
    val hasServerArt = series != null && (
        !series.posterUrl.isNullOrBlank() ||
            !info?.posterUrl.isNullOrBlank() || !info?.backdropUrl.isNullOrBlank()
        )
    LaunchedEffect(seriesId, info, state.seriesProviderInfoLoading) {
        val s = series ?: return@LaunchedEffect
        val infoSettled = state.seriesProviderInfo.containsKey(seriesId) ||
            !state.seriesProviderInfoLoading.contains(seriesId)
        if (!hasServerArt && tmdbPosterUrl == null && infoSettled) {
            tmdbPosterUrl = viewModel.resolveTmdbPoster(
                tmdbId = info?.tmdbId ?: s.tmdbId,
                title = s.displayName,
                isMovie = false,
            )
            tmdbLookupDone = true
        }
    }

    // TMDB metadata backfill (same opt-in gate as the poster fallback).
    // Fetched only when the server left at least one of plot / genre / cast /
    // director blank after provider-info settled; server values always win,
    // TMDB only fills the holes. Fully-described libraries never hit TMDB.
    var tmdbDetails by remember(seriesId) { mutableStateOf<TmdbDetails?>(null) }
    LaunchedEffect(seriesId, info, state.seriesProviderInfoLoading) {
        val s = series ?: return@LaunchedEffect
        val infoSettled = state.seriesProviderInfo.containsKey(seriesId) ||
            !state.seriesProviderInfoLoading.contains(seriesId)
        val missingMeta = (info?.effectivePlot ?: s.plot).isNullOrBlank() ||
            (info?.effectiveGenre ?: s.genre).isNullOrBlank() ||
            info?.effectiveCast.isNullOrBlank() ||
            info?.effectiveDirector.isNullOrBlank()
        if (missingMeta && tmdbDetails == null && infoSettled) {
            tmdbDetails = viewModel.resolveTmdbDetails(
                tmdbId = info?.tmdbId ?: s.tmdbId,
                title = s.displayName,
                isMovie = false,
            )
        }
    }

    // Structured TMDB credits for the Cast & Crew strip. Independent of the
    // missingMeta gate above: servers only ever send comma-separated name
    // strings, so headshots always need TMDB. The resolver returns null when
    // the TMDB opt-in or key is absent and the strip simply does not render.
    var tmdbCredits by remember(seriesId) { mutableStateOf<TmdbCredits?>(null) }
    LaunchedEffect(seriesId, info, state.seriesProviderInfoLoading) {
        val s = series ?: return@LaunchedEffect
        val infoSettled = state.seriesProviderInfo.containsKey(seriesId) ||
            !state.seriesProviderInfoLoading.contains(seriesId)
        if (tmdbCredits == null && infoSettled) {
            tmdbCredits = viewModel.resolveTmdbCredits(
                tmdbId = info?.tmdbId ?: s.tmdbId,
                title = s.displayName,
                isMovie = false,
            )
        }
    }
    var bioPerson by remember { mutableStateOf<TmdbPerson?>(null) }

    BackHandler(enabled = true) { onBack() }

    val episodes = state.episodesBySeries[seriesId].orEmpty()
    val isLoading = seriesId in state.episodesLoadingFor
    val error = state.episodesErrorFor[seriesId]

    val seasons = remember(episodes) {
        episodes
            .groupBy { it.seasonNumber ?: 0 }
            .toSortedMap()
    }

    // Target episode + button label (Apple tvSeriesTarget, VODDetailView 731-751).
    val target = remember(episodes, episodeProgress) { seriesTarget(episodes, episodeProgress) }

    // Apple seats the season that holds the resume target (seatSelectedSeason,
    // VODDetailView 904-911) rather than defaulting to the first non-special.
    var selectedSeason by remember(seasons.keys) {
        mutableStateOf(seasons.keys.firstOrNull { it != 0 } ?: seasons.keys.firstOrNull() ?: 0)
    }
    var seasonSeated by remember(seriesId) { mutableStateOf(false) }
    LaunchedEffect(target?.episode?.uuid, seasons.keys) {
        val season = target?.episode?.seasonNumber ?: return@LaunchedEffect
        if (!seasonSeated && seasons.containsKey(season)) {
            selectedSeason = season
            seasonSeated = true
        }
    }

    val episodesInSeason = remember(seasons, selectedSeason) {
        seasons[selectedSeason]
            ?.sortedBy { it.episodeNumber ?: Int.MAX_VALUE }
            ?: emptyList()
    }

    // Per-episode watch progress for the row / card chrome, keyed by the
    // episode uuid (the same key the player writes).
    val progressByEpisode = remember(episodeProgress) { episodeProgress.associateBy { it.videoId } }

    // TMDB season data for the selected season: stills (w780 first, the
    // provider's still as the fallback), the episode name when the provider's
    // title is useless, and the guest stars / crew for the episode-scoped
    // Cast and Crew row (Apple VODDetailView 871-873, 793-798, 2259-2273).
    var tmdbSeason by remember(seriesId) { mutableStateOf<Map<Int, Map<Int, TmdbEpisodeInfo>>>(emptyMap()) }
    LaunchedEffect(seriesId, selectedSeason, info, series == null) {
        val s = series ?: return@LaunchedEffect
        if (tmdbSeason.containsKey(selectedSeason)) return@LaunchedEffect
        val fetched = viewModel.resolveTmdbSeason(
            tmdbId = info?.tmdbId ?: s.tmdbId,
            title = s.displayName,
            seasonNumber = selectedSeason,
        ) ?: return@LaunchedEffect
        tmdbSeason = tmdbSeason + (selectedSeason to fetched)
    }
    val seasonStills = tmdbSeason[selectedSeason].orEmpty()

    // The focused episode card latches here and retitles / re-orders the Cast
    // and Crew row. The latch deliberately survives focus leaving the card so
    // D-pad Down from a card still lands on the strip (Apple 561-565).
    var peopleEpisodeNumber by remember(seriesId) { mutableStateOf<Int?>(null) }
    val castCrewPeople = remember(tmdbCredits, peopleEpisodeNumber, seasonStills) {
        val base = tmdbCredits?.let { c -> c.cast + c.directors }.orEmpty()
        val episodeInfo = peopleEpisodeNumber?.let { seasonStills[it] }
        (base + episodeInfo?.guestStars.orEmpty() + episodeInfo?.crew.orEmpty()).distinctBy { it.id }
    }
    val castCrewTitle = peopleEpisodeNumber
        ?.let { "Cast & Crew · Episode $it" }
        ?: "Cast & Crew"

    // Card title: TMDB's episode name first, else the provider's own title
    // with the SHOW name and the S01E01 markers stripped (the raw provider
    // title is usually just the show's name, which is what every Android card
    // was showing), else "Episode N". tvOS: VODDetailView.swift:793-798.
    fun episodeTitle(ep: DispatcharrVODEpisode): String {
        val tmdbName = ep.episodeNumber?.let { seasonStills[it]?.name }?.takeIf { it.isNotBlank() }
        if (tmdbName != null) return tmdbName
        val own = cleanedEpisodeTitle(ep.displayName, series?.displayName ?: "")
        if (own.length > 2) return own
        return "Episode ${ep.episodeNumber ?: ep.id}"
    }

    fun episodeStill(ep: DispatcharrVODEpisode): String? =
        ep.episodeNumber
            ?.let { seasonStills[it]?.stillPath }
            ?.let { viewModel.tmdbStillImageUrl(it) }
            ?: ep.stillImageUrl

    // Capture episode metadata + an up-next queue (rest of the series, ordered
    // by season then episode, capped at 50) before launching the player, so
    // finishing this episode advances Continue Watching to the next one
    // (iOS Issue #19). This is a METADATA-ONLY annotate: a full save(0) here
    // wiped the resume point on every launch from the detail page.
    val playEpisode: (DispatcharrVODEpisode, Boolean) -> Unit = { ep, fromStart ->
        val ordered = episodes.sortedWith(
            compareBy({ it.seasonNumber ?: 0 }, { it.episodeNumber ?: Int.MAX_VALUE }),
        )
        val idx = ordered.indexOfFirst { it.uuid == ep.uuid }
        val queue = if (idx >= 0) {
            ordered.drop(idx + 1).take(50).map {
                UpNextEntry(
                    vodId = it.uuid,
                    title = it.displayName,
                    posterUrl = it.stillImageUrl,
                    streamUrl = null, // re-resolved by uuid at play time
                    seasonNumber = it.seasonNumber ?: 0,
                    episodeNumber = it.episodeNumber ?: 0,
                )
            }
        } else emptyList()
        watchVm.captureEpisodePlay(
            videoId = ep.uuid,
            title = ep.displayName,
            posterUrl = ep.stillImageUrl,
            seriesId = seriesId.toString(),
            seasonNumber = ep.seasonNumber ?: 0,
            episodeNumber = ep.episodeNumber ?: 0,
            streamUrl = null,
            upNextQueue = WatchProgressViewModel.encodeQueue(queue),
        )
        onEpisodeClick(ep, fromStart)
    }

    val toggleWatched: (DispatcharrVODEpisode) -> Unit = { ep ->
        watchVm.setEpisodeWatched(
            videoId = ep.uuid,
            title = ep.displayName,
            posterUrl = ep.stillImageUrl,
            seriesId = seriesId.toString(),
            seasonNumber = ep.seasonNumber ?: 0,
            episodeNumber = ep.episodeNumber ?: 0,
            watched = progressByEpisode[ep.uuid]?.isFinished != true,
        )
    }

    val edgeInset = TV_DETAIL_INSET

    // TV: external links surface as a QR dialog (no browser on Android TV).
    var qrLink by remember { mutableStateOf<TvQrLink?>(null) }
    // TV trailer menu: when a YouTube app can take VIEW for a watch URL the
    // menu offers to play right on this device, with QR as the second row.
    // Boxes without YouTube keep the straight-to-QR path. Resolved once per
    // entry (the installed-package set can't change under this screen) and
    // it needs the https/www.youtube.com <queries> entry in the manifest or
    // API 30+ package-visibility filtering blanks the lookup.
    val youtubeResolvable = remember {
        runCatching {
            context.packageManager.resolveActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=x")),
                0,
            )
        }.getOrNull() != null
    }
    var trailerMenuUrl by remember { mutableStateOf<String?>(null) }

    val trailerUrl = info?.effectiveTrailer?.let { youtubeUrl(it) }
    val tmdbUrl = (info?.tmdbId ?: series?.tmdbId)?.takeIf { it.isNotBlank() }?.let {
        "https://www.themoviedb.org/tv/$it"
    }
    val openLink: (String, String) -> Unit = { label, url ->
        if (label == "Trailer" && youtubeResolvable) {
            trailerMenuUrl = url
        } else {
            qrLink = TvQrLink(
                title = label,
                caption = when (label) {
                    "Trailer" -> "Scan with your phone to watch the trailer on YouTube."
                    else -> "Scan with your phone to view this title on TMDB."
                },
                url = url,
            )
        }
    }

    // Scroll-to-top fix (single-owner page scroll model, unchanged): whichever
    // focusable container is topmost THIS composition calls this on gaining
    // focus to bring the hero back. On TV the hero's own action row is now the
    // topmost focusable, so it owns the call.
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val maybeScrollHeroIntoView: () -> Unit = {
        if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) {
            scope.launch {
                // The focus move that lands here queues its own bring-into-view,
                // which steals the scroll mutex and cancels this animation
                // mid-flight, leaving the hero clipped. Retry until the list
                // genuinely rests at the top.
                repeat(4) {
                    runCatching { listState.animateScrollToItem(0) }
                    if (listState.firstVisibleItemIndex == 0 &&
                        listState.firstVisibleItemScrollOffset == 0
                    ) {
                        return@launch
                    }
                    delay(90L)
                }
            }
        }
    }
    // Mirrors SeriesInfoSection's own chip-visibility logic; keep in sync.
    // On TV the chips moved into the hero action row, so the info block never
    // holds a focusable there.

    // TV: focus the primary Play button the moment it exists (Apple makes it
    // prefersDefaultFocus and re-asserts it with a retry loop, 427-445).
    val firstActionFocus = remember(seriesId) { FocusRequester() }
    var initialFocusDone by remember(seriesId) { mutableStateOf(false) }
    LaunchedEffect(target != null, episodesInSeason.isNotEmpty()) {
        if (initialFocusDone) return@LaunchedEffect
        repeat(10) {
            if (runCatching { firstActionFocus.requestFocus() }.isSuccess) {
                initialFocusDone = true
                return@LaunchedEffect
            }
            delay(16L)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (series == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (resolvingSeries) {
                    androidx.compose.material3.CircularProgressIndicator()
                } else {
                    Text(
                        text = "Series not found",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            // TV: same large-card deadband spec as the VOD grids; the Cast &
            // Crew row's person cards otherwise bounce the whole screen on
            // D-pad left/right (user report, round 2).
            val bringSpec = com.aeriotv.android.ui.tv.TvLargeCardBringIntoViewSpec
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides bringSpec,
            ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 60.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                item {
                    val plot = tmdbDetails?.overview?.takeIf { it.isNotBlank() } ?: cachedOverview
                        ?: info?.effectivePlot?.takeIf { it.isNotBlank() }
                        ?: series.plot?.takeIf { it.isNotBlank() }
                    val genreToken = (info?.effectiveGenre ?: series.genre ?: tmdbDetails?.genres)
                        ?.split(',', '/', '|')?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
                    TvDetailHero(
                        artUrl = cachedBackdropUrl ?: info?.backdropUrl ?: series.posterUrl ?: tmdbPosterUrl,
                        // tvOS shows VODDisplayItem.displayName, which
                        // drops every trailing "(YYYY)" group
                        // (VODModels.swift:1284).
                        title = displayTitle(series.displayName, null),
                        // tvOS series meta: year, first genre token (a
                        // series carries no runtime), then the rating.
                        metaParts = listOfNotNull(
                            info?.year?.toString() ?: series.year?.toString() ?: tmdbDetails?.year,
                            genreToken,
                        ),
                        rating = (info?.rating?.takeIf { it.isNotBlank() } ?: series.rating)
                            ?.let { runCatching { String.format("%.1f", it.toDouble()) }.getOrDefault(it) }
                            ?.takeIf { it.isNotBlank() && it != "0.0" }
                            ?: tmdbDetails?.voteAverage,
                        plot = plot,
                    ) {
                        TvHeroActionButton(
                            title = when {
                                isLoading && episodes.isEmpty() -> "Loading…"
                                else -> target?.label ?: "Play"
                            },
                            icon = Icons.Filled.PlayArrow,
                            primary = true,
                            modifier = Modifier
                                .focusRequester(firstActionFocus)
                                .onFocusChanged { if (it.isFocused) maybeScrollHeroIntoView() },
                            onClick = { target?.let { playEpisode(it.episode, false) } },
                        )
                        if (target?.resuming == true) {
                            TvHeroActionButton(
                                title = "Play from Beginning",
                                icon = Icons.Filled.Replay,
                                onClick = { playEpisode(target.episode, true) },
                            )
                        }
                        if (versionOptions.size > 1) {
                            TvHeroActionButton(
                                title = "Version: ${selectedVersion?.label ?: "Auto"}",
                                icon = Icons.Outlined.Tune,
                                onClick = { showVersionPicker = true },
                            )
                        }
                        trailerUrl?.let { url ->
                            TvHeroActionButton(
                                title = "Trailer",
                                icon = Icons.Outlined.PlayCircle,
                                onClick = { openLink("Trailer", url) },
                            )
                        }
                        tmdbUrl?.let { url ->
                            TvHeroActionButton(
                                title = "TMDB",
                                icon = Icons.Outlined.Info,
                                onClick = { openLink("View on TMDB", url) },
                            )
                        }
                    }
                }

                // tvOS order puts the seasons section directly under the hero
                // and Cast and Crew after it; the phone keeps cast above the
                // episode list (VODDetailView 349-383).

                if (seasons.size > 1) {
                    item(key = "season-picker") {
                        SeasonPicker(
                            seasons = seasons.keys.toList(),
                            selected = selectedSeason,
                            onSelect = { selectedSeason = it },
                            edgeInset = edgeInset,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                } else if (episodes.isNotEmpty()) {
                    // Apple renders a static "Episodes" header for a
                    // single-season show (VODDetailView 856-860).
                    item(key = "episodes-header") {
                        Text(
                            text = "Episodes",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(horizontal = edgeInset),
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }

                if (isLoading && episodes.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                } else if (error != null && episodes.isEmpty()) {
                    item {
                        Text(
                            text = "Couldn't load episodes: $error",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = edgeInset, vertical = 24.dp),
                        )
                    }
                } else if (episodes.isEmpty()) {
                    item {
                        Text(
                            text = "Server returned no episodes for this series.",
                            style = MaterialTheme.typography.bodyMedium.subtext(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = edgeInset, vertical = 24.dp),
                        )
                    }
                } else {
                    // tvOS episode CARDS in a horizontal strip, 32 pt spacing
                    // and 36 pt vertical headroom for the focus scale.
                    item(key = "episode-strip") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = edgeInset, vertical = 18.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            items(items = episodesInSeason, key = { it.id }) { ep ->
                                TvEpisodeCard(
                                    title = episodeTitle(ep),
                                    meta = listOfNotNull(
                                        ep.durationSeconds?.takeIf { it > 0 }?.let { formatEpisodeDuration(it) },
                                        ep.airDate?.let { formatAirDate(it) }?.takeIf { it.isNotBlank() },
                                    ).joinToString(" · ").takeIf { it.isNotBlank() },
                                    episodeNumber = ep.episodeNumber,
                                    stillUrl = episodeStill(ep),
                                    progress = progressByEpisode[ep.uuid],
                                    onClick = { playEpisode(ep, false) },
                                    onToggleWatched = { toggleWatched(ep) },
                                    modifier = Modifier.onFocusChanged {
                                        if (it.isFocused) peopleEpisodeNumber = ep.episodeNumber
                                    },
                                )
                            }
                        }
                    }
                }

                if (castCrewPeople.isNotEmpty()) {
                    item(key = "cast-crew-tv") {
                        CastCrewSection(
                            title = castCrewTitle,
                            people = castCrewPeople,
                            profileUrl = viewModel::tmdbProfileImageUrl,
                            onPersonClick = { bioPerson = it },
                        )
                    }
                }

                item(key = "details-block") {
                    val genre = info?.effectiveGenre?.takeIf { it.isNotBlank() }
                        ?: series.genre?.takeIf { it.isNotBlank() } ?: tmdbDetails?.genres
                    val cast = info?.effectiveCast?.takeIf { it.isNotBlank() } ?: tmdbDetails?.castTop
                    val director = info?.effectiveDirector?.takeIf { it.isNotBlank() } ?: tmdbDetails?.director
                    // tvOS tvFacts order (VODDetailView.swift:1124-1147)
                    // laid into a 2-column row-major grid: left column
                    // Genre then Seasons, right column Released then
                    // Director.
                    val facts = buildList {
                        genre?.let { add("Genre" to joinGenres(it)) }
                        info?.effectiveReleaseDate?.takeIf { it.length > 4 }
                            ?.let { add("Released" to it) }
                        if (episodes.isNotEmpty()) {
                            val sN = seasons.size
                            val eN = episodes.size
                            add(
                                "Seasons" to
                                    "$sN season${if (sN == 1) "" else "s"}, " +
                                    "$eN episode${if (eN == 1) "" else "s"}",
                            )
                        }
                        director?.let { add("Director" to it) }
                        if (castCrewPeople.isEmpty()) cast?.let { add("Cast" to it) }
                        info?.effectiveCountry?.takeIf { it.isNotBlank() }?.let { add("Country" to it) }
                    }
                    TvDetailsBlock(facts = facts) {
                        TmdbSourceNote(
                            tmdbPosterUsed = tmdbPosterUrl != null,
                            tmdbDetailsPresent = tmdbDetails != null,
                            hasProviderArt = hasServerArt,
                            tmdbConfigured = tmdbConfigured,
                            lookupDone = tmdbLookupDone,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }

                if (relatedItems.isNotEmpty()) {
                    item(key = "related") {
                        TvRelatedSection(
                            items = relatedItems,
                            watchlisted = { it.key in watchlistKeys },
                            onOpen = { item ->
                                item.movieUuid?.let(onOpenMovie) ?: item.seriesId?.let(onOpenSeries)
                            },
                            onToggleWatchlist = { watchlistVm.toggle(it) },
                        )
                    }
                }
                item(key = "tmdb-attribution") {
                    // iOS puts the long TMDB attribution at the very bottom of
                    // the page on BOTH platforms (VODDetailView 377-380).
                    TmdbAttribution(
                        modifier = Modifier.padding(horizontal = edgeInset).padding(top = 24.dp),
                        long = true,
                    )
                }
            }
            }
        }

        // Hidden on Android TV: remote BACK pops the screen, and the floating
        // pill would otherwise be an invisible D-pad focus stop.

        if (showVersionPicker) {
            VodVersionPickerSheet(
                options = versionOptions,
                selected = selectedVersion,
                onSelect = { option ->
                    viewModel.selectSeriesVersion(seriesId, option)
                    showVersionPicker = false
                },
                onDismiss = { showVersionPicker = false },
            )
        }

        qrLink?.let { link ->
            TvQrLinkDialog(
                title = link.title,
                caption = link.caption,
                url = link.url,
                onDismiss = { qrLink = null },
            )
        }

        trailerMenuUrl?.let { url ->
            // Guard never armed: this menu opens from a short press, and the
            // rows' own OK latch already ignores the opening press's release.
            TvActionMenuDialog(
                title = "Trailer",
                actions = listOf(
                    TvMenuAction(
                        label = "Play in YouTube",
                        icon = Icons.Filled.PlayArrow,
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            runCatching { context.startActivity(intent) }
                        },
                    ),
                    TvMenuAction(
                        label = "Show QR Code",
                        icon = Icons.Filled.QrCode,
                        onClick = {
                            qrLink = TvQrLink(
                                title = "Trailer",
                                caption = "Scan with your phone to watch the trailer on YouTube.",
                                url = url,
                            )
                        },
                    ),
                ),
                guard = rememberTvMenuGuard(),
                onDismiss = { trailerMenuUrl = null },
            )
        }

        bioPerson?.let { person ->
            // Known For tiles resolve against the library and push the
            // matched detail route. No dismissal on success: the push
            // disposes this composition (dialog included), and on BACK the
            // dialog state re-lands closed over THIS title, which is the
            // desired return-to-origin behavior.
            PersonBioDialog(
                person = person,
                fetchBio = viewModel::resolveTmdbPersonBio,
                profileUrl = viewModel::tmdbProfileImageUrl,
                onDismiss = { bioPerson = null },
                onTileClick = { item ->
                    when (val target2 = viewModel.resolveKnownForTarget(item)) {
                        is OnDemandViewModel.KnownForTarget.Movie -> { onOpenMovie(target2.uuid); true }
                        is OnDemandViewModel.KnownForTarget.Series -> { onOpenSeries(target2.id); true }
                        null -> false
                    }
                },
            )
        }
    }
}

/**
 * Plex-style Cast & Crew strip: TMDB headshot, real name, character or crew
 * role. Cast leads, creators follow (deduped upstream). Cards open
 * [PersonBioDialog]; the 3dp primary focus ring matches the poster cards on
 * the On Demand shelves so D-pad focus reads the same at 10 feet.
 */
@Composable
private fun CastCrewSection(
    title: String,
    people: List<TmdbPerson>,
    profileUrl: (String?, String) -> String?,
    onPersonClick: (TmdbPerson) -> Unit,
    modifier: Modifier = Modifier,
) {
    val edgeInset = TV_DETAIL_INSET
    Column(modifier = modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = edgeInset),
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(
                horizontal = edgeInset,
                // tvOS reserves 36 pt = 18 dp of headroom so the focus scale
                // never clips; phone 12 dp matches the iPhone cast strip.
                vertical = 18.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items = people, key = { it.id }) { person ->
                PersonCard(
                    person = person,
                    photoUrl = profileUrl(person.profilePath, "w185"),
                    onClick = { onPersonClick(person) },
                )
            }
        }
    }
}

@Composable
private fun PersonCard(
    person: TmdbPerson,
    photoUrl: String?,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .width(100.dp)
            .onFocusChanged { focused = it.isFocused }
            .tvFocusScale(focused)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
                .then(
                    if (focused) Modifier.border(
                        3.dp,
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(10.dp),
                    ) else Modifier,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (!photoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = person.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        // Name and role centered under the photo (Logan 2026-09-10, all platforms).
        // tvOS: name .labelMedium (20 pt -> 10 sp, medium), role .labelSmall
        // (18 pt -> 9 sp). labelLarge semibold at 14 sp wrapped
        // "Yoshitaka Yamaya" across two lines on the 100 dp card.
        Text(
            text = person.name,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        person.role?.takeIf { it.isNotBlank() }?.let { role ->
            Text(
                text = role,
                fontSize = (9.sp).subtext(),
                lineHeight = (11.sp).subtext(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PillButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .tvFocusScale(focused)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
            .border(
                width = 2.dp,
                color = if (focused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(50),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.textAccent,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium.subtext(),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.width(80.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.textAccent,
        )
    }
}

@Composable
private fun SeasonPicker(
    seasons: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    edgeInset: androidx.compose.ui.unit.Dp = 16.dp,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = edgeInset),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items = seasons) { season ->
            val isSelected = season == selected
            // TV chrome canon (ui/tv/TvChrome.kt): same capsule + ring rules
            // as the library group pills (tvOS MoviesPillStyle).
            com.aeriotv.android.ui.tv.TvPill(
                // Apple always renders "Season {n}" (VODDetailView 849).
                label = "Season $season",
                selected = isSelected,
                onClick = { onSelect(season) },
            )
            return@items
        }
    }
}

@Composable
private fun FloatingBackButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .statusBarsPadding()
            .padding(top = 8.dp, start = 8.dp),
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun formatEpisodeDuration(secs: Int): String {
    val h = secs / 3600
    val m = (secs % 3600) / 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}

/**
 * Dispatcharr emits air_date as `yyyy-MM-dd` (POSIX, UTC). Format to the
 * user's locale short style - iOS does the same conversion in
 * VODEpisode.displayAirDate (VODModels.swift lines 469-484).
 */
private fun formatAirDate(raw: String): String {
    val trimmed = raw.takeIf { it.length >= 10 } ?: return raw
    return runCatching {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val date = parser.parse(trimmed.substring(0, 10)) ?: return raw
        DateFormat.getDateInstance(DateFormat.SHORT).format(date)
    }.getOrDefault(raw)
}

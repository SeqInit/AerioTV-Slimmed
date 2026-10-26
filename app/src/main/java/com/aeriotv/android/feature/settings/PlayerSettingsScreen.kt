package com.aeriotv.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.aeriotv.android.core.ui.SkipIntervals
import com.aeriotv.android.ui.adaptive.LocalTabBarBottomInset
import com.aeriotv.android.ui.settings.LocalSettingsSubPageHost
import com.aeriotv.android.ui.settings.SettingsSliderRow
import com.aeriotv.android.ui.settings.SettingsDetailTopBar
import com.aeriotv.android.ui.settings.SettingsPickerOption
import com.aeriotv.android.ui.settings.SettingsPickerRow
import com.aeriotv.android.ui.settings.SettingsSection
import com.aeriotv.android.ui.settings.SettingsSectionFooter
import com.aeriotv.android.ui.settings.SettingsSubGroup
import com.aeriotv.android.ui.settings.SettingsSubPageHost
import com.aeriotv.android.ui.settings.SettingsToggleRow
import com.aeriotv.android.ui.settings.settingsFormWidth
import com.aeriotv.android.ui.settings.settingsItemsSummary
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Settings > Player. Everything about fullscreen playback: the info card, live
 * rewind, playback buffering and recovery, gestures, and the TV display-mode
 * controls.
 *
 * Settings phase 1 regroup: rows came from App Behaviors (info card, rewind,
 * skip intervals, gestures, stream recovery, audio, display), Network (buffer
 * size) and the retired Multiview page. Keys, control types and copy are
 * carried over unchanged.
 */
@Composable
fun PlayerSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    // GH #94: read straight from AppPreferences (no SettingsViewModel hop).
    val playerPrefsContext = androidx.compose.ui.platform.LocalContext.current
    val playerPrefs = androidx.compose.runtime.remember {
        dagger.hilt.android.EntryPointAccessors.fromApplication(
            playerPrefsContext.applicationContext,
            PlayerSettingsEntryPoint::class.java,
        ).appPreferences()
    }
    val playerPrefsScope = androidx.compose.runtime.rememberCoroutineScope()
    val skipWithoutControls by playerPrefs.skipWithoutControls.collectAsStateWithLifecycle(initialValue = false)

    val radioModeByDefault by viewModel.radioModeByDefault
        .collectAsStateWithLifecycle(initialValue = false)
    val showChannelInfoCard by viewModel.playerShowChannelInfoCard
        .collectAsStateWithLifecycle(initialValue = true)
    val cardChannelLogo by viewModel.playerCardShowChannelLogo
        .collectAsStateWithLifecycle(initialValue = true)
    val cardChannelName by viewModel.playerCardShowChannelName
        .collectAsStateWithLifecycle(initialValue = true)
    val cardProgramName by viewModel.playerCardShowProgramName
        .collectAsStateWithLifecycle(initialValue = true)
    val cardProgramTime by viewModel.playerCardShowProgramTime
        .collectAsStateWithLifecycle(initialValue = true)
    val cardProgramSubtitle by viewModel.playerCardShowProgramSubtitle
        .collectAsStateWithLifecycle(initialValue = true)
    val cardProgramDescription by viewModel.playerCardShowProgramDescription
        .collectAsStateWithLifecycle(initialValue = true)

    val liveRewindEnabled by viewModel.liveRewindEnabled.collectAsStateWithLifecycle(initialValue = false)
    val liveRewindDepth by viewModel.liveRewindDepthMinutes.collectAsStateWithLifecycle(initialValue = 30)
    val keepRecent by viewModel.liveRewindKeepRecent.collectAsStateWithLifecycle(initialValue = false)
    val keepCount by viewModel.liveRewindKeepCount.collectAsStateWithLifecycle(initialValue = 2)

    val skipBackSeconds by viewModel.skipBackSeconds
        .collectAsStateWithLifecycle(initialValue = SkipIntervals.DEFAULT_BACK_SECONDS)
    val skipForwardSeconds by viewModel.skipForwardSeconds
        .collectAsStateWithLifecycle(initialValue = SkipIntervals.DEFAULT_FORWARD_SECONDS)
    val bufferSize by viewModel.streamBufferSize.collectAsStateWithLifecycle(initialValue = "default")
    val hdrToSdr by viewModel.hdrToSdr
        .collectAsStateWithLifecycle(initialValue = com.aeriotv.android.core.playback.HdrToneMap.AUTO)
    val autoRecoverFrozenStreams by viewModel.autoRecoverFrozenStreams
        .collectAsStateWithLifecycle(initialValue = true)
    val audioPassthrough by viewModel.audioPassthroughEnabled.collectAsStateWithLifecycle(initialValue = false)

    val appleTVChannelFlip by viewModel.appleTVChannelFlip.collectAsStateWithLifecycle(initialValue = true)

    val startupRefreshRate by viewModel.startupRefreshRate.collectAsStateWithLifecycle(initialValue = "off")
    val matchContentResolution by viewModel.matchContentResolution.collectAsStateWithLifecycle(initialValue = false)
    val matchFrameRate by viewModel.matchFrameRate.collectAsStateWithLifecycle(initialValue = false)

    SettingsSubPageHost {
    // Profiles phase 2b: a Child sees only the basics (info card, skip lengths).
    val childRole = com.aeriotv.android.core.profiles.ProfileRoles.active.collectAsStateWithLifecycle().value ==
        com.aeriotv.android.core.profiles.ProfileRole.Child
    Column(modifier = Modifier.fillMaxSize()) {
        SettingsDetailTopBar(title = "Player", onBack = onBack)

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .settingsFormWidth()
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = 12.dp,
                        bottom = LocalTabBarBottomInset.current,
                    ),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // MARK: On-Screen Display
                //
                // Apple's eyebrow is "On-Screen Display" on both platforms;
                // the master row below is what says "Info Card".
                SettingsSection(
                    header = "On-Screen Display",
                    // tvOS prints this footer inside the Info Card sheet only.
                    footer = null,
                ) {
                    // Phase 3, item 4: six toggles behind one master row whose
                    // subtitle names what is on ("Logo, name, time" / "All 6").
                    // TV keeps them inline under the header.
                    val cardParts = listOf(
                        Triple("Channel Logo", "logo", cardChannelLogo) to
                            viewModel::setPlayerCardShowChannelLogo,
                        Triple("Channel Name", "name", cardChannelName) to
                            viewModel::setPlayerCardShowChannelName,
                        Triple("Program Name", "program", cardProgramName) to
                            viewModel::setPlayerCardShowProgramName,
                        Triple("Program Time", "time", cardProgramTime) to
                            viewModel::setPlayerCardShowProgramTime,
                        Triple("Program Subtitle", "subtitle", cardProgramSubtitle) to
                            viewModel::setPlayerCardShowProgramSubtitle,
                        Triple("Program Description", "description", cardProgramDescription) to
                            viewModel::setPlayerCardShowProgramDescription,
                    )
                    SettingsSubGroup(
                        title = "Info Card",
                        summary = settingsItemsSummary(
                            enabled = cardParts.filter { it.first.third }
                                .map { it.first.second }
                                .let { parts ->
                                    // Sentence-case the first item only, so the
                                    // subtitle reads "Logo, name, time".
                                    parts.mapIndexed { i, p ->
                                        if (i == 0) p.replaceFirstChar { c -> c.uppercase() } else p
                                    }
                                },
                            total = cardParts.size,
                        ),
                        // Apple: rectangle.on.rectangle.
                        leadingIcon = Icons.Filled.FilterNone,
                        footer = PLAYER_INFO_CARD_FOOTER,
                        // tvOS PlayerSettingsView: the six switches sit behind
                        // an "Info Card" master row that opens an option sheet.
                        tvOptionsSheet = true,
                    ) {
                        cardParts.forEach { (part, setter) ->
                            SettingsToggleRow(
                                title = part.first,
                                // tvOS rows carry info.circle; iOS toggles are bare.
                                leadingIcon = Icons.Outlined.Info,
                                checked = part.third,
                                onCheckedChange = setter,
                            )
                        }
                    }
                    // GH #127 (Apple d051505): only the pop-up on a channel
                    // change; the card still shows with the player controls.
                    SettingsToggleRow(
                        title = "Pop Up Info Card on Channel Change",
                        subtitle = "Show the channel and program card for a few seconds after a channel change. The card still appears with the player controls.",
                        leadingIcon = Icons.Filled.FilterNone,
                        checked = showChannelInfoCard,
                        onCheckedChange = viewModel::setPlayerShowChannelInfoCard,
                    )
                }

                // MARK: Radio Mode
                //
                // Radio Mode by Default (Discord request, Apple parity): every
                // live channel pick opens Radio Mode; the long-press Watch
                // still plays video. Default off.
                if (!childRole) SettingsSection(
                    header = "Radio Mode",
                    footer = RADIO_MODE_BY_DEFAULT_FOOTER,
                ) {
                    SettingsToggleRow(
                        title = "Radio Mode by Default",
                        leadingIcon = Icons.Filled.Radio,
                        checked = radioModeByDefault,
                        onCheckedChange = viewModel::setRadioModeByDefault,
                    )
                }

                // MARK: Live Rewind
                //
                // Apple layout: the master toggle, then (while it is on) one
                // "Live Rewind" subgroup whose subtitle is the depth ("30
                // minutes") and whose page holds Rewind Up To, Keep Recent
                // Channels Live and Channels to Keep. On TV the subgroup
                // renders inline with no master row, as tvOS does
                // (showsMasterRow: false). The depth estimate moved onto the
                // Rewind Up To footer, where Apple prints it.
                if (!childRole) SettingsSection(
                    header = "Live Rewind",
                    // Apple TV prints this directly under the toggle instead.
                    footer = null,
                ) {
                    SettingsToggleRow(
                        title = "Pause & Rewind Live TV",
                        subtitle = "Buffer fullscreen live playback on this device",
                        leadingIcon = Icons.Filled.Replay30,
                        checked = liveRewindEnabled,
                        onCheckedChange = viewModel::setLiveRewindEnabled,
                    )
                    SettingsSectionFooter(LIVE_REWIND_FOOTER)
                    if (liveRewindEnabled) {
                        val snappedDepth = REWIND_DEPTH_MINUTES.minByOrNull {
                            abs(it - liveRewindDepth)
                        } ?: liveRewindDepth
                        SettingsSubGroup(
                            title = "Live Rewind",
                            summary = formatDepthMinutes(snappedDepth),
                            leadingIcon = Icons.Filled.Replay30,
                            footer = "How far back you can rewind the channel you are watching. Buffered video is released as soon as you leave the channel. Keeping recent channels live holds an extra stream connection per channel; opening a channel beyond the limit drops the oldest.",
                        ) {
                            // The sub-page host shows one page at a time and
                            // drops a body whose row leaves composition, so a
                            // picker pushed from inside this pushed page would
                            // close itself on open. With the host hidden the
                            // picker lays its choices out inline on this page
                            // (title, options, footer), one level deep.
                            CompositionLocalProvider(LocalSettingsSubPageHost provides null) {
                                SettingsPickerRow(
                                    title = "Rewind Up To",
                                    tvChoiceSheet = true,
                                    inlineTitle = true,
                                    options = REWIND_DEPTH_MINUTES.map {
                                        SettingsPickerOption(it, formatDepthMinutes(it))
                                    },
                                    selected = snappedDepth,
                                    onSelect = viewModel::setLiveRewindDepthMinutes,
                                    footer = "How far back you can rewind the channel you are watching. Buffered video is released as soon as you leave the channel. " +
                                        depthEstimateText(liveRewindDepth),
                                )
                            }
                            SettingsToggleRow(
                                title = "Keep Recent Channels Live",
                                subtitle = "Buffer flipped-away channels in the background",
                                leadingIcon = Icons.AutoMirrored.Filled.PlaylistPlay,
                                checked = keepRecent,
                                onCheckedChange = viewModel::setLiveRewindKeepRecent,
                            )
                            // One footer sentence under the toggle, every form
                            // factor (Apple parity).
                            SettingsSectionFooter("Kept channels keep one connection to your server open until you stop them.")
                            if (keepRecent) {
                                SteppedSliderRow(
                                    label = "Channels to Keep",
                                    values = listOf(1, 2, 3, 4, 5),
                                    selected = keepCount,
                                    format = { it.toString() },
                                    onSelect = viewModel::setLiveRewindKeepCount,
                                )
                            }
                        }
                    }
                }

                // MARK: Playback
                //
                // Apple order: Skip Back, Skip Forward, (Stream Buffer),
                // Buffer Size, Auto-Recover. Android has no Stream Buffer
                // preference, so that row and its footer sentence are absent
                // until one exists. tvOS prints the skip footer right under
                // the skip rows and gives Auto-Recover a subtitle; iOS folds
                // both into the section footer.
                SettingsSection(
                    header = "Playback",
                    footer = null,
                ) {
                    // tvOS: pill row 5s 10s 15s 30s 60s, as Display Scale.
                    ScaleSliderRow(
                        label = "Skip Back",
                        value = skipBackSeconds.toFloat(),
                        onValueChange = { viewModel.setSkipBackSeconds(it.toInt()) },
                        segments = SkipIntervals.CHOICES.map { it.toFloat() to "${it}s" },
                    )
                    // tvOS: pill row 5s 10s 15s 30s 60s, as Display Scale.
                    ScaleSliderRow(
                        label = "Skip Forward",
                        value = skipForwardSeconds.toFloat(),
                        onValueChange = { viewModel.setSkipForwardSeconds(it.toInt()) },
                        segments = SkipIntervals.CHOICES.map { it.toFloat() to "${it}s" },
                    )
                    // GH #94 (Apple parity): bare-video Left/Right skip.
                    SettingsToggleRow(
                        title = "Skip Without Controls",
                        checked = skipWithoutControls,
                        onCheckedChange = { v -> playerPrefsScope.launch { playerPrefs.setSkipWithoutControls(v) } },
                    )
                    SettingsSectionFooter("With the controls hidden, Left and Right skip right away instead of opening the timeline.")
                    SettingsSectionFooter("How far the skip buttons and a single left or right press move in live rewind, catch-up, recordings, movies, and TV shows. Holding left or right still scrubs faster the longer you hold.")
                    if (!childRole) SettingsPickerRow(
                        title = "Buffer Size",
                        tvChoiceSheet = true,
                        inlineTitle = true,
                        options = BUFFER_OPTIONS.map {
                            SettingsPickerOption(it.id, it.label, it.detail)
                        },
                        // Unknown ids (the retired "small") read as Default.
                        selected = BUFFER_OPTIONS.firstOrNull { it.id == bufferSize }?.id
                            ?: BUFFER_OPTIONS.first().id,
                        onSelect = viewModel::setStreamBufferSize,
                        footer = "Buffer Size controls how much stream data is pre-loaded. Larger buffers reduce stuttering on poor connections but add startup delay.",
                    )
                    // ANDROID: decoder-side HDR to SDR (core/playback HdrToneMap).
                    SettingsPickerRow(
                        title = "HDR to SDR",
                        tvChoiceSheet = true,
                        inlineTitle = true,
                        options = listOf(
                            SettingsPickerOption(com.aeriotv.android.core.playback.HdrToneMap.AUTO, "Auto", "When the TV can't show the stream's HDR"),
                            SettingsPickerOption(com.aeriotv.android.core.playback.HdrToneMap.ALWAYS, "Always", "Every HDR stream plays as SDR"),
                            SettingsPickerOption(com.aeriotv.android.core.playback.HdrToneMap.OFF, "Off", "Pass HDR through untouched"),
                        ),
                        selected = hdrToSdr,
                        onSelect = viewModel::setHdrToSdr,
                        footer = "Converts HDR10 (and HLG) video to SDR on this device so it doesn't look washed out on a TV that can't display it. Uses the video decoder's own tone mapping on Android 13 and later; some decoders don't support it. Applies to the next channel you tune.",
                    )
                    if (!childRole) SettingsToggleRow(
                        title = "Auto-Recover Frozen Streams",
                        subtitle = "If a live stream stops sending video, the player reloads it to recover. Turn this off if live channels restart or stutter during commercial breaks; a brief freeze may show instead.",
                        leadingIcon = Icons.Filled.Refresh,
                        checked = autoRecoverFrozenStreams,
                        onCheckedChange = viewModel::setAutoRecoverFrozenStreams,
                    )
                }

                if (!childRole) SettingsSection(
                    header = "Audio",
                    footer = "Turn this on only if your audio gear supports surround formats. " +
                        "Leave it off for TV speakers, headphones or Bluetooth, or if a " +
                        "channel plays with no sound. When off, AerioTV decodes the audio " +
                        "on this device, which works with every setup.",
                ) {
                    SettingsToggleRow(
                        title = "Surround Sound Passthrough",
                        subtitle = "Sends surround audio, such as 5.1, to your TV, soundbar or receiver untouched so it can decode it.",
                        checked = audioPassthrough,
                        onCheckedChange = viewModel::setAudioPassthroughEnabled,
                    )
                }

                // MARK: Gestures
                if (!childRole) SettingsSection(
                    header = "Gestures",
                    // tvOS / Android TV flip channels with D-pad up/down, not a
                    // swipe, so the "accidental swipes" caution is meaningless on
                    // a remote (user request: drop the note on TV). Phones keep it.
                    footer = "Turn off if accidental D-pad presses are flipping channels during playback. Phones and tablets use the matching swipe-up / swipe-down gesture on the same toggle.",
                ) {
                    SettingsToggleRow(
                        title = "Up / Down Channel Change",
                        subtitle = "While the player chrome is visible, press up for the next channel and down for the previous. Live single-stream playback only.",
                        // tvOS arrow.up.and.down; the iOS toggle is bare.
                        leadingIcon = Icons.Filled.SwapVert,
                        checked = appleTVChannelFlip,
                        onCheckedChange = viewModel::setAppleTVChannelFlip,
                    )
                }

                // MARK: Display (TV only)
                //
                // GH #38 + #40: each switch is a real HDMI mode change (brief
                // black screen), deliberate and user-opted.
                if (!childRole) {
                    SettingsSection(
                        header = "Display",
                        footer = "Startup Refresh Rate switches the display once at app launch so it is already on your main content rate before the first channel (changes apply on next launch). Match Frame Rate switches the refresh rate to the stream's frame rate and keeps the current resolution. Match Content Resolution outputs at the stream's resolution so your TV does the upscaling. The screen blinks briefly on each switch.",
                    ) {
                        SettingsToggleRow(
                            title = "Match Content Resolution",
                            subtitle = "Output 1080p streams at 1080p and let the TV upscale. Off keeps the display at its native mode.",
                            checked = matchContentResolution,
                            onCheckedChange = viewModel::setMatchContentResolution,
                        )
                        SettingsToggleRow(
                            title = "Match Frame Rate",
                            subtitle = "Switch to 50 Hz or 60 Hz to match the stream so motion stays smooth. The resolution is not changed.",
                            checked = matchFrameRate,
                            onCheckedChange = viewModel::setMatchFrameRate,
                        )
                        val rates = listOf(
                            SettingsPickerOption("off", "Off (System Default)"),
                            SettingsPickerOption("50", "50 Hz"),
                            SettingsPickerOption("59.94", "59.94 Hz"),
                            SettingsPickerOption("60", "60 Hz"),
                        )
                        SettingsPickerRow(
                            title = "Startup Refresh Rate",
                            tvChoiceSheet = true,
                            inlineTitle = true,
                            options = rates,
                            selected = rates.firstOrNull { it.value == startupRefreshRate }?.value
                                ?: "off",
                            onSelect = viewModel::setStartupRefreshRate,
                        )
                    }
                }
            }
        }
    }
    }
}

/** Apple's playerInfoCardFooter; used by the section and the pushed page. */
private const val PLAYER_INFO_CARD_FOOTER =
    "Choose what appears on the program info card in the player while the controls are showing."

data class BufferOption(val id: String, val label: String, val detail: String, val cachingMs: Int)

/**
 * Discord (di5cord20, Formuler Z11, 2026-08-09): "Default -> Small made
 * zapping worse." It cannot have. The live player applies
 * `minBufferMs = maxOf(4_000, bufferFloorMs)` (AerioExoPlayerHolder), and the
 * old ladder was 300 / 1000 / 3000 / 8000 ms, so **Small, Default and Large
 * all collapsed onto the same 4s/8s LoadControl**: three of the four options
 * did nothing at all, while their subtitles advertised latencies (300 ms,
 * 1 second, 3 seconds) the player never used. Extra Large was worse than
 * inert - 8000 produced min == max == 8s, a LoadControl with no headroom
 * between its two bounds.
 *
 * The 4s floor is not an accident: it is the measured fix for the recurring
 * mid-stream micro-stutter on the Streamer (see the LoadControl comment in
 * AerioExoPlayerHolder, which walks through why 500ms and 2.5s both failed).
 * Nothing may sit below it, which leaves no room for a real "Small" - so it
 * is gone, and anyone on it is mapped to Default, which is exactly what they
 * were already getting.
 *
 * Default keeps its current behaviour to the millisecond, so no existing
 * install changes. Large and Extra Large start doing what their labels always
 * claimed.
 */
internal val BUFFER_OPTIONS: List<BufferOption> = listOf(
    BufferOption("default", "Default", "4 seconds - recommended", 4_000),
    BufferOption("large", "Large", "8 seconds - unstable connections", 8_000),
    BufferOption("xlarge", "Extra Large", "16 seconds - very poor networks", 16_000),
)

/** Unknown ids (including the retired "small") resolve to Default. */
internal fun bufferMillisFor(id: String): Int =
    BUFFER_OPTIONS.firstOrNull { it.id == id }?.cachingMs ?: 4_000

/** Keep Available ladder (2026-07-11 rework, user directive round 2:
 *  the user-meaningful knob is HOW FAR BACK you can rewind, not how
 *  long files persist - retention is now a fixed internal 1 hour). */
private val REWIND_DEPTH_MINUTES = listOf(15, 30, 60, 90, 120, 180)

private fun formatDepthMinutes(mins: Int): String = when {
    mins < 60 -> "$mins minutes"
    mins == 60 -> "1 hour"
    mins % 60 == 0 -> "${mins / 60} hours"
    else -> "${mins / 60}h ${mins % 60}m"
}

/**
 * Storage estimate under the Keep Available slider: scales with the
 * depth choice (which bounds live disk usage now that retention is a
 * fixed short window) at typical stream bitrates - HD ~4 Mbps, FHD ~8,
 * UHD ~20 - so the user can pick what fits their streams and disk.
 */
private fun depthEstimateText(mins: Int): String {
    fun gb(mbps: Int): String {
        val v = mbps * 7.5 * mins / 1024.0
        return if (v < 10) String.format("~%.1f GB", v) else "~${v.roundToInt()} GB"
    }
    return "Uses up to ${gb(4)} in HD, ${gb(8)} in FHD, or ${gb(20)} in UHD while you watch."
}

/**
 * Discrete-stop slider row: label left, current value right, the shared
 * Settings slider beneath (thin continuous track, no tick dots). TV-safe via
 * the escape modifier [SettingsSlider] applies for every caller (the #90
 * lesson: UP/DOWN must move focus off the slider, LEFT/RIGHT adjust).
 */
@Composable
internal fun SteppedSliderRow(
    label: String,
    values: List<Int>,
    selected: Int,
    format: (Int) -> String,
    onSelect: (Int) -> Unit,
    footer: String? = null,
) {
    // Snap legacy/custom persisted values (e.g. 48h from the removed
    // custom dialog) to the nearest ladder stop for display; the pref
    // itself is only rewritten when the user moves the slider.
    val idx = values.indices.minByOrNull { abs(values[it] - selected) } ?: 0
    SettingsSliderRow(
        label = label,
        valueText = format(values[idx]),
        // Apple's stepped rows print the value in labelSmall, tertiary tint.
        dimValue = true,
        index = idx,
        lastIndex = values.lastIndex,
        onIndexChange = { newIdx -> if (values[newIdx] != selected) onSelect(values[newIdx]) },
        footer = footer,
    )
}

private const val LIVE_REWIND_FOOTER =
    "Buffers the channel you are watching so you can pause and " +
        "rewind live TV. Uses device storage while you watch; " +
        "buffered video is removed automatically."

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface PlayerSettingsEntryPoint {
    fun appPreferences(): com.aeriotv.android.core.preferences.AppPreferences
}

private const val RADIO_MODE_BY_DEFAULT_FOOTER =
    "Every channel opens in Radio Mode. Long-press a channel and choose Watch to play video."

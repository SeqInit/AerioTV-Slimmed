package com.aeriotv.android.feature.player

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.aeriotv.android.core.data.EPGProgramme
import com.aeriotv.android.core.data.M3UChannel
import com.aeriotv.android.core.ui.ClockFormat
import com.aeriotv.android.core.ui.rememberClockMode
import com.aeriotv.android.ui.theme.textAccent
import com.aeriotv.android.ui.tv.tvFocusScale
import java.util.Date

/** A recently watched channel for the tiles row, with what airs on it now. */
internal data class SidebarRecent(val channel: M3UChannel, val now: EPGProgramme?)

/**
 * ANDROID (no Apple counterpart): the live player controls of the
 * Sidebar-style TV interface, drawn instead of PlayerChromeOverlay's
 * controls while that interface is on (LiveRewindChromeSection decides).
 * The chrome is one bottom panel over a dark gradient:
 *
 *  - program block: channel logo, title, time range with a small progress
 *    bar and minutes left, channel number and name, HD / FPS / audio chips,
 *    and the next program's time and title;
 *  - a full-width seek bar for the airing program with elapsed / duration
 *    under it. With a rolling buffer only the reachable stretch (oldest
 *    buffered to live) is accent, grey on both sides, and the thumb marks
 *    playback; the band's left edge follows the buffer tail as the window
 *    rolls. Without a buffer it fills from the program start to now.
 *    With a rolling buffer it takes focus: Left / Right scrub through the
 *    buffer (never past live), OK commits at once;
 *  - the transport row: skip back, rewind, play/pause, fast-forward, skip
 *    forward, then LIVE (only while behind live) and Record on the right;
 *  - a tiles row peeking under the transport. Down swaps the transport row
 *    for the tiles (TV guide, History, recent channels, Clear, Options); Up
 *    from the tiles brings the transport back.
 *
 * Colours come from the app's colour scheme (Appearance > theme), so the
 * panel follows the user's theme like every other surface. Every action is
 * the host's: this composable owns only layout and focus.
 */
@Composable
internal fun SidebarPlayerChrome(
    visible: Boolean,
    channel: M3UChannel?,
    nowProgramme: EPGProgramme?,
    nextProgramme: EPGProgramme?,
    formatChips: List<String>,
    /** Playback position as wall time; null = at the live edge (now). */
    positionWallMs: Long?,
    atLive: Boolean,
    /** A rolling buffer exists, so rewind / skip back / scrub can do something. */
    canSeek: Boolean,
    /** Rewindable wall-time range (oldest buffered, newest buffered) while a
     *  buffer rolls (empty when Rewind is on but nothing can buffer: all
     *  grey); null = Rewind off, the bar fills up to playback. */
    bufferRange: Pair<Long, Long>?,
    isPaused: Boolean,
    canRecord: Boolean,
    recents: List<SidebarRecent>,
    onTogglePause: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    /** Seek bar: one scrub step (direction, key autorepeat); the host clamps to the buffer. */
    onScrubStep: (Int, Boolean) -> Unit,
    /** Seek bar OK: commit the pending scrub now. */
    onScrubCommit: () -> Unit,
    onRestartProgram: () -> Unit,
    onGoLive: () -> Unit,
    onRecord: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenHistory: () -> Unit,
    onTuneRecent: (String) -> Unit,
    onClearHistory: () -> Unit,
    onOpenOptions: () -> Unit,
    /** Restart the host's auto-hide countdown (every focus move / press). */
    onInteraction: () -> Unit,
) {
    var tilesMode by remember { mutableStateOf(false) }
    val pauseFocus = remember { FocusRequester() }
    val firstTileFocus = remember { FocusRequester() }
    // Each reveal starts on the transport with Play/Pause focused (sidebar style).
    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        tilesMode = false
        kotlinx.coroutines.delay(100)
        runCatching { pauseFocus.requestFocus() }
    }
    LaunchedEffect(tilesMode, visible) {
        if (!visible) return@LaunchedEffect
        androidx.compose.runtime.withFrameNanos { }
        runCatching { if (tilesMode) firstTileFocus.requestFocus() else pauseFocus.requestFocus() }
    }
    // A ticking clock while shown: the time left, progress and live position.
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(visible) {
        while (visible) {
            nowMs = System.currentTimeMillis()
            kotlinx.coroutines.delay(1_000)
        }
    }

    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        val scrim = MaterialTheme.colorScheme.background
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(360.dp)
                    // Dark enough behind the text that bright video never
                    // washes it out (the panel reads as a dim band).
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.3f to scrim.copy(alpha = 0.6f),
                            1f to scrim.copy(alpha = 0.88f),
                        ),
                    ),
            )
            // The tiles row peeks under the transport (only its top edge on
            // screen); Down slides the panel up so the tiles replace it.
            val panelOffset by animateDpAsState(if (tilesMode) 0.dp else TILE_HEIGHT - TILE_PEEK, label = "sidebarPanel")
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .offset(y = panelOffset)
                    .padding(start = 38.dp, end = 38.dp, bottom = 18.dp)
                    .onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown) onInteraction()
                        false
                    },
            ) {
                ProgramBlock(channel, nowProgramme, nextProgramme, formatChips, nowMs)
                Spacer(Modifier.height(10.dp))
                val position = positionWallMs ?: nowMs
                SeekBar(
                    programme = nowProgramme,
                    position = position,
                    bufferRange = bufferRange,
                    focusable = canSeek && !tilesMode,
                    onScrubStep = onScrubStep,
                    onScrubCommit = onScrubCommit,
                )
                if (!tilesMode) {
                    TransportRow(
                        programme = nowProgramme,
                        position = position,
                        atLive = atLive,
                        canSeek = canSeek,
                        isPaused = isPaused,
                        canRecord = canRecord,
                        pauseFocus = pauseFocus,
                        onTogglePause = onTogglePause,
                        onRewind = onRewind,
                        onForward = onForward,
                        onRestartProgram = onRestartProgram,
                        onGoLive = onGoLive,
                        onRecord = onRecord,
                        onDown = { tilesMode = true },
                    )
                } else {
                    Spacer(Modifier.height(12.dp))
                }
                TilesRow(
                    focusable = tilesMode,
                    recents = recents,
                    nowMs = nowMs,
                    firstFocus = firstTileFocus,
                    onUp = { tilesMode = false },
                    onOpenGuide = onOpenGuide,
                    onOpenHistory = onOpenHistory,
                    onTuneRecent = onTuneRecent,
                    onClearHistory = onClearHistory,
                    onOpenOptions = onOpenOptions,
                )
            }
        }
    }
}

@Composable
private fun ProgramBlock(
    channel: M3UChannel?,
    programme: EPGProgramme?,
    next: EPGProgramme?,
    chips: List<String>,
    nowMs: Long,
) {
    val cs = MaterialTheme.colorScheme
    val meta = cs.onSurfaceVariant
    val clockMode = rememberClockMode()
    val fmt = remember(clockMode) { ClockFormat.guideShort(clockMode) }
    val range: (EPGProgramme) -> String = { p -> fmt.format(Date(p.startMillis)) + " — " + fmt.format(Date(p.endMillis)) }
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(modifier = Modifier.width(LOGO_WIDTH).height(56.dp), contentAlignment = Alignment.CenterStart) {
            if (channel != null && channel.tvgLogo.isNotBlank()) {
                AsyncImage(
                    model = channel.tvgLogo, contentDescription = null, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                programme?.title ?: channel?.name.orEmpty(),
                fontSize = 22.sp, fontWeight = FontWeight.Medium, color = cs.onBackground,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (programme != null) {
                    Text(range(programme), fontSize = 13.sp, color = meta, maxLines = 1)
                    val total = (programme.endMillis - programme.startMillis).coerceAtLeast(1L)
                    val fraction = ((nowMs - programme.startMillis).toFloat() / total).coerceIn(0f, 1f)
                    Box(Modifier.width(40.dp).height(3.dp).clip(CircleShape).background(cs.onBackground.copy(alpha = 0.25f))) {
                        Box(Modifier.fillMaxWidth(fraction).height(3.dp).background(cs.onBackground.copy(alpha = 0.85f)))
                    }
                    val left = ((programme.endMillis - nowMs + 59_999L) / 60_000L).coerceAtLeast(0L)
                    Text("$left min", fontSize = 13.sp, color = meta, maxLines = 1)
                }
                if (channel != null) {
                    val number = channel.channelNumber?.takeIf { it.isNotBlank() }
                    Text(
                        if (number != null) "$number  ${channel.name}" else channel.name,
                        fontSize = 13.sp, fontWeight = FontWeight.Medium, color = cs.onBackground,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 240.dp),
                    )
                }
                chips.forEach { chip ->
                    Text(
                        chip, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = cs.onBackground.copy(alpha = 0.9f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(cs.onBackground.copy(alpha = 0.22f))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }
            if (next != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(range(next), fontSize = 13.sp, color = meta, maxLines = 1)
                    Text(next.title, fontSize = 14.sp, color = meta, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun SeekBar(
    programme: EPGProgramme?,
    position: Long,
    bufferRange: Pair<Long, Long>?,
    focusable: Boolean,
    onScrubStep: (Int, Boolean) -> Unit,
    onScrubCommit: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val thumb by animateDpAsState(if (focused) 16.dp else 9.dp, label = "sidebarThumb")
    fun fractionOf(wallMs: Long): Float = programme?.let { p ->
        val total = (p.endMillis - p.startMillis).coerceAtLeast(1L)
        ((wallMs - p.startMillis).toFloat() / total).coerceIn(0f, 1f)
    } ?: 0f
    val fraction = fractionOf(position)
    // Accent band: the reachable buffer clamped to this program (a kept
    // channel's or long window's tail can predate the program start), or
    // program start -> playback when no buffer rolls. Gaps are ignored.
    val bandStart = if (bufferRange != null && programme != null) fractionOf(bufferRange.first) else 0f
    val bandEnd = if (bufferRange != null && programme != null) fractionOf(bufferRange.second) else fraction
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(18.dp)
            // Left / Right scrub through the buffer; the host clamps the
            // target to [oldest buffered, live], so the bar can go back and
            // forward up to now but never past it.
            .onPreviewKeyEvent { e ->
                if (!focused || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val repeat = ((e.nativeKeyEvent as? AndroidKeyEvent)?.repeatCount ?: 0) > 0
                when (e.key) {
                    Key.DirectionLeft -> { onScrubStep(-1, repeat); true }
                    Key.DirectionRight -> { onScrubStep(+1, repeat); true }
                    else -> false
                }
            }
            .focusProperties { canFocus = focusable }
            .clickable(interactionSource = interaction, indication = null, onClick = onScrubCommit),
        contentAlignment = Alignment.CenterStart,
    ) {
        val track = if (focused) 5.dp else 3.dp
        Box(Modifier.fillMaxWidth().height(track).clip(CircleShape).background(cs.onBackground.copy(alpha = 0.3f)))
        if (bandEnd > bandStart) {
            Row(Modifier.fillMaxWidth()) {
                if (bandStart > 0f) Spacer(Modifier.weight(bandStart))
                Box(Modifier.weight(bandEnd - bandStart).height(track).clip(CircleShape).background(cs.primary))
                if (bandEnd < 1f) Spacer(Modifier.weight(1f - bandEnd))
            }
        }
        if (programme != null) {
            // Thumb at the playback (or pending scrub) position; Row + weight
            // avoids measuring the bar (no BoxWithConstraints, see
            // GroupSidebar's note).
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (fraction > 0f) Spacer(Modifier.weight(fraction))
                Box(
                    Modifier.size(thumb).clip(CircleShape)
                        .background(if (focused) cs.primary else cs.onBackground.copy(alpha = 0.85f)),
                )
                if (fraction < 1f) Spacer(Modifier.weight(1f - fraction))
            }
        }
    }
}

@Composable
private fun TransportRow(
    programme: EPGProgramme?,
    position: Long,
    atLive: Boolean,
    canSeek: Boolean,
    isPaused: Boolean,
    canRecord: Boolean,
    pauseFocus: FocusRequester,
    onTogglePause: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onRestartProgram: () -> Unit,
    onGoLive: () -> Unit,
    onRecord: () -> Unit,
    onDown: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 14.dp)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionDown -> { onDown(); true }
                    // Without a buffer the seek bar cannot take focus, and an
                    // unconsumed Up falls through to the player's channel flip
                    // while the controls are open. Swallow it instead.
                    Key.DirectionUp -> !canSeek
                    else -> false
                }
            },
    ) {
        if (programme != null) {
            val elapsed = (position - programme.startMillis).coerceAtLeast(0L)
            val total = (programme.endMillis - programme.startMillis).coerceAtLeast(0L)
            Text(
                formatClock(elapsed) + " / " + formatClock(total),
                fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.align(Alignment.CenterStart),
            )
        }
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            TransportButton(Icons.Filled.SkipPrevious, "Start of program", enabled = canSeek && programme != null, onClick = onRestartProgram)
            TransportButton(Icons.Filled.FastRewind, "Rewind", enabled = canSeek, onClick = onRewind)
            TransportButton(
                if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                if (isPaused) "Play" else "Pause",
                enabled = true, onClick = onTogglePause, size = 40.dp,
                modifier = Modifier.focusRequester(pauseFocus),
            )
            TransportButton(Icons.Filled.FastForward, "Fast forward", enabled = canSeek && !atLive, onClick = onForward)
            TransportButton(Icons.Filled.SkipNext, "Go live", enabled = !atLive, onClick = onGoLive)
        }
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            LiveBadge(atLive = atLive, onClick = onGoLive)
            if (canRecord) RecordDot(onClick = onRecord)
        }
    }
}

@Composable
private fun TransportButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
) {
    val cs = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (focused) cs.primary else Color.Transparent)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, contentDescription = description,
            tint = when {
                focused -> cs.onPrimary
                enabled -> cs.onBackground.copy(alpha = 0.85f)
                else -> cs.onBackground.copy(alpha = 0.3f)
            },
            modifier = Modifier.size(size * 0.6f),
        )
    }
}

/**
 * LIVE: a button only while playback is behind live (timeshifted or paused);
 * at the live edge it is a plain, unfocusable label.
 */
@Composable
private fun LiveBadge(atLive: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val ink = when {
        focused -> cs.onPrimary
        atLive -> cs.onBackground
        else -> cs.onBackground.copy(alpha = 0.7f)
    }
    Text(
        "LIVE", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = ink,
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(if (focused) cs.primary else Color.Transparent)
            .border(1.dp, if (focused) cs.primary else cs.onBackground.copy(alpha = 0.7f), RoundedCornerShape(3.dp))
            .clickable(enabled = !atLive, interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 2.dp),
    )
}

@Composable
private fun RecordDot(onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(if (focused) cs.onBackground.copy(alpha = 0.2f) else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(15.dp).clip(CircleShape).background(if (focused) cs.error else cs.onBackground.copy(alpha = 0.85f)))
    }
}

@Composable
private fun TilesRow(
    focusable: Boolean,
    recents: List<SidebarRecent>,
    nowMs: Long,
    firstFocus: FocusRequester,
    onUp: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenHistory: () -> Unit,
    onTuneRecent: (String) -> Unit,
    onClearHistory: () -> Unit,
    onOpenOptions: () -> Unit,
) {
    Row(
        modifier = Modifier.onPreviewKeyEvent { e ->
            if (focusable && e.type == KeyEventType.KeyDown && e.key == Key.DirectionUp) { onUp(); true } else false
        },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerTile(focusable, onOpenGuide, Modifier.focusRequester(firstFocus)) {
            TileIconLabel(Icons.AutoMirrored.Filled.ViewList, "TV guide")
        }
        PlayerTile(focusable, onOpenHistory) { TileIconLabel(Icons.Filled.History, "History") }
        recents.forEach { recent ->
            PlayerTile(focusable, { onTuneRecent(recent.channel.id) }) { RecentTileContent(recent, nowMs) }
        }
        if (recents.isNotEmpty()) {
            PlayerTile(focusable, onClearHistory) { TileIconLabel(Icons.Filled.Delete, "Clear") }
        }
        PlayerTile(focusable, onOpenOptions) { TileIconLabel(Icons.Filled.Tune, "Options") }
    }
}

@Composable
private fun PlayerTile(
    focusable: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = modifier
            .width(TILE_WIDTH)
            .height(TILE_HEIGHT)
            .tvFocusScale(focused, focusedScale = 1.1f)
            .clip(shape)
            .background(if (focused) cs.surfaceVariant else cs.surface.copy(alpha = 0.9f))
            .border(2.dp, if (focused) cs.primary else Color.Transparent, shape)
            // Peeking under the transport, the tiles must not take focus.
            .focusProperties { canFocus = focusable }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun TileIconLabel(icon: ImageVector, label: String) {
    val ink = MaterialTheme.colorScheme.onSurface
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(34.dp))
        Text(label, fontSize = 15.sp, color = ink, maxLines = 1)
    }
}

@Composable
private fun RecentTileContent(recent: SidebarRecent, nowMs: Long) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (recent.channel.tvgLogo.isNotBlank()) {
                AsyncImage(
                    model = recent.channel.tvgLogo, contentDescription = recent.channel.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.heightIn(max = 40.dp).widthIn(max = 90.dp),
                )
            } else {
                Text(recent.channel.name, fontSize = 13.sp, color = cs.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(
                recent.now?.title ?: recent.channel.name,
                fontSize = 11.sp, color = cs.textAccent,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        recent.now?.let { p ->
            val total = (p.endMillis - p.startMillis).coerceAtLeast(1L)
            val fraction = ((nowMs - p.startMillis).toFloat() / total).coerceIn(0f, 1f)
            Box(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().height(2.dp)
                    .background(cs.onSurface.copy(alpha = 0.2f)),
            ) {
                Box(Modifier.fillMaxWidth(fraction).height(2.dp).background(cs.onSurface.copy(alpha = 0.8f)))
            }
        }
    }
}

/**
 * HD / 60 FPS / STEREO chips from the player's format readout
 * ("1080p · 59.94 fps") and the audio track's channel count.
 */
internal fun sidebarFormatChips(formatBadge: String?, audioChannels: Int?): List<String> = buildList {
    val height = formatBadge?.let { Regex("""(\d{3,4})p""").find(it)?.groupValues?.get(1)?.toIntOrNull() }
    when {
        height == null -> Unit
        height >= 2160 -> add("4K")
        height >= 1080 -> add("FHD")
        height >= 720 -> add("HD")
        else -> add("SD")
    }
    formatBadge?.let { Regex("""([\d.]+) fps""").find(it)?.groupValues?.get(1)?.toFloatOrNull() }
        ?.takeIf { it > 0f }
        ?.let { add("${kotlin.math.round(it).toInt()} FPS") }
    when (audioChannels) {
        null, 0 -> Unit
        1 -> add("MONO")
        2 -> add("STEREO")
        6 -> add("5.1")
        8 -> add("7.1")
        else -> add("${audioChannels}CH")
    }
}

/** 48:30 under an hour, 1:00:00 from an hour up. */
private fun formatClock(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

private val LOGO_WIDTH = 130.dp
private val TILE_WIDTH = 120.dp
private val TILE_HEIGHT = 90.dp
/** How much of the tiles row shows under the transport before Down. */
private val TILE_PEEK = 26.dp

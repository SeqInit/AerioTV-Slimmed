package com.aeriotv.android.feature.livetv.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.aeriotv.android.core.data.EPGProgramme
import com.aeriotv.android.core.data.M3UChannel
import com.aeriotv.android.core.ui.ClockFormat
import com.aeriotv.android.core.ui.EpgFlagsRow
import com.aeriotv.android.core.ui.LocalShowEpgBadges
import com.aeriotv.android.core.ui.epgFlags
import com.aeriotv.android.core.ui.rememberClockMode
import com.aeriotv.android.core.ui.seasonEpisodeLabel
import com.aeriotv.android.core.ui.subtitleIsRedundant
import com.aeriotv.android.feature.player.MiniPlayerChrome
import com.aeriotv.android.ui.scale.subtext
import com.aeriotv.android.ui.theme.forText
import com.aeriotv.android.ui.theme.textAccent
import java.util.Date

/**
 * ANDROID (no Apple counterpart): the guide header of the Sidebar-style TV
 * interface (Settings > Live TV > Interface Style). The header splits the top
 * of the guide in two: the preview player on the LEFT, the focused program's
 * details on the RIGHT, the grid below. This replaces the Channel Preview
 * banner (GuidePreviewBanner) in that layout; the focus contract is the same
 * (the description is the header's one focus target and OK opens Program
 * Info), so GuideScreen wires both the same way.
 *
 * The preview slot does not host a player itself: the one ExoPlayer lives in
 * PersistentExoWindow at the activity root. The slot publishes its root rect
 * to [MiniPlayerChrome.dockRectPx] and the mini frame docks onto it. While
 * nothing plays the slot shows the program art (same lookup and cache as the
 * banner), else the channel logo.
 */
object SidebarGuideHeader {
    /** Header height. A 16:9 slot this tall minus padding is ~313 dp wide,
     *  about a third of a 960 dp TV canvas, the proportion the layout is built around. */
    val height = 196.dp
}

@Composable
fun SidebarGuideHeader(
    program: EPGProgramme?,
    channel: M3UChannel?,
    nowMs: Long,
    onOpenInfo: () -> Unit,
    descriptionFocus: FocusRequester,
    downTarget: FocusRequester?,
    /** Down handled by the host (lands on the clock). Return true when consumed. */
    onDown: (() -> Boolean)? = null,
    miniActive: Boolean,
    /** Only the ACTIVE tab may dock the mini: the guide stays composed behind
     *  other tabs, and a stale rect would park the mini over their content. */
    publishDock: Boolean,
    modifier: Modifier = Modifier,
) {
    DisposableEffect(publishDock) {
        if (!publishDock) MiniPlayerChrome.dockRectPx.value = Rect.Zero
        onDispose { MiniPlayerChrome.dockRectPx.value = Rect.Zero }
    }
    val (art, artKnown) = rememberPreviewArt(program, channel)
    val colors = MaterialTheme.colorScheme
    val clockMode = rememberClockMode()
    val fmt = remember(clockMode) { ClockFormat.guideShort(clockMode) }
    val showBadges = LocalShowEpgBadges.current
    val slotHeight = SidebarGuideHeader.height - 20.dp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(SidebarGuideHeader.height)
            .background(colors.background)
            .padding(start = 20.dp, end = 28.dp, top = 12.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = slotHeight * 16f / 9f, height = slotHeight)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black)
                .onGloballyPositioned {
                    if (publishDock) MiniPlayerChrome.dockRectPx.value = it.boundsInRoot()
                },
            contentAlignment = Alignment.Center,
        ) {
            // The docked video draws over this slot (the window is zIndexed
            // above the NavHost), so the art only shows while nothing plays.
            if (!miniActive) {
                when {
                    art != null -> AsyncImage(
                        model = art, contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    artKnown && channel != null && channel.tvgLogo.isNotBlank() -> AsyncImage(
                        model = channel.tvgLogo, contentDescription = null, contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(28.dp),
                    )
                }
            }
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (channel != null) {
                val number = channel.channelNumber?.takeIf { it.isNotBlank() }
                Text(
                    if (number != null) "$number  ${channel.name}" else channel.name,
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.textAccent,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (program == null) {
                Text("No program information", fontSize = 13.sp.subtext(), fontWeight = FontWeight.Medium, color = colors.tertiary)
                return@Column
            }
            Text(
                program.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.onBackground,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            val sub = program.subTitle?.takeIf { !subtitleIsRedundant(it, program.title, program.description) }
            if (sub != null) {
                Text(sub, fontSize = 12.sp.subtext(), fontWeight = FontWeight.Medium, fontStyle = FontStyle.Italic, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val range = fmt.format(Date(program.startMillis)) + " - " + fmt.format(Date(program.endMillis))
                Text(range, fontSize = 11.sp.subtext(), fontWeight = FontWeight.Medium, color = colors.onSurfaceVariant, maxLines = 1)
                val live = program.startMillis <= nowMs && nowMs < program.endMillis
                if (live) {
                    val left = ((program.endMillis - nowMs) / 60_000L).toInt().coerceAtLeast(1)
                    Text("·", fontSize = 11.sp.subtext(), color = colors.tertiary)
                    Text(if (left >= 60) "${left / 60} h ${left % 60} min left" else "$left min left", fontSize = 11.sp.subtext(), fontWeight = FontWeight.Medium, color = colors.onSurfaceVariant)
                }
                val se = seasonEpisodeLabel(program.season, program.episode)
                if (se != null) {
                    Text("·", fontSize = 11.sp.subtext(), color = colors.tertiary)
                    Text(se, fontSize = 11.sp.subtext(), fontWeight = FontWeight.Medium, color = colors.onSurfaceVariant)
                }
                if (showBadges) EpgFlagsRow(flags = program.epgFlags(), compact = true)
            }
            if (program.startMillis <= nowMs && nowMs < program.endMillis) {
                val total = (program.endMillis - program.startMillis).coerceAtLeast(1L)
                val progress = ((nowMs - program.startMillis).toFloat() / total).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(0.5f).height(3.dp).clip(RoundedCornerShape(2.dp)),
                    color = colors.primary,
                    trackColor = colors.surfaceVariant,
                    drawStopIndicator = {},
                )
            }
            if (program.description.isNotBlank()) {
                val interaction = remember { MutableInteractionSource() }
                var focused by remember { mutableStateOf(false) }
                Text(
                    program.description, fontSize = 12.sp.subtext(), lineHeight = 16.sp.subtext(),
                    color = colors.onBackground.copy(alpha = 0.85f).forText(),
                    maxLines = 4, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        // Same platter treatment as the banner description:
                        // the inset is cancelled so the text stays flush.
                        .offset(x = (-6).dp)
                        .focusRequester(descriptionFocus)
                        .focusProperties {
                            if (downTarget != null) down = downTarget
                        }
                        .onPreviewKeyEvent { e ->
                            onDown != null && e.type == KeyEventType.KeyDown &&
                                e.key == Key.DirectionDown && onDown()
                        }
                        .onFocusChanged {
                            if (it.isFocused && !focused) com.aeriotv.android.ui.tv.TvFocusTrace.focus("sidebar-header:description")
                            focused = it.isFocused
                        }
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (focused) Color.White.copy(alpha = 0.08f) else Color.Transparent)
                        .clickable(interactionSource = interaction, indication = null, onClick = onOpenInfo)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

package com.aeriotv.android.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.draw.clip
import com.aeriotv.android.ui.tv.tvFocusScale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.composed
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aeriotv.android.core.tv.rememberTvMenuGuard
import com.aeriotv.android.ui.settings.dpadFocusRing

/** Radio Mode card subtitle (Apple wording). */
const val RADIO_SUBTITLE = "Playing in Radio Mode"

/** Radio Mode status while an audio-only pause-to-fill hold builds the
 *  buffer back up. */
const val RADIO_BUFFERING = "Buffering"

/** Radio Mode status while the stream failed: the player overlay's wording. */
const val RADIO_UNAVAILABLE = "Channel Unavailable"

/**
 * Radio Mode dock card on TV (Android#127): a band at the top of the Live TV
 * Guide and List with "Switch to Video Mode" and "Stop". Renders nothing
 * while Radio Mode is off. Focus is pulled to Switch when it appears (the
 * D-pad cannot reliably climb from the grid to the top of the screen),
 * guarded so the OK release that chose Radio Mode cannot act on it.
 */
@Composable
fun RadioTvBannerSlot(
    modifier: Modifier = Modifier,
    onReturnFocus: (() -> Boolean)? = null,
    playFocusRequester: FocusRequester? = null,
    upTarget: FocusRequester? = null,
    downTarget: FocusRequester? = null,
    // Radio start from the guide (Logan 2026-10-15): when set, the banner
    // does NOT pull focus on start; this runs instead so focus stays on the
    // chosen cell. Up still reaches the banner.
    radioKeepFocus: (() -> Unit)? = null,
) {
    val radio by RadioMode.state.collectAsStateWithLifecycle()
    val radioSession = radio ?: return
    val ctx = androidx.compose.ui.platform.LocalContext.current
    RadioTvBanner(
        channelName = radioSession.channel.name,
        onReturn = { RadioMode.requestReturn(ctx) },
        onStop = {
            // tvOS parity (2026-10-15): Stop restores the origin cell
            // captured at Radio Mode start, not the row focused now.
            RadioMode.stop(ctx, "card")
        },
        onReturnFocus = onReturnFocus,
        playFocusRequester = playFocusRequester,
        upTarget = upTarget,
        downTarget = downTarget,
        keepFocus = radioKeepFocus,
        modifier = modifier,
    )
}

/**
 * Radio banner button: a capsule on the [com.aeriotv.android.ui.tv.TvPill]
 * metrics. Focus ring 2 dp, white on a filled capsule and accent otherwise,
 * scale 1.05, exactly as the group pills. [content] receives the focused ink
 * (onSurface) for plain capsules, or null at rest.
 */
@Composable
private fun TvBannerCapsule(
    onClick: () -> Unit,
    fill: androidx.compose.ui.graphics.Color?,
    border: androidx.compose.ui.graphics.Color?,
    modifier: Modifier = Modifier,
    content: @Composable (androidx.compose.ui.graphics.Color?) -> Unit,
) {
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val colors = MaterialTheme.colorScheme
    val shape = androidx.compose.foundation.shape.CircleShape
    val ringWidth = com.aeriotv.android.ui.tv.TvChrome.ringWidth
    Row(
        modifier = modifier
            .tvFocusScale(focused, focusedScale = 1.05f)
            .clip(shape)
            .then(if (fill != null) Modifier.background(fill) else Modifier)
            .then(if (border != null && !focused) Modifier.border(1.dp, border, shape) else Modifier)
            .border(
                width = ringWidth,
                color = when {
                    !focused -> androidx.compose.ui.graphics.Color.Transparent
                    fill != null -> androidx.compose.ui.graphics.Color.White
                    else -> colors.primary
                },
                shape = shape,
            )
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(
                horizontal = com.aeriotv.android.ui.tv.TvChrome.pillHorizontalPadding,
                vertical = com.aeriotv.android.ui.tv.TvChrome.pillVerticalPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        content(if (focused) colors.onSurface else null)
    }
}

/** Radio Mode banner on TV (Android#127): a full-width band
 *  with "Switch to Video Mode" and "Stop". */
@Composable
private fun RadioTvBanner(
    channelName: String,
    onReturn: () -> Unit,
    onStop: () -> Unit,
    onReturnFocus: (() -> Boolean)?,
    playFocusRequester: FocusRequester?,
    upTarget: FocusRequester?,
    downTarget: FocusRequester?,
    keepFocus: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val guard = rememberTvMenuGuard()
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val returnFocus: () -> Unit = {
        val ok = onReturnFocus?.invoke() == true ||
            focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down)
        com.aeriotv.android.ui.tv.TvFocusTrace.guide("refocus after=radio-banner result=$ok")
    }
    val ownFocus = remember { FocusRequester() }
    val returnFocusRequester = playFocusRequester ?: ownFocus
    LaunchedEffect(Unit) {
        // Guard only the OK that just chose Radio Mode; a banner re-shown
        // after Home (Streamer 2026-10-12) swallowed the first Return press.
        if (android.os.SystemClock.uptimeMillis() - RadioMode.startedAtMs < 1_500L) guard.arm()
        kotlinx.coroutines.delay(350L)
        if (keepFocus != null) {
            keepFocus()
            return@LaunchedEffect
        }
        repeat(20) {
            if (runCatching { returnFocusRequester.requestFocus() }.isSuccess) return@LaunchedEffect
            kotlinx.coroutines.delay(50L)
        }
    }
    val tvNav = Modifier.focusProperties {
        if (upTarget != null) up = upTarget
        if (downTarget != null) down = downTarget
    }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val radioHolder = remember {
        dagger.hilt.android.EntryPointAccessors.fromApplication(
            ctx.applicationContext,
            com.aeriotv.android.feature.main.MainScaffoldEntryPoint::class.java,
        ).exoPlayerHolder()
    }
    // Failed stream: the player overlay's wording, never "Playing".
    val radioStreamDown by radioHolder.streamUnavailable
        .collectAsStateWithLifecycle()
    val radioFailedId by radioHolder.failedChannel.collectAsStateWithLifecycle()
    val radioUnavailable = radioStreamDown || radioFailedId != null
    val radioHolding by radioHolder.audioOnlyHolding.collectAsStateWithLifecycle()
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f))
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Radio,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(18.dp),
        )
        androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channelName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = when {
                    radioUnavailable -> RADIO_UNAVAILABLE
                    radioHolding -> RADIO_BUFFERING
                    else -> RADIO_SUBTITLE
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TvBannerCapsule(
            onClick = guard.wrap {
                guard.arm()
                onReturn()
            },
            fill = accent,
            border = null,
            modifier = tvNav.radioTrace("banner:switch").focusRequester(returnFocusRequester),
        ) { _ ->
            val onAccent = MaterialTheme.colorScheme.onPrimary
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = onAccent, modifier = Modifier.size(12.dp))
            Text("Switch to Video Mode", fontSize = com.aeriotv.android.ui.tv.TvChrome.pillFontSize, fontWeight = FontWeight.SemiBold,
                maxLines = 1, softWrap = false, color = onAccent)
        }
        TvBannerCapsule(
            onClick = guard.wrap {
                returnFocus()
                onStop()
            },
            fill = null,
            border = null,
            modifier = tvNav.radioTrace("banner:stop"),
        ) { ink ->
            Text("Stop", fontSize = com.aeriotv.android.ui.tv.TvChrome.pillFontSize, fontWeight = FontWeight.SemiBold,
                maxLines = 1, softWrap = false,
                color = ink ?: MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Names a radio banner button in the [FOCUS] trace. */
private fun Modifier.radioTrace(name: String): Modifier = composed {
    var had by remember { androidx.compose.runtime.mutableStateOf(false) }
    this.then(Modifier.onFocusChanged {
        if (it.isFocused && !had) com.aeriotv.android.ui.tv.TvFocusTrace.focus(name)
        else if (!it.isFocused && had) com.aeriotv.android.ui.tv.TvFocusTrace.blurred(name)
        had = it.isFocused
    })
}

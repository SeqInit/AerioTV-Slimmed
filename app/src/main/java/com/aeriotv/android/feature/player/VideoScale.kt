package com.aeriotv.android.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.AspectRatioFrameLayout
import com.aeriotv.android.feature.settings.SettingsViewModel
import kotlin.math.hypot

/** Letterbox / pillarbox the stream inside the surface (default). */
const val VIDEO_SCALE_FIT = "fit"

/**
 * Scale up preserving aspect until the shorter dimension fills the surface,
 * cropping the overflow. On a 4:3 display this removes pillar bars baked
 * into a 16:9 stream.
 */
const val VIDEO_SCALE_FILL = "fill"

/** Stretch the stream to the surface, ignoring the source aspect ratio. */
const val VIDEO_SCALE_STRETCH = "stretch"

/** Pinch-out ratio that commits to Fill. */
private const val PINCH_OUT_RATIO = 1.15f

/** Pinch-in ratio that commits to Fit. */
private const val PINCH_IN_RATIO = 0.85f

fun videoScaleLabel(mode: String): String = when (mode) {
    VIDEO_SCALE_FILL -> "Fill"
    VIDEO_SCALE_STRETCH -> "Stretch"
    else -> "Fit"
}

/** Options row order: Fit -> Fill -> Stretch -> Fit. */
fun nextVideoScaleMode(current: String): String = when (current) {
    VIDEO_SCALE_FIT -> VIDEO_SCALE_FILL
    VIDEO_SCALE_FILL -> VIDEO_SCALE_STRETCH
    else -> VIDEO_SCALE_FIT
}

/**
 * Map a Cast / companion CMD_SET_ASPECT key onto a video scale mode. The wire
 * protocol predates this setting: its "zoom" is our Fill (crop, aspect kept)
 * and its "fill" is our Stretch.
 */
fun videoScaleFromCastAspectKey(key: String?): String = when (key) {
    "zoom" -> VIDEO_SCALE_FILL
    "fill" -> VIDEO_SCALE_STRETCH
    else -> VIDEO_SCALE_FIT
}

/** Inverse of [videoScaleFromCastAspectKey], for the receiver's state reply. */
fun castAspectKeyFromVideoScale(mode: String): String = when (mode) {
    VIDEO_SCALE_FILL -> "zoom"
    VIDEO_SCALE_STRETCH -> "fill"
    else -> "fit"
}

/**
 * Resolve the Media3 resize mode for a player surface.
 *
 * Fill maps to RESIZE_MODE_ZOOM (aspect preserved, overflow cropped) and
 * Stretch to RESIZE_MODE_FILL (aspect ignored). Picture in Picture always
 * stays Fit: the PiP window is already letterboxed to the source aspect, so
 * cropping or stretching there only hurts.
 */
fun videoScaleResizeMode(
    scaleMode: String,
    inPip: Boolean = false,
): Int = when {
    inPip -> AspectRatioFrameLayout.RESIZE_MODE_FIT
    scaleMode == VIDEO_SCALE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    scaleMode == VIDEO_SCALE_STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
}

/**
 * Shared, transient signal for the centered "Fit" / "Fill" / "Stretch" label.
 *
 * The pinch detector is a [Modifier] (see [videoScalePinch]) so it can live in
 * the SAME modifier chain as the player's tap / drag layer: overlapping Compose
 * siblings do NOT all get pointer events (hit testing stops at the topmost hit
 * sibling), so a full-size pinch Box on top of the tap layer swallowed every
 * tap. Modifiers cannot emit UI, so the label reads this object instead. Only
 * one player is on screen at a time, so a single holder is enough.
 */
private object VideoScalePinchSignal {
    var label by mutableStateOf(videoScaleLabel(VIDEO_SCALE_FIT))
    var serial by mutableIntStateOf(0)
}

/**
 * Pinch out -> Fill, pinch in -> Fit, for touch devices.
 *
 * Add this to the same modifier chain as the existing tap / vertical-drag
 * layer: several `pointerInput` modifiers in one chain all receive events.
 * Single-pointer events are never consumed, so tap-to-toggle-chrome and the
 * channel-flip drag keep working untouched. Once a second pointer is down the
 * gesture is claimed (changes consumed) so the pinch does not also register as
 * a tap.
 */
fun Modifier.videoScalePinch(
    settingsVm: SettingsViewModel,
    enabled: Boolean = true,
    onPinchIn: (() -> Unit)? = null,
): Modifier = if (!enabled) {
    this
} else {
    this.pointerInput(settingsVm, onPinchIn) {
        awaitPointerEventScope {
            while (true) {
                var event = awaitPointerEvent()
                // Single touch: leave it completely alone.
                if (event.changes.size < 2) continue
                event.changes.forEach { it.consume() }
                val start = pinchSpan(event.changes[0].position, event.changes[1].position)
                var ratio = 1f
                while (event.changes.size >= 2 && event.changes.any { it.pressed }) {
                    event = awaitPointerEvent()
                    event.changes.forEach { it.consume() }
                    if (event.changes.size >= 2 && start > 0f) {
                        ratio = pinchSpan(
                            event.changes[0].position,
                            event.changes[1].position,
                        ) / start
                    }
                }
                val next = when {
                    ratio > PINCH_OUT_RATIO -> VIDEO_SCALE_FILL
                    ratio < PINCH_IN_RATIO -> VIDEO_SCALE_FIT
                    else -> null
                }
                if (next != null) {
                    settingsVm.setVideoScaleMode(next)
                    VideoScalePinchSignal.label = videoScaleLabel(next)
                    VideoScalePinchSignal.serial++
                }
                // iPad parity: the player's minimize pinch is a simultaneous
                // gesture with the same 0.85 threshold, so a pinch in sets Fit
                // AND minimizes (HomeView.swift MagnificationGesture).
                if (ratio < PINCH_IN_RATIO) onPinchIn?.invoke()
            }
        }
    }
}

private fun pinchSpan(
    a: androidx.compose.ui.geometry.Offset,
    b: androidx.compose.ui.geometry.Offset,
): Float = hypot((a.x - b.x), (a.y - b.y))

/**
 * "Video Scale: Fit / Fill / Stretch" row for the on-demand options sheet. Takes the
 * view model rather than a callback so the caller adds no lambda of its own
 * (VODPlayerScreen register pressure). Tapping cycles in place; the sheet
 * stays open so the change is visible behind it.
 */
@Composable
fun VideoScaleOptionRow(settingsVm: SettingsViewModel) {
    val scaleMode by settingsVm.videoScaleMode
        .collectAsStateWithLifecycle(initialValue = VIDEO_SCALE_FIT)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { settingsVm.cycleVideoScaleMode(scaleMode) }
            .padding(horizontal = 8.dp, vertical = 12.dp),
    ) {
        Text(
            text = "Video Scale: ${videoScaleLabel(scaleMode)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}


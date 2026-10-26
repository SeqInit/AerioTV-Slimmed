package com.aeriotv.android.core.tv

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Guards a long-press-triggered DropdownMenu against the D-pad CENTER
 * key's RELEASE auto-selecting the menu's first item on Android TV.
 *
 * The bug: `combinedClickable.onLongClick` fires while OK is still held
 * down (~500ms into the hold). The DropdownMenu opens and its first
 * item (e.g. "Program Info") gains focus. When the user finally lifts
 * off the button, that KEY_UP is delivered to the newly-focused menu
 * item and registers as a click -- so the menu "auto-picks" item 0 the
 * instant the user releases the long press. On the Streamer this makes
 * the actions menu effectively unusable (you always land on Program
 * Info).
 *
 * The fix: swallow any menu-item click that lands within [graceMs] of
 * the menu opening. The spurious release always falls inside that
 * window (it happens within a few hundred ms of onLongClick firing).
 * A deliberate navigate-then-select takes longer than the grace window,
 * so real picks pass through untouched.
 *
 *
 * Usage:
 *   val guard = rememberTvMenuGuard()
 *   ...combinedClickable(onLongClick = { menuOpen = true; guard.arm() })
 *   DropdownMenuItem(onClick = guard.wrap { menuOpen = false; doThing() })
 */
class TvMenuGuard(private val graceMs: Long) {
    private var armedAt = 0L

    /** Call when the menu opens from a long-press. */
    fun arm() {
        armedAt = SystemClock.uptimeMillis()
        android.util.Log.d(TAG, "arm at=$armedAt grace=${graceMs}ms")
    }

    /** Wrap a click callback. Swallows the click if it arrives within
     *  graceMs of [arm] (the spurious long-press-release that fires on the
     *  newly-focused menu item OR, on some screens, back on the row itself). */
    fun wrap(action: () -> Unit): () -> Unit = {
        val elapsed = SystemClock.uptimeMillis() - armedAt
        if (elapsed >= graceMs) {
            android.util.Log.d(TAG, "wrap PASS elapsed=${elapsed}ms (>= $graceMs)")
            action()
        } else {
            android.util.Log.d(TAG, "wrap SWALLOW elapsed=${elapsed}ms (< $graceMs) -- spurious long-press release")
        }
    }

    private companion object { const val TAG = "AerioLongPress" }
}

/**
 * 600ms grace by default: comfortably longer than the gap between
 * onLongClick firing and the user's button release (which is near-
 * instant), but shorter than any deliberate "navigate to an item and
 * press OK" interaction, so it never eats a real selection.
 */
@Composable
fun rememberTvMenuGuard(graceMs: Long = 600L): TvMenuGuard {
    return remember { TvMenuGuard(graceMs) }
}

package com.aeriotv.android.feature.livetv.grid

import com.aeriotv.android.ui.scale.subtext
import com.aeriotv.android.ui.theme.textAccent
import com.aeriotv.android.ui.theme.forText
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.aeriotv.android.core.data.EPGProgramme
import com.aeriotv.android.core.data.M3UChannel
import com.aeriotv.android.core.ui.ClockFormat
import com.aeriotv.android.core.ui.EpgFlagsRow
import com.aeriotv.android.core.ui.LocalShowEpgBadges
import com.aeriotv.android.core.ui.ProgramArtSlot
import com.aeriotv.android.core.ui.epgFlags
import com.aeriotv.android.core.ui.rememberClockMode
import com.aeriotv.android.core.ui.seasonEpisodeLabel
import com.aeriotv.android.core.ui.subtitleIsRedundant
import com.aeriotv.android.feature.livetv.ProgramInfoEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Date

/** Session art cache keyed by program id or cleaned title; null = every source missed. */
private val previewArt = mutableStateMapOf<String, String?>()

/** Art the banner already resolved for this program, for sheets opened from the guide (tvOS artCache). */
fun cachedPreviewArt(dispatcharrProgramId: Int?, title: String): String? {
    val key = dispatcharrProgramId?.let { "pid:$it" } ?: ("t:" + cleanPreviewTitle(title))
    return previewArt[key]
}

/** Resolved art for a focused program: [url] null with [known] true = every source missed. */
internal data class PreviewArt(val url: String?, val known: Boolean)

/**
 * The art lookup behind the Channel Preview banner, shared with the Sidebar-style
 * header (ANDROID) so both surfaces resolve and cache art the same way.
 */
@Composable
internal fun rememberPreviewArt(program: EPGProgramme?, channel: M3UChannel?): PreviewArt {
    val context = LocalContext.current
    val artKey = program?.let { p -> p.dispatcharrProgramId?.let { "pid:$it" } ?: ("t:" + cleanPreviewTitle(p.title)) }
    LaunchedEffect(artKey) {
        val p = program ?: return@LaunchedEffect
        val key = artKey ?: return@LaunchedEffect
        if (previewArt.containsKey(key)) return@LaunchedEffect
        val entry = EntryPointAccessors.fromApplication(context.applicationContext, ProgramInfoEntryPoint::class.java)
        var url: String? = null
        val pid = p.dispatcharrProgramId
        val playlist = entry.appPreferences()
        // Program detail icon (Direct Connect) first. Same order and the same
        // URL rules as Apple's GuidePreviewArtCache (Logan 2026-09-17: the
        // two platforms showed different art for the same program).
        var detailSkipped = false
        if (pid != null) {
            val base = ActivePlaylistBase.baseUrl
            val playlistId = ActivePlaylistBase.playlistId
            if (base == null || playlistId == null) {
                // The playlist globals are not set yet on the first focus
                // after launch. Do not cache a miss for that; retry next time.
                detailSkipped = true
            } else {
                // A failed detail lookup (a 404 for a regenerated program id
                // that could not be re-resolved, or any network error) is
                // never cached as "no art"; the next focus retries.
                val result = runCatching {
                    com.aeriotv.android.feature.livetv.fetchProgramDetailResolving(
                        entry, base, playlistId, pid,
                        listOfNotNull(p.channelId, channel?.tvgID),
                        p.startMillis, p.title,
                    )
                }.getOrNull()
                if (result == null || result.failed) detailSkipped = true
                url = result?.detail?.bestPosterString?.let { raw -> resolvePreviewArtUrl(raw, base) }
            }
        }
        // Order on every art surface: server detail icon, then the program's
        // OWN feed icon (XMLTV `<icon src>`), then TMDB by title only when
        // both are empty. Sports events match nothing useful on TMDB (an
        // NFL pregame matched an unrelated movie poster, Onn 2026-10-12).
        if (url == null) url = p.iconUrl?.takeIf { it.isNotBlank() }
        // A failed detail lookup never falls through to TMDB: the right art
        // may arrive on the next try, and a TMDB guess would be cached.
        if (url == null && !detailSkipped && p.title.isNotBlank() && playlist.programPostersTmdbEnabled.first()) {
            val apiKey = playlist.tmdbApiKey.first()
            if (apiKey.isNotBlank()) {
                val isMovie = p.category.lowercase().let { it.contains("movie") || it.contains("film") }
                url = runCatching {
                    withContext(Dispatchers.IO) {
                        // One typed search hit (Apple's lookupArt): landscape
                        // backdrop first, poster only when there is no
                        // backdrop. No second poster-only search, which is
                        // what produced portrait art here while Apple showed
                        // the landscape card.
                        val tmdb = entry.tmdbService()
                        val art = tmdb.lookupArt(cleanPreviewTitle(p.title), isMovie, apiKey)
                        art?.backdrop?.takeIf { it.isNotBlank() }?.let { tmdb.imageUrlFor(it, "w780") }
                            ?: art?.poster?.takeIf { it.isNotBlank() }?.let { tmdb.imageUrlFor(it, "w500") }
                    }
                }.getOrNull()
            }
        }
        // Nothing is cached after a skipped or failed detail step, so the
        // next focus asks the server again.
        if (!detailSkipped) previewArt[key] = url
    }
    return PreviewArt(
        url = artKey?.let { previewArt[it] },
        known = artKey != null && previewArt.containsKey(artKey),
    )
}

/** Trailing "(2024)" style year tags never help an art lookup. */
private fun cleanPreviewTitle(raw: String): String =
    raw.replace(Regex("""\s*\((\d{4})\)\s*$"""), "").trim()

/** The active playlist's Dispatcharr origin, published by GuideScreen for the art fetch. */
object ActivePlaylistBase {
    @Volatile var baseUrl: String? = null
    @Volatile var playlistId: String? = null
}

/** Width/height below which banner art counts as a portrait poster. */
internal const val PORTRAIT_ART_MAX_ASPECT = 0.8f

/**
 * The radius the guide's program art rounds to when Appearance > "Rounded
 * corners in Guide view" is on: the SAME 6 dp the guide rail logos use, so
 * logos and program art in the guide share one silhouette (Logan 2026-09-16).
 */
private val GUIDE_ART_CORNER = 6.dp

/**
 * Mirrors Apple's `VODService.resolveImageURL`: absolute URLs pass through,
 * a flat single-segment image path is a TMDB path and goes to the TMDB
 * image host, anything else is relative to the playlist origin.
 */
internal fun resolvePreviewArtUrl(raw: String, base: String, size: String = "w780"): String? {
    if (raw.isBlank()) return null
    if (raw.startsWith("http://") || raw.startsWith("https://")) return raw
    val leading = raw.removePrefix("/")
    val isImage = leading.endsWith(".jpg") || leading.endsWith(".jpeg") ||
        leading.endsWith(".png") || leading.endsWith(".webp")
    if (isImage && !leading.contains("/")) return "https://image.tmdb.org/t/p/$size/$leading"
    return base.trimEnd('/') + "/" + leading
}

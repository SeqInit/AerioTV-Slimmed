package com.aeriotv.android.feature.ondemand

import com.aeriotv.android.ui.scale.subtext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Phone/tablet-only building blocks shared by MovieDetailScreen and
 * SeriesDetailScreen, each copied from the iPhone VODDetailView. The TV
 * screens keep their own private composables; nothing here is reached on a
 * TV form factor.
 */

/**
 * TMDB provenance note at the end of the info block (iOS tmdbSourceNote,
 * VODDetailView 1362-1414): confirms when artwork or details came from TMDB,
 * and turns an art-less title into a hint to add a key. Renders nothing when
 * no state applies.
 *
 * @param tmdbPosterUsed a TMDB poster is on screen
 * @param tmdbDetailsPresent TMDB filled in blank text fields
 * @param hasProviderArt the server supplied any artwork
 * @param tmdbConfigured opt-in on and a key saved
 * @param lookupDone the poster lookup has run (so a miss is a real miss)
 */
@Composable
internal fun TmdbSourceNote(
    tmdbPosterUsed: Boolean,
    tmdbDetailsPresent: Boolean,
    hasProviderArt: Boolean,
    tmdbConfigured: Boolean,
    lookupDone: Boolean,
) {
    val accent = MaterialTheme.colorScheme.primary
    when {
        tmdbPosterUsed && tmdbDetailsPresent -> TmdbNoteRow(
            icon = Icons.Filled.Verified,
            tint = accent,
            text = "Poster and missing details pulled from TMDB using your API key.",
        )
        tmdbPosterUsed -> TmdbNoteRow(
            icon = Icons.Filled.Verified,
            tint = accent,
            text = "Poster pulled from TMDB using your API key.",
        )
        tmdbDetailsPresent -> TmdbNoteRow(
            icon = Icons.Filled.Verified,
            tint = accent,
            text = "Missing details filled in from TMDB using your API key.",
        )
        !hasProviderArt && !tmdbConfigured -> TmdbNoteRow(
            icon = Icons.Filled.AutoAwesome,
            tint = accent,
            text = "No artwork from your provider. Enter a TMDB API key in Settings > Movies & TV Shows " +
                "to fill it in automatically. Only works when TMDB has a matching title.",
        )
        !hasProviderArt && lookupDone -> TmdbNoteRow(
            icon = Icons.Filled.Search,
            tint = MaterialTheme.colorScheme.tertiary,
            text = "No matching title found on TMDB.",
        )
    }
}

@Composable
private fun TmdbNoteRow(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, text: String) {
    Row(
        modifier = Modifier.padding(top = 4.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.subtext(),
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}


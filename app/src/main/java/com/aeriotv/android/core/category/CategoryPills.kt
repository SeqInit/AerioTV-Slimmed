package com.aeriotv.android.core.category

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

fun String.categoryTokens(): List<String> =
    split(',', '/', ';').map { it.trim() }.filter { it.isNotEmpty() }

val METADATA_TOKENS: Set<String> = setOf(
    "episode", "series", "movie", "film", "feature", "feature film",
    "short", "short film", "special", "premiere", "season premiere",
    "series premiere", "finale", "season finale", "series finale",
    "rerun", "repeat", "live", "pilot", "made-for-tv movie",
    "made for tv movie", "miniseries", "limited series",
)

/**
 * tvOS ProgramInfoView.CategoryPill (halved): 20 pt medium, 16/8 padding,
 * capsule filled with the resolved bucket color at 85% and white text;
 * unresolved (or [forceNeutral], the metadata tokens) = tertiary fill at
 * 25% with secondary text.
 */
@Composable
fun TvCategoryPill(token: String, palette: CategoryPaletteState?, forceNeutral: Boolean = false) {
    val tint = if (forceNeutral) null else palette?.resolveBaseColor(token)
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .background(color = tint?.copy(alpha = 0.85f) ?: colors.tertiary.copy(alpha = 0.25f), shape = RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = token,
            fontSize = 10.sp,
            color = if (tint != null) androidx.compose.ui.graphics.Color.White else colors.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
    }
}

fun List<String>.partitionCategoryTokens(): Pair<List<String>, List<String>> =
    partition { it.lowercase(Locale.getDefault()) in METADATA_TOKENS }

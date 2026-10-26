package com.aeriotv.android.feature.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.aeriotv.android.ui.tv.tvFocusScale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * GH #57 (Logan 2026-08-10): the TV Guide's round Manage Groups control.
 *
 * The TV guide cleanup stripped the leading control circles off the guide's
 * pill row, and the comment left behind said Manage Groups stayed reachable
 * from the List view's Tune circle. That is a view switch to reach a setting
 * about the view you are already in, and for anyone whose default Live TV
 * view is the Guide the feature simply looks absent. Logan's placement: a
 * round button AFTER the last group when pills are showing, and beside the
 * "Groups" header in sidebar mode.
 *
 * Styled off the guide's own TV pill (30dp tall, soft surfaceVariant fill, a
 * 2dp white focus ring - the same ring a programme cell draws) rather than
 * the phone circle, so it reads as part of the pill row instead of a
 * Material chip dropped into it.
 */
@Composable
internal fun TvManageGroupsCircle(
    hiddenGroupsCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // tvOS TVManageGroupsButtonStyle (ManageGroupsSheet.swift): a 30 pt
    // accent glyph with 7 pt padding on the elevated fill, white glyph +
    // accent 2 pt ring + 1.05 scale on focus, 0.85 alpha at rest. Halved.
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .alpha(if (focused) 1f else 0.85f)
            .tvFocusScale(focused, focusedScale = 1.05f)
            .size(22.dp)
            .clip(CircleShape)
            .background(colors.surfaceVariant)
            .border(2.dp, if (focused) colors.primary else Color.Transparent, CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Tune,
            contentDescription = if (hiddenGroupsCount == 0) "Manage groups"
            else "Manage groups ($hiddenGroupsCount hidden)",
            tint = if (focused) Color.White else colors.primary,
            modifier = Modifier.size(14.dp),
        )
        if (hiddenGroupsCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 1.dp, end = 1.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFA502)),
            )
        }
    }
}


package com.aeriotv.android.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewSidebar
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aeriotv.android.core.preferences.TvInterfaceLayout
import com.aeriotv.android.feature.onboarding.components.SourceTypeCard
import com.aeriotv.android.ui.adaptive.rememberViewport
import com.aeriotv.android.ui.scale.subtext

/**
 * ANDROID (TV only, no Apple counterpart): the last step of first-time setup.
 * Picks the TV interface (Settings > Live TV > Interface Style) so a new user
 * meets the layout they want instead of finding the option later. Laid out
 * like Choose Source Type, with the same cards. [onChoose] receives a
 * [TvInterfaceLayout] value; the host saves it and opens the app.
 */
@Composable
fun ChooseInterfaceScreen(onChoose: (String) -> Unit) {
    val vp = rememberViewport()
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // D-pad lands on the first card, as the setup screens before it do.
        repeat(5) {
            androidx.compose.runtime.withFrameNanos { }
            if (runCatching { firstFocus.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = vp.gutter, vertical = 32.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = if (vp.onboardingMaxWidth != androidx.compose.ui.unit.Dp.Unspecified) {
                Modifier.widthIn(max = vp.onboardingMaxWidth).fillMaxWidth()
            } else {
                Modifier.fillMaxWidth()
            },
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Choose Your Interface",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "How AerioTV looks on this TV. You can change it anytime in Settings > Live TV > Interface Style.",
                style = MaterialTheme.typography.bodyMedium.subtext(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            SourceTypeCard(
                icon = Icons.Filled.ViewAgenda,
                title = "Standard",
                subtitle = "A tab bar across the top and a program preview banner above the guide.",
                modifier = Modifier
                    .focusRequester(firstFocus)
                    .tappable { onChoose(TvInterfaceLayout.STANDARD) },
            )
            SourceTypeCard(
                icon = Icons.AutoMirrored.Filled.ViewSidebar,
                title = "Sidebar Style",
                subtitle = "A menu and channel groups on the left, the playing channel docked beside the guide, and Sidebar-style player controls.",
                modifier = Modifier.tappable { onChoose(TvInterfaceLayout.SIDEBAR) },
            )
        }
    }
}

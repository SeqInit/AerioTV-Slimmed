package com.aeriotv.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Subject
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.RoundedCorner
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aeriotv.android.core.category.CategoryPaletteState
import com.aeriotv.android.core.category.ProgramCategory
import com.aeriotv.android.ui.adaptive.LocalTabBarBottomInset
import com.aeriotv.android.core.preferences.GuideProgramInfoButton
import com.aeriotv.android.ui.scale.AlertDialog
import com.aeriotv.android.ui.settings.SettingsActionRow
import com.aeriotv.android.ui.settings.SettingsDialogTextButton
import com.aeriotv.android.ui.settings.SettingsDetailTopBar
import com.aeriotv.android.ui.settings.SettingsSection
import com.aeriotv.android.ui.settings.SettingsSubGroup
import com.aeriotv.android.ui.settings.SettingsSubPageHost
import com.aeriotv.android.ui.settings.SettingsToggleRow
import com.aeriotv.android.ui.settings.settingsCountSummary
import com.aeriotv.android.ui.settings.settingsFormWidth

/**
 * Settings > Live TV. Everything that shapes the guide and the channel list:
 * presentation toggles, the List view choice, the TV guide layout, groups,
 * program badges, the Live TV display scale, and the category colors.
 *
 * Settings phase 1 regroup: the rows here came from App Behaviors (default
 * view, groups, guide layout, badges), Appearance (presentation, artwork,
 * display scale, category colors and palette) and, on TV, the Group Selection
 * row that used to sit under Remote Control. Every persisted key, control type
 * and string is carried over unchanged.
 */
@Composable
fun LiveTvSettingsScreen(
    onBack: () -> Unit,
    onOpenAddMoreCategories: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    // There is no Live TV List view (Logan 2026-09-16), so the list-facing
    // copy is written for the Guide's channel column. List-only keys keep
    // their synced values.

    val showChannelLogos by viewModel.showChannelLogos.collectAsStateWithLifecycle(initialValue = true)
    val channelLogoSize by viewModel.channelLogoSize.collectAsStateWithLifecycle(initialValue = 100)
    val showChannelNumbers by viewModel.showChannelNumbers.collectAsStateWithLifecycle(initialValue = true)
    val showChannelNames by viewModel.showChannelNames.collectAsStateWithLifecycle(initialValue = true)
    val showProgramSubtitles by viewModel.showProgramSubtitles.collectAsStateWithLifecycle(initialValue = true)
    val roundedArtworkGuide by viewModel.roundedArtworkGuide.collectAsStateWithLifecycle(initialValue = false)
    val roundedProgramCells by viewModel.roundedProgramCells.collectAsStateWithLifecycle(initialValue = false)
    val defaultLiveTVView by viewModel.defaultLiveTVView.collectAsStateWithLifecycle(initialValue = "")
    val showEpgBadges by viewModel.showEpgBadges.collectAsStateWithLifecycle(initialValue = true)
    val hiddenEpgBadges by viewModel.hiddenEpgBadges.collectAsStateWithLifecycle(initialValue = emptySet())
    val scaleLiveTV by viewModel.displayScaleLiveTV.collectAsStateWithLifecycle(initialValue = 1.0f)
    val programInfoButton by viewModel.guideProgramInfoButton
        .collectAsStateWithLifecycle(initialValue = GuideProgramInfoButton.OFF)
    var confirmClearRecents by remember { mutableStateOf(false) }
    val palette by viewModel.categoryPalette.collectAsStateWithLifecycle(initialValue = CategoryPaletteState.Default)

    var pickerTarget by remember { mutableStateOf<ProgramCategory?>(null) }

    SettingsSubPageHost {
    Column(modifier = Modifier.fillMaxSize()) {
        SettingsDetailTopBar(title = "Live TV", onBack = onBack)

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.settingsFormWidth(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = LocalTabBarBottomInset.current,
                ),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // MARK: Guide Presentation
                //
                // Copy is Apple's word for word. `listViewShown` stands in
                // for Apple's split: iOS (and tvOS with TVListView on) uses
                // the "Live TV list" strings, tvOS with the list removed uses
                // the Guide-column strings. Leading glyphs and the section
                // footer exist only in Apple's tvOS body; the iOS List rows
                // carry neither, so both are TV only here.
                item("guide-presentation") {
                    SettingsSection(
                        header = "Guide Presentation",
                        footer = "Turn logos or numbers off to give long channel names more room in the Guide's channel column.",
                    ) {
                        SettingsToggleRow(
                            title = "Show Channel Logos",
                            subtitle = "Display each channel's logo in the Guide's channel column.",
                            leadingIcon = Icons.Filled.Tv,
                            checked = showChannelLogos,
                            onCheckedChange = viewModel::setShowChannelLogos,
                        )
                        // Same row, ladder and labels as Settings > Multiview >
                        // Logo Size; shown only while Show Channel Logos is on.
                        if (showChannelLogos) {
                            SteppedSliderRow(
                                label = "Logo Size",
                                values = com.aeriotv.android.core.ui.LIVE_TV_LOGO_SIZES,
                                selected = channelLogoSize,
                                format = { com.aeriotv.android.core.ui.liveTvLogoSizeLabel(it) },
                                onSelect = viewModel::setChannelLogoSize,
                                footer = "Size of the channel logos in the guide, the channel list and the player's channel list.",
                            )
                        }
                        SettingsToggleRow(
                            title = "Show Channel Numbers",
                            subtitle = "Display each channel's number in the Guide's channel column.",
                            leadingIcon = Icons.Filled.Numbers,
                            checked = showChannelNumbers,
                            onCheckedChange = viewModel::setShowChannelNumbers,
                        )
                        SettingsToggleRow(
                            title = "Show Channel Names",
                            subtitle = "Turn off to hide channel names in the Guide's channel column.",
                            leadingIcon = Icons.Filled.TextFields,
                            checked = showChannelNames,
                            onCheckedChange = viewModel::setShowChannelNames,
                        )
                        SettingsToggleRow(
                            title = "Show Program Subtitles",
                            subtitle = "Turn off to hide the episode or match name under each program title in the Guide, for EPGs that repeat the description there.",
                            leadingIcon = Icons.AutoMirrored.Filled.Subject,
                            checked = showProgramSubtitles,
                            onCheckedChange = viewModel::setShowProgramSubtitles,
                        )
                        SettingsToggleRow(
                            title = "Rounded Guide Logos",
                            subtitle = "Round the channel logos in the guide.",
                            leadingIcon = Icons.Filled.GridOn,
                            checked = roundedArtworkGuide,
                            onCheckedChange = viewModel::setRoundedArtworkGuide,
                        )
                        SettingsToggleRow(
                            title = "Rounded Program Cells",
                            subtitle = "Round the program cells in the guide to match the logos.",
                            leadingIcon = Icons.Filled.RoundedCorner,
                            checked = roundedProgramCells,
                            onCheckedChange = viewModel::setRoundedProgramCells,
                        )
                        // GH #127 (Apple d051505): phone and tablet only; TV
                        // reaches Program Info from the long-press menu.
                    }
                }

                // MARK: Groups
                //
                // The Default Group picker moved to Live TV's Manage Groups
                // sheet (Logan 2026-09-17, Apple parity): the group list, the
                // hide/show checks and the default all live in one place now.
                // Group Selection below is all this section carries; the TV
                // guide always opens its groups as the sidebar panel, so
                // there is nothing to choose there.
                item("group-selection") {
                }

                // MARK: Badges
                item("badges") {
                    SettingsSection(
                        header = "Badges",
                        footer = "Show the LIVE, NEW, and season/episode pills on the guide, channel list, and program info. Remembered separately for Android TV and phone or tablet, and synced across your TVs.",
                    ) {
                        SettingsToggleRow(
                            title = "Show Program Badges",
                            subtitle = "LIVE, NEW, and season/episode pills on the guide",
                            // tvOS "tag" glyph; the iOS toggle has none.
                            leadingIcon = Icons.Filled.Sell,
                            checked = showEpgBadges,
                            onCheckedChange = { viewModel.setShowEpgBadges(it) },
                        )
                        if (showEpgBadges) {
                            // Phase 3, item 4: the five per-badge toggles
                            // collapse under one master row whose subtitle says
                            // how many are on. TV keeps them inline.
                            val badges = listOf("NEW", "REPEAT", "LIVE", "PREMIERE", "FINALE")
                            val shown = badges.count { it !in hiddenEpgBadges }
                            SettingsSubGroup(
                                title = "Badge Types",
                                summary = settingsCountSummary(shown, badges.size),
                                leadingIcon = Icons.Filled.Sell,
                                footer = "Which badges appear. Turning one off hides it everywhere badges are shown.",
                                tvOptionsSheet = true,
                            ) {
                                badges.forEach { badge ->
                                    SettingsToggleRow(
                                        title = "${badge.first()}${badge.drop(1).lowercase()} Badge",
                                        checked = badge !in hiddenEpgBadges,
                                        onCheckedChange = { on ->
                                            viewModel.setBadgeHidden(badge, hidden = !on)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                // MARK: Display Scale
                settingsCard(
                    header = "Display Scale",
                    // tvOS prints no footer under the Guide scale row.
                    footer = null,
                ) {
                    ScaleSliderRow(
                        label = "Guide",
                        value = scaleLiveTV,
                        onValueChange = viewModel::setDisplayScaleLiveTV,
                    )
                }

                // MARK: Colors
                //
                // tvOS: the master toggle only; the palette is edited on the
                // phone or tablet, as Apple's tvOS page points to iPhone/iPad.
                settingsCard(header = "Colors", footer = null) {
                    SettingsToggleRow(
                        title = "Color Programs by Category",
                        subtitle = "Tint guide cells by program type. Customize the palette on phone or tablet in Settings > Live TV.",
                        leadingIcon = Icons.Filled.Palette,
                        checked = palette.masterEnabled,
                        onCheckedChange = viewModel::setCategoryColorsEnabled,
                    )
                }

                // MARK: Recently Watched (GH #130, Apple 5e34cf6)
                if (com.aeriotv.android.core.profiles.ProfileRoles.active.value != com.aeriotv.android.core.profiles.ProfileRole.Child) item("recently-watched") {
                    SettingsSection(header = "Recently Watched") {
                        SettingsActionRow(
                            label = "Clear Recently Watched",
                            leadingIcon = Icons.Filled.History,
                            destructive = true,
                            onClick = { confirmClearRecents = true },
                        )
                    }
                }
            }
        }
    }

    }

    if (confirmClearRecents) {
        AlertDialog(
            onDismissRequest = { confirmClearRecents = false },
            title = { Text("Clear Recently Watched?") },
            text = { Text("Removes every channel from Recently Watched.") },
            confirmButton = {
                SettingsDialogTextButton(
                    label = "Clear",
                    destructive = true,
                    onClick = {
                        confirmClearRecents = false
                        viewModel.clearRecentChannels()
                    },
                )
            },
            dismissButton = {
                SettingsDialogTextButton(label = "Cancel", onClick = { confirmClearRecents = false })
            },
        )
    }

    pickerTarget?.let { bucket ->
        HexPickerDialog(
            bucket = bucket,
            currentHex = palette.hexFor(bucket),
            onDismiss = { pickerTarget = null },
            onSave = { hex ->
                viewModel.setCategoryBucketHex(bucket, hex)
                pickerTarget = null
            },
            onReset = {
                viewModel.setCategoryBucketHex(bucket, null)
                pickerTarget = null
            },
        )
    }

}


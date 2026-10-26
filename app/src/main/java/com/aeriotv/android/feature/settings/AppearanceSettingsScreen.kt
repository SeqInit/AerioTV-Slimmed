package com.aeriotv.android.feature.settings

import androidx.compose.material.icons.filled.Palette
import com.aeriotv.android.ui.scale.subtext
import com.aeriotv.android.ui.theme.textAccent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import com.aeriotv.android.ui.settings.settingsRowCard
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aeriotv.android.core.category.parseHex
import com.aeriotv.android.ui.settings.SettingsToggleAffordance
import com.aeriotv.android.ui.settings.SettingsDetailTopBar
import com.aeriotv.android.ui.settings.SettingsDialogTextButton
import com.aeriotv.android.ui.settings.dpadFocusRing
import com.aeriotv.android.ui.settings.SettingsPickerOption
import com.aeriotv.android.ui.settings.SettingsPickerRow
import com.aeriotv.android.ui.settings.SettingsSection
import com.aeriotv.android.ui.settings.SettingsSubPageHost
import com.aeriotv.android.ui.settings.SettingsTextField
import com.aeriotv.android.ui.settings.settingsFormWidth
import com.aeriotv.android.ui.settings.settingsRowFocusCard
import com.aeriotv.android.core.preferences.TEXT_SCALE_MAX
import com.aeriotv.android.core.preferences.TEXT_SCALE_MIN
import com.aeriotv.android.ui.theme.AppTheme
import kotlin.math.roundToInt
import com.aeriotv.android.ui.theme.AppearanceMode
import com.aeriotv.android.ui.adaptive.LocalTabBarBottomInset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val currentTheme by viewModel.selectedTheme.collectAsStateWithLifecycle(initialValue = AppTheme.Aerio)
    val appearanceMode by viewModel.appearanceMode.collectAsStateWithLifecycle(initialValue = AppearanceMode.Dark)
    val textScale by viewModel.textScale.collectAsStateWithLifecycle(initialValue = 1.0f)
    val subtextScale by viewModel.subtextScale.collectAsStateWithLifecycle(initialValue = 1.0f)
    val textContrast by viewModel.textContrast.collectAsStateWithLifecycle(initialValue = 0f)
    val useCustomAccent by viewModel.useCustomAccent.collectAsStateWithLifecycle(initialValue = false)
    val customAccentHex by viewModel.customAccentHex.collectAsStateWithLifecycle(initialValue = "")
    val timeFormat by viewModel.timeFormat.collectAsStateWithLifecycle(initialValue = "system")
    val trueBlack by viewModel.trueBlack.collectAsStateWithLifecycle()

    var accentPickerOpen by remember { mutableStateOf(false) }

    SettingsSubPageHost {
    Column(modifier = Modifier.fillMaxSize()) {
        SettingsDetailTopBar(title = "Appearance", onBack = onBack)

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier.settingsFormWidth(),
                // Bottom padding covers the bottom-nav bar (~80 dp) plus
                // 24 dp breathing room, so the last LazyColumn item ("Reset
                // Colors to Defaults" under the Palette card) isn't clipped
                // behind MainScaffold's NavigationBar. Without this, the
                // user can't scroll past the nav bar to reach the Reset row
                // or the Add More Categories navigator, because the LazyColumn
                // hits its content edge first.
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = LocalTabBarBottomInset.current,
                ),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // COLOR THEME card - the brand presets. Apple splits the old
                // combined "Theme" card into Color Theme / Light and Dark Mode
                // / Accent; Android matches that structure here.
                // tvOS: Color Theme is ONE choice row with the Custom Accent
                // toggle under it in the same section, and no footers.
                item("tv-color-theme") {
                    SettingsSection(header = "Color Theme") {
                        SettingsPickerRow(
                            title = "Color Theme",
                            options = AppTheme.entries.map { SettingsPickerOption(it, it.displayName) },
                            selected = currentTheme,
                            onSelect = viewModel::setSelectedTheme,
                            leadingIcon = Icons.Filled.Brush,
                            tvChoiceSheet = true,
                        )
                        CustomAccentRow(
                            enabled = useCustomAccent,
                            hex = customAccentHex,
                            onToggle = viewModel::setUseCustomAccent,
                            onPick = { accentPickerOpen = true },
                        )
                    }
                }

                // LIGHT AND DARK MODE card. Orthogonal to the theme above:
                // this picks surface luminance, the theme picks hue.
                // Selecting the Light THEME does not flip this control.
                item("light-dark-mode") {
                    SettingsSection(
                        header = "Light and Dark Mode",
                        footer = null,
                    ) {
                        SettingsPickerRow(
                            title = "Mode",
                            options = APPEARANCE_MODE_ORDER.map { mode ->
                                SettingsPickerOption(
                                    value = mode,
                                    label = appearanceModeLabel(mode),
                                    subtitle = appearanceModeSubtitle(mode),
                                )
                            },
                            selected = appearanceMode,
                            onSelect = viewModel::setAppearanceMode,
                            leadingIcon = Icons.Filled.Contrast,
                            tvChoiceSheet = true,
                        )
                        // GH #105: OLED true black. Applies to the dark
                        // rendition only (Dark, or System resolving to dark).
                        com.aeriotv.android.ui.settings.SettingsToggleRow(
                            title = "True Black Background",
                            subtitle = "Use pure black for backgrounds, for OLED screens.",
                            checked = trueBlack,
                            onCheckedChange = viewModel::setTrueBlack,
                        )
                    }
                }

                // ACCENT card - the custom accent override for the theme's
                // preset accent color.

                // PREVIEW card - shows how the active theme reads on a card,
                // including the accent-tinted title and Now-Playing pill.
                item {
                    PreviewCard(
                        theme = currentTheme,
                        customAccentHex = customAccentHex.takeIf { useCustomAccent },
                        trueBlack = trueBlack,
                    )
                }

                // TEXT SIZE card: one app-wide multiplier on every sp (applied
                // at the composition root). The Display Scale sliders below
                // still multiply on top for their own surfaces.
                item("tv-Text Size") {
                    SettingsSection(header = "Text Size") {
                        TvPercentStepperRow(
                            title = "Text Size",
                            stops = TEXT_SCALE_STOPS,
                            value = textScale,
                            default = 1f,
                            onValueChange = viewModel::setTextScale,
                        )
                    }
                }

                // SUBTEXT SIZE card: extra multiplier for secondary copy only
                // (descriptions, subtitles, metadata), stacking on Text Size.
                item("tv-Subtext Size") {
                    SettingsSection(header = "Subtext Size") {
                        TvPercentStepperRow(
                            title = "Subtext Size",
                            stops = TEXT_SCALE_STOPS,
                            value = subtextScale,
                            default = 1f,
                            onValueChange = viewModel::setSubtextScale,
                        )
                    }
                }

                // TEXT CONTRAST card: blends dimmed and accent-tinted text
                // toward plain white (dark) / black (light).
                item("tv-Text Contrast") {
                    SettingsSection(header = "Text Contrast") {
                        TvPercentStepperRow(
                            title = "Text Contrast",
                            stops = TEXT_CONTRAST_STOPS,
                            value = textContrast,
                            default = 0f,
                            onValueChange = viewModel::setTextContrast,
                        )
                    }
                }

                // TIME FORMAT card: every clock in the app (guide header,
                // cell ranges, program info, DVR) follows this.
                item("time-format") {
                    SettingsSection(
                        header = "Time Format",
                        footer = null,
                    ) {
                        SettingsPickerRow(
                            title = "Time Format",
                            options = TIME_FORMAT_OPTIONS.map {
                                SettingsPickerOption(it.first, it.second)
                            },
                            selected = TIME_FORMAT_OPTIONS.firstOrNull { it.first == timeFormat }
                                ?.first ?: "system",
                            onSelect = viewModel::setTimeFormat,
                            leadingIcon = Icons.Filled.Schedule,
                            tvChoiceSheet = true,
                        )
                    }
                }

            }
        }
    }
    }

    if (accentPickerOpen) {
        AccentPickerDialog(
            current = customAccentHex,
            preset = currentTheme.accentPrimary,
            onDismiss = { accentPickerOpen = false },
            onSave = { hex ->
                viewModel.setCustomAccentHex(hex)
                viewModel.setUseCustomAccent(true)
                accentPickerOpen = false
            },
            onReset = {
                viewModel.setCustomAccentHex("")
                viewModel.setUseCustomAccent(false)
                accentPickerOpen = false
            },
        )
    }
}

/**
 * LazyListScope helper that lays out a header/card/footer triplet in three
 * items so the rounded card and the header keep their iOS-style 6dp gap and
 * the footer renders below the card edge. Matches the AppBehaviors /
 * Multiview / DVR SettingsCard composable visually, just unrolled for
 * LazyColumn.
 */
internal fun LazyListScope.settingsCard(
    header: String,
    footer: String?,
    content: @Composable () -> Unit,
) {
    item {
        Column {
            com.aeriotv.android.ui.settings.SettingsSectionHeader(header)
            com.aeriotv.android.ui.settings.SettingsCard { content() }
            if (footer != null) com.aeriotv.android.ui.settings.SettingsSectionFooter(footer)
        }
    }
}

private val TIME_FORMAT_OPTIONS = listOf(
    "system" to "System",
    "12" to "12-hour",
    "24" to "24-hour",
)

/** Picker order for the Light and Dark Mode card, matching Apple. */
private val APPEARANCE_MODE_ORDER = listOf(
    AppearanceMode.System,
    AppearanceMode.Light,
    AppearanceMode.Dark,
)

private fun appearanceModeLabel(mode: AppearanceMode): String = when (mode) {
    AppearanceMode.Dark -> "Dark"
    AppearanceMode.Light -> "Light"
    AppearanceMode.System -> "System"
}

private fun appearanceModeSubtitle(mode: AppearanceMode): String = when (mode) {
    AppearanceMode.Dark -> "Dark surfaces (default)"
    AppearanceMode.Light -> "Light surfaces"
    AppearanceMode.System -> "Follow the system setting"
}

/**
 * Custom accent color row. Mirrors iOS Appearance > "Custom Accent Color"
 * toggle + color picker (ThemeManager useCustomAccent / customAccentHex).
 * Toggle enables the override; tapping the swatch opens the hex picker.
 */
@Composable
private fun CustomAccentRow(
    enabled: Boolean,
    hex: String,
    onToggle: (Boolean) -> Unit,
    onPick: () -> Unit,
) {
    val swatch = if (enabled && hex.length == 6) parseHex(hex)
    else MaterialTheme.colorScheme.primary
    // Whole row is the toggle target (visible focus stop on TV); the swatch
    // stays a second focusable that opens the picker.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .settingsRowFocusCard()
            .clickable {
                val next = !enabled
                onToggle(next)
                if (next && hex.isBlank()) onPick()
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Apple TV leads this row with a palette glyph, like its other rows.
        androidx.compose.material3.Icon(
            imageVector = androidx.compose.material.icons.Icons.Filled.Palette,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        // with a hairline border, standing in for an icon.
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (hex.length == 6) parseHex(hex) else MaterialTheme.colorScheme.primary)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(4.dp),
                ),
        )
        Spacer(Modifier.size(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Custom Accent Color",
                style = com.aeriotv.android.ui.settings.settingsRowTitleStyle(),
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Medium,
            )
            // iOS has no subtitle (the section footer explains the row);
            // tvOS carries this one.
            Text(
                text = "Override the theme accent with a custom hex color",
                style = com.aeriotv.android.ui.settings.settingsFootnoteStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (enabled) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(50))
                    .background(swatch)
                    .border(
                        width = 1.5.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(50),
                    )
                    .dpadFocusRing(RoundedCornerShape(50))
                    .clickable(onClick = onPick),
            )
            Spacer(Modifier.size(8.dp))
        }
        SettingsToggleAffordance(checked = enabled, onCheckedChange = onToggle)
    }
}

/**
 * Live preview tile that renders the active accent over the active card
 * background, so the user sees the actual cyan/orange/etc. they're about
 * to commit to before leaving the screen. The accent is pulled from the
 * customAccentHex when set, otherwise from the theme's preset.
 */
@Composable
private fun PreviewCard(theme: AppTheme, customAccentHex: String?, trueBlack: Boolean = false) {
    val accent = if (customAccentHex != null && customAccentHex.length == 6) parseHex(customAccentHex)
    else theme.accentPrimary
    TvPreviewRow(theme = theme, accent = accent)
}

/**
 * Apple TV's Preview: a bare row (no card) under a standard section header:
 * accent swatch, "<Theme> · Solid" over "Applied across the entire app", and
 * "Sample" over "Secondary text" on the trailing edge. Android has no glass
 * effect, so the surface reads Solid, as Apple's default does.
 */
@Composable
private fun TvPreviewRow(theme: AppTheme, accent: Color) {
    Column {
        com.aeriotv.android.ui.settings.SettingsSectionHeader("Preview")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(accent),
            )
            Spacer(Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${theme.displayName} · Solid",
                    style = com.aeriotv.android.ui.settings.settingsRowTitleStyle(),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "Applied across the entire app",
                    style = com.aeriotv.android.ui.settings.settingsFootnoteStyle(),
                    color = accent,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Sample",
                    style = com.aeriotv.android.ui.settings.settingsRowTitleStyle(),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "Secondary text",
                    style = com.aeriotv.android.ui.settings.settingsFootnoteStyle(),
                    color = accent,
                )
            }
        }
    }
}

/**
 * Movies & TV Shows stops, matching Apple exactly (Logan 2026-09-18): the
 * Movies scale runs 85% .. 150% on every platform, and Live TV now shares that
 * ceiling (see [SCALE_SEGMENTS]).
 */
internal val MOVIES_SCALE_SEGMENTS: List<Pair<Float, String>> = listOf(
    0.85f to "85%", 1.00f to "100%", 1.15f to "115%",
    1.25f to "125%", 1.35f to "135%", 1.50f to "150%",
)

/**
 * Live TV stops. Logan 2026-09-18: 85% .. 150%, the same ceiling Movies uses -
 * the 175% step is gone on every platform and stored values above 1.5 are
 * clamped when [com.aeriotv.android.core.preferences.AppPreferences.displayScaleLiveTV]
 * is read.
 */
private val SCALE_SEGMENTS: List<Pair<Float, String>> = listOf(
    0.85f to "85%", 0.92f to "92%", 1.00f to "100%", 1.15f to "115%",
    1.25f to "125%", 1.50f to "150%",
)

/** Text Contrast stops: 0% .. 100% in 10% steps. */
/**
 * tvOS Text Size / Subtext Size / Text Contrast row: minus, percent, plus on
 * one focusable row (D-pad Left/Right), then a Reset pill to its right that
 * D-pad Right reaches from the top stop. Values are percent of [stops].
 */
@Composable
private fun TvPercentStepperRow(
    title: String,
    stops: List<Float>,
    value: Float,
    default: Float,
    onValueChange: (Float) -> Unit,
) {
    val percents = remember(stops) { stops.map { (it * 100).roundToInt() } }
    val current = (value * 100).roundToInt()
    val atDefault = current == (default * 100).roundToInt()
    // One row card holds the stepper AND the Reset pill (Apple draws Reset
    // inside the row, trailing the plus). The card's ring lights while
    // either part has focus; the stepper draws no chrome of its own.
    var anyFocused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .onFocusChanged { anyFocused = it.hasFocus }
            .settingsRowCard(anyFocused),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            com.aeriotv.android.ui.settings.LocalSuppressRowChrome provides true,
        ) {
            com.aeriotv.android.ui.settings.SettingsIntStepperRow(
                title = title,
                options = percents,
                value = current,
                onValueChange = { onValueChange(it / 100f) },
                format = { "$it%" },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .padding(end = 12.dp)
                .clip(RoundedCornerShape(50))
                .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(50))
                .dpadFocusRing(
                    shape = RoundedCornerShape(50),
                    washTint = MaterialTheme.colorScheme.primary,
                )
                .clickable(enabled = !atDefault) { onValueChange(default) }
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                text = "Reset",
                style = com.aeriotv.android.ui.settings.settingsRowValueStyle(),
                color = MaterialTheme.colorScheme.textAccent.copy(alpha = if (atDefault) 0.4f else 1f),
            )
        }
    }
}

private val TEXT_CONTRAST_STOPS: List<Float> = (0..10).map { it / 10f }

/** Text Size / Subtext Size stops: 85% .. 150% in 5% steps. */
private val TEXT_SCALE_STOPS: List<Float> =
    (0..((TEXT_SCALE_MAX - TEXT_SCALE_MIN) * 20f).roundToInt()).map { TEXT_SCALE_MIN + it * 0.05f }

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ScaleSliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    segments: List<Pair<Float, String>> = SCALE_SEGMENTS,
) {
    // tvOS renders Display Scale as inline percentage segments, not a slider
    // (cleaner with a remote + no focus-trap). The selected segment is filled.
    BoxWithConstraints(modifier = modifier) {
        // TV always keeps the single-line row (10-foot layout is wide and the
        // D-pad expects one horizontal axis). Everywhere else it depends on
        // whether the label and all seven segments actually fit.
        val segments: @Composable (Modifier) -> Unit = { segModifier ->
            FlowRow(
                modifier = segModifier,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                segments.forEach { (segValue, segLabel) ->
                    val selected = kotlin.math.abs(value - segValue) < 0.03f
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                else Color.Transparent,
                            )
                            .dpadFocusRing(
                                shape = RoundedCornerShape(8.dp),
                                washTint = MaterialTheme.colorScheme.primary,
                            )
                            .clickable { onValueChange(segValue) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = segLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selected) MaterialTheme.colorScheme.textAccent
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
        val labelText: @Composable (Modifier) -> Unit = { labelModifier ->
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Medium,
                modifier = labelModifier,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            labelText(Modifier.weight(1f))
            segments(Modifier)
        }
    }
}

@Composable
private fun AccentPickerDialog(
    current: String,
    preset: Color,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onReset: () -> Unit,
) {
    var input by remember { mutableStateOf(current.uppercase()) }
    val sanitized = input.trim().removePrefix("#").uppercase()
    val isValid = sanitized.length == 6 && sanitized.all { it in HEX_CHARS_ACCENT }
    val preview = if (isValid) parseHex(sanitized) else preset

    com.aeriotv.android.ui.scale.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            SettingsDialogTextButton(
                label = "Save",
                onClick = { if (isValid) onSave(sanitized) },
                enabled = isValid,
            )
        },
        dismissButton = {
            Row {
                SettingsDialogTextButton(label = "Reset", onClick = onReset)
                SettingsDialogTextButton(label = "Cancel", onClick = onDismiss)
            }
        },
        title = { Text("Custom Accent") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(preview)
                            .border(
                                1.5.dp,
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                RoundedCornerShape(8.dp),
                            ),
                    )
                    Spacer(Modifier.size(12.dp))
                    Text(
                        text = if (isValid) "Preview $sanitized" else "Enter 6-char hex",
                        style = MaterialTheme.typography.bodySmall.subtext(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                SettingsTextField(
                    label = "Hex Color",
                    value = input,
                    onValueChange = { raw ->
                        input = raw.removePrefix("#").uppercase().filter { it in HEX_CHARS_ACCENT }.take(6)
                    },
                    placeholder = "1AC4D8",
                    keyboardOptions = com.aeriotv.android.ui.textfield.aerioTextFieldKeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Ascii,
                        capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Characters,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                    ),
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onBackground,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private val HEX_CHARS_ACCENT: Set<Char> = (('0'..'9') + ('A'..'F')).toSet()

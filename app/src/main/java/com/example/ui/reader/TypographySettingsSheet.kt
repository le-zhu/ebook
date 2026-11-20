package com.example.ui.reader

import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.ViewDay
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.widthIn
import com.example.ui.EbookTextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.EbookBottomSheet
import com.example.ui.EbookBadge
import com.example.ui.layoutChoiceHeight
import com.example.ui.actionLabelStyle
import com.example.ui.actionVerticalPadding
import com.example.model.PageTurnMode
import com.example.model.ReaderPreferences
import com.example.model.ReaderTheme
import com.example.ui.theme.ControlHeight
import com.example.ui.theme.ReaderBodySerifFont
import com.example.ui.theme.resolveReaderPalette
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.model.LayoutMode
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Material 3 ModalBottomSheet for real-time typography, pagination mode, and 5-theme switching.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TypographySettingsSheet(
    preferences: ReaderPreferences,
    mode: LayoutMode,
    onModeChange: (LayoutMode) -> Unit,
    onDismiss: () -> Unit,
    onThemeSelected: (ReaderTheme) -> Unit,
    onPageTurnModeSelected: (PageTurnMode) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    onHorizontalMarginChange: (Float) -> Unit,
    onResetClick: () -> Unit,
    systemColors: ColorScheme = MaterialTheme.colorScheme,
) {
    val isSystemDark = isSystemInDarkTheme()

    EbookBottomSheet(onDismiss) {
        Column(
            modifier = Modifier
                .testTag("settings_sheet")
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 24.dp),
        ) {
            // Sheet Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (mode != LayoutMode.ORIGINAL) EbookTextButton(
                    label = if (mode == LayoutMode.CUSTOM) "从全局重置" else "重置全局",
                    onClick = onResetClick,
                    modifier = Modifier.testTag("settings_reset_button"),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val modes = listOf(LayoutMode.ORIGINAL to "原书排版", LayoutMode.GLOBAL to "默认排版", LayoutMode.CUSTOM to "本书自定义")
                modes.forEach { (choice, label) ->
                    SettingChoice(label, choice == mode, { onModeChange(choice) },
                        Modifier.weight(1f).testTag("layout_" + choice.name))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                when (mode) {
                    LayoutMode.ORIGINAL -> "采用书内字体与段落样式，翻页方式跟随全局设置。"
                    LayoutMode.GLOBAL -> "这里的修改会应用到所有使用默认排版的书。"
                    LayoutMode.CUSTOM -> "这里的修改只对这本书生效。"
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (mode == LayoutMode.ORIGINAL) return@Column
            Spacer(Modifier.height(16.dp))
            // 5 Reading Themes Circular Swatches
            Text(
                text = stringResource(R.string.settings_theme_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))

            val themeItems = listOf(
                ReaderTheme.SYSTEM to R.string.settings_theme_system,
                ReaderTheme.PURE_WHITE to R.string.settings_theme_white,
                ReaderTheme.WARM_PAPER to R.string.settings_theme_warm,
                ReaderTheme.SOFT_GRAY to R.string.settings_theme_gray,
                ReaderTheme.AMOLED_BLACK to R.string.settings_theme_amoled,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                themeItems.forEach { (theme, labelRes) ->
                    val previewPalette = resolveReaderPalette(theme, isSystemDark, systemColors)
                    val selected = preferences.theme == theme

                    ThemeColorSwatch(
                        label = stringResource(labelRes),
                        backgroundColor = previewPalette.background,
                        textColor = previewPalette.contentText,
                        isSystemTheme = theme == ReaderTheme.SYSTEM,
                        selected = selected,
                        onClick = { onThemeSelected(theme) },
                        modifier = Modifier.weight(1f).testTag("theme_swatch_${theme.name.lowercase()}"),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.settings_page_turn_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))

            val modes = listOf(
                PageTurnMode.PAGINATED to R.string.settings_mode_paginated,
                PageTurnMode.SCROLLED to R.string.settings_mode_scrolled,
            )
            Row(
                modifier = Modifier.fillMaxWidth().selectableGroup().testTag("settings_page_turn_segmented"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                modes.forEach { (choice, labelRes) ->
                    SettingChoice(
                        label = stringResource(labelRes), selected = preferences.pageTurnMode == choice,
                        onClick = { onPageTurnModeSelected(choice) },
                        modifier = Modifier.weight(1f).testTag("page_mode_" + choice.name.lowercase()),
                        icon = if (choice == PageTurnMode.PAGINATED) Icons.AutoMirrored.Rounded.MenuBook else Icons.Rounded.ViewDay,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Font Size Slider with +/- steppers
            SliderControlHeader(
                label = stringResource(R.string.settings_font_size_label),
                valueBadge = "${preferences.fontSizeSp.roundToInt()} sp",
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FontSizeStep(
                    Icons.Rounded.Remove, R.string.settings_font_decrease_cd, "font_size_decrease",
                    enabled = preferences.fontSizeSp > ReaderPreferences.MIN_FONT_SIZE_SP,
                ) { onFontSizeChange(preferences.fontSizeSp - 1f) }
                Spacer(modifier = Modifier.width(8.dp))
                Slider(
                    value = preferences.fontSizeSp,
                    onValueChange = { onFontSizeChange(it.roundToInt().toFloat()) },
                    valueRange = ReaderPreferences.MIN_FONT_SIZE_SP..ReaderPreferences.MAX_FONT_SIZE_SP,
                    steps = (ReaderPreferences.MAX_FONT_SIZE_SP - ReaderPreferences.MIN_FONT_SIZE_SP).roundToInt() - 1,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("font_size_slider"),
                )
                Spacer(modifier = Modifier.width(8.dp))
                FontSizeStep(
                    Icons.Rounded.Add, R.string.settings_font_increase_cd, "font_size_increase",
                    enabled = preferences.fontSizeSp < ReaderPreferences.MAX_FONT_SIZE_SP,
                ) { onFontSizeChange(preferences.fontSizeSp + 1f) }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Line Height Slider
            SliderControlHeader(
                label = stringResource(R.string.settings_line_height_label),
                valueBadge = String.format(Locale.US, "%.2f×", preferences.lineHeightMultiplier),
            )
            Slider(
                value = preferences.lineHeightMultiplier,
                onValueChange = { raw ->
                    val stepped = (raw * 20f).roundToInt() / 20f
                    onLineHeightChange(stepped)
                },
                valueRange = ReaderPreferences.MIN_LINE_HEIGHT..ReaderPreferences.MAX_LINE_HEIGHT,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("line_height_slider"),
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Page Horizontal Margin Slider
            SliderControlHeader(
                label = stringResource(R.string.settings_margin_label),
                valueBadge = "${preferences.horizontalMarginDp.roundToInt()} dp",
            )
            Slider(
                value = preferences.horizontalMarginDp,
                onValueChange = { onHorizontalMarginChange(it.roundToInt().toFloat()) },
                valueRange = ReaderPreferences.MIN_MARGIN_DP..ReaderPreferences.MAX_MARGIN_DP,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("margin_slider"),
            )
        }
    }
}

@Composable
private fun FontSizeStep(icon: ImageVector, description: Int, tag: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick, Modifier.size(layoutChoiceHeight()).testTag(tag), enabled = enabled) {
        Icon(icon, contentDescription = stringResource(description), modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SettingChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    icon: ImageVector? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val foreground = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant
    Surface(
        selected = selected, onClick = onClick,
        modifier = modifier.semantics { role = Role.RadioButton },
        shape = MaterialTheme.shapes.medium,
        color = if (selected) scheme.secondaryContainer else scheme.surfaceContainer,
        contentColor = foreground,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = layoutChoiceHeight()).padding(horizontal = 5.dp, vertical = actionVerticalPadding),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(label, style = if (selected) actionLabelStyle else MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ThemeColorSwatch(
    label: String,
    backgroundColor: Color,
    textColor: Color,
    isSystemTheme: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Surface(
                shape = CircleShape,
                color = backgroundColor,
                border = BorderStroke(
                    width = if (selected) 2.5.dp else 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                ),
                shadowElevation = if (selected) 3.dp else 0.dp,
                modifier = Modifier.widthIn(max = ControlHeight).aspectRatio(1f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isSystemTheme) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    } else {
                        Text(
                            text = "Aa",
                            fontFamily = ReaderBodySerifFont,
                            fontWeight = FontWeight.Medium,
                            style = MaterialTheme.typography.titleMedium,
                            color = textColor,
                        )
                    }
                }
            }

            if (selected) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun SliderControlHeader(
    label: String,
    valueBadge: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        EbookBadge(valueBadge)
    }
}

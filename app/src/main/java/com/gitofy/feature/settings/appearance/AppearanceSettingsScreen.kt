package com.gitofy.feature.settings.appearance

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gitofy.core.designsystem.components.GITOFYCard
import com.gitofy.core.designsystem.components.GITOFYTopAppBar
import com.gitofy.core.designsystem.components.SettingRow
import com.gitofy.core.designsystem.components.SettingRowDivider
import com.gitofy.core.designsystem.components.SettingSwitchRow
import com.gitofy.core.designsystem.theme.LocalSpacing
import com.gitofy.core.settings.AnimationLevel
import com.gitofy.core.settings.FontSize
import com.gitofy.core.settings.FontFamilyOption
import com.gitofy.core.settings.UiDensity
import com.gitofy.feature.settings.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val s = uiState.appSettings

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { GITOFYTopAppBar(title = "Appearance", onBack = onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Theme — PRD §6
            item {
                SectionHeader("Theme")
                GITOFYCard(modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg)) {
                    Column {
                        ThemeChipRow(
                            label = "System",
                            selected = s.themeMode == com.gitofy.core.settings.ThemeMode.SYSTEM,
                            onClick = { viewModel.setThemeMode(com.gitofy.feature.settings.ThemeMode.SYSTEM) }
                        )
                        SettingRowDivider()
                        ThemeChipRow(
                            label = "Light",
                            selected = s.themeMode == com.gitofy.core.settings.ThemeMode.LIGHT,
                            onClick = { viewModel.setThemeMode(com.gitofy.feature.settings.ThemeMode.LIGHT) }
                        )
                        SettingRowDivider()
                        ThemeChipRow(
                            label = "Dark",
                            selected = s.themeMode == com.gitofy.core.settings.ThemeMode.DARK,
                            onClick = { viewModel.setThemeMode(com.gitofy.feature.settings.ThemeMode.DARK) }
                        )
                    }
                }
            }

            // Dynamic Color — PRD §6
            item {
                SectionHeader("Dynamic Color")
                GITOFYCard(modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg)) {
                    SettingSwitchRow(
                        title = "Dynamic Color",
                        supportingText = "Use Android wallpaper colors across Gitofy",
                        icon = Icons.Default.Palette,
                        checked = s.dynamicColor,
                        onCheckedChange = viewModel::setDynamicColor
                    )
                }
            }

            // AMOLED — PRD §6
            item {
                SectionHeader("AMOLED / Pure Black")
                GITOFYCard(modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg)) {
                    SettingSwitchRow(
                        title = "Pure Black",
                        supportingText = "Use pure black surfaces in dark mode",
                        icon = Icons.Default.Contrast,
                        checked = s.amoledMode,
                        onCheckedChange = viewModel::setAmoledMode
                    )
                }
            }

            // Accent Color — PRD §6
            item {
                SectionHeader("Accent Color")
                GITOFYCard(modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg)) {
                    Column(modifier = Modifier.padding(LocalSpacing.current.md)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val accentColors = listOf(
                                "#0B72B9" to "Sky",
                                "#5849E8" to "Indigo",
                                "#0E8F6B" to "Emerald",
                                "#B8720E" to "Amber",
                                "#A33D6E" to "Rose",
                                "#3D3D46" to "Graphite"
                            )
                            accentColors.forEach { (hex, name) ->
                                AccentColorDot(
                                    color = parseColor(hex),
                                    selected = s.accentColorHex.equals(hex, ignoreCase = true),
                                    onClick = { viewModel.setAccentColor(hex) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Selected: ${s.accentColorHex}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                SectionHeader("Font Style")
                GITOFYCard(modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg)) {
                    Column {
                        FontChipRow("Default", s.fontFamily == FontFamilyOption.DEFAULT) { viewModel.setFontFamily(FontFamilyOption.DEFAULT) }
                        SettingRowDivider()
                        FontChipRow("Serif", s.fontFamily == FontFamilyOption.SERIF) { viewModel.setFontFamily(FontFamilyOption.SERIF) }
                        SettingRowDivider()
                        FontChipRow("Monospace", s.fontFamily == FontFamilyOption.MONOSPACE) { viewModel.setFontFamily(FontFamilyOption.MONOSPACE) }
                        SettingRowDivider()
                        FontChipRow("System", s.fontFamily == FontFamilyOption.SYSTEM) { viewModel.setFontFamily(FontFamilyOption.SYSTEM) }
                    }
                }
            }

            item {
                SectionHeader("Interaction")
                GITOFYCard(modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg)) {
                    SettingSwitchRow(title = "Haptic Feedback", supportingText = "Use touch feedback for important actions", icon = Icons.Default.Vibration, checked = s.hapticFeedback, onCheckedChange = viewModel::setHapticFeedback)
                }
            }

            // UI Density — PRD §6
            item {
                SectionHeader("UI Density")
                GITOFYCard(modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg)) {
                    Column {
                        DensityChipRow("Compact", s.uiDensity == UiDensity.COMPACT) { viewModel.setUiDensity(UiDensity.COMPACT) }
                        SettingRowDivider()
                        DensityChipRow("Comfortable", s.uiDensity == UiDensity.COMFORTABLE) { viewModel.setUiDensity(UiDensity.COMFORTABLE) }
                        SettingRowDivider()
                        DensityChipRow("Spacious", s.uiDensity == UiDensity.SPACIOUS) { viewModel.setUiDensity(UiDensity.SPACIOUS) }
                    }
                }
            }

            // Animations — PRD §6
            item {
                SectionHeader("Animations")
                GITOFYCard(modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg)) {
                    Column {
                        AnimationChipRow("Full", s.animationLevel == AnimationLevel.FULL) { viewModel.setAnimationLevel(AnimationLevel.FULL) }
                        SettingRowDivider()
                        AnimationChipRow("Reduced", s.animationLevel == AnimationLevel.REDUCED) { viewModel.setAnimationLevel(AnimationLevel.REDUCED) }
                        SettingRowDivider()
                        AnimationChipRow("Off", s.animationLevel == AnimationLevel.OFF) { viewModel.setAnimationLevel(AnimationLevel.OFF) }
                    }
                }
            }

            // Font Size — PRD §6
            item {
                SectionHeader("Font Size")
                GITOFYCard(modifier = Modifier.fillMaxWidth().padding(horizontal = LocalSpacing.current.lg)) {
                    Column {
                        FontChipRow("Small", s.fontSize == FontSize.SMALL) { viewModel.setFontSize(FontSize.SMALL) }
                        SettingRowDivider()
                        FontChipRow("Default", s.fontSize == FontSize.DEFAULT) { viewModel.setFontSize(FontSize.DEFAULT) }
                        SettingRowDivider()
                        FontChipRow("Large", s.fontSize == FontSize.LARGE) { viewModel.setFontSize(FontSize.LARGE) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = LocalSpacing.current.lg, vertical = 4.dp)
    )
}

@Composable
private fun ThemeChipRow(label: String, selected: Boolean, onClick: () -> Unit) {
    SettingRow(
        title = label,
        icon = if (selected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
        onClick = onClick
    )
}

@Composable
private fun DensityChipRow(label: String, selected: Boolean, onClick: () -> Unit) {
    SettingRow(title = label, icon = if (selected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked, onClick = onClick)
}

@Composable
private fun AnimationChipRow(label: String, selected: Boolean, onClick: () -> Unit) {
    SettingRow(title = label, icon = if (selected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked, onClick = onClick)
}

@Composable
private fun FontChipRow(label: String, selected: Boolean, onClick: () -> Unit) {
    SettingRow(title = label, icon = if (selected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked, onClick = onClick)
}

@Composable
private fun AccentColorDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

private fun parseColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        Color(0xFF0B72B9)
    }
}

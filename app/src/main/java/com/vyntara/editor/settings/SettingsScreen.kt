package com.vyntara.editor.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vyntara.editor.capabilities.DeviceProfile
import com.vyntara.editor.i18n.AppLanguage
import com.vyntara.editor.i18n.Strings
import com.vyntara.editor.ui.theme.LocalAppTheme
import com.vyntara.editor.ui.theme.VyntaraColors

@Composable
fun SettingsScreen(
    settings: AppSettings,
    profile: DeviceProfile?,
    storageLabel: String,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onClearCache: () -> Unit,
    onDeleteAllProjects: () -> Unit,
    onResetSettings: () -> Unit,
    onRefreshDiagnostics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lang = AppLanguage.fromCode(settings.languageCode)
    fun t(key: String) = Strings.t(lang, key)
    val colors = LocalAppTheme.current.colors
    val metrics = LocalAppTheme.current.metrics
    var confirmReset by remember { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var showDiag by remember { mutableStateOf(false) }
    var cacheMsg by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = metrics.contentPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(4.dp))
            Text(t("settings_title"), color = colors.text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
        }

        item {
            SectionCard(t("settings_personalization"), colors) {
                Text(t("theme_title"), color = colors.muted, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ThemeDot(AppThemeId.CRIMSON, Color(0xFFE11D2E), settings.themeId, colors) {
                        onUpdate { it.copy(themeId = AppThemeId.CRIMSON) }
                    }
                    ThemeDot(AppThemeId.VIOLET, Color(0xFF7B5CFF), settings.themeId, colors) {
                        onUpdate { it.copy(themeId = AppThemeId.VIOLET) }
                    }
                    ThemeDot(AppThemeId.OCEAN, Color(0xFF0EA5E9), settings.themeId, colors) {
                        onUpdate { it.copy(themeId = AppThemeId.OCEAN) }
                    }
                    ThemeDot(AppThemeId.EMERALD, Color(0xFF10B981), settings.themeId, colors) {
                        onUpdate { it.copy(themeId = AppThemeId.EMERALD) }
                    }
                    ThemeDot(AppThemeId.AMBER, Color(0xFFF59E0B), settings.themeId, colors) {
                        onUpdate { it.copy(themeId = AppThemeId.AMBER) }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    when (settings.themeId) {
                        AppThemeId.CRIMSON -> t("theme_crimson")
                        AppThemeId.VIOLET -> t("theme_violet")
                        AppThemeId.OCEAN -> t("theme_ocean")
                        AppThemeId.EMERALD -> t("theme_emerald")
                        AppThemeId.AMBER -> t("theme_amber")
                    },
                    color = colors.text,
                    fontSize = 14.sp
                )

                Spacer(Modifier.height(16.dp))
                OptionRow(t("ui_density"), colors)
                ChoiceRow(
                    listOf(
                        UiDensity.COMPACT to t("density_compact"),
                        UiDensity.COMFORTABLE to t("density_comfortable"),
                        UiDensity.SPACIOUS to t("density_spacious")
                    ),
                    settings.uiDensity,
                    colors
                ) { onUpdate { s -> s.copy(uiDensity = it) } }

                Spacer(Modifier.height(12.dp))
                OptionRow(t("card_corners"), colors)
                ChoiceRow(
                    listOf(
                        CardCorners.SHARP to t("corners_sharp"),
                        CardCorners.ROUNDED to t("corners_rounded"),
                        CardCorners.PILL to t("corners_pill")
                    ),
                    settings.cardCorners,
                    colors
                ) { onUpdate { s -> s.copy(cardCorners = it) } }

                ToggleRow(t("show_fab_labels"), settings.showNavLabels, colors) {
                    onUpdate { s -> s.copy(showNavLabels = it) }
                }
            }
        }

        item {
            SectionCard(t("settings_language"), colors) {
                ChoiceRow(
                    listOf("es" to t("lang_es"), "en" to t("lang_en")),
                    settings.languageCode,
                    colors
                ) { onUpdate { s -> s.copy(languageCode = it) } }
            }
        }

        item {
            SectionCard(t("settings_performance"), colors) {
                OptionRow(t("preview_quality"), colors)
                ChoiceRow(
                    listOf(
                        PreviewQuality.AUTO to t("quality_auto"),
                        PreviewQuality.LOW to t("quality_low"),
                        PreviewQuality.MEDIUM to t("quality_medium"),
                        PreviewQuality.HIGH to t("quality_high")
                    ),
                    settings.previewQuality,
                    colors
                ) { onUpdate { s -> s.copy(previewQuality = it) } }

                ToggleRow(t("keep_screen_on"), settings.keepScreenOn, colors) {
                    onUpdate { s -> s.copy(keepScreenOn = it) }
                }
                ToggleRow(t("haptics"), settings.haptics, colors) {
                    onUpdate { s -> s.copy(haptics = it) }
                }
                ToggleRow(t("thermal_guard"), settings.thermalGuard, colors) {
                    onUpdate { s -> s.copy(thermalGuard = it) }
                }
            }
        }

        item {
            SectionCard(t("settings_editor_defaults"), colors) {
                OptionRow(t("default_resolution"), colors)
                ChoiceRow(
                    listOf(720, 1080, 1440, 2160).map { it to "${it}p" },
                    settings.defaultResolutionLongSide,
                    colors
                ) { onUpdate { s -> s.copy(defaultResolutionLongSide = it) } }

                OptionRow(t("default_fps"), colors)
                ChoiceRow(
                    listOf(24, 30, 60).map { it to "$it" },
                    settings.defaultFps,
                    colors
                ) { onUpdate { s -> s.copy(defaultFps = it) } }

                ToggleRow(t("snap_enabled"), settings.snapEnabled, colors) {
                    onUpdate { s -> s.copy(snapEnabled = it) }
                }
                ToggleRow(t("grid_enabled"), settings.gridEnabled, colors) {
                    onUpdate { s -> s.copy(gridEnabled = it) }
                }

                OptionRow(t("auto_save"), colors)
                ChoiceRow(
                    listOf(
                        AutoSaveInterval.OFF to t("auto_save_off"),
                        AutoSaveInterval.S30 to t("auto_save_30"),
                        AutoSaveInterval.S60 to t("auto_save_60"),
                        AutoSaveInterval.S120 to t("auto_save_120")
                    ),
                    settings.autoSave,
                    colors
                ) { onUpdate { s -> s.copy(autoSave = it) } }
            }
        }

        item {
            SectionCard(t("settings_boot"), colors) {
                ToggleRow(t("cinematic_boot"), settings.cinematicBoot, colors) {
                    onUpdate { s -> s.copy(cinematicBoot = it) }
                }
                ToggleRow(t("boot_sound"), settings.bootSound, colors) {
                    onUpdate { s -> s.copy(bootSound = it) }
                }
                ToggleRow(t("reduce_motion"), settings.reduceMotion, colors) {
                    onUpdate { s -> s.copy(reduceMotion = it) }
                }
            }
        }

        item {
            SectionCard(t("settings_storage"), colors) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(t("storage_used"), color = colors.text, fontSize = 15.sp)
                    Text(storageLabel, color = colors.muted, fontSize = 15.sp)
                }
                ActionLine(t("clear_cache"), colors) {
                    onClearCache()
                    cacheMsg = t("clear_cache_done")
                }
                cacheMsg?.let { Text(it, color = colors.success, fontSize = 13.sp) }
                ActionLine(t("delete_all_projects"), colors, danger = true) {
                    confirmDeleteAll = true
                }
            }
        }

        item {
            SectionCard(t("settings_diagnostics"), colors) {
                ActionLine(t("diag_codecs"), colors) { showDiag = true }
                ActionLine(t("diag_refresh"), colors, onClick = onRefreshDiagnostics)
            }
        }

        item {
            SectionCard(t("settings_about"), colors) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(t("about_version"), color = colors.text, fontSize = 15.sp)
                    Text("0.1", color = colors.muted, fontSize = 15.sp)
                }
                Spacer(Modifier.height(8.dp))
                ActionLine(t("reset_settings"), colors, danger = true) { confirmReset = true }
            }
        }

        item { Spacer(Modifier.height(48.dp)) }
    }

    if (showDiag) {
        AlertDialog(
            onDismissRequest = { showDiag = false },
            title = { Text(t("settings_diagnostics")) },
            text = {
                Text(
                    buildString {
                        appendLine("SDK: ${profile?.androidSdk ?: "?"}")
                        appendLine("GL: ${profile?.glEsVersion ?: "?"}")
                        appendLine("Vulkan: ${profile?.hasVulkan}")
                        appendLine("Tex: ${profile?.maxTextureSize}")
                        appendLine("Mem: ${profile?.availableMemoryMb} MB")
                        appendLine("Preview: ${profile?.previewResolutionCap}p")
                        appendLine("vDec: ${profile?.supportedVideoDecoders?.size ?: 0}")
                        appendLine("vEnc: ${profile?.supportedVideoEncoders?.size ?: 0}")
                    },
                    color = colors.text,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showDiag = false }) { Text(t("ok")) }
            },
            containerColor = colors.card
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(t("reset_settings")) },
            text = { Text(t("reset_confirm")) },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; onResetSettings() }) {
                    Text(t("ok"), color = colors.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text(t("cancel")) }
            },
            containerColor = colors.card
        )
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text(t("delete_all_projects")) },
            text = { Text(t("reset_confirm")) },
            confirmButton = {
                TextButton(onClick = { confirmDeleteAll = false; onDeleteAllProjects() }) {
                    Text(t("ok"), color = colors.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) { Text(t("cancel")) }
            },
            containerColor = colors.card
        )
    }
}

@Composable
private fun SectionCard(title: String, colors: VyntaraColors, content: @Composable () -> Unit) {
    val radius = LocalAppTheme.current.metrics.cardRadius
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius))
            .background(colors.card)
            .padding(16.dp)
    ) {
        Text(title, color = colors.primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun OptionRow(text: String, colors: VyntaraColors) {
    Text(text, color = colors.muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun <T> ChoiceRow(
    options: List<Pair<T, String>>,
    selected: T,
    colors: VyntaraColors,
    onSelect: (T) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { (value, label) ->
                    val sel = value == selected
                    Text(
                        text = label,
                        color = if (sel) Color.White else colors.text,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (sel) colors.primary else colors.surface)
                            .border(1.dp, if (sel) colors.primary else colors.muted.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .clickable { onSelect(value) }
                            .padding(vertical = 14.dp, horizontal = 12.dp)
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, colors: VyntaraColors, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .height(48.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = colors.text, fontSize = 15.sp, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = colors.primary, checkedThumbColor = Color.White)
        )
    }
}

@Composable
private fun ActionLine(label: String, colors: VyntaraColors, danger: Boolean = false, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (danger) colors.danger else colors.text,
        fontSize = 15.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp)
    )
    HorizontalDivider(color = colors.muted.copy(alpha = 0.15f))
}

@Composable
private fun ThemeDot(
    id: AppThemeId,
    color: Color,
    selected: AppThemeId,
    colors: VyntaraColors,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected == id) 3.dp else 1.dp,
                color = if (selected == id) Color.White else colors.muted.copy(alpha = 0.4f),
                shape = CircleShape
            )
            .clickable(onClick = onClick)
    )
}

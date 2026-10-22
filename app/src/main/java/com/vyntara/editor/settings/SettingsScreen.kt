package com.vyntara.editor.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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

@OptIn(ExperimentalLayoutApi::class)
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(metrics.contentPadding)
    ) {
        Text(t("settings_title"), color = colors.text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(metrics.sectionGap))

        // ——— 1 Personalization ———
        Section(t("settings_personalization"), colors) {
            Text(t("theme_title"), color = colors.muted, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ThemeSwatch(AppThemeId.CRIMSON, Color(0xFFE11D2E), settings.themeId, colors) {
                    onUpdate { it.copy(themeId = AppThemeId.CRIMSON) }
                }
                ThemeSwatch(AppThemeId.VIOLET, Color(0xFF7B5CFF), settings.themeId, colors) {
                    onUpdate { it.copy(themeId = AppThemeId.VIOLET) }
                }
                ThemeSwatch(AppThemeId.OCEAN, Color(0xFF0EA5E9), settings.themeId, colors) {
                    onUpdate { it.copy(themeId = AppThemeId.OCEAN) }
                }
                ThemeSwatch(AppThemeId.EMERALD, Color(0xFF10B981), settings.themeId, colors) {
                    onUpdate { it.copy(themeId = AppThemeId.EMERALD) }
                }
                ThemeSwatch(AppThemeId.AMBER, Color(0xFFF59E0B), settings.themeId, colors) {
                    onUpdate { it.copy(themeId = AppThemeId.AMBER) }
                }
            }
            Text(
                when (settings.themeId) {
                    AppThemeId.CRIMSON -> t("theme_crimson")
                    AppThemeId.VIOLET -> t("theme_violet")
                    AppThemeId.OCEAN -> t("theme_ocean")
                    AppThemeId.EMERALD -> t("theme_emerald")
                    AppThemeId.AMBER -> t("theme_amber")
                },
                color = colors.text, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)
            )

            Spacer(Modifier.height(14.dp))
            Label(t("ui_density"), colors)
            ChipRow(
                options = listOf(
                    UiDensity.COMPACT to t("density_compact"),
                    UiDensity.COMFORTABLE to t("density_comfortable"),
                    UiDensity.SPACIOUS to t("density_spacious")
                ),
                selected = settings.uiDensity,
                colors = colors
            ) { onUpdate { s -> s.copy(uiDensity = it) } }

            Spacer(Modifier.height(10.dp))
            Label(t("card_corners"), colors)
            ChipRow(
                options = listOf(
                    CardCorners.SHARP to t("corners_sharp"),
                    CardCorners.ROUNDED to t("corners_rounded"),
                    CardCorners.PILL to t("corners_pill")
                ),
                selected = settings.cardCorners,
                colors = colors
            ) { onUpdate { s -> s.copy(cardCorners = it) } }

            ToggleRow(t("show_fab_labels"), settings.showNavLabels, colors) {
                onUpdate { s -> s.copy(showNavLabels = it) }
            }

            Label(t("accent_override"), colors)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccentDot(0L, settings.accentOverrideArgb == 0L, colors, t("accent_none")) {
                    onUpdate { it.copy(accentOverrideArgb = 0L) }
                }
                listOf(0xFFE11D2EL, 0xFFFF6B00L, 0xFF7B5CFFL, 0xFF00E5FFL, 0xFFF59E0BL).forEach { argb ->
                    AccentDot(argb, settings.accentOverrideArgb == argb, colors, null) {
                        onUpdate { it.copy(accentOverrideArgb = argb) }
                    }
                }
            }
        }

        // ——— 2 Language ———
        Section(t("settings_language"), colors) {
            ChipRow(
                options = listOf(
                    "es" to t("lang_es"),
                    "en" to t("lang_en")
                ),
                selected = settings.languageCode,
                colors = colors
            ) { onUpdate { s -> s.copy(languageCode = it) } }
        }

        // ——— 3 Performance ———
        Section(t("settings_performance"), colors) {
            Label(t("preview_quality"), colors)
            ChipRow(
                options = listOf(
                    PreviewQuality.AUTO to t("quality_auto"),
                    PreviewQuality.LOW to t("quality_low"),
                    PreviewQuality.MEDIUM to t("quality_medium"),
                    PreviewQuality.HIGH to t("quality_high")
                ),
                selected = settings.previewQuality,
                colors = colors
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

        // ——— 4 Editor defaults ———
        Section(t("settings_editor_defaults"), colors) {
            Label(t("default_resolution"), colors)
            ChipRow(
                options = listOf(720, 1080, 1440, 2160).map { it to "${it}p" },
                selected = settings.defaultResolutionLongSide,
                colors = colors
            ) { onUpdate { s -> s.copy(defaultResolutionLongSide = it) } }

            Label(t("default_fps"), colors)
            ChipRow(
                options = listOf(24, 30, 60).map { it to "$it" },
                selected = settings.defaultFps,
                colors = colors
            ) { onUpdate { s -> s.copy(defaultFps = it) } }

            Label(t("default_aspect"), colors)
            ChipRow(
                options = listOf("16:9", "9:16", "1:1", "4:5").map { it to it },
                selected = settings.defaultAspectLabel,
                colors = colors
            ) { onUpdate { s -> s.copy(defaultAspectLabel = it) } }

            ToggleRow(t("snap_enabled"), settings.snapEnabled, colors) {
                onUpdate { s -> s.copy(snapEnabled = it) }
            }
            ToggleRow(t("grid_enabled"), settings.gridEnabled, colors) {
                onUpdate { s -> s.copy(gridEnabled = it) }
            }

            Label(t("timeline_height"), colors)
            ChipRow(
                options = listOf(0 to "S", 1 to "M", 2 to "L"),
                selected = settings.timelineHeightLevel,
                colors = colors
            ) { onUpdate { s -> s.copy(timelineHeightLevel = it) } }

            Label(t("auto_save"), colors)
            ChipRow(
                options = listOf(
                    AutoSaveInterval.OFF to t("auto_save_off"),
                    AutoSaveInterval.S30 to t("auto_save_30"),
                    AutoSaveInterval.S60 to t("auto_save_60"),
                    AutoSaveInterval.S120 to t("auto_save_120")
                ),
                selected = settings.autoSave,
                colors = colors
            ) { onUpdate { s -> s.copy(autoSave = it) } }
        }

        // ——— 5 Boot ———
        Section(t("settings_boot"), colors) {
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

        // ——— 6 Storage ———
        Section(t("settings_storage"), colors) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(t("storage_used"), color = colors.text, fontSize = 14.sp)
                Text(storageLabel, color = colors.muted, fontSize = 14.sp)
            }
            ActionRow(t("clear_cache"), colors) {
                onClearCache()
                cacheMsg = t("clear_cache_done")
            }
            cacheMsg?.let {
                Text(it, color = colors.success, fontSize = 12.sp)
            }
            ActionRow(t("export_backup"), colors) { /* scaffold for future */ }
            ActionRow(t("import_backup"), colors) { }
            ActionRow(t("delete_all_projects"), colors, danger = true) {
                confirmDeleteAll = true
            }
        }

        // ——— 7 Diagnostics ———
        Section(t("settings_diagnostics"), colors) {
            ActionRow(t("diag_codecs"), colors) { showDiag = true }
            ActionRow(t("diag_gpu"), colors) { showDiag = true }
            ActionRow(t("diag_refresh"), colors, onClick = onRefreshDiagnostics)
            ActionRow(t("diag_share"), colors) { }
        }

        // ——— 8 About ———
        Section(t("settings_about"), colors) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(t("about_version"), color = colors.text, fontSize = 14.sp)
                Text("0.1", color = colors.muted, fontSize = 14.sp)
            }
            ActionRow(t("about_changelog"), colors) { }
            ActionRow(t("about_licenses"), colors) { }
            ActionRow(t("reset_settings"), colors, danger = true) { confirmReset = true }
        }

        Spacer(Modifier.height(48.dp))
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
                        appendLine("Preview cap: ${profile?.previewResolutionCap}p")
                        appendLine("vDec: ${profile?.supportedVideoDecoders?.size ?: 0}")
                        appendLine("vEnc: ${profile?.supportedVideoEncoders?.size ?: 0}")
                        profile?.notes?.forEach { appendLine("• $it") }
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
private fun Section(title: String, colors: com.vyntara.editor.ui.theme.VyntaraColors, content: @Composable () -> Unit) {
    val radius = LocalAppTheme.current.metrics.cardRadius
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
            .clip(RoundedCornerShape(radius))
            .background(colors.card)
            .padding(14.dp)
    ) {
        Text(title, color = colors.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun Label(text: String, colors: com.vyntara.editor.ui.theme.VyntaraColors) {
    Text(text, color = colors.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
}

@Composable
private fun <T> ChipRow(
    options: List<Pair<T, String>>,
    selected: T,
    colors: com.vyntara.editor.ui.theme.VyntaraColors,
    onSelect: (T) -> Unit
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val sel = value == selected
            Text(
                text = label,
                color = if (sel) Color.White else colors.text,
                fontSize = 12.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (sel) colors.primary else colors.surface)
                    .border(1.dp, if (sel) colors.primary else colors.muted.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .clickable { onSelect(value) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    colors: com.vyntara.editor.ui.theme.VyntaraColors,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = colors.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = colors.primary,
                checkedThumbColor = Color.White
            )
        )
    }
}

@Composable
private fun ActionRow(
    label: String,
    colors: com.vyntara.editor.ui.theme.VyntaraColors,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    Text(
        text = label,
        color = if (danger) colors.danger else colors.text,
        fontSize = 14.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
    )
    HorizontalDivider(color = colors.muted.copy(alpha = 0.15f))
}

@Composable
private fun ThemeSwatch(
    id: AppThemeId,
    color: Color,
    selected: AppThemeId,
    colors: com.vyntara.editor.ui.theme.VyntaraColors,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(40.dp)
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

@Composable
private fun AccentDot(
    argb: Long,
    selected: Boolean,
    colors: com.vyntara.editor.ui.theme.VyntaraColors,
    label: String?,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (argb == 0L) colors.surface else Color((argb and 0xFFFFFFFFL).toULong()))
                .border(
                    if (selected) 2.dp else 1.dp,
                    if (selected) colors.primary else colors.muted.copy(alpha = 0.4f),
                    CircleShape
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (argb == 0L) Text("∅", color = colors.muted, fontSize = 12.sp)
        }
        if (label != null) {
            Text(label, color = colors.muted, fontSize = 9.sp)
        }
    }
}

package com.vyntara.editor.project

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.vyntara.editor.home.LocalLanguage
import com.vyntara.editor.i18n.Strings
import com.vyntara.editor.settings.AppSettings
import com.vyntara.editor.ui.theme.LocalAppTheme
import com.vyntara.editor.ui.theme.VyntaraColors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateProjectSheet(
    profile: DeviceProfile?,
    settings: AppSettings = AppSettings(),
    onDismiss: () -> Unit,
    onCreate: (ProjectSettings) -> Unit
) {
    val colors = LocalAppTheme.current.colors
    val lang = LocalLanguage.current
    fun t(key: String) = Strings.t(lang, key)

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf("") }
    var aspect by remember { mutableStateOf(AspectPreset.RATIO_16_9) }
    var resolution by remember {
        mutableStateOf(
            ResolutionPreset.entries.find { it.longSide == settings.defaultResolutionLongSide }
                ?: ResolutionPreset.P1080
        )
    }
    var fps by remember { mutableIntStateOf(settings.defaultFps) }
    var background by remember { mutableStateOf(BackgroundPreset.BLACK) }
    var customW by remember { mutableIntStateOf(1920) }
    var customH by remember { mutableIntStateOf(1080) }
    var customColorHex by remember { mutableStateOf("1A1A2E") }
    var error by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(t("create_title"), color = colors.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(t("create_name")) },
                placeholder = { Text(t("create_name_hint")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors()
            )

            SectionLabel(t("create_aspect"), colors)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AspectPreset.entries.forEach { preset ->
                    Chip(
                        label = if (preset == AspectPreset.CUSTOM) t("aspect_custom") else preset.label,
                        selected = aspect == preset,
                        enabled = true,
                        onClick = { aspect = preset }
                    )
                }
            }

            if (aspect == AspectPreset.CUSTOM) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = customW.toString(),
                        onValueChange = { customW = it.filter(Char::isDigit).toIntOrNull() ?: customW },
                        label = { Text(t("create_width")) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = fieldColors()
                    )
                    OutlinedTextField(
                        value = customH.toString(),
                        onValueChange = { customH = it.filter(Char::isDigit).toIntOrNull() ?: customH },
                        label = { Text(t("create_height")) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = fieldColors()
                    )
                }
            }

            SectionLabel(t("create_resolution"), colors)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ResolutionPreset.entries.forEach { preset ->
                    val blocked = CapabilityLimits.resolutionBlockReason(preset.longSide, profile)
                    Chip(
                        label = preset.label,
                        selected = resolution == preset,
                        enabled = blocked == null,
                        onClick = {
                            if (blocked == null) resolution = preset else error = blocked
                        }
                    )
                }
            }

            SectionLabel(t("create_fps"), colors)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FPS_OPTIONS.forEach { value ->
                    val blocked = CapabilityLimits.fpsBlockReason(value, profile)
                    Chip(
                        label = "$value fps",
                        selected = fps == value,
                        enabled = blocked == null,
                        onClick = {
                            if (blocked == null) fps = value else error = blocked
                        }
                    )
                }
            }

            SectionLabel(t("create_background"), colors)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BackgroundPreset.entries.forEach { preset ->
                    val selected = background == preset
                    val bgLabel = when (preset) {
                        BackgroundPreset.BLACK -> t("bg_black")
                        BackgroundPreset.WHITE -> t("bg_white")
                        BackgroundPreset.LIGHT_GRAY -> t("bg_gray")
                        BackgroundPreset.GREEN -> t("bg_green")
                        BackgroundPreset.BLUE -> t("bg_blue")
                        BackgroundPreset.TRANSPARENT -> t("bg_transparent")
                        BackgroundPreset.CUSTOM -> t("bg_custom")
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    when (preset) {
                                        BackgroundPreset.TRANSPARENT -> Color(0xFF2A2A35)
                                        BackgroundPreset.CUSTOM -> try {
                                            Color(("FF" + customColorHex.padEnd(6, '0').take(6)).toLong(16))
                                        } catch (_: Exception) {
                                            Color.DarkGray
                                        }
                                        else -> preset.color ?: Color.DarkGray
                                    }
                                )
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) colors.secondary else colors.muted.copy(alpha = 0.4f),
                                    shape = CircleShape
                                )
                                .clickable { background = preset },
                            contentAlignment = Alignment.Center
                        ) {
                            if (preset == BackgroundPreset.TRANSPARENT) {
                                Text("∅", color = colors.muted, fontSize = 14.sp)
                            }
                        }
                        Text(bgLabel, color = colors.muted, fontSize = 10.sp)
                    }
                }
            }
            if (background == BackgroundPreset.CUSTOM) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = customColorHex,
                    onValueChange = {
                        customColorHex = it.filter { c -> c.isDigit() || c in 'a'..'f' || c in 'A'..'F' }.take(6)
                    },
                    label = { Text(t("create_color_hex")) },
                    prefix = { Text("#", color = colors.muted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors()
                )
            }

            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = colors.danger, fontSize = 13.sp)
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    val longSide = if (aspect == AspectPreset.CUSTOM) maxOf(customW, customH) else resolution.longSide
                    val resReason = CapabilityLimits.resolutionBlockReason(longSide, profile)
                    val fpsReason = CapabilityLimits.fpsBlockReason(fps, profile)
                    when {
                        resReason != null -> error = resReason
                        fpsReason != null -> error = fpsReason
                        aspect == AspectPreset.CUSTOM && (customW < 16 || customH < 16) ->
                            error = "16×16 min"
                        else -> {
                            val (w, h) = if (aspect == AspectPreset.CUSTOM) customW to customH
                            else aspect.computeSize(resolution.longSide)
                            val argb = try {
                                ("FF" + customColorHex.padEnd(6, '0').take(6)).toLong(16)
                            } catch (_: Exception) {
                                0xFF000000L
                            }
                            onCreate(
                                ProjectSettings(
                                    title = title.ifBlank { t("create_name_hint") },
                                    width = w,
                                    height = h,
                                    fps = fps,
                                    aspect = aspect,
                                    background = background,
                                    customBackgroundArgb = argb
                                )
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
            ) {
                Text(t("create_action"), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, colors: VyntaraColors) {
    Text(
        text = text,
        color = colors.muted,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)
    )
}

@Composable
private fun Chip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalAppTheme.current.colors
    val bg = when {
        !enabled -> colors.card.copy(alpha = 0.5f)
        selected -> colors.primary.copy(alpha = 0.35f)
        else -> colors.card
    }
    val border = when {
        selected -> colors.primary
        !enabled -> colors.muted.copy(alpha = 0.2f)
        else -> colors.muted.copy(alpha = 0.35f)
    }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .clickable(enabled = true, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = if (!enabled) "🔒 $label" else label,
            color = if (enabled) colors.text else colors.muted,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun fieldColors() = with(LocalAppTheme.current.colors) {
    OutlinedTextFieldDefaults.colors(
        focusedTextColor = text,
        unfocusedTextColor = text,
        focusedBorderColor = primary,
        unfocusedBorderColor = muted.copy(alpha = 0.4f),
        focusedLabelColor = secondary,
        unfocusedLabelColor = muted,
        cursorColor = secondary
    )
}

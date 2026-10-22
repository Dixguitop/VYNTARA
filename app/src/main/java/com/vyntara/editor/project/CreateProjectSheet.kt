package com.vyntara.editor.project

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
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

@OptIn(ExperimentalMaterial3Api::class)
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
    var aspect by remember {
        mutableStateOf(
            AspectPreset.entries.find { it.label == settings.defaultAspectLabel }
                ?: AspectPreset.RATIO_16_9
        )
    }
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
    var acceptFpsRisk by remember { mutableStateOf(false) }

    var aspectExpanded by remember { mutableStateOf(false) }
    var resExpanded by remember { mutableStateOf(false) }
    var fpsExpanded by remember { mutableStateOf(false) }
    var bgExpanded by remember { mutableStateOf(false) }

    val fpsWarning = CapabilityLimits.fpsBlockReason(fps, profile)
    val resWarning = CapabilityLimits.resolutionBlockReason(resolution.longSide, profile)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp)
        ) {
            Text(t("create_title"), color = colors.text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(t("create_name")) },
                placeholder = { Text(t("create_name_hint")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                colors = fieldColors(),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, color = colors.text)
            )

            Spacer(Modifier.height(16.dp))
            Text(t("create_aspect"), color = colors.muted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            DropdownField(
                value = if (aspect == AspectPreset.CUSTOM) t("aspect_custom") else aspect.label,
                expanded = aspectExpanded,
                onExpandedChange = { aspectExpanded = it },
                colors = colors
            ) {
                AspectPreset.entries.forEach { preset ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (preset == AspectPreset.CUSTOM) t("aspect_custom") else preset.label,
                                fontSize = 16.sp
                            )
                        },
                        onClick = {
                            aspect = preset
                            aspectExpanded = false
                        }
                    )
                }
            }

            if (aspect == AspectPreset.CUSTOM) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = customW.toString(),
                        onValueChange = { customW = it.filter(Char::isDigit).toIntOrNull() ?: customW },
                        label = { Text(t("create_width")) },
                        modifier = Modifier.weight(1f).height(60.dp),
                        singleLine = true,
                        colors = fieldColors()
                    )
                    OutlinedTextField(
                        value = customH.toString(),
                        onValueChange = { customH = it.filter(Char::isDigit).toIntOrNull() ?: customH },
                        label = { Text(t("create_height")) },
                        modifier = Modifier.weight(1f).height(60.dp),
                        singleLine = true,
                        colors = fieldColors()
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(t("create_resolution"), color = colors.muted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            DropdownField(
                value = resolution.label,
                expanded = resExpanded,
                onExpandedChange = { resExpanded = it },
                colors = colors
            ) {
                ResolutionPreset.entries.forEach { preset ->
                    val warn = CapabilityLimits.resolutionBlockReason(preset.longSide, profile)
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(preset.label, fontSize = 16.sp)
                                if (warn != null) {
                                    Spacer(Modifier.size(8.dp))
                                    Icon(Icons.Outlined.Warning, null, tint = colors.danger, modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        onClick = {
                            resolution = preset
                            resExpanded = false
                            acceptFpsRisk = false
                        }
                    )
                }
            }
            if (resWarning != null) {
                WarningRow(resWarning, colors)
            }

            Spacer(Modifier.height(16.dp))
            Text(t("create_fps"), color = colors.muted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            DropdownField(
                value = "$fps fps",
                expanded = fpsExpanded,
                onExpandedChange = { fpsExpanded = it },
                colors = colors
            ) {
                FPS_OPTIONS.forEach { value ->
                    val warn = CapabilityLimits.fpsBlockReason(value, profile)
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("$value fps", fontSize = 16.sp)
                                if (warn != null) {
                                    Spacer(Modifier.size(8.dp))
                                    Icon(Icons.Outlined.Warning, null, tint = Color(0xFFFBBF24), modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        onClick = {
                            fps = value
                            fpsExpanded = false
                            acceptFpsRisk = false
                        }
                    )
                }
            }
            if (fpsWarning != null) {
                WarningRow(
                    if (lang == com.vyntara.editor.i18n.AppLanguage.ENGLISH)
                        "Not optimal on this device. App may run slower. You can still use it at your own risk."
                    else
                        "No es optimo en este dispositivo. La app puede ir mas lenta. Puedes usarlo bajo tu propio riesgo.",
                    colors,
                    allowContinue = true,
                    accepted = acceptFpsRisk,
                    onAccept = { acceptFpsRisk = true },
                    acceptLabel = if (lang == com.vyntara.editor.i18n.AppLanguage.ENGLISH) "I accept the risk" else "Acepto el riesgo"
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(t("create_background"), color = colors.muted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            val bgLabel = when (background) {
                BackgroundPreset.BLACK -> t("bg_black")
                BackgroundPreset.WHITE -> t("bg_white")
                BackgroundPreset.LIGHT_GRAY -> t("bg_gray")
                BackgroundPreset.GREEN -> t("bg_green")
                BackgroundPreset.BLUE -> t("bg_blue")
                BackgroundPreset.TRANSPARENT -> t("bg_transparent")
                BackgroundPreset.CUSTOM -> t("bg_custom")
            }
            DropdownField(
                value = bgLabel,
                expanded = bgExpanded,
                onExpandedChange = { bgExpanded = it },
                colors = colors,
                leading = {
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(
                                when (background) {
                                    BackgroundPreset.TRANSPARENT -> Color(0xFF2A2A35)
                                    BackgroundPreset.CUSTOM -> try {
                                        Color(("FF" + customColorHex.padEnd(6, '0').take(6)).toLong(16))
                                    } catch (_: Exception) {
                                        Color.DarkGray
                                    }
                                    else -> background.color ?: Color.DarkGray
                                }
                            )
                            .border(1.dp, colors.muted.copy(alpha = 0.4f), CircleShape)
                    )
                }
            ) {
                BackgroundPreset.entries.forEach { preset ->
                    val label = when (preset) {
                        BackgroundPreset.BLACK -> t("bg_black")
                        BackgroundPreset.WHITE -> t("bg_white")
                        BackgroundPreset.LIGHT_GRAY -> t("bg_gray")
                        BackgroundPreset.GREEN -> t("bg_green")
                        BackgroundPreset.BLUE -> t("bg_blue")
                        BackgroundPreset.TRANSPARENT -> t("bg_transparent")
                        BackgroundPreset.CUSTOM -> t("bg_custom")
                    }
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(preset.color ?: Color(0xFF2A2A35))
                                )
                                Spacer(Modifier.size(10.dp))
                                Text(label, fontSize = 16.sp)
                            }
                        },
                        onClick = {
                            background = preset
                            bgExpanded = false
                        }
                    )
                }
            }
            if (background == BackgroundPreset.CUSTOM) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = customColorHex,
                    onValueChange = {
                        customColorHex = it.filter { c -> c.isDigit() || c in 'a'..'f' || c in 'A'..'F' }.take(6)
                    },
                    label = { Text(t("create_color_hex")) },
                    prefix = { Text("#", color = colors.muted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    colors = fieldColors()
                )
            }

            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = colors.danger, fontSize = 14.sp)
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    val longSide = if (aspect == AspectPreset.CUSTOM) maxOf(customW, customH) else resolution.longSide
                    val hardResBlock = longSide > 2160 || longSide < 16
                    when {
                        hardResBlock -> error = "16–2160"
                        fpsWarning != null && !acceptFpsRisk -> {
                            error = if (lang == com.vyntara.editor.i18n.AppLanguage.ENGLISH)
                                "Confirm that you accept the FPS risk"
                            else
                                "Confirma que aceptas el riesgo de FPS"
                        }
                        aspect == AspectPreset.CUSTOM && (customW < 16 || customH < 16) ->
                            error = "16x16 min"
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
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
            ) {
                Text(t("create_action"), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    value: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    colors: VyntaraColors,
    leading: (@Composable () -> Unit)? = null,
    menu: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = onExpandedChange) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            leadingIcon = leading,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
                .height(56.dp),
            colors = fieldColors(),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, color = colors.text)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier.background(colors.card)
        ) {
            menu()
        }
    }
}

@Composable
private fun WarningRow(
    message: String,
    colors: VyntaraColors,
    allowContinue: Boolean = false,
    accepted: Boolean = false,
    onAccept: () -> Unit = {},
    acceptLabel: String = ""
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.card)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Warning, null, tint = Color(0xFFFBBF24), modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
            Text(message, color = colors.muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
        }
        if (allowContinue) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (accepted) acceptLabel else acceptLabel,
                color = if (accepted) colors.success else colors.primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clickable(onClick = onAccept)
                    .padding(vertical = 8.dp)
            )
        }
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

package com.vyntara.editor.project

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.vyntara.editor.i18n.AppLanguage
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

    // Only one panel open at a time
    var openPanel by remember { mutableStateOf<String?>(null) }

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
                .padding(bottom = 36.dp)
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

            Spacer(Modifier.height(14.dp))
            FieldLabel(t("create_aspect"), colors)
            CompactPicker(
                label = if (aspect == AspectPreset.CUSTOM) t("aspect_custom") else aspect.label,
                expanded = openPanel == "aspect",
                onToggle = { openPanel = if (openPanel == "aspect") null else "aspect" },
                colors = colors
            ) {
                AspectPreset.entries.forEach { preset ->
                    CompactOption(
                        text = if (preset == AspectPreset.CUSTOM) t("aspect_custom") else preset.label,
                        selected = aspect == preset,
                        colors = colors
                    ) {
                        aspect = preset
                        openPanel = null
                    }
                }
            }
            if (aspect == AspectPreset.CUSTOM) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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

            Spacer(Modifier.height(12.dp))
            FieldLabel(t("create_resolution"), colors)
            CompactPicker(
                label = resolution.label,
                expanded = openPanel == "res",
                onToggle = { openPanel = if (openPanel == "res") null else "res" },
                colors = colors
            ) {
                ResolutionPreset.entries.forEach { preset ->
                    val warn = CapabilityLimits.resolutionBlockReason(preset.longSide, profile)
                    CompactOption(
                        text = preset.label,
                        selected = resolution == preset,
                        colors = colors,
                        trailingWarn = warn != null
                    ) {
                        resolution = preset
                        openPanel = null
                        acceptFpsRisk = false
                    }
                }
            }
            if (resWarning != null) {
                RiskNote(resWarning, colors)
            }

            Spacer(Modifier.height(12.dp))
            FieldLabel(t("create_fps"), colors)
            CompactPicker(
                label = "$fps fps",
                expanded = openPanel == "fps",
                onToggle = { openPanel = if (openPanel == "fps") null else "fps" },
                colors = colors
            ) {
                FPS_OPTIONS.forEach { value ->
                    val warn = CapabilityLimits.fpsBlockReason(value, profile)
                    CompactOption(
                        text = "$value fps",
                        selected = fps == value,
                        colors = colors,
                        trailingWarn = warn != null
                    ) {
                        fps = value
                        openPanel = null
                        acceptFpsRisk = false
                    }
                }
            }
            if (fpsWarning != null) {
                RiskNote(
                    if (lang == AppLanguage.ENGLISH)
                        "Not optimal on this device. May run slower. You can continue at your own risk."
                    else
                        "No es optimo en este dispositivo. Puede ir mas lenta. Continua bajo tu propio riesgo.",
                    colors,
                    acceptLabel = if (lang == AppLanguage.ENGLISH) "I accept the risk" else "Acepto el riesgo",
                    accepted = acceptFpsRisk,
                    onAccept = { acceptFpsRisk = true }
                )
            }

            Spacer(Modifier.height(12.dp))
            FieldLabel(t("create_background"), colors)
            val bgLabel = when (background) {
                BackgroundPreset.BLACK -> t("bg_black")
                BackgroundPreset.WHITE -> t("bg_white")
                BackgroundPreset.LIGHT_GRAY -> t("bg_gray")
                BackgroundPreset.GREEN -> t("bg_green")
                BackgroundPreset.BLUE -> t("bg_blue")
                BackgroundPreset.TRANSPARENT -> t("bg_transparent")
                BackgroundPreset.CUSTOM -> t("bg_custom")
            }
            CompactPicker(
                label = bgLabel,
                expanded = openPanel == "bg",
                onToggle = { openPanel = if (openPanel == "bg") null else "bg" },
                colors = colors,
                leading = {
                    Box(
                        Modifier
                            .size(18.dp)
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
                            .border(1.dp, colors.muted.copy(alpha = 0.35f), CircleShape)
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
                    CompactOption(
                        text = label,
                        selected = background == preset,
                        colors = colors,
                        leadingColor = preset.color ?: Color(0xFF2A2A35)
                    ) {
                        background = preset
                        openPanel = null
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
                Spacer(Modifier.height(10.dp))
                Text(it, color = colors.danger, fontSize = 13.sp)
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    val longSide = if (aspect == AspectPreset.CUSTOM) maxOf(customW, customH) else resolution.longSide
                    when {
                        longSide !in 16..2160 -> error = "16–2160"
                        fpsWarning != null && !acceptFpsRisk ->
                            error = if (lang == AppLanguage.ENGLISH)
                                "Accept the FPS risk to continue"
                            else
                                "Acepta el riesgo de FPS para continuar"
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
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
            ) {
                Text(t("create_action"), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String, colors: VyntaraColors) {
    Text(text, color = colors.muted, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun CompactPicker(
    label: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    colors: VyntaraColors,
    leading: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.card)
                .border(1.dp, colors.muted.copy(alpha = 0.28f), RoundedCornerShape(12.dp))
                .clickable(onClick = onToggle)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.size(10.dp))
            }
            Text(label, color = colors.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = colors.muted,
                modifier = Modifier.size(22.dp)
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .heightIn(max = 180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.card)
                    .border(1.dp, colors.muted.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    .verticalScroll(rememberScrollState())
            ) {
                content()
            }
        }
    }
}

@Composable
private fun CompactOption(
    text: String,
    selected: Boolean,
    colors: VyntaraColors,
    trailingWarn: Boolean = false,
    leadingColor: Color? = null,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (selected) colors.primary.copy(alpha = 0.18f) else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingColor != null) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(leadingColor).border(1.dp, colors.muted.copy(alpha = 0.3f), CircleShape))
            Spacer(Modifier.size(10.dp))
        }
        Text(
            text,
            color = if (selected) colors.primary else colors.text,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        if (trailingWarn) {
            Icon(Icons.Outlined.Warning, null, tint = Color(0xFFFBBF24), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun RiskNote(
    message: String,
    colors: VyntaraColors,
    acceptLabel: String? = null,
    accepted: Boolean = false,
    onAccept: () -> Unit = {}
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
            Icon(Icons.Outlined.Warning, null, tint = Color(0xFFFBBF24), modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(message, color = colors.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
        }
        if (acceptLabel != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                acceptLabel,
                color = if (accepted) colors.success else colors.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable(onClick = onAccept).padding(vertical = 6.dp)
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

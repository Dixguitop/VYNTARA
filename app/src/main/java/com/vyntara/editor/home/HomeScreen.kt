package com.vyntara.editor.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vyntara.editor.capabilities.DeviceProfile
import com.vyntara.editor.i18n.AppLanguage
import com.vyntara.editor.i18n.Strings
import com.vyntara.editor.ui.theme.LocalAppTheme

@Composable
fun HomeScreen(
    projectCount: Int = 0,
    storageLabel: String = "0 B",
    profile: DeviceProfile? = null,
    onOpenSettings: () -> Unit = {},
    onOpenProjects: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LocalAppTheme.current.colors
    val metrics = LocalAppTheme.current.metrics
    // Language comes from theme provider parent via settings — read from a composition approach:
    // Home doesn't get settings directly; MainShell will pass language through strings via parent.
    // For simplicity, we read Ambient via LocalAppTheme only; language is injected via Strings with a local.
    // Actually MainShell should pass language. Use a CompositionLocal or parameter.
    // Quick fix: accept optional lang code - for now use Spanish keys if we don't have access.
    // Better: pass lang from shell. I'll use a simple approach - Strings with CompositionLocal.

    val lang = LocalLanguage.current
    fun t(key: String) = Strings.t(lang, key)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(metrics.contentPadding)
    ) {
        Text("VYNTARA", color = colors.text, fontSize = 28.sp, fontWeight = FontWeight.Light, letterSpacing = 4.sp)
        Text(t("creative_engine"), color = colors.primary, fontSize = 12.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(t("home_stat_projects"), "$projectCount", colors, metrics.cardRadius, Modifier.weight(1f), onOpenProjects)
            StatCard(t("home_stat_storage"), storageLabel, colors, metrics.cardRadius, Modifier.weight(1f), onOpenSettings)
        }
        Spacer(Modifier.height(10.dp))
        StatCard(
            t("home_stat_profile"),
            profile?.let { "${it.previewResolutionCap}p · GL ${it.glEsVersion}" } ?: "—",
            colors,
            metrics.cardRadius,
            Modifier.fillMaxWidth(),
            onOpenSettings
        )

        Spacer(Modifier.height(16.dp))
        InfoCard(t("home_welcome_title"), t("home_welcome_body"), colors, metrics.cardRadius)
        Spacer(Modifier.height(10.dp))
        InfoCard(t("home_news_title"), t("home_news_body"), colors, metrics.cardRadius)
        Spacer(Modifier.height(10.dp))
        InfoCard(t("home_soon_title"), t("home_soon_body"), colors, metrics.cardRadius)
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    colors: com.vyntara.editor.ui.theme.VyntaraColors,
    radius: androidx.compose.ui.unit.Dp,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier
            .clip(RoundedCornerShape(radius))
            .background(colors.card)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Text(title, color = colors.muted, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = colors.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun InfoCard(
    title: String,
    body: String,
    colors: com.vyntara.editor.ui.theme.VyntaraColors,
    radius: androidx.compose.ui.unit.Dp
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius))
            .background(colors.card)
            .padding(16.dp)
    ) {
        Text(title, color = colors.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(body, color = colors.muted, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

/** CompositionLocal for language so every screen can translate. */
val LocalLanguage = androidx.compose.runtime.staticCompositionLocalOf { AppLanguage.SPANISH }

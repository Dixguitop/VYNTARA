package com.vyntara.editor.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
import com.vyntara.editor.i18n.Strings
import com.vyntara.editor.ui.theme.LocalAppTheme

@Composable
fun StartScreen(modifier: Modifier = Modifier) {
    val colors = LocalAppTheme.current.colors
    val metrics = LocalAppTheme.current.metrics
    val lang = LocalLanguage.current
    fun t(key: String) = Strings.t(lang, key)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(metrics.contentPadding)
    ) {
        Text(t("start_title"), color = colors.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text(t("start_subtitle"), color = colors.muted, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
        Step(1, t("start_1_title"), t("start_1_body"), colors, metrics.cardRadius)
        Step(2, t("start_2_title"), t("start_2_body"), colors, metrics.cardRadius)
        Step(3, t("start_3_title"), t("start_3_body"), colors, metrics.cardRadius)
        Step(4, t("start_4_title"), t("start_4_body"), colors, metrics.cardRadius)
        Step(5, t("start_5_title"), t("start_5_body"), colors, metrics.cardRadius)
    }
}

@Composable
private fun Step(
    n: Int,
    title: String,
    body: String,
    colors: com.vyntara.editor.ui.theme.VyntaraColors,
    radius: androidx.compose.ui.unit.Dp
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(radius))
            .background(colors.card)
            .padding(14.dp)
    ) {
        Text("$n. $title", color = colors.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(body, color = colors.muted, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

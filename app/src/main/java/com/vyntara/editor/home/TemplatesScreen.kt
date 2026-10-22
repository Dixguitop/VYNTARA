package com.vyntara.editor.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vyntara.editor.i18n.Strings
import com.vyntara.editor.ui.theme.LocalAppTheme

@Composable
fun TemplatesScreen(modifier: Modifier = Modifier) {
    val colors = LocalAppTheme.current.colors
    val lang = LocalLanguage.current
    fun t(key: String) = Strings.t(lang, key)
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(t("templates_title"), color = colors.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(t("templates_soon"), color = colors.muted, fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            Text(t("templates_hint"), color = colors.muted.copy(alpha = 0.7f), fontSize = 13.sp)
        }
    }
}

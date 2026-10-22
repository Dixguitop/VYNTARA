package com.vyntara.editor.boot

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vyntara.editor.R

/**
 * ============================================================================
 * LOGO — PUNTO ÚNICO DE INTEGRACIÓN
 * ============================================================================
 *
 * Dónde poner tu logo oficial:
 *
 *   1. Copia el archivo a:
 *        app/src/main/res/drawable/
 *
 *      Nombres válidos de ejemplo:
 *        - ic_vyntara_logo.png
 *        - ic_vyntara_logo.webp
 *        - ic_vyntara_logo.xml   (vector)
 *
 *   2. Cambia SOLO la constante [LOGO_DRAWABLE] de abajo para que apunte
 *      a tu recurso (por ejemplo R.drawable.ic_vyntara_logo).
 *
 *   3. No hace falta tocar el resto de la animación. Este composable
 *      solo aplica escala, alpha y posición; no deforma el diseño.
 *
 * El archivo ic_vyntara_logo_placeholder.xml es temporal de desarrollo.
 * ============================================================================
 */
private val LOGO_DRAWABLE: Int = R.drawable.ic_vyntara_logo_placeholder

private val SoftWhite = Color(0xFFE8E8F0)
private val DimLabel = Color(0xFF8B8BA0)

@Composable
fun BootLogo(
    alpha: Float,
    scale: Float,
    size: Dp = 120.dp,
    showWordmark: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.graphicsLayer {
            this.alpha = alpha
            scaleX = scale
            scaleY = scale
        },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = LOGO_DRAWABLE),
            contentDescription = "Vyntara",
            modifier = Modifier.size(size),
            contentScale = ContentScale.Fit
        )

        if (showWordmark) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "VYNTARA",
                color = SoftWhite.copy(alpha = 0.92f),
                fontSize = 22.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 8.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "CREATIVE ENGINE",
                color = DimLabel.copy(alpha = 0.75f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 3.sp
            )
        }
    }
}

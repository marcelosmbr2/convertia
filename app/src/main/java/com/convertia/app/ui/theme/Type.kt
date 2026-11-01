package com.convertia.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// O mockup usa Inter e JetBrains Mono. Por enquanto fica a fonte do sistema;
// as fontes entram quando forem empacotadas em res/font.
internal val ConvertiaTypography = Typography().run {
    copy(
        // Título das telas ("Baixar", "Converter", ...).
        headlineLarge = headlineLarge.copy(
            fontSize = 30.sp,
            lineHeight = 33.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.03).em,
        ),
    )
}

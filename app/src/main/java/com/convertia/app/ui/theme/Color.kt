package com.convertia.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Paleta do mockup (mockup/Convertia v3.dc.html). O design usa muitos cinzas próximos;
 * os nomes descrevem o papel principal de cada um, mas o mesmo tom aparece em outros
 * lugares do mockup — use o que bate com o hex da tela que está sendo implementada.
 */
object Palette {
    // Fundos
    /** Fundo das telas. */
    val Background = Color(0xFF0F0F11)

    /** Barras fixas acima da navegação (resumo do Baixar, ações do Converter, rodapé da Fila). */
    val BackgroundDeep = Color(0xFF0C0C0E)

    /** Fundo do estado vazio do Converter. */
    val BackgroundEmpty = Color(0xFF111114)

    /** Cartões, barra de navegação, campos. */
    val Card = Color(0xFF141417)

    /** Bottom sheets e blocos de carregamento. */
    val Sheet = Color(0xFF17171A)

    /** Linha selecionada, botões discretos. */
    val Raised = Color(0xFF1A1A1E)

    /** Chips e campos dentro de sheets; também a divisória entre linhas. */
    val Raised2 = Color(0xFF1C1C20)

    /** Botões cinza ("Colar", "Converter" na Fila), fundo dos badges. */
    val Raised3 = Color(0xFF1F1F24)

    /** Item atual no seletor de formato. */
    val Selected = Color(0xFF26262C)

    // Bordas e linhas
    /** Borda dos cartões. */
    val Border = Color(0xFF222227)

    /** Borda de chips e botões; fundo do item selecionado em controles segmentados. */
    val BorderStrong = Color(0xFF2A2A30)

    /** Borda de miniaturas e do contêiner dos segmentados; cor da grade de pontos. */
    val BorderSubtle = Color(0xFF24242A)

    /** Borda do campo de busca e do topo dos sheets. */
    val BorderSheet = Color(0xFF26262C)

    /** Borda tracejada do cartão de dica. */
    val BorderDashed = Color(0xFF2C2C33)

    /** Divisória entre linhas de uma lista. */
    val Divider = Color(0xFF1C1C20)

    /** Divisória acima das barras fixas e da navegação. */
    val DividerStrong = Color(0xFF1E1E22)

    /** Trilho das barras de progresso. */
    val Track = Color(0xFF26262A)

    // Cinzas intermediários (indicadores, anéis, barras)
    val Gray33 = Color(0xFF33333A)
    val Gray3A = Color(0xFF3A3A42)
    val Gray44 = Color(0xFF44444C)
    val Gray4A = Color(0xFF4A4A52)
    val Gray55 = Color(0xFF55555E)
    val Gray5A = Color(0xFF5A5A62)
    val Gray6B = Color(0xFF6B6B74)

    // Texto
    val TextPrimary = Color(0xFFEDEDEF)
    val TextEmphasis = Color(0xFFC9C9CF)
    val TextSecondary = Color(0xFFA1A1AA)
    val TextMeta = Color(0xFF8B8B95)
    val TextMeta2 = Color(0xFF858590)
    val TextTertiary = Color(0xFF7C7C86)
    val TextMuted = Color(0xFF6E6E78)

    // Marca
    /** Laranja da marca: botões principais e ícone do app. */
    val Accent = Color(0xFFF07152)

    /** Laranja ao pressionar o botão principal. */
    val AccentPressed = Color(0xFFF4846A)

    /** Laranja claro: aba ativa, sobretítulos do onboarding. */
    val AccentText = Color(0xFFF5866D)

    /** Texto e ícones sobre o laranja. */
    val OnAccent = Color(0xFF1A0E0A)

    // Estados
    val Danger = Color(0xFFE5484D)
    val DangerText = Color(0xFFF0777A)

    // Toast
    val ToastBackground = Color(0xFFE6E6EA)
    val ToastText = Color(0xFF17171A)

    // Ícones do sheet "Adicionar arquivos"
    val GalleryTint = Color(0xFF8FB0F5)
    val FilesTint = Color(0xFFC9CCD3)

    // Miniaturas listradas (no lugar das capas de vídeo)
    val StripeA = Color(0xFF151518)
    val StripeB = Color(0xFF19191D)

    /** Véu atrás dos sheets. */
    val Scrim = Color(0x99000000)
}

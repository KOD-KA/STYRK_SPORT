package ru.styrk.app.ui.theme

import androidx.compose.ui.graphics.Color


// VKA SPORT PALETTE


val Black =
    Color(0xFF000000)


val DarkGray =
    Color(0xFF424242)


val LightGray =
    Color(0xFFDADADA)


val SoftGray =
    Color(0xFFDEDCDC)


val White =
    Color(0xFFFFFFFF)

val Gold =
    Color(0xFFC9A84C)

// Светлый фон страницы тренировки, чтобы белые карточки не сливались (п10)
val TrainingBg =
    Color(0xFFF0F0F3)

// LIGHT THEME

// ИСПРАВЛЕНО: раньше BackgroundLight = SoftGray — тот же цвет, что и у
// самих карточек (см. RecordCard/ArchiveCard), из-за чего карточки
// визуально сливались с общим фоном экрана. По дизайну (PDF-макет)
// страница должна быть белой, а карточки — светло-серыми поверх неё.
val BackgroundLight =
    White


val SurfaceLight =
    White


val PrimaryLight =
    Black


val TextPrimaryLight =
    Black




// DARK THEME


val BackgroundDark =
    Black


val SurfaceDark =
    DarkGray


val PrimaryDark =
    White


val TextPrimaryDark =
    White
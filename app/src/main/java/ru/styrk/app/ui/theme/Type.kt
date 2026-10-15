package ru.styrk.app.ui.theme


import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ru.styrk.app.R




private val Inter =
    FontFamily(
        androidx.compose.ui.text.font.Font(
            resId = ru.styrk.app.R.font.inter_regular,
            weight = FontWeight.Normal
        ),

        androidx.compose.ui.text.font.Font(
            resId = ru.styrk.app.R.font.inter_medium,
            weight = FontWeight.Medium
        ),

        androidx.compose.ui.text.font.Font(
            resId = ru.styrk.app.R.font.inter_bold,
            weight = FontWeight.Bold
        )
    )



val Typography = Typography(



    // основной текст
    bodyLarge = TextStyle(

        fontFamily = Inter,

        fontWeight = FontWeight.Normal,

        fontSize = 14.sp,

        lineHeight = 20.sp

    ),



    // вторичный текст
    bodyMedium = TextStyle(

        fontFamily = Inter,

        fontWeight = FontWeight.Normal,

        fontSize = 12.sp,

        lineHeight = 16.sp

    ),



    // мелкие подписи
    bodySmall = TextStyle(

        fontFamily = Inter,

        fontWeight = FontWeight.Normal,

        fontSize = 10.sp,

        lineHeight = 14.sp

    ),



    // заголовки блоков
    titleMedium = TextStyle(

        fontFamily = Inter,

        fontWeight = FontWeight.Medium,

        fontSize = 16.sp,

        lineHeight = 22.sp

    ),



    // заголовки экранов
    titleLarge = TextStyle(

        fontFamily = Inter,

        fontWeight = FontWeight.Medium,

        fontSize = 20.sp,

        lineHeight = 26.sp

    ),



    // рекорды
    headlineLarge = TextStyle(

        fontFamily = Inter,

        fontWeight = FontWeight.Bold,

        fontSize = 24.sp,

        lineHeight = 30.sp

    )

)
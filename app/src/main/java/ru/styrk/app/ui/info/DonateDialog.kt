package ru.styrk.app.ui.info

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import ru.styrk.app.ui.theme.Black
import ru.styrk.app.ui.theme.Gold
import ru.styrk.app.ui.theme.White

/**
 * ПОДДЕРЖАТЬ АВТОРА.
 *
 * Раньше отсюда вела одна ссылка на Boosty — и всё. Ссылка устарела,
 * а сама затея была неудачной: человек нажимал «Поддержать» и уходил
 * из приложения на страницу подписки, где надо разбираться, за что
 * с него просят деньги ежемесячно.
 *
 * Теперь два способа, оба разовые, оба — одно касание.
 *
 * QR-кодов здесь нет, и это не упущение. Код нужен, когда платят
 * с ДРУГОГО устройства: с экрана компьютера его наводят камерой
 * телефона. А здесь человек уже держит телефон в руках — ему остаётся
 * нажать, и банк откроется сам. Код на этом экране был бы картинкой,
 * которую некому сканировать. В дневнике на сайте они остались: там
 * он как раз к месту.
 */
private const val CLOUDTIPS_URL = "https://pay.cloudtips.ru/p/f4fe0512"
private const val SBER_URL = "https://messenger.sbrf.ru/sl/QmyzHJpPGRVKMDDWX"

@Composable
fun DonateDialog(onDismiss: () -> Unit, onOpen: (String) -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Black)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 22.dp)
        ) {
            Text(
                "ПОДДЕРЖАТЬ", color = White.copy(alpha = 0.45f), fontSize = 10.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 2.sp
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Спасибо, что пользуетесь STYRK",
                color = White, fontSize = 19.sp, fontWeight = FontWeight.Bold, lineHeight = 25.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Приложение делает один человек, в свободное время и без рекламы. " +
                    "Любая сумма — это несколько вечеров, которые пойдут на новые " +
                    "возможности, а не на что-то другое.",
                color = White.copy(alpha = 0.6f), fontSize = 13.sp, lineHeight = 19.sp
            )

            // Золотая черта: единственное украшение здесь, и оно же
            // отделяет рассказ от действия.
            Spacer(Modifier.height(18.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Brush.horizontalGradient(listOf(Gold, Color.Transparent)))
            )
            Spacer(Modifier.height(18.dp))

            Way(
                title = "CloudTips",
                note = "Карта любого банка, Apple Pay, Google Pay"
            ) { onOpen(CLOUDTIPS_URL) }

            Spacer(Modifier.height(10.dp))

            Way(
                title = "СберБанк",
                note = "Перевод по ссылке в приложении Сбера"
            ) { onOpen(SBER_URL) }

            Spacer(Modifier.height(16.dp))
            Text(
                "Поддержка ничего не включает и не отключает: все возможности " +
                    "дневника были и останутся общими.",
                color = White.copy(alpha = 0.38f), fontSize = 11.sp, lineHeight = 16.sp
            )

            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onDismiss() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "ЗАКРЫТЬ", color = White.copy(alpha = 0.55f), fontSize = 12.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp
                )
            }
        }
    }
}

/**
 * Один способ — строкой.
 *
 * Нажимается вся строка целиком, а не кнопка сбоку: попасть пальцем
 * в строку высотой в палец проще, чем в кнопку, и промахнуться тут
 * нечем — других действий в строке нет.
 */
@Composable
private fun Way(title: String, note: String, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(White.copy(alpha = 0.07f))
            .clickable { onOpen() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(
                note, color = White.copy(alpha = 0.45f),
                fontSize = 11.sp, lineHeight = 15.sp
            )
        }
        Spacer(Modifier.width(10.dp))
        Text("→", color = Gold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

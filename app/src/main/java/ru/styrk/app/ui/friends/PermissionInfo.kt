package ru.styrk.app.ui.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.styrk.app.ui.theme.Black
import ru.styrk.app.ui.theme.DarkGray
import ru.styrk.app.ui.theme.Gold
import ru.styrk.app.ui.theme.LightGray
import ru.styrk.app.ui.theme.White

/**
 * Что именно отдаёт каждое право — для окна под «?» у галочки.
 *
 * Галочка с одним словом рядом («Рекорды») не говорит главного: что
 * увидит друг, а чего не увидит. Человек ставит её наугад или не ставит
 * вовсе. Здесь это сказано словами — тем же языком, что в окне согласия
 * на тренерство.
 *
 * Веб-дневник берёт эти же тексты отсюда (tools/собрать_инфо.py), чтобы
 * объяснение одного и того же права не разошлось на двух платформах.
 */
data class PermText(
    val key: String,
    val title: String,
    val sees: List<String>,
    val notSees: List<String>,
    val note: String
)

object PermissionTexts {

    val ALL: List<PermText> = listOf(
        PermText(
            key = "records",
            title = "Рекорды",
            sees = listOf(
                "Ваш лучший результат в каждом упражнении: вес и повторы, время или дистанцию — и дату, когда он поставлен.",
                "График по упражнению: лучший результат каждой тренировки с ним и её дату — рядом с его графиком.",
                "Сравнение ваших рекордов с его — по упражнениям, которые есть у обоих."
            ),
            notSees = listOf(
                "Тренировки целиком: все подходы, остальные упражнения дня, длительность.",
                "Вес на весах, замеры, фото и календарь."
            ),
            note = "Считает сервер и отдаёт другу только итоговые числа — дневник целиком к нему не уходит. " +
                "Сняли галочку — он перестаёт их видеть сразу."
        ),
        PermText(
            key = "volume",
            title = "Объём за неделю",
            sees = listOf(
                "Сколько килограммов вы подняли за последние 7 дней: сумма «вес × повторы» по всем подходам.",
                "Сколько за эти 7 дней было тренировок и подходов."
            ),
            notSees = listOf(
                "Из каких упражнений и подходов сложился этот объём.",
                "Рекорды, вес и замеры — если они не открыты отдельно."
            ),
            note = "Удобно, чтобы держать друг друга в тонусе: видно, кто в эту неделю сделал больше."
        ),
        PermText(
            key = "weight",
            title = "График веса",
            sees = listOf(
                "Линию вашего веса на весах по датам — из журнала тела."
            ),
            notSees = listOf(
                "Замеры тела (талия, грудь, бицепс и другие) и фото — их видит только тренер и только с отдельной галочкой.",
                "Рекорды и тренировки — если они не открыты отдельно."
            ),
            note = "Весом мериться с приятелем — обычное дело, а скрывают его чаще, чем рекорды. " +
                "Поэтому это отдельная галочка, и по умолчанию она снята."
        ),
        PermText(
            key = "notify",
            title = "Сообщать о тренировках",
            sees = listOf(
                "Сообщение в Телеграм-бот STYRK, когда вы начинаете тренировку: ваш ник и группа мышц.",
                "Сообщение, когда заканчиваете: сколько длилась, сколько было упражнений и подходов."
            ),
            notSees = listOf(
                "Упражнения, веса и подходы.",
                "Тренировки, записанные задним числом: о них не сообщаем."
            ),
            note = "Приходит, только если у друга к STYRK привязан Телеграм. Тренеру о тренировке, " +
                "которую назначил он, сообщение приходит и без этой галочки — пока он ваш тренер."
        ),
        PermText(
            key = "coach",
            title = "Ваш тренер",
            sees = listOf(
                "Все ваши тренировки: упражнения, подходы, веса, даты.",
                "Рекорды, объём и календарь: что запланировано и что выполнено.",
                "Тренировку вживую, пока вы занимаетесь. Может вписать за вас подход — он появится у вас, будто вы ввели его сами.",
                "Может назначать вам тренировки: они приходят в календарь, а согласиться или отказаться решаете вы.",
                "Сообщение о начале и конце тренировки, которую назначил он."
            ),
            notSees = listOf(
                "Замеры тела, вес и фото — это отдельная галочка, по умолчанию снята.",
                "Возможности переписать уже записанное: прошлые тренировки остаются такими, какими вы их внесли."
            ),
            note = "Доступ можно открыть до определённого числа и отозвать в любой момент кнопкой " +
                "«Отозвать доверие» — он пропадает сразу."
        ),
        PermText(
            key = "body",
            title = "Замеры тела",
            sees = listOf(
                "Ваш вес, замеры и снимки — всё, что лежит в журнале тела."
            ),
            notSees = listOf(
                "Друзья: замеры уходят только тренеру и только вместе с правом «он мой тренер»."
            ),
            note = "Без тренерства эта галочка не работает: замеры отдаются тренеру, а не всякому другу."
        )
    )

    fun of(key: String): PermText? = ALL.find { it.key == key }
}

/** «?» рядом с галочкой. Круг, а не голый знак: в него легко попасть пальцем. */
@Composable
fun InfoMark(onClick: () -> Unit) {
    Box(
        Modifier
            .padding(start = 8.dp)
            .size(26.dp)
            .clip(CircleShape)
            .border(1.dp, LightGray, CircleShape)
            .background(White)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text("?", color = DarkGray, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

/** Окно с объяснением права — как остальные окна приложения: белое, «Понятно». */
@Composable
fun PermissionInfoDialog(key: String, onDismiss: () -> Unit) {
    val t = PermissionTexts.of(key) ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = White,
        titleContentColor = Black,
        textContentColor = DarkGray,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Понятно", color = Black) } },
        title = { Text(t.title, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Section("ЧТО ОН ПОЛУЧИТ", t.sees)
                Spacer(Modifier.height(12.dp))
                Section("ЧЕГО НЕ ПОЛУЧИТ", t.notSees)
                Spacer(Modifier.height(12.dp))
                Text(t.note, color = DarkGray, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
    )
}

@Composable
private fun Section(title: String, lines: List<String>) {
    Text(title, color = Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
    lines.forEach { line ->
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Text("—", color = Gold, fontSize = 13.sp, lineHeight = 18.sp)
            Spacer(Modifier.width(8.dp))
            Text(line, color = Black, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

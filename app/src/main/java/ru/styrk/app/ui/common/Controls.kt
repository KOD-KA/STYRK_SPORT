package ru.styrk.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.styrk.app.ui.theme.Black
import ru.styrk.app.ui.theme.DarkGray
import ru.styrk.app.ui.theme.LightGray
import ru.styrk.app.ui.theme.White

/*
 * Мелочи, из которых собраны окна.
 *
 * Лежат в одном месте не ради экономии строк, а ради одинаковости:
 * пока каждое окно рисовало свою галочку и свою строку-действие,
 * в одном и том же окне встречались три разных размера и два разных
 * квадратика. Правило простое: если это видно человеку и встречается
 * дважды — оно живёт здесь.
 */

/**
 * Квадратик отметки.
 *
 * Обводка есть всегда, а не только когда отмечено. Без неё пустая
 * отметка — белый квадрат на белом, и на светлой карточке она
 * пропадала совсем: оставалась надпись, отъехавшая вправо неизвестно
 * от чего. Ровно так выглядела галочка «показывать замеры тела»
 * в карточке тренера.
 */
@Composable
fun MarkBox(checked: Boolean) {
    Box(
        Modifier
            .size(20.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (checked) Black else White)
            .border(1.5.dp, if (checked) Black else LightGray, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Text("✓", color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Отметка со словом. Нажимается вся строка, а не квадратик в шесть точек. */
@Composable
fun CheckRow(
    text: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MarkBox(checked)
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            color = if (enabled) Black else DarkGray,
            fontSize = 13.sp, lineHeight = 18.sp
        )
    }
}

/**
 * Действие строкой внутри карточки.
 *
 * Готовая кнопка Material сюда не годится: у неё свои отступы по бокам,
 * и надпись уезжает правее того текста, под которым стоит. В карточке
 * это видно сразу — заголовок, объяснение и отметка выстроены по левому
 * краю, а действие одно сдвинуто вправо и висит в лишней пустоте.
 *
 * Размер один на все окна. Раньше в одном и том же окне встречались
 * три: «Принять» и «Отклонить» стояли рядом и были разной величины,
 * потому что у одной надписи размер написан, а у другой взят по
 * умолчанию.
 *
 * `strong` — для того из двух действий, которое человек выберет чаще.
 * Не цветом и не рамкой: в окне и так довольно всего.
 */
@Composable
fun RowAction(
    text: String,
    enabled: Boolean = true,
    strong: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            // Погашенное — приглушённый тёмный, а не светло-серый:
            // светло-серое читается на белом и пропадает на карточке,
            // а карточки здесь везде.
            color = if (!enabled) DarkGray.copy(alpha = .45f)
                    else if (strong) Black else DarkGray,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = if (strong) FontWeight.Medium else FontWeight.Normal
        )
    }
}

/**
 * Главная кнопка окна: во всю ширину и чёрным.
 *
 * Нажимается вся, а не буквы на ней. Раньше в двух окнах внутри чёрного
 * прямоугольника стояла обычная кнопка Material: чёрное было нарисовано,
 * а нажималась только середина — промах по краю не делал ничего.
 *
 * Растёт вместе со шрифтом, а не обрезает надпись: при крупном шрифте
 * «СИНХРОНИЗИРОВАТЬ» в кнопку фиксированной высоты не помещается.
 *
 * Модификатор снаружи нужен для рядов: там кнопка обязана тянуться
 * на высоту соседа. Сама по себе она этого не делает — вне ряда
 * `fillMaxHeight` растянул бы её на весь оставшийся экран.
 */
@Composable
fun WideButton(
    text: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) Black else LightGray)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, color = White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp, textAlign = TextAlign.Center
        )
    }
}

/**
 * Второстепенная кнопка: та же высота и форма, что у [WideButton],
 * но контуром. Для пары действий под главной кнопкой — «Особые
 * действия» и «Выйти»: они заметны как кнопки, но не спорят с главной.
 */
@Composable
fun GhostButton(
    text: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, if (enabled) Black else LightGray, RoundedCornerShape(12.dp))
            .background(White)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 10.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, color = if (enabled) Black else DarkGray.copy(alpha = .45f),
            fontSize = 12.sp, fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp, textAlign = TextAlign.Center
        )
    }
}

/** Два действия рядом. Промежуток задан здесь, а не пустотой в кнопке. */
@Composable
fun ActionRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) { content() }
}

package ru.styrk.app.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.styrk.app.ui.theme.Black
import ru.styrk.app.ui.theme.Gold
import ru.styrk.app.ui.theme.White

/**
 * Полоска «удалено — отменить», как в телеграме.
 *
 * Появляется снизу, живёт несколько секунд и уезжает. Пока она на
 * экране, запись из базы ещё не ушла: отмена возвращает её целиком,
 * со всеми подходами и отметками для сервера.
 *
 * Показывается один раз на всё приложение — в [ru.styrk.app.ui.main.MainScreen],
 * поверх нижней панели. Если бы каждая вкладка рисовала свою, полоска
 * пропадала бы при переходе на другую вкладку, а удаление всё равно
 * продолжало бы отсчёт — и отменить его стало бы нечем.
 *
 * @param text  что именно удалено
 * @param onUndo вернуть запись
 */
@Composable
fun UndoBar(
    text: String?,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Пока полоска уезжает, text уже равен null. Держим последнюю
    // надпись отдельно — иначе на выходе текст мигал бы пустотой.
    var last by remember { mutableStateOf("") }
    if (text != null) last = text

    AnimatedVisibility(
        visible = text != null,
        enter = slideInVertically(tween(220)) { it } + fadeIn(tween(220)),
        exit = slideOutVertically(tween(180)) { it } + fadeOut(tween(180)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .background(Black, RoundedCornerShape(14.dp))
                .padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(last, color = White, fontSize = 14.sp)
            Text(
                "ОТМЕНИТЬ",
                color = Gold,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable { onUndo() }
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}
package ru.styrk.app.ui.common

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt

/**
 * Отступ под клавиатуру — ровно на ту часть, которую она закрывает.
 *
 * Вместо imePadding(). Тот отступает на ВСЮ высоту клавиатуры,
 * считая от низа экрана, — и это верно, только если элемент стоит
 * у самого низа. На экране тренировки под списком стоит кнопка
 * «Завершить тренировку» (74 точки), и список отступал на клавиатуру
 * плюс эти 74 точки: кнопка пряталась под клавиатурой, а над
 * клавиатурой висела пустая полоса ровно её высоты.
 *
 * Здесь иначе: элемент меряет, сколько места от его низа до низа
 * окна уже занято чем-то другим, и отступает только на остаток.
 * Что бы ни лежало ниже — панель, кнопка, системная полоска, — край
 * содержимого ложится точно на край клавиатуры.
 *
 * Меряется внешняя рамка (до отступа), а она от отступа не меняется —
 * поэтому замер не гоняется сам за собой.
 */
@Composable
fun Modifier.keyboardGap(): Modifier {
    val density = LocalDensity.current
    val ime = WindowInsets.ime.getBottom(density)
    var below by remember { mutableStateOf(0) }
    val pad = (ime - below).coerceAtLeast(0)
    return this
        .onGloballyPositioned { c ->
            val rootHeight = c.findRootCoordinates().size.height
            val bottom = c.positionInRoot().y + c.size.height
            val b = (rootHeight - bottom).roundToInt().coerceAtLeast(0)
            if (b != below) below = b
        }
        .padding(bottom = with(density) { pad.toDp() })
}

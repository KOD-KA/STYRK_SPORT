package ru.styrk.app.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ru.styrk.app.ui.theme.Black
import ru.styrk.app.ui.theme.DarkGray
import ru.styrk.app.ui.theme.Gold
import ru.styrk.app.ui.theme.SoftGray
import ru.styrk.app.ui.theme.White
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Линейный график без сторонних библиотек (Compose Canvas).
 *
 * Устроен как в веб-версии, чтобы одно и то же приложение не вело себя
 * на телефоне и в браузере по-разному:
 *
 *  – сам график показывает последние [maxPoints] точек и НИЧЕГО, кроме
 *    графика: никаких кнопок поверх него;
 *  – касание точки показывает значение и дату (в браузере — наведение);
 *  – всю историю открывает отдельная кнопка [AllRecordsButton], которую
 *    ставит вызывающий экран — в шапке карточки, напротив заголовка.
 *    Там же она стоит и в браузере.
 *
 * @param values значения по порядку (старые → новые)
 * @param pointLabels подписи (обычно даты) для КАЖДОГО значения
 * @param unit единица для подсказки: «кг», «см»
 */
private const val FULL_LIMIT = 1000

/** Минимум ширины на точку в полном виде, иначе линия слипается. */
private const val POINT_DP = 22

@Composable
fun SimpleLineChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
    startLabel: String? = null,
    endLabel: String? = null,
    pointLabels: List<String>? = null,
    maxPoints: Int = 10,
    unit: String? = null
) {
    if (values.size < 2) {
        Text(
            "Нужно минимум две записи, чтобы построить график",
            color = DarkGray, fontSize = 12.sp
        )
        return
    }

    val shown = if (values.size > maxPoints) values.takeLast(maxPoints) else values
    val shownLabels = pointLabels?.let { if (it.size > maxPoints) it.takeLast(maxPoints) else it }
    val hidden = values.size - shown.size

    val textMeasurer = rememberTextMeasurer()
    val scale = remember(shown) { scaleOf(shown) }
    val chartLeft = axisWidth(scale, textMeasurer)
    val rightPad = with(LocalDensity.current) { RIGHT_PAD_DP.dp.toPx() }
    var selected by remember(shown) { mutableStateOf<Int?>(null) }

    Column(modifier = modifier) {

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .padding(top = 6.dp, bottom = 2.dp)
                // Касание — как наведение мышью в браузере: показывается
                // ближайшая точка. Попадать пальцем в кружок радиусом
                // три с половиной точки — занятие для снайпера.
                .pointerInput(shown) {
                    detectTapGestures { tap ->
                        val i = nearestIndex(
                            tap.x, size.width.toFloat(), chartLeft, rightPad, shown.size
                        )
                        selected = if (selected == i) null else i
                    }
                }
                // Провели пальцем — подсказка едет следом, как курсор.
                .pointerInput(shown) {
                    detectHorizontalDragGestures { change, _ ->
                        selected = nearestIndex(
                            change.position.x, size.width.toFloat(), chartLeft, rightPad, shown.size
                        )
                    }
                }
        ) {
            drawChart(
                shown, scale, chartLeft, textMeasurer,
                selected = selected,
                selectedLabel = selected?.let { shownLabels?.getOrNull(it) },
                unit = unit
            )
        }

        Spacer(Modifier.height(2.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                shownLabels?.firstOrNull() ?: startLabel ?: "",
                color = DarkGray, fontSize = 10.sp
            )
            Spacer(Modifier.weight(1f))
            if (hidden > 0) {
                Text(
                    "последние ${shown.size} из ${values.size}",
                    color = DarkGray, fontSize = 10.sp
                )
                Spacer(Modifier.weight(1f))
            }
            Text(
                shownLabels?.lastOrNull() ?: endLabel ?: "",
                color = DarkGray, fontSize = 10.sp
            )
        }
    }
}

/**
 * Кнопка «ВСЕ ЗАПИСИ» для шапки карточки — напротив заголовка, как
 * в браузере. Живёт здесь, а не на каждом экране отдельно, чтобы у всех
 * графиков она выглядела одинаково.
 */
@Composable
fun AllRecordsButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .border(1.dp, Black.copy(alpha = 0.30f), RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text("ВСЕ ЗАПИСИ", color = Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

// ═══════════════════ ПОЛНЫЙ ВИД ═══════════════════

/**
 * Вся история на весь экран, телефон поворачивается боком.
 * Показывается вызывающим экраном по кнопке [AllRecordsButton].
 */
@Composable
fun FullHistoryChart(
    values: List<Float>,
    labels: List<String>?,
    title: String,
    unit: String? = null,
    onClose: () -> Unit
) {
    if (values.size < 2) return

    val context = LocalContext.current

    // Поворачиваем телефон боком на время просмотра и возвращаем как было
    // при закрытии. Через requestedOrientation, а не подсказкой «поверните
    // телефон»: у большинства автоповорот выключен, и подсказка была бы
    // издевательством.
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        val previous = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = previous
        }
    }

    val full = if (values.size > FULL_LIMIT) values.takeLast(FULL_LIMIT) else values
    val fullLabels = labels?.let { if (it.size > FULL_LIMIT) it.takeLast(FULL_LIMIT) else it }

    val textMeasurer = rememberTextMeasurer()
    val scale = remember(full) { scaleOf(full) }
    val chartLeft = axisWidth(scale, textMeasurer)
    val rightPad = with(LocalDensity.current) { RIGHT_PAD_DP.dp.toPx() }
    var selected by remember(full) { mutableStateOf<Int?>(null) }
    val scroll = rememberScrollState()

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(White)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, color = Black, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "записей: ${full.size} · нажмите на точку",
                        color = DarkGray, fontSize = 11.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(SoftGray, RoundedCornerShape(18.dp))
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("✕", color = Black, fontSize = 15.sp)
                }
            }

            Spacer(Modifier.height(8.dp))

            // Ширину считаем снаружи прокрутки: внутри неё ширина
            // неограниченная, и «занять весь экран» там не выразить.
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val wide = maxOf(maxWidth, (full.size * POINT_DP).dp)
                Box(Modifier.fillMaxSize().horizontalScroll(scroll)) {
                    Canvas(
                        modifier = Modifier
                            .width(wide)
                            .fillMaxHeight()
                            .pointerInput(full) {
                                detectTapGestures { tap ->
                                    val i = nearestIndex(
                                        tap.x, size.width.toFloat(), chartLeft, rightPad, full.size
                                    )
                                    selected = if (selected == i) null else i
                                }
                            }
                    ) {
                        drawChart(
                            full, scale, chartLeft, textMeasurer,
                            selected = selected,
                            selectedLabel = selected?.let { fullLabels?.getOrNull(it) },
                            unit = unit
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fullLabels?.firstOrNull() ?: "", color = DarkGray, fontSize = 10.sp)
                Text(fullLabels?.lastOrNull() ?: "", color = DarkGray, fontSize = 10.sp)
            }
        }
    }
}

/** Activity из Context: она нужна, чтобы попросить поворот экрана. */
private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

// ═══════════════════ РАСЧЁТЫ ═══════════════════

/**
 * Поле справа, за которым линия не рисуется.
 *
 * Без него последняя точка ставилась ровно на краю холста и обрезалась
 * ровно наполовину — а выделенная, с обводкой, ещё заметнее. Крайняя
 * точка на графике веса это «сколько я вешу сейчас», её и смотрят чаще
 * остальных. Ширины хватает на обводку выделения целиком.
 */
private const val RIGHT_PAD_DP = 9

/** Ближайшая к пальцу точка. Отдельной функцией, потому что нужна
 *  и компактному виду, и полному, и обязана считать так же, как рисует. */
private fun nearestIndex(x: Float, width: Float, chartLeft: Float, rightPad: Float, count: Int): Int {
    if (count < 2) return 0
    val stepX = (width - chartLeft - rightPad) / (count - 1)
    if (stepX <= 0f) return 0
    return ((x - chartLeft) / stepX).roundToInt().coerceIn(0, count - 1)
}

/** Границы и шаг сетки — нужны и при рисовании, и при расчёте отступа. */
internal data class Scale(val low: Float, val high: Float, val step: Float, val lines: Int)

internal fun scaleOf(values: List<Float>): Scale {
    val minV = values.min()
    val maxV = values.max()

    // «Круглый» шаг сетки: 1, 2, 2.5, 5, 10, 20, 25, 50…
    val rawSpan = (maxV - minV).takeIf { it > 0f } ?: (abs(maxV).coerceAtLeast(1f) * 0.1f)
    val rawStep = rawSpan / 3f
    val magnitude = 10f.pow(floor(log10(rawStep.toDouble())).toFloat())
    val norm = rawStep / magnitude
    val niceNorm = when {
        norm <= 1f   -> 1f
        norm <= 2f   -> 2f
        norm <= 2.5f -> 2.5f
        norm <= 5f   -> 5f
        else         -> 10f
    }
    val step = niceNorm * magnitude
    val low = floor(minV / step) * step
    val high = ceil(maxV / step) * step
    val span = (high - low).takeIf { it > 0f } ?: step
    return Scale(low, high, step, (span / step).toInt() + 1)
}

internal val AXIS_STYLE = TextStyle(color = DarkGray, fontSize = 9.sp)

/** Отступ слева под подписи оси. По самой широкой подписи — иначе при
 *  отрицательных или длинных числах они наезжают на сетку. */
@Composable
internal fun axisWidth(scale: Scale, tm: TextMeasurer): Float {
    val a = tm.measure(SetFormat.num(scale.high), AXIS_STYLE).size.width
    val b = tm.measure(SetFormat.num(scale.low), AXIS_STYLE).size.width
    return with(LocalDensity.current) { maxOf(a, b) + 8.dp.toPx() }
}

// ═══════════════════ РИСОВАНИЕ ═══════════════════

private fun DrawScope.drawChart(
    values: List<Float>,
    scale: Scale,
    chartLeft: Float,
    textMeasurer: TextMeasurer,
    selected: Int? = null,
    selectedLabel: String? = null,
    unit: String? = null
) {
    if (values.size < 2) return

    val h = size.height
    val span = (scale.high - scale.low).takeIf { it > 0f } ?: scale.step
    val rightPad = RIGHT_PAD_DP.dp.toPx()
    val chartW = (size.width - chartLeft - rightPad).coerceAtLeast(1f)
    val stepX = chartW / (values.size - 1)

    fun yOf(v: Float): Float = h * (1f - (v - scale.low) / span)
    fun xOf(i: Int): Float = chartLeft + stepX * i

    // Сетка + подписи слева
    for (i in 0 until scale.lines) {
        val value = scale.high - scale.step * i
        val y = yOf(value)
        drawLine(
            color = DarkGray.copy(alpha = 0.16f),
            start = Offset(chartLeft, y),
            end = Offset(size.width, y),
            strokeWidth = 1.dp.toPx()
        )
        val measured = textMeasurer.measure(SetFormat.num(value), AXIS_STYLE)
        drawText(
            textLayoutResult = measured,
            topLeft = Offset(
                (chartLeft - 6.dp.toPx() - measured.size.width).coerceAtLeast(0f),
                (y - measured.size.height / 2f).coerceIn(0f, h - measured.size.height)
            )
        )
    }

    // Заливка под линией — как в браузере. Глаз читает не саму линию,
    // а фигуру под ней: с заливкой направление видно сразу, без
    // прослеживания кривой по точкам.
    //
    // Затемнение идёт сверху вниз и растворяется у нижнего края: сплошная
    // подложка спорила бы с подписями оси и с самой линией.
    val area = Path()
    area.moveTo(xOf(0), h)
    values.forEachIndexed { i, v -> area.lineTo(xOf(i), yOf(v)) }
    area.lineTo(xOf(values.size - 1), h)
    area.close()
    drawPath(
        area,
        brush = Brush.verticalGradient(
            colors = listOf(Black.copy(alpha = 0.10f), Black.copy(alpha = 0f)),
            startY = 0f,
            endY = h
        )
    )

    // Линия
    val path = Path()
    values.forEachIndexed { i, v ->
        if (i == 0) path.moveTo(xOf(i), yOf(v)) else path.lineTo(xOf(i), yOf(v))
    }
    drawPath(path, color = Black, style = Stroke(width = 2.dp.toPx()))

    // Точки
    values.forEachIndexed { i, v ->
        drawCircle(color = Black, radius = 3.5.dp.toPx(), center = Offset(xOf(i), yOf(v)))
    }

    // ── выбранная точка и подсказка ──
    val idx = selected ?: return
    if (idx !in values.indices) return
    val point = Offset(xOf(idx), yOf(values[idx]))

    // Пунктир от точки ВНИЗ, к оси. Раньше линия шла и вверх тоже —
    // получался луч сквозь точку, будто она к чему-то тянется наверху.
    // Наверху ничего нет: подпись даты внизу, и указывать нужно туда.
    drawLine(
        color = Gold,
        start = point,
        end = Offset(point.x, h),
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(
            floatArrayOf(4.dp.toPx(), 4.dp.toPx()), 0f
        )
    )
    drawCircle(color = White, radius = 6.dp.toPx(), center = point)
    drawCircle(color = Gold, radius = 6.dp.toPx(), center = point, style = Stroke(2.dp.toPx()))
    drawCircle(color = Black, radius = 3.dp.toPx(), center = point)

    val value = SetFormat.num(values[idx]) + (unit?.let { " $it" } ?: "")
    val text = listOfNotNull(value, selectedLabel).joinToString(" · ")
    val tip = textMeasurer.measure(text, TextStyle(color = White, fontSize = 12.sp))
    val padH = 10.dp.toPx()
    val padV = 6.dp.toPx()
    val boxW = tip.size.width + padH * 2
    val boxH = tip.size.height + padV * 2

    // Подсказка не должна вылезать за края — ни вбок, ни вверх. Иначе
    // у крайних точек (а их смотрят чаще всего) её просто не видно.
    val x = (point.x - boxW / 2).coerceIn(0f, (size.width - boxW).coerceAtLeast(0f))
    val above = point.y - boxH - 12.dp.toPx()
    val y = (if (above >= 0f) above else point.y + 12.dp.toPx())
        .coerceIn(0f, (h - boxH).coerceAtLeast(0f))

    drawRoundRect(
        color = Black,
        topLeft = Offset(x, y),
        size = Size(boxW, boxH),
        cornerRadius = CornerRadius(10.dp.toPx())
    )
    drawText(textLayoutResult = tip, topLeft = Offset(x + padH, y + padV))
}
package ru.styrk.app.ui.friends

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import ru.styrk.app.sync.SyncApi
import ru.styrk.app.sync.SyncPrefs
import ru.styrk.app.ui.common.AXIS_STYLE
import ru.styrk.app.ui.common.RowAction
import ru.styrk.app.ui.common.SetFormat
import ru.styrk.app.ui.common.axisWidth
import ru.styrk.app.ui.common.scaleOf
import ru.styrk.app.ui.theme.Black
import ru.styrk.app.ui.theme.DarkGray
import ru.styrk.app.ui.theme.Gold
import ru.styrk.app.ui.theme.LightGray
import ru.styrk.app.ui.theme.SoftGray
import ru.styrk.app.ui.theme.White

/**
 * Окно «Сравнить с другом».
 *
 * Обе колонки приходят с сервера, и своя тоже. Считать свою на телефоне
 * было бы естественнее — но тогда она выходила бы из своей базы, а чужая
 * из присланного на сервер. Разошлись бы они ровно в тот день, когда
 * человек не успел синхронизироваться, и объяснить это было бы нечем.
 *
 * Показываются только упражнения, которые есть у обоих: сравнение —
 * это две величины рядом, а строка с прочерком напротив ничего
 * не сравнивает.
 */
@Composable
fun CompareDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { SyncPrefs(context) }
    val api = remember { SyncApi(prefs) }

    var friends by remember { mutableStateOf<List<SyncApi.Friend>>(emptyList()) }
    var chosen by remember { mutableStateOf<String?>(null) }
    var mine by remember { mutableStateOf<SyncApi.Comparison?>(null) }
    var theirs by remember { mutableStateOf<SyncApi.Comparison?>(null) }
    var busy by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    /* Линии считает сервер — и свою тоже. Считать свою здесь было бы
       естественнее и хуже: две линии на одном поле обязаны быть
       посчитаны одинаково, а два разных счёта разойдутся на первом же
       спорном подходе. */
    var wser by remember { mutableStateOf<SyncApi.Series?>(null) }
    var ex by remember { mutableStateOf<String?>(null) }
    var exser by remember { mutableStateOf<SyncApi.Series?>(null) }

    LaunchedEffect(Unit) {
        when (val r = api.friends()) {
            is SyncApi.Result.Ok -> {
                friends = r.data.friends
                // Один друг — выбирать не из чего, показываем сразу.
                if (friends.size == 1) chosen = friends.first().handle
            }
            is SyncApi.Result.Fail -> message = r.message
        }
        busy = false
    }

    LaunchedEffect(chosen) {
        val h = chosen ?: return@LaunchedEffect
        busy = true
        message = null
        mine = null
        theirs = null
        when (val a = api.myStats()) {
            is SyncApi.Result.Ok -> mine = a.data
            is SyncApi.Result.Fail -> message = a.message
        }
        when (val b = api.compare(h)) {
            is SyncApi.Result.Ok -> theirs = b.data
            is SyncApi.Result.Fail -> message = b.message
        }
        busy = false
        // Линию тянем следом, а не вместе: числа человек читает сразу,
        // а график подождёт полсекунды и не задержит их.
        wser = null
        ex = null
        exser = null
        when (val c = api.series(h)) {
            is SyncApi.Result.Ok -> wser = c.data
            is SyncApi.Result.Fail -> Unit
        }
    }

    LaunchedEffect(chosen, ex) {
        val h = chosen ?: return@LaunchedEffect
        val name = ex ?: return@LaunchedEffect
        exser = null
        when (val r = api.series(h, name)) {
            is SyncApi.Result.Ok -> if (ex == name) exser = r.data
            is SyncApi.Result.Fail -> message = r.message
        }
    }

    val maxBody = (LocalConfiguration.current.screenHeightDp * 0.72).dp

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(White)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Black)
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Text(
                    "СРАВНИТЬ", color = White, fontSize = 13.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 3.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    chosen?.let { "Вы и @$it" }
                        ?: "Видно только то, что друг открыл вам сам.",
                    color = White.copy(.62f), fontSize = 12.sp, lineHeight = 17.sp
                )
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxBody)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {

                if (friends.isEmpty() && !busy) {
                    Text(
                        "Друзей пока нет. Профиль → Друзья — там можно позвать по нику.",
                        color = DarkGray, fontSize = 12.sp, lineHeight = 17.sp
                    )
                } else if (chosen == null) {
                    Text("С КЕМ", color = DarkGray, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Spacer(Modifier.height(8.dp))
                    friends.forEach { f ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { chosen = f.handle }
                                .background(SoftGray, RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("@${f.handle}", color = Black, fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    if (f.theirShare.nothing) "пока ничего не открыл"
                                    else "открыл: " + listOfNotNull(
                                        "рекорды".takeIf { f.theirShare.records },
                                        "объём".takeIf { f.theirShare.volume }
                                    ).joinToString(" и "),
                                    color = DarkGray, fontSize = 11.sp
                                )
                            }
                            Text("›", color = DarkGray, fontSize = 18.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                } else {
                    val them = theirs
                    if (friends.size > 1) {
                        RowAction("← выбрать другого", enabled = !busy) { chosen = null }
                        Spacer(Modifier.height(4.dp))
                    }

                    if (busy) {
                        Text("Считаем…", color = DarkGray, fontSize = 13.sp)
                    } else if (them == null) {
                        Text(
                            message ?: "Не удалось получить данные",
                            color = Black, fontSize = 13.sp, lineHeight = 18.sp
                        )
                    } else if (them.allowed.nothing) {
                        Text(
                            "@${them.handle} пока ничего вам не открыл.",
                            color = Black, fontSize = 13.sp, lineHeight = 18.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Это его выбор, и попросить об этом можно только словами — " +
                                    "изнутри приложения такой просьбы нет.",
                            color = DarkGray, fontSize = 12.sp, lineHeight = 17.sp
                        )
                    } else {

                        // ── Объём за неделю ──
                        val myVol = mine?.volume
                        val theirVol = them.volume
                        if (theirVol != null && myVol != null) {
                            Text("ОБЪЁМ ЗА НЕДЕЛЮ", color = DarkGray, fontSize = 10.sp,
                                fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                            Spacer(Modifier.height(8.dp))
                            /* Ряд меряется по высокой колонке, обе тянутся
                               до общей: длинный ник переносится на две
                               строки, и без этого одна карточка выходит
                               выше другой. */
                            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                                VolumeCell("вы", myVol, myVol.kg >= theirVol.kg,
                                    Modifier.weight(1f).fillMaxHeight())
                                Spacer(Modifier.width(10.dp))
                                VolumeCell("@${them.handle}", theirVol,
                                    theirVol.kg >= myVol.kg,
                                    Modifier.weight(1f).fillMaxHeight())
                            }
                            Spacer(Modifier.height(18.dp))
                        } else if (!them.allowed.volume) {
                            Text("Объём он вам не открыл", color = DarkGray, fontSize = 12.sp)
                            Spacer(Modifier.height(14.dp))
                        }

                        /* ── Линии ──

                           Числа отвечают «кто сильнее сегодня», линия —
                           «кто растёт». Второй вопрос и есть тот, ради
                           которого меряются, но начинать с него нельзя:
                           сначала человек ищет знакомое. */
                        Text("ВЕС НА ВЕСАХ", color = DarkGray, fontSize = 10.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        Spacer(Modifier.height(8.dp))
                        val w = wser
                        if (w == null) {
                            Text("Считаем…", color = DarkGray, fontSize = 12.sp)
                        } else if (w.mine.isEmpty() && w.theirs.orEmpty().isEmpty()) {
                            Text(
                                if (w.allowed) "Ни вы, ни он себя пока не взвешивали."
                                else "Он график веса не открыл, а вы себя пока не взвешивали.",
                                color = DarkGray, fontSize = 12.sp, lineHeight = 17.sp
                            )
                        } else {
                            TwoLines(w.mine, w.theirs, "кг", them.handle)
                            if (!w.allowed) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Он график веса не открыл — здесь только ваша линия. " +
                                            "Это отдельная галочка у него в друзьях.",
                                    color = DarkGray, fontSize = 11.sp, lineHeight = 15.sp
                                )
                            }
                        }
                        Spacer(Modifier.height(18.dp))

                        Text("ПРОГРЕСС В УПРАЖНЕНИИ", color = DarkGray, fontSize = 10.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        Spacer(Modifier.height(8.dp))
                        if (!them.allowed.records) {
                            Text(
                                "Тоже за галочкой «Рекорды»: график — это те же рекорды, " +
                                        "только по неделям.",
                                color = DarkGray, fontSize = 12.sp, lineHeight = 17.sp
                            )
                        } else {
                            val myNames = mine?.records.orEmpty().map { it.name }.toSet()
                            val bothNames = them.records.orEmpty()
                                .map { it.name }.filter { it in myNames }
                            if (bothNames.isEmpty()) {
                                Text("Общих упражнений пока нет.",
                                    color = DarkGray, fontSize = 12.sp)
                            } else {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    bothNames.forEach { name ->
                                        ExPill(name, name == ex) {
                                            ex = if (ex == name) null else name
                                        }
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                                val es = exser
                                if (ex == null) {
                                    Text(
                                        "Выберите упражнение: покажем лучшее за каждую " +
                                                "неделю за последние полгода.",
                                        color = DarkGray, fontSize = 12.sp, lineHeight = 17.sp
                                    )
                                } else if (es == null) {
                                    Text("Считаем…", color = DarkGray, fontSize = 12.sp)
                                } else {
                                    TwoLines(es.mine, es.theirs, es.unit, them.handle)
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "Лучшее за неделю" +
                                                (if (es.unit.isNotBlank()) ", ${es.unit}" else "") +
                                                "." +
                                                (if (es.mixed)
                                                    " Осторожно: вы считаете это упражнение " +
                                                            "по-разному — линии на одном поле " +
                                                            "сравнивать нельзя."
                                                else ""),
                                        color = DarkGray, fontSize = 11.sp, lineHeight = 15.sp
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(18.dp))

                        // ── Рекорды ──
                        if (!them.allowed.records) {
                            Text("Рекорды он вам не открыл", color = DarkGray, fontSize = 12.sp)
                        } else {
                            val myRec = mine?.records.orEmpty().associateBy { it.name }
                            val common = them.records.orEmpty()
                                .filter { myRec.containsKey(it.name) }

                            Text("РЕКОРДЫ", color = DarkGray, fontSize = 10.sp,
                                fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                            Spacer(Modifier.height(8.dp))

                            if (common.isEmpty()) {
                                Text(
                                    "Общих упражнений пока нет. Сравнивать можно только то, " +
                                            "что делали оба.",
                                    color = DarkGray, fontSize = 12.sp, lineHeight = 17.sp
                                )
                            } else {
                                Row(Modifier.fillMaxWidth()) {
                                    Text("", Modifier.weight(1.4f))
                                    Head("вы", Modifier.weight(1f))
                                    Head("@${them.handle}", Modifier.weight(1f))
                                }
                                Spacer(Modifier.height(4.dp))
                                common.forEach { theirR ->
                                    val myR = myRec[theirR.name] ?: return@forEach
                                    CompareRow(theirR.name, shownOf(myR), shownOf(theirR))
                                }

                                val theirNames = them.records.orEmpty().map { it.name }.toSet()
                                val onlyMine = myRec.keys.count { it !in theirNames }
                                val onlyTheirs = theirNames.count { it !in myRec.keys }
                                if (onlyMine > 0 || onlyTheirs > 0) {
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        "Ещё " + listOfNotNull(
                                            "$onlyMine только у вас".takeIf { onlyMine > 0 },
                                            "$onlyTheirs только у него".takeIf { onlyTheirs > 0 }
                                        ).joinToString(", ") + " — их не с чем сравнить.",
                                        color = DarkGray, fontSize = 11.sp, lineHeight = 15.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Отказ показываем только когда он не сказан выше: иначе
                // одна и та же строка выходит дважды подряд.
                if (theirs != null) {
                    message?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it, color = Black, fontSize = 12.sp, lineHeight = 17.sp)
                    }
                }

                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    RowAction("Закрыть", strong = true) { onDismiss() }
                }
            }
        }
    }
}

/** Одно значение рекорда словами плюс число, по которому видно, кто выше. */
private data class Shown(val text: String, val score: Float)

private fun shownOf(r: SyncApi.FriendRecord): Shown = when (r.measureType) {
    "REPS" -> Shown("${r.maxReps} повт.", r.maxReps.toFloat())
    "TIME" -> Shown(SetFormat.time(r.bestSeconds ?: 0), (r.bestSeconds ?: 0).toFloat())
    "DISTANCE" -> Shown(SetFormat.num(r.bestDistanceKm ?: 0f) + " км", r.bestDistanceKm ?: 0f)
    // Упражнение со своим весом: тип «вес × повторы», но веса нет.
    // Тогда меряемся повторами — как и на своём экране рекордов.
    else ->
        if (r.maxWeight > 0f) Shown("${SetFormat.num(r.maxWeight)} × ${r.maxWeightReps}", r.maxWeight)
        else Shown("${r.maxReps} повт.", r.maxReps.toFloat())
}

@Composable
private fun Head(text: String, modifier: Modifier) {
    Text(
        text, modifier = modifier, color = DarkGray, fontSize = 10.sp,
        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
    )
}

/**
 * Строка сравнения. Впереди идущий выделен, но не подписан «победитель»:
 * дневник — не соревнование, и человек сам решает, что для него значит
 * эта разница.
 */
@Composable
private fun CompareRow(name: String, mine: Shown, theirs: Shown) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, Modifier.weight(1.4f), color = Black, fontSize = 12.sp, lineHeight = 16.sp)
        Cell(mine.text, mine.score >= theirs.score, Modifier.weight(1f))
        Cell(theirs.text, theirs.score >= mine.score, Modifier.weight(1f))
    }
}

@Composable
private fun Cell(text: String, ahead: Boolean, modifier: Modifier) {
    Text(
        text, modifier = modifier,
        color = if (ahead) Black else DarkGray,
        fontSize = 13.sp,
        fontWeight = if (ahead) FontWeight.Bold else FontWeight.Normal,
        textAlign = TextAlign.Center
    )
}

/**
 * Поле сравнения: две линии.
 *
 * Устроено как графики в дневнике, и это главное. Сетка с подписями
 * значений слева, мягкая заливка под своей линией, кружки по краям,
 * крайние даты снизу. Два разных графика в одном приложении — это два
 * разных приложения, и человек чувствует это раньше, чем понимает.
 *
 * Своя линия чёрная и с заливкой, чужая золотая и без: две заливки
 * на одном поле превращают его в грязь.
 *
 * Ось идёт по времени, а не по номеру точки. У двоих разное число
 * записей, и по номерам линии разъехались бы: у одного пять точек
 * за месяц, у другого двадцать.
 */
@Composable
private fun TwoLines(
    mine: List<SyncApi.Dot>,
    theirs: List<SyncApi.Dot>?,
    unit: String,
    handle: String
) {
    val all = mine + theirs.orEmpty()
    if (all.size < 2) {
        Text(
            "Нужна хотя бы вторая запись — по одной точке линия не строится.",
            color = DarkGray, fontSize = 12.sp, lineHeight = 17.sp
        )
        return
    }

    val tm = rememberTextMeasurer()
    val scale = remember(all) { scaleOf(all.map { it.v }) }
    val left = axisWidth(scale, tm)
    val t0 = all.minOf { it.d }
    val t1 = all.maxOf { it.d }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(White)
            .border(1.dp, LightGray, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        // Подпись сверху, а не числа на точках: четыре числа на поле —
        // и график снова становится таблицей.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Legend(Black, "вы", mine.lastOrNull()?.v, unit)
            if (theirs != null) {
                Spacer(Modifier.width(16.dp))
                Legend(Gold, "@$handle", theirs.lastOrNull()?.v, unit)
            }
        }
        Spacer(Modifier.height(8.dp))

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
        ) {
            val h = size.height
            val bottom = 16.dp.toPx()          // место под даты
            /* Отступ сверху — под верхнюю подпись шкалы.

               Без него верхняя направляющая ложится ровно на край,
               и её число обрезается пополам: половина цифры за краем,
               половина под ним. Заметно сразу, а причина не видна
               никак — число просто «неправильной» высоты. */
            val top = 8.dp.toPx()
            val span = (scale.high - scale.low).takeIf { it > 0f } ?: scale.step
            val right = 9.dp.toPx()
            val chartW = (size.width - left - right).coerceAtLeast(1f)

            fun yOf(v: Float) = top + (h - top - bottom) * (1f - (v - scale.low) / span)
            fun xOf(d: Long) =
                if (t1 == t0) left + chartW / 2f
                else left + chartW * (d - t0).toFloat() / (t1 - t0).toFloat()

            // Сетка и подписи слева — те же, что в дневнике.
            for (i in 0 until scale.lines) {
                val value = scale.high - scale.step * i
                val y = yOf(value)
                drawLine(
                    color = DarkGray.copy(alpha = 0.16f),
                    start = Offset(left, y), end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx()
                )
                val measured = tm.measure(SetFormat.num(value), AXIS_STYLE)
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(
                        (left - 6.dp.toPx() - measured.size.width).coerceAtLeast(0f),
                        // Подпись стоит по центру своей линии и всегда
                        // целиком: ни выше края, ни ниже поля.
                        (y - measured.size.height / 2f)
                            .coerceIn(0f, (h - measured.size.height).coerceAtLeast(0f))
                    )
                )
            }

            fun line(pts: List<SyncApi.Dot>, color: Color, fill: Boolean) {
                if (pts.isEmpty()) return
                if (pts.size > 1) {
                    if (fill) {
                        val area = Path()
                        area.moveTo(xOf(pts.first().d), h - bottom)
                        pts.forEach { area.lineTo(xOf(it.d), yOf(it.v)) }
                        area.lineTo(xOf(pts.last().d), h - bottom)
                        area.close()
                        drawPath(
                            area,
                            brush = Brush.verticalGradient(
                                colors = listOf(color.copy(alpha = 0.10f), color.copy(alpha = 0f)),
                                startY = 0f, endY = h - bottom
                            )
                        )
                    }
                    val path = Path()
                    pts.forEachIndexed { i, p ->
                        if (i == 0) path.moveTo(xOf(p.d), yOf(p.v))
                        else path.lineTo(xOf(p.d), yOf(p.v))
                    }
                    drawPath(
                        path, color,
                        style = Stroke(
                            width = 2.dp.toPx(),
                            cap = StrokeCap.Round, join = StrokeJoin.Round
                        )
                    )
                }
                // Кружки только по краям: два десятка точек на двух линиях
                // сливаются в рябь и спорят друг с другом.
                listOfNotNull(pts.firstOrNull(), pts.lastOrNull()).forEach { p ->
                    drawCircle(White, 3.4.dp.toPx(), Offset(xOf(p.d), yOf(p.v)))
                    drawCircle(
                        color, 3.4.dp.toPx(), Offset(xOf(p.d), yOf(p.v)),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }

            line(mine, Black, fill = true)
            theirs?.let { line(it, Gold, fill = false) }
        }

        Row(Modifier.fillMaxWidth()) {
            Text(dayWord(t0), color = DarkGray, fontSize = 9.sp, modifier = Modifier.weight(1f))
            Text(dayWord(t1), color = DarkGray, fontSize = 9.sp)
        }
    }
}

@Composable
private fun Legend(color: Color, who: String, last: Float?, unit: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).background(color, RoundedCornerShape(3.dp)))
        Spacer(Modifier.width(6.dp))
        Text(
            who + (last?.let {
                " " + numWord(it) + (if (unit.isNotBlank()) " $unit" else "")
            } ?: ""),
            color = DarkGray, fontSize = 11.sp
        )
    }
}

/** «78» или «71,5» — лишний нолик после запятой ничего не сообщает. */
private fun numWord(v: Float): String =
    if (v == v.toLong().toFloat()) v.toLong().toString()
    else String.format(java.util.Locale.forLanguageTag("ru"), "%.1f", v)

private val DAY_SHORT: java.time.format.DateTimeFormatter =
    java.time.format.DateTimeFormatter.ofPattern("dd.MM")

private fun dayWord(day: Long): String =
    runCatching { java.time.LocalDate.ofEpochDay(day).format(DAY_SHORT) }.getOrDefault("")

/** Название упражнения кнопкой: выпадающий список на телефоне неудобен. */
@Composable
private fun ExPill(name: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (on) Black else White)
            .border(1.dp, if (on) Black else LightGray, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Text(name, color = if (on) White else Black, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun VolumeCell(
    who: String,
    v: SyncApi.Volume,
    ahead: Boolean,
    modifier: Modifier
) {
    /* Отстающая колонка — тоже карточка, только с обводкой вместо
       заливки. Белая без обводки на белом окне превращалась в текст
       без карточки: одна колонка есть, другой будто нет. */
    Column(
        modifier
            .background(if (ahead) SoftGray else White, RoundedCornerShape(14.dp))
            .border(
                1.dp,
                if (ahead) Color.Transparent else LightGray,
                RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(who, color = DarkGray, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            if (v.kg >= 1000) "%.1f т".format(v.kg / 1000.0) else "${v.kg} кг",
            color = if (ahead) Black else DarkGray,
            fontSize = 19.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "${v.workouts} трен. · ${v.sets} подх.",
            color = DarkGray, fontSize = 11.sp, textAlign = TextAlign.Center
        )
    }
}

/** Полоска «Сравнить с другом» над списком рекордов. */
@Composable
fun CompareStrip(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .background(SoftGray, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("⚖", color = Gold, fontSize = 16.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Сравнить с другом", color = Black, fontSize = 14.sp,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text("Рекорды и объём рядом", color = DarkGray, fontSize = 11.sp)
        }
        Text("›", color = DarkGray, fontSize = 18.sp)
    }
}

package ru.styrk.app.ui.records

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import ru.styrk.app.sync.SyncApi
import ru.styrk.app.sync.SyncPrefs
import ru.styrk.app.ui.common.SetFormat
import ru.styrk.app.ui.theme.Black
import ru.styrk.app.ui.theme.DarkGray
import ru.styrk.app.ui.theme.Gold
import ru.styrk.app.ui.theme.LightGray
import ru.styrk.app.ui.theme.SoftGray
import ru.styrk.app.ui.theme.White
import kotlin.math.roundToInt

/**
 * ДОСКА ДРУЗЕЙ — то же, что вкладка «Против всех» в мини-приложении.
 *
 * Зачем отдельной вкладкой, а не окном «Сравнить». Окно открывают,
 * когда уже решили сравниться; вкладку видят каждый раз, открывая
 * рекорды. Разница та же, что между «посмотреть счёт» и «жить
 * с турнирной таблицей на стене».
 *
 * ВСЕ числа здесь — и свои тоже — приходят с сервера. Считать свою
 * строку на телефоне было бы естественнее и хуже: своё посчиталось бы
 * из здешней базы, чужое из серверной, и разошлись бы они ровно в тот
 * день, когда человек не успел синхронизироваться. Лучше одинаково
 * запоздалые числа, чем два разных счёта в одной таблице.
 *
 * Разрядной лестницы («где я по нормативам») здесь нет — она живёт
 * в дневнике и на телефон пока не перенесена. Эта вкладка про людей,
 * а не про таблицы федерации.
 */
@Composable
fun FriendsBoardScreen() {

    val context = LocalContext.current
    val prefs = remember { SyncPrefs(context) }
    val api = remember { SyncApi(prefs) }

    var busy by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var mine by remember { mutableStateOf<SyncApi.Comparison?>(null) }
    var others by remember { mutableStateOf<List<SyncApi.Comparison>>(emptyList()) }
    var friendCount by remember { mutableStateOf(0) }
    /** false — сравниваем суммы, true — сумму на килограмм веса тела. */
    var relative by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }
    var showCompare by remember { mutableStateOf(false) }

    LaunchedEffect(reload) {
        if (!prefs.isSignedIn) { busy = false; return@LaunchedEffect }
        busy = true
        message = null
        when (val fr = api.friends()) {
            is SyncApi.Result.Fail -> { message = fr.message; busy = false; return@LaunchedEffect }
            is SyncApi.Result.Ok -> {
                val list = fr.data.friends
                friendCount = list.size
                /* Свои числа и чужие — одним заходом. Друзей у человека
                   единицы, и спрашивать их по очереди значило бы ждать
                   столько же раз подряд. */
                coroutineScope {
                    val meJob = async { api.myStats() }
                    val jobs = list.map { f -> async { api.compare(f.handle) } }
                    val meRes = meJob.await()
                    if (meRes is SyncApi.Result.Ok) mine = meRes.data
                    // Друг, который не ответил, просто не попадёт в таблицу:
                    // показывать «не смогли спросить @васю» незачем, а ронять
                    // из-за него всю доску — тем более.
                    others = jobs.awaitAll().mapNotNull { r ->
                        if (r is SyncApi.Result.Ok) r.data else null
                    }
                }
            }
        }
        busy = false
    }

    // ── состояния, в которых показывать нечего ──────────────────────
    if (!prefs.isSignedIn) {
        Empty(
            "Нужен аккаунт",
            "Сравнение живёт на сервере: без входа сравнивать не с кем. " +
                "Профиль → Аккаунт."
        )
        return
    }
    if (busy && mine == null && others.isEmpty()) {
        Empty("Спрашиваем сервер…", null)
        return
    }
    val failed = message
    if (failed != null) {
        Empty(failed, null, onRetry = { reload++ })
        return
    }
    if (friendCount == 0) {
        Empty(
            "Друзей пока нет",
            "Профиль → Друзья: позвать можно по нику. Видно при этом только то, " +
                "что человек откроет вам сам."
        )
        return
    }

    val openRecords = others.filter { it.allowed.records && it.records != null }
    val openVolume = others.filter { it.allowed.volume && it.volume != null }
    val shut = friendCount - (openRecords.map { it.handle } + openVolume.map { it.handle })
        .distinct().size

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {

        // ═══ КТО СИЛЬНЕЕ ═══
        if (openRecords.isNotEmpty()) {
            val rows = buildList<Strength> {
                add(strengthRow("вы", true, mine?.records))
                openRecords.forEach { add(strengthRow("@" + it.handle, false, it.records)) }
            }.filter { it.total > 0f }
                .sortedByDescending { if (relative) it.perKg else it.total }

            item { BoardLabel("КТО СИЛЬНЕЕ") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("По сумме", !relative) { relative = false }
                    Pill("На килограмм веса", relative) { relative = true }
                }
            }
            if (rows.isEmpty()) {
                item { Hint("Ни у кого ещё нет ни одного из трёх движений.") }
            } else {
                val top = (if (relative) rows.first().perKg else rows.first().total)
                    .coerceAtLeast(0.001f)
                item { TableHead(if (relative) "на кг веса" else "сумма") }
                rows.forEachIndexed { i, r ->
                    item {
                        BoardRow(
                            place = i + 1,
                            who = r.who,
                            me = r.me,
                            note = if (r.full) null else "неполная",
                            value = if (relative) {
                                if (r.perKg > 0f) ratio(r.perKg) + " ×" else "—"
                            } else SetFormat.num(r.total) + " кг",
                            second = r.bodyWeight?.let { SetFormat.num(it) } ?: "—",
                            share = (if (relative) r.perKg else r.total) / top
                        )
                    }
                }
            }
            /* Вас в таблице нет — и это надо сказать словами.

               Раньше строка «вы» просто пропадала, если у человека нет
               ни приседа, ни жима лёжа, ни становой. Выглядело так,
               будто дневник его потерял, хотя рекордов у него десятки. */
            if (rows.isNotEmpty() && rows.none { it.me }) {
                item {
                    Hint(
                        "Вас в этой таблице нет: сумма считается по приседу со штангой, " +
                            "жиму штанги лёжа и становой, а их в ваших рекордах пока нет."
                    )
                }
            }
            item {
                Hint(
                    "Сумма — присед плюс жим лёжа плюс становая, оценка одноповторного " +
                        "максимума по лучшим подходам. У кого сделаны не все три, сумма " +
                        "помечена неполной: сравнивать её с полной нельзя. «На килограмм " +
                        "веса» уравнивает людей разного размера грубее, чем очки IPF GL, " +
                        "зато понятно, из чего считается."
                )
            }
        }

        // ═══ НЕДЕЛЯ ═══
        val myVolume = mine?.volume
        if (openVolume.isNotEmpty() && myVolume != null) {
            val rows = (listOf(Triple("вы", true, myVolume)) +
                openVolume.mapNotNull { c -> c.volume?.let { Triple("@" + c.handle, false, it) } })
                .sortedByDescending { it.third.kg }
            val top = rows.first().third.kg.coerceAtLeast(1L)

            item { Spacer(Modifier.height(6.dp)) }
            item { BoardLabel("НЕДЕЛЯ") }
            item { TableHead("объём") }
            rows.forEachIndexed { i, (who, me, v) ->
                item {
                    BoardRow(
                        place = i + 1,
                        who = who,
                        me = me,
                        note = null,
                        value = bigVolume(v.kg),
                        second = "${v.workouts} трен.",
                        share = v.kg.toFloat() / top.toFloat()
                    )
                }
            }
            item {
                Hint(
                    "Поднятый объём за последние семь дней. Объём — не сила: его легко " +
                        "набрать лёгким весом. Но пропущенную неделю он показывает честнее " +
                        "любого рекорда."
                )
            }
        }

        // ═══ ПО УПРАЖНЕНИЯМ ═══
        val board = leaderBoard(mine?.records, openRecords)
        if (board.common > 0) {
            item { Spacer(Modifier.height(6.dp)) }
            item { BoardLabel("ПО УПРАЖНЕНИЯМ") }
            item { Hero(board.ahead.size, board.common) }
            if (board.ahead.isNotEmpty()) {
                item { Hint("Где вы впереди — сверху самый крупный отрыв.") }
                for (c in board.ahead.take(6)) item { GapRow(c, ahead = true) }
            }
            if (board.chase.isNotEmpty()) {
                item { Hint("Где вас обходят — сверху те, до кого рукой подать.") }
                for (c in board.chase.take(6)) item { GapRow(c, ahead = false) }
            } else {
                item { Hint("В каждом общем упражнении вы впереди.") }
            }
            if (board.ahead.size > 6 || board.chase.size > 6) {
                item { Hint("Показаны по шесть с каждой стороны из ${board.common}.") }
            }
        }

        // ═══ ХВОСТ ═══
        item { Spacer(Modifier.height(6.dp)) }
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Black)
                    .clickable { showCompare = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Column {
                    Text("Один на один", color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Графики роста и веса рядом, по одному другу",
                        color = White.copy(alpha = 0.55f), fontSize = 12.sp
                    )
                }
                Text("→", color = White, fontSize = 18.sp, modifier = Modifier.align(Alignment.CenterEnd))
            }
        }
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SoftGray)
                    .clickable(enabled = !busy) { reload++ }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (busy) "Обновляем…" else "Обновить",
                    color = if (busy) DarkGray else Black,
                    fontSize = 13.sp, fontWeight = FontWeight.Medium
                )
            }
        }
        if (shut > 0) {
            item {
                Hint(
                    "$shut ${plural(shut, "друг", "друга", "друзей")} ничего не " +
                        (if (shut == 1) "открыл" else "открыли") + " — их здесь нет. " +
                        "Это их выбор, и попросить об этом можно только словами."
                )
            }
        }
    }

    if (showCompare) {
        ru.styrk.app.ui.friends.CompareDialog(onDismiss = { showCompare = false })
    }
}

// ═══════════════════════════════════════════════════════════════════
//  СЧЁТ
// ═══════════════════════════════════════════════════════════════════

/**
 * Три движения, из которых складывается сумма троеборья.
 * Имена ровно те же, что в справочнике упражнений: сумма считается
 * по названию, и «Жим лёжа» вместо «Жим штанги лёжа» её обнулит.
 */
private val LIFTS = listOf(
    "Приседания со штангой",
    "Жим штанги лёжа",
    "Становая тяга"
)

/**
 * Оценка одноповторного максимума — так же, как в «Вашем разряде»
 * (Standards.oneRM): формула Бжицки, не больше десяти повторов.
 *
 * Считается во Float, а не в Double, и это не мелочь: дневник в браузере
 * считает ровно так же (там для этого нарочно зажат Math.fround).
 * Человек видит два экрана одного приложения и вправе ждать одинаковых
 * чисел, а не «142.4 здесь против 142.5 там».
 */
private fun oneRepMax(weight: Float, reps: Int): Float =
    ru.styrk.app.data.model.Standards.oneRM(weight, reps)

private data class Strength(
    val who: String,
    val me: Boolean,
    val total: Float,
    /** true — сделаны все три движения. Неполную сумму сравнивать с полной нельзя. */
    val full: Boolean,
    val bodyWeight: Float?
) {
    val perKg: Float get() = if (bodyWeight != null && bodyWeight > 0f) total / bodyWeight else 0f
}

private fun strengthRow(who: String, me: Boolean, records: List<SyncApi.FriendRecord>?): Strength {
    val by = (records ?: emptyList()).associateBy { it.name }
    var total = 0f
    var full = true
    LIFTS.forEach { name ->
        val r = by[name]
        if (r != null && r.maxWeight > 0f) total += oneRepMax(r.maxWeight, r.maxWeightReps)
        else full = false
    }
    // Вес тела берём из самого свежего рекорда: там он записан на день,
    // когда человек этот рекорд поставил.
    val bw = (records ?: emptyList())
        .filter { (it.athleteWeight ?: 0f) > 0f }
        .maxByOrNull { it.recordDate ?: 0L }
        ?.athleteWeight
    return Strength(who, me, (total * 10f).roundToInt() / 10f, full, bw)
}

/** Одно значение рекорда словами плюс число, по которому видно, кто выше. */
private data class Shown(val text: String, val score: Float)

private fun shownOf(r: SyncApi.FriendRecord): Shown = when (r.measureType) {
    "REPS" -> Shown("${r.maxReps} повт.", r.maxReps.toFloat())
    "TIME" -> Shown(SetFormat.time(r.bestSeconds ?: 0), (r.bestSeconds ?: 0).toFloat())
    "DISTANCE" -> Shown(SetFormat.num(r.bestDistanceKm ?: 0f) + " км", r.bestDistanceKm ?: 0f)
    else ->
        if (r.maxWeight > 0f) Shown("${SetFormat.num(r.maxWeight)} × ${r.maxWeightReps}", r.maxWeight)
        else Shown("${r.maxReps} повт.", r.maxReps.toFloat())
}

private data class Gap(
    val name: String,
    val who: String,
    val mine: String,
    val theirs: String,
    val gap: Float,
    /** true — разница измеряется в килограммах и её можно назвать числом. */
    val kg: Boolean
)

private data class Board(val common: Int, val ahead: List<Gap>, val chase: List<Gap>)

/**
 * В каких упражнениях вы впереди, а где догоняете.
 *
 * Раньше в приложении не считалось ни то ни другое. В дневнике сначала
 * считалось и то и другое, а показывалось только второе — список тех,
 * кто вас обходит. Дневник, который показывает человеку исключительно
 * его отставания, ведёт себя как придирчивый тренер, а не как дневник.
 */
private fun leaderBoard(
    myRecords: List<SyncApi.FriendRecord>?,
    others: List<SyncApi.Comparison>
): Board {
    val mine = (myRecords ?: emptyList()).associateBy { it.name }
    var common = 0
    val ahead = ArrayList<Gap>()
    val chase = ArrayList<Gap>()
    mine.forEach { (name, myRec) ->
        val a = shownOf(myRec)
        var best: Pair<Shown, SyncApi.Comparison>? = null
        others.forEach { f ->
            val r = f.records?.firstOrNull { it.name == name } ?: return@forEach
            val b = shownOf(r)
            if (best == null || b.score > best!!.first.score) best = b to f
        }
        val bestPair = best ?: return@forEach
        common++
        val theirRec = bestPair.second.records?.firstOrNull { it.name == name }
        val kg = (theirRec?.measureType ?: "WEIGHT_REPS") == "WEIGHT_REPS" &&
            (theirRec?.maxWeight ?: 0f) > 0f
        val row = Gap(
            name = name, who = bestPair.second.handle,
            mine = a.text, theirs = bestPair.first.text,
            gap = kotlin.math.abs(a.score - bestPair.first.score), kg = kg
        )
        if (a.score >= bestPair.first.score) ahead.add(row) else chase.add(row)
    }
    // Догонять — с ближайшего; радоваться — с самого крупного отрыва.
    return Board(common, ahead.sortedByDescending { it.gap }, chase.sortedBy { it.gap })
}

/**
 * Крупный объём словами.
 *
 * Тонны вместо пятизначных чисел: «18 т» читается целиком и сразу
 * говорит, много это или мало, а «18 420 кг» читается по слогам и ни
 * с чем не сравнивается на глаз. Порог — тысяча килограммов и ни граммом
 * ниже: «0,9 т» хуже, чем «900 кг».
 */
private fun bigVolume(kg: Long): String = when {
    kg >= 10_000 -> "${kg / 1000} т"
    kg >= 1_000 -> "%.1f т".format(kg / 1000.0).replace('.', ',')
    else -> "$kg кг"
}

/**
 * Сумма на килограмм веса — с двумя знаками после запятой.
 *
 * Общий SetFormat.num даёт один знак, и его здесь мало: у двоих людей
 * рядом выходит «5,4» и «5,4», а разница между ними есть. Для весов
 * в килограммах одного знака хватает с запасом, поэтому правим только
 * это место, а не общее правило.
 */
private fun ratio(v: Float): String = "%.2f".format(v)

private fun plural(n: Int, one: String, few: String, many: String): String {
    val a = n % 100
    if (a in 11..14) return many
    return when (a % 10) {
        1 -> one
        2, 3, 4 -> few
        else -> many
    }
}

// ═══════════════════════════════════════════════════════════════════
//  ВИД
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun Empty(title: String, note: String?, onRetry: (() -> Unit)? = null) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(SoftGray)
                .padding(16.dp)
        ) {
            Text(title, color = Black, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            if (note != null) {
                Spacer(Modifier.height(6.dp))
                Text(note, color = DarkGray, fontSize = 13.sp, lineHeight = 19.sp)
            }
            if (onRetry != null) {
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Black)
                        .clickable { onRetry() }
                        .padding(horizontal = 18.dp, vertical = 9.dp)
                ) {
                    Text("Ещё раз", color = White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun BoardLabel(text: String) {
    Text(
        text, color = DarkGray, fontSize = 10.sp,
        fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp
    )
}

@Composable
private fun Hint(text: String) {
    Text(text, color = DarkGray, fontSize = 11.sp, lineHeight = 16.sp)
}

@Composable
private fun Pill(text: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (on) Black else SoftGray)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text,
            color = if (on) White else DarkGray,
            fontSize = 12.sp,
            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun TableHead(valueLabel: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(22.dp))
        Text("кто", Modifier.weight(1f), color = LightGray, fontSize = 10.sp)
        Text(
            valueLabel, Modifier.width(86.dp), color = LightGray, fontSize = 10.sp,
            textAlign = TextAlign.End
        )
        Text(
            "вес", Modifier.width(62.dp), color = LightGray, fontSize = 10.sp,
            textAlign = TextAlign.End
        )
    }
}

/**
 * Строка таблицы плюс полоска под ней.
 *
 * Полоска нужна не для красоты: числа вроде «487,5» и «512» на глаз
 * почти одинаковы, а длина полоски говорит о разрыве раньше, чем
 * человек успевает прочитать цифры.
 */
@Composable
private fun BoardRow(
    place: Int,
    who: String,
    me: Boolean,
    note: String?,
    value: String,
    second: String,
    share: Float
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                place.toString(), Modifier.width(22.dp),
                color = if (me) Black else LightGray, fontSize = 12.sp,
                fontWeight = if (me) FontWeight.Bold else FontWeight.Normal
            )
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    who, color = Black, fontSize = 13.sp,
                    fontWeight = if (me) FontWeight.Bold else FontWeight.Medium
                )
                if (note != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(note, color = LightGray, fontSize = 10.sp)
                }
            }
            Text(
                value, Modifier.width(86.dp), color = Black, fontSize = 13.sp,
                fontWeight = if (me) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.End
            )
            Text(
                second, Modifier.width(62.dp), color = DarkGray, fontSize = 12.sp,
                textAlign = TextAlign.End
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(SoftGray)
        ) {
            Box(
                Modifier
                    // Ноль здесь запрещён, да и полоска шириной в ничто
                    // выглядела бы поломкой вёрстки, а не пустым значением.
                    .fillMaxWidth(share.coerceIn(0.02f, 1f))
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (me) Black else Gold)
            )
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun Hero(first: Int, common: Int) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Black)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text("$first из $common", color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            "упражнений, где вы первый",
            color = White.copy(alpha = 0.55f), fontSize = 12.sp
        )
    }
}

@Composable
private fun GapRow(c: Gap, ahead: Boolean) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(c.name, color = Black, fontSize = 13.sp, lineHeight = 17.sp)
                Text(
                    "у вас ${c.mine} · у @${c.who} ${c.theirs}",
                    color = DarkGray, fontSize = 11.sp
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                // Знак — от вас к нему: «+30 кг» — у вас на 30 больше,
                // «−5 кг» — на 5 меньше. Отрыв минусом читался как проигрыш.
                when {
                    !c.kg -> if (ahead) "впереди" else "догнать"
                    ahead -> "+" + SetFormat.num((c.gap * 10f).roundToInt() / 10f) + " кг"
                    else -> "−" + SetFormat.num((c.gap * 10f).roundToInt() / 10f) + " кг"
                },
                color = if (ahead) Black else DarkGray,
                fontSize = 13.sp, fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = SoftGray)
        Spacer(Modifier.height(4.dp))
    }
}

package ru.styrk.app.ui.records

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.styrk.app.data.model.ExerciseHistory
import ru.styrk.app.data.model.FedStandards
import ru.styrk.app.data.model.Standards
import ru.styrk.app.ui.theme.Black
import ru.styrk.app.ui.theme.DarkGray
import ru.styrk.app.ui.theme.Gold
import ru.styrk.app.ui.theme.LightGray
import ru.styrk.app.ui.theme.SoftGray
import ru.styrk.app.ui.theme.White
import kotlin.math.roundToInt

/**
 * ВАШ РАЗРЯД — лестница ЕВСК, та же, что в веб-дневнике.
 *
 * Раньше на её месте стояла придуманная лесенка «новичок — любитель —
 * продвинутый — мастер», где «мастер» был подписан как «уровень
 * занимающихся 5+ лет». Подпись честная, слово — нет: «мастер спорта»
 * в этой стране значит совершенно определённую вещь, и называть так
 * результат по выдуманной таблице нельзя.
 *
 * Теперь здесь настоящие нормы: девять ступеней, девять весовых
 * категорий, две дисциплины — классика и экипировка. Числа перенесены
 * из дневника машинно и машинно же сверены: три с лишним тысячи
 * сравнений, ноль расхождений.
 *
 * Открытым лежит только итог — сумма троеборья. Подробности по каждому
 * движению занимают несколько экранов прокрутки, а нужны не каждый
 * раз: человек заходит посмотреть «где я», а не изучать таблицу.
 */
@Composable
fun RankBoard(
    records: Map<String, ExerciseHistory>,
    sex: String?,
    bodyWeight: Float?,
    onPickSex: (String) -> Unit
) {
    // ── пол ещё не выбран ────────────────────────────────────────────
    if (sex != "m" && sex != "f") {
        Card {
            Text("Мужские или женские нормативы?", color = Black,
                fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Разрядные таблицы у мужчин и у женщин разные — без этого разряд " +
                    "посчитать нечестно. Спросим один раз, поменять можно в профиле.",
                color = DarkGray, fontSize = 12.sp, lineHeight = 17.sp
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice("Мужские", Modifier.weight(1f)) { onPickSex("m") }
                Choice("Женские", Modifier.weight(1f)) { onPickSex("f") }
            }
        }
        return
    }
    val male = sex == "m"

    // ── нет веса тела ────────────────────────────────────────────────
    if (bodyWeight == null || bodyWeight <= 0f) {
        Card {
            Text("Нужен вес тела", color = Black, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Нормативы даны по весовым категориям, и без веса категорию " +
                    "не определить. Вес пишется сам при старте тренировки или руками: " +
                    "Профиль → Записать замеры.",
                color = DarkGray, fontSize = 12.sp, lineHeight = 17.sp
            )
        }
        return
    }

    // ── три движения ─────────────────────────────────────────────────
    val lifts = Standards.LIFTS.map { l ->
        val r = records[l.name]
        val kg = if (r != null && r.maxWeight > 0f)
            Standards.oneRM(r.maxWeight, r.maxWeightReps) else 0f
        Triple(l, kg, r)
    }
    if (lifts.none { it.second > 0f }) {
        Card {
            Text("Пока не с чем сверяться", color = Black,
                fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Лестница строится по трём движениям: присед, жим лёжа, становая. " +
                    "Сделайте хотя бы одно — и здесь появится ваша ступень, ваша " +
                    "весовая категория и расстояние до следующей.",
                color = DarkGray, fontSize = 12.sp, lineHeight = 17.sp
            )
        }
        // Троеборья нет, а подтягивания с весом или бицепс есть —
        // соревнования федераций всё равно показываем.
        if (fedResults(records, bodyWeight.toDouble()).isNotEmpty()) {
            FedSection(records, male, bodyWeight, emptyList())
        }
        return
    }

    var equipped by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf(false) }

    val total = (lifts.sumOf { it.second.toDouble() } * 10.0).roundToInt() / 10f
    val full = lifts.all { it.second > 0f }
    val gl = if (full) Standards.glPoints(male, bodyWeight, total) else null

    Column(Modifier.fillMaxWidth()) {

        Text(
            "ВАШ РАЗРЯД", color = DarkGray, fontSize = 10.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp
        )
        Spacer(Modifier.height(10.dp))

        /* Переключатель дисциплины. Экипировка — не украшение и не шутка:
           это вторая дисциплина той же таблицы, и нормы в ней выше,
           потому что снаряжение возвращает часть усилия. */
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Классика", !equipped) { equipped = false }
            Pill("В экипировке", equipped) { equipped = true }
        }
        Spacer(Modifier.height(10.dp))

        val parts = lifts.joinToString(" · ") { (l, kg, _) ->
            l.cap.lowercase() + " " + (if (kg > 0f) Standards.fmt((kg * 10f).roundToInt() / 10f) else "—")
        }

        RankCard(
            cap = "Сумма троеборья",
            kg = total,
            steps = Standards.steps(male, bodyWeight, "total", equipped),
            crown = true,
            sub = parts + "\nвес " + Standards.fmt(bodyWeight) + " кг · категория " +
                Standards.classWord(male, bodyWeight) + " · " +
                (if (equipped) "в экипировке" else "классика, без экипировки"),
            foot = (if (full) "" else "Одно из трёх движений ещё не сделано — сумма неполная. ") +
                "Нормы ЕВСК 2026. Разряд присваивают на соревнованиях; " +
                "здесь линейка, а не протокол."
        )

        Spacer(Modifier.height(10.dp))

        /* Подпись на кнопке перечисляет то, что ВНУТРИ, а не то, что
           бывает вообще: иначе строка обещает присед человеку, который
           приседа не делал. */
        val extras = Standards.EXTRA.mapNotNull { l ->
            val r = records[l.name]
            val kg = if (r != null && r.maxWeight > 0f)
                Standards.oneRM(r.maxWeight, r.maxWeightReps) else 0f
            if (kg > 0f) Triple(l, kg, r) else null
        }
        val inside = (lifts.filter { it.second > 0f }.map { it.first.cap } +
                fedResults(records, bodyWeight.toDouble()).map { it.first.cap } +
                extras.map { it.first.cap })
            .joinToString(", ") { it.lowercase() }

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(SoftGray)
                .clickable { open = !open }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Разряды по движениям", color = Black,
                    fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (open) "свернуть" else inside,
                    color = DarkGray, fontSize = 11.sp, lineHeight = 15.sp
                )
            }
            Text(if (open) "⌃" else "⌄", color = DarkGray, fontSize = 16.sp)
        }

        if (open) {
            lifts.filter { it.second > 0f }.forEach { (l, kg, rec) ->
                Spacer(Modifier.height(10.dp))
                RankCard(
                    cap = l.cap,
                    kg = (kg * 10f).roundToInt() / 10f,
                    steps = Standards.steps(male, bodyWeight, l.key, equipped),
                    crown = false,
                    sub = oneRmLine(rec),
                    foot = if (l.key == "bench")
                        "Жим — единственное из трёх, у которого есть собственная норма." +
                            (if (equipped) "" else " Звание МСМК в жиме классическом не присваивают.")
                    else
                        "Отдельной нормы на это движение нет: ориентир — доля от нормы суммы."
                )
            }
            if (gl != null) {
                Spacer(Modifier.height(10.dp))
                Card {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Очки IPF GL", Modifier.weight(1f), color = Black,
                            fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(
                            Standards.fmt((gl * 10.0).roundToInt() / 10f),
                            color = Black, fontSize = 18.sp, fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Так сравнивают людей разного веса на соревнованиях: сумма, " +
                            "приведённая к весу тела по официальной формуле. Сто очков — " +
                            "примерно мировой рекорд. Считается по классике.",
                        color = DarkGray, fontSize = 11.sp, lineHeight = 16.sp
                    )
                }
            }

            FedSection(records, male, bodyWeight, extras)

            Spacer(Modifier.height(8.dp))
            Text(
                "Нормативы " + (if (male) "мужские" else "женские") + " — меняется в профиле.",
                color = LightGray, fontSize = 11.sp
            )
        }
    }
}

/** Результаты по дисциплинам федераций: дисциплина, оценка на 1 раз, рекорд. */
private fun fedResults(
    records: Map<String, ExerciseHistory>, bw: Double
): List<Triple<FedStandards.Disc, Double, ExerciseHistory?>> =
    FedStandards.DISCS.mapNotNull { d ->
        // Из нескольких упражнений справочника — лучшая оценка.
        var best = 0.0
        var bestRec: ExerciseHistory? = null
        d.names.forEach { n ->
            val r = records[n] ?: return@forEach
            if (r.maxWeight <= 0f) return@forEach
            val v = FedStandards.oneRM(r.maxWeight.toDouble(), r.maxWeightReps, d.bodyweight, bw)
            if (v > best) { best = v; bestRec = r }
        }
        if (best > 0.0) Triple(d, best, bestRec) else null
    }

/**
 * Свёрнутый список «Соревнования федераций» — внутри «Разрядов по движениям»,
 * а без троеборья — сам по себе.
 */
@Composable
private fun FedSection(
    records: Map<String, ExerciseHistory>,
    male: Boolean,
    bodyWeight: Float,
    extras: List<Triple<Standards.Lift, Float, ExerciseHistory?>>
) {
    var fedOpen by remember { mutableStateOf(false) }
    val bw = bodyWeight.toDouble()
    val fed = fedResults(records, bw)
    val pull = fed.firstOrNull { it.first.key == "pull" }?.second
    val dip = fed.firstOrNull { it.first.key == "dip" }?.second
    Column(Modifier.fillMaxWidth()) {
        /* ── Соревнования федераций ─────────────────────────────

           Свёрнутый список внутри списка. Разряды здесь настоящие,
           но это разряды федерации, а не звание от Минспорта:
           «МС ISF» и «мастер спорта России» — разные вещи, и мешать
           их с ЕВСК в одну кучу значило бы вводить в заблуждение. */
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(SoftGray)
                .clickable { fedOpen = !fedOpen }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Соревнования федераций", color = Black,
                    fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (fedOpen) "свернуть"
                    else "не звание МС России · стритлифтинг, строгий подъём на бицепс",
                    color = DarkGray, fontSize = 11.sp, lineHeight = 15.sp
                )
            }
            Text(if (fedOpen) "⌃" else "⌄", color = DarkGray, fontSize = 16.sp)
        }

        if (fedOpen) {
            Spacer(Modifier.height(8.dp))
            Text(
                "По этим движениям проводят соревнования и присваивают разряды, " +
                    "но это разряды федерации — не звание от Минспорта, как в пауэрлифтинге.",
                color = DarkGray, fontSize = 11.sp, lineHeight = 16.sp
            )
            fed.forEach { (d, kg, rec) ->
                val st = FedStandards.steps(d.key, male, bw) ?: return@forEach
                Spacer(Modifier.height(10.dp))
                FedCard(
                    cap = d.cap, kg = kg, steps = st,
                    sub = fedLine(rec, d.bodyweight) + "\n" + d.source + " · категория " +
                        FedStandards.classWord(d.key, male, bw),
                    foot = d.note
                )
            }
            // Двоеборье — только когда есть оба движения.
            if (pull != null && dip != null) {
                val st = FedStandards.steps("street", male, bw)
                if (st != null) {
                    Spacer(Modifier.height(10.dp))
                    FedCard(
                        cap = FedStandards.STREET_CAP,
                        kg = Math.round((pull + dip) * 10.0) / 10.0,
                        steps = st,
                        sub = "подтягивания " + FedStandards.fmt(pull) + " + брусья " +
                            FedStandards.fmt(dip) + "\nISF-Россия · категория " +
                            FedStandards.classWord("street", male, bw),
                        foot = "Сумма двух движений с дополнительным весом."
                    )
                }
            }
            extras.forEach { (l, kg, rec) ->
                val st = Standards.orient(male, bodyWeight, l.key) ?: return@forEach
                Spacer(Modifier.height(10.dp))
                OrientCard(
                    cap = l.cap,
                    kg = (kg * 10f).roundToInt() / 10f,
                    steps = st,
                    sub = oneRmLine(rec),
                    foot = l.note
                )
            }
            if (fed.isEmpty() && extras.isEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Пока нечего сравнить. Сюда попадут подтягивания и брусья " +
                        "с дополнительным весом, строгий подъём штанги на бицепс " +
                        "и жим стоя — как только они появятся в ваших рекордах.",
                    color = DarkGray, fontSize = 12.sp, lineHeight = 17.sp
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Подтягивания, отжимания и пресс на количество без веса: общей " +
                    "разрядной таблицы нет — у каждой федерации своя, поэтому здесь их нет.",
                color = LightGray, fontSize = 11.sp, lineHeight = 15.sp
            )
        }

    }
}

/** Подпись к результату федерации: откуда оценка. */
private fun fedLine(rec: ExerciseHistory?, bodyweight: Boolean): String {
    if (rec == null) return ""
    val set = Standards.fmt(rec.maxWeight) + " × " + maxOf(rec.maxWeightReps, 1)
    return if (bodyweight) "дополнительный вес на 1 раз — оценка по подходу +$set"
    else "сколько подняли бы на 1 раз — оценка по подходу $set"
}

/**
 * Ступень федерации: как [RankCard], но со своей лестницей из десяти
 * ступеней (до «Элиты») и своими словами.
 */
@Composable
private fun FedCard(cap: String, kg: Double, steps: Array<Double?>, sub: String, foot: String) {
    // Юношеские ступени уходят, как только взят III разряд — как и в ЕВСК.
    val youthDone = steps[3] != null && kg >= steps[3]!!
    val from = if (youthDone) 3 else 0
    val p = FedStandards.place(kg, steps)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SoftGray)
            .padding(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(cap, Modifier.weight(1f), color = DarkGray, fontSize = 11.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            Column(horizontalAlignment = Alignment.End) {
                Text(FedStandards.fmt(kg) + " кг", color = Black,
                    fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("на 1 раз", color = DarkGray, fontSize = 10.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (p.idx < 0) "нет разряда" else FedStandards.LONG[p.idx],
            color = if (p.idx < 0) DarkGray else Black, fontSize = 18.sp, fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (i in from until steps.size) {
                if (steps[i] == null) continue
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (i <= p.idx) Black else LightGray.copy(alpha = 0.5f))
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        FedStandards.SHORT[i],
                        color = if (i <= p.idx) Black else LightGray,
                        fontSize = 9.sp,
                        fontWeight = if (i <= p.idx) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        val next = p.next
        if (next != null && p.need != null) {
            Text(
                "до " + FedStandards.GENIT[next] + " ещё " +
                    FedStandards.fmt(Math.round((p.need - kg) * 10.0) / 10.0) + " кг",
                color = Black, fontSize = 13.sp, fontWeight = FontWeight.Bold
            )
        } else {
            Text("Выше ступеней нет.", color = DarkGray, fontSize = 12.sp)
        }
        Spacer(Modifier.height(10.dp))
        Text(sub, color = DarkGray, fontSize = 11.sp, lineHeight = 16.sp)
        Spacer(Modifier.height(8.dp))
        Text(foot, color = LightGray, fontSize = 11.sp, lineHeight = 15.sp)
    }
}

private fun oneRmLine(rec: ExerciseHistory?): String =
    if (rec != null && rec.maxWeight > 0f)
        "сколько подняли бы на 1 раз — оценка по подходу " + Standards.fmt(rec.maxWeight) + " × " +
            (if (rec.maxWeightReps > 0) rec.maxWeightReps else 1)
    else "сколько подняли бы на 1 раз — оценка"

// ═══════════════════════════════════════════════════════════════════
//  КАРТОЧКИ
// ═══════════════════════════════════════════════════════════════════

/**
 * Одна ступень лестницы: где стоит результат и сколько до следующей.
 *
 * [crown] — карточка суммы: она чёрная и крупная, остальные светлые
 * и поменьше. Сумма и есть разряд; всё прочее — подробности к ней.
 */
@Composable
private fun RankCard(
    cap: String,
    kg: Float,
    steps: Array<Float?>,
    crown: Boolean,
    sub: String,
    foot: String
) {
    val p = Standards.place(kg, steps)
    val word = Standards.word(p.idx)
    val fg = if (crown) White else Black
    val dim = if (crown) White.copy(alpha = 0.5f) else DarkGray

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (crown) 18.dp else 14.dp))
            .background(if (crown) Black else SoftGray)
            .padding(if (crown) 18.dp else 14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(cap, Modifier.weight(1f), color = dim, fontSize = 11.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            // Под числом — что это за число. Без подписи «126,7 кг» у
            // приседа читалось как рекорд, которого человек не ставил:
            // это оценка, сколько он поднял бы на один раз.
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    Standards.fmt(kg) + " кг", color = fg,
                    fontSize = if (crown) 20.sp else 16.sp, fontWeight = FontWeight.Bold
                )
                Text(if (crown) "сумма на 1 раз" else "на 1 раз", color = dim, fontSize = 10.sp)
            }
        }
        Spacer(Modifier.height(if (crown) 8.dp else 6.dp))
        Text(
            word, color = if (p.idx < 0) dim else if (crown) Gold else Black,
            fontSize = if (crown) 26.sp else 18.sp, fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(10.dp))
        Ladder(steps, p.idx, crown)

        p.need?.let { need ->
            Spacer(Modifier.height(10.dp))
            Text(
                "до " + Standards.GENIT[p.next ?: 0] + " ещё " +
                    Standards.fmt(((need - kg) * 10f).roundToInt() / 10f) + " кг",
                color = if (crown) Gold else Black, fontSize = 13.sp, fontWeight = FontWeight.Bold
            )
        }
        if (p.need == null && p.idx >= 0) {
            Spacer(Modifier.height(10.dp))
            Text("Выше ступеней нет.", color = dim, fontSize = 12.sp)
        }

        Spacer(Modifier.height(10.dp))
        Text(sub, color = dim, fontSize = 11.sp, lineHeight = 16.sp)
        Spacer(Modifier.height(8.dp))
        Text(foot, color = if (crown) White.copy(alpha = 0.32f) else LightGray,
            fontSize = 10.sp, lineHeight = 14.sp)
    }
}

/**
 * Сама лесенка: короткие подписи ступеней, взятые — заполнены.
 *
 * Юношеские ступени пропадают, как только взят III взрослый: взрослому
 * человеку «II юношеский» не говорит ничего, а места в строке занимает
 * столько же, сколько КМС.
 */
@Composable
private fun Ladder(steps: Array<Float?>, idx: Int, crown: Boolean) {
    val from = if (idx >= Standards.YOUTH) Standards.YOUTH else 0
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (i in from until steps.size) {
            val exists = steps[i] != null
            val taken = exists && i <= idx
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            when {
                                taken && crown -> Gold
                                taken -> Black
                                crown -> White.copy(alpha = 0.16f)
                                else -> LightGray.copy(alpha = 0.5f)
                            }
                        )
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (exists) Standards.SHORT[i] else "—",
                    color = when {
                        !exists -> if (crown) White.copy(alpha = 0.2f) else LightGray
                        taken && crown -> Gold
                        taken -> Black
                        crown -> White.copy(alpha = 0.4f)
                        else -> LightGray
                    },
                    fontSize = 9.sp,
                    fontWeight = if (taken) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

/**
 * Карточка ориентира — для движений без разрядной таблицы.
 *
 * Выглядит похоже на разрядную и подписана нарочно иначе: «обычный /
 * зал / сильно» вместо разрядов. Выдавать ориентир за норматив нельзя,
 * даже если очень хочется, чтобы у каждой карточки была одна и та же
 * красивая лесенка.
 */
@Composable
private fun OrientCard(cap: String, kg: Float, steps: FloatArray, sub: String, foot: String) {
    var idx = -1
    for (i in steps.indices) if (kg >= steps[i]) idx = i
    val next = if (idx + 1 < steps.size) idx + 1 else null

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SoftGray)
            .padding(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(cap, Modifier.weight(1f), color = DarkGray, fontSize = 11.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            Column(horizontalAlignment = Alignment.End) {
                Text(Standards.fmt(kg) + " кг", color = Black,
                    fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("на 1 раз", color = DarkGray, fontSize = 10.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (idx < 0) "начало пути" else Standards.ORIENT_LONG[idx],
            color = Black, fontSize = 18.sp, fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (i in steps.indices) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (i <= idx) Black else LightGray.copy(alpha = 0.5f))
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        Standards.ORIENT_SHORT[i],
                        color = if (i <= idx) Black else LightGray,
                        fontSize = 9.sp,
                        fontWeight = if (i <= idx) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
        if (next != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                "до " + Standards.ORIENT_GENIT[next] + " ещё " +
                    Standards.fmt(((steps[next] - kg) * 10f).roundToInt() / 10f) + " кг",
                color = Black, fontSize = 13.sp, fontWeight = FontWeight.Bold
            )
        } else {
            Spacer(Modifier.height(10.dp))
            Text("Выше ориентиров нет — дальше только рекорды.",
                color = DarkGray, fontSize = 12.sp)
        }
        Spacer(Modifier.height(10.dp))
        Text(sub, color = DarkGray, fontSize = 11.sp, lineHeight = 16.sp)
        Spacer(Modifier.height(8.dp))
        Text(foot, color = LightGray, fontSize = 10.sp, lineHeight = 14.sp)
    }
}

/** Светлая карточка-объяснение: одна на все состояния «показать нечего». */
@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SoftGray)
            .padding(14.dp),
        content = content
    )
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
            text, color = if (on) White else DarkGray, fontSize = 12.sp,
            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun Choice(text: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Black)
            .clickable { onClick() }
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

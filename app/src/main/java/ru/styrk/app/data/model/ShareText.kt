package ru.styrk.app.data.model

import ru.styrk.app.ui.common.ruPlural
import java.time.format.TextStyle
import java.util.Locale

/**
 * Тренировка и план — обычным текстом, чтобы поделиться.
 *
 * Куда он уходит: в мессенджер, в письмо, в заметки, в файл .txt.
 * Везде шрифт разный, чаще всего пропорциональный, поэтому никаких
 * таблиц из пробелов — они ровные только в моноширинном и разъезжаются
 * везде, кроме него. Каждый подход — своей строкой: «2) 82,5 кг × 6».
 * Так читается в любом шрифте и в любой ширине.
 *
 * В Telegram через бота уходит другая карточка — с таблицей: её
 * собирает сервер, и там моноширинный блок есть (см. shareText
 * в server.js).
 *
 * ВАЖНО: веб-дневник собирает ровно такой же текст (workoutText
 * в app.html). Правило одно на двоих — поправили здесь, поправьте
 * и там, иначе одна тренировка с телефона и из браузера уйдёт
 * по-разному. Сверку держит tools/проверить_R189_текст.
 */
object ShareText {

    private val RU = Locale.forLanguageTag("ru")
    private val MONTHS = listOf("января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря")
    const val FOOTER = "STYRK — дневник тренировок · styrk.ru"

    // ── числа ─────────────────────────────────────────────────────

    /**
     * Одна цифра после запятой, а целое — без «,0»: 80 → «80»,
     * 82.5 → «82,5». Округление через десятые целым числом, а не
     * через формат строки: так же считает браузер, и 8,45 у обоих
     * округлится одинаково.
     */
    fun r1(v: Double): String {
        val t = Math.round(v * 10)
        val whole = t / 10
        val frac = Math.abs(t % 10)
        return if (frac == 0L) whole.toString() else "$whole,$frac"
    }

    fun r1(v: Float): String = r1(v.toDouble())

    /** 95 секунд → «1:35». */
    fun mmss(sec: Int): String {
        val s = maxOf(0, sec)
        return "${s / 60}:" + (s % 60).toString().padStart(2, '0')
    }

    fun dur(min: Long): String {
        val h = min / 60
        val m = min % 60
        return when {
            h > 0 && m > 0 -> "$h ч $m мин"
            h > 0 -> "$h ч"
            else -> "$m мин"
        }
    }

    /** Объём: до тонны — килограммы, дальше тонны с одним знаком, от десяти — целые. */
    fun vol(kg: Double): String = when {
        kg >= 10000 -> "${Math.round(kg / 1000)} т"
        kg >= 1000 -> {
            val t = Math.round(kg / 100)
            if (t % 10 == 0L) "${t / 10} т" else "${t / 10},${t % 10} т"
        }
        else -> "${Math.round(kg)} кг"
    }

    private fun two(n: Int) = n.toString().padStart(2, '0')

    // ── тренировка ────────────────────────────────────────────────

    /** Тема письма и заголовок окна «поделиться». */
    fun subject(w: CompletedWorkout): String =
        "Тренировка ${w.dateTime.dayOfMonth} ${MONTHS[w.dateTime.monthValue - 1]}: " +
                w.muscleGroup.ifBlank { "Тренировка" }

    /** «STYRK 2026-10-15 Грудь, Трицепс.txt» — без знаков, которые не любят файловые системы. */
    fun fileName(w: CompletedWorkout): String {
        val d = w.dateTime
        return "STYRK ${d.year}-${two(d.monthValue)}-${two(d.dayOfMonth)} " +
                safe(w.muscleGroup.ifBlank { "Тренировка" }) + ".txt"
    }

    private fun safe(s: String): String =
        s.replace(Regex("[\\\\/:*?\"<>|\\r\\n\\t]"), "").trim().take(60).trim()

    /**
     * Тренировка целиком.
     *
     * [isRecord] — держит ли подход рекорд (в приложении это
     * viewModel.isSetRecord). Трофей ставим одному подходу на
     * упражнение — первому такому: три одинаковых по 85 кг — это
     * один рекорд, а не три. Так же делает и сервер.
     */
    fun workout(
        w: CompletedWorkout,
        isRecord: (String, MeasureType, WorkoutSet) -> Boolean = { _, _, _ -> false }
    ): String {
        val shown = w.exercises.filter { it.sets.isNotEmpty() }
        val end = w.dateTime
        val start = end.minusMinutes(w.durationMinutes)
        val out = StringBuilder()

        out.append("🏋️ ").append(w.muscleGroup.ifBlank { "Тренировка" }).append('\n')
        val day = "${end.dayOfMonth} ${MONTHS[end.monthValue - 1]} ${end.year}"
        val hm = { t: java.time.LocalDateTime -> "${two(t.hour)}:${two(t.minute)}" }
        out.append(
            if (w.durationMinutes > 0) "$day · ${hm(start)}–${hm(end)} · ${dur(w.durationMinutes)}"
            else "$day · ${hm(end)}"
        ).append('\n')

        val volume = shown.sumOf { ex -> ex.sets.sumOf { PairedExercises.volume(it.weight, it.reps, ex.name) } }
        val nSets = shown.sumOf { it.sets.size }
        val stats = listOfNotNull(
            if (volume > 0) "объём ${vol(volume)}" else null,
            "${shown.size} ${ruPlural(shown.size, "упражнение", "упражнения", "упражнений")}",
            "$nSets ${ruPlural(nSets, "подход", "подхода", "подходов")}"
        ).joinToString(" · ")
        out.append(cap(stats)).append('\n')

        // Трофеи считаем заранее: число нужно в шапке, до упражнений.
        val hits = shown.map { ex -> ex.sets.indexOfFirst { isRecord(ex.name, ex.measureType, it) } }
        val recs = hits.count { it >= 0 }
        val extra = listOfNotNull(
            w.athleteWeight?.takeIf { it > 0f }?.let { "собственный вес ${r1(it)} кг" },
            if (recs > 0) "🏆 $recs ${ruPlural(recs, "рекорд", "рекорда", "рекордов")}" else null
        ).joinToString(" · ")
        if (extra.isNotEmpty()) out.append(cap(extra)).append('\n')

        /* Разделы по группам — только когда групп больше одной.
           С одной группой заголовок повторил бы первую строку. */
        val groups = shown.mapNotNull { it.muscleGroup?.title }.distinct()
        val showGroups = groups.size > 1
        var prevGroup: String? = null

        shown.forEachIndexed { i, ex ->
            out.append('\n')
            val g = ex.muscleGroup?.title
            if (showGroups && g != null && g != prevGroup) {
                out.append(g.uppercase(RU)).append('\n')
                prevGroup = g
            }
            out.append(ex.name).append('\n')
            exerciseLines(ex, hits[i]).forEach { out.append(it).append('\n') }
        }

        w.notes?.trim()?.takeIf { it.isNotEmpty() }?.let {
            out.append('\n').append("Заметка: «").append(it).append("»\n")
        }
        out.append('\n').append(FOOTER)
        return out.toString()
    }

    private fun exerciseLines(ex: WorkoutExercise, hit: Int): List<String> {
        val sets = ex.sets
        val lines = ArrayList<String>()
        val noWeight = sets.all { it.weight <= 0f }
        sets.forEachIndexed { i, s ->
            val v = when (ex.measureType) {
                MeasureType.WEIGHT_REPS ->
                    if (noWeight) "${s.reps} повт."
                    else if (s.weight > 0f) "${r1(s.weight)} кг × ${s.reps}"
                    else "без веса × ${s.reps}"
                MeasureType.REPS -> "${s.reps} повт."
                MeasureType.TIME -> mmss(s.seconds ?: 0)
                MeasureType.DISTANCE -> {
                    val km = "${r1(s.distanceKm ?: 0f)} км"
                    val t = s.seconds ?: 0
                    if (t > 0) "$km · ${mmss(t)}" else km
                }
                MeasureType.CARDIO -> listOfNotNull(
                    s.load?.let { "нагрузка ${r1(it)}" },
                    s.speed?.let { "${r1(it)} км/ч" },
                    s.seconds?.let { mmss(it) }
                ).joinToString(" · ").ifEmpty { "—" }
            }
            lines += "${i + 1}) $v" + if (i == hit) " 🏆" else ""
        }

        val foot: String? = when (ex.measureType) {
            MeasureType.WEIGHT_REPS ->
                if (!noWeight) {
                    val v = sets.sumOf { PairedExercises.volume(it.weight, it.reps, ex.name) }
                    "макс ${r1(sets.maxOf { it.weight })} кг · объём ${Math.round(v)} кг" +
                            if (PairedExercises.isPaired(ex.name)) " (за две гантели)" else ""
                } else if (sets.size > 1) "всего ${sets.sumOf { it.reps }} повт." else null
            MeasureType.REPS ->
                if (sets.size > 1) "всего ${sets.sumOf { it.reps }} повт." else null
            MeasureType.TIME ->
                if (sets.size > 1) "всего ${mmss(sets.sumOf { it.seconds ?: 0 })}" else null
            MeasureType.DISTANCE ->
                if (sets.size > 1) "всего ${r1(sets.sumOf { (it.distanceKm ?: 0f).toDouble() })} км" else null
            MeasureType.CARDIO -> null
        }
        if (foot != null) lines += foot
        return lines
    }

    private fun cap(s: String): String =
        if (s.isNotEmpty() && s[0].isLetter()) s[0].uppercaseChar() + s.substring(1) else s

    // ── план ──────────────────────────────────────────────────────

    fun planSubject(p: PlannedWorkout): String =
        "План тренировки ${p.date.dayOfMonth} ${MONTHS[p.date.monthValue - 1]}: " +
                p.muscleGroup.ifBlank { "Тренировка" }

    fun planFileName(p: PlannedWorkout): String =
        "STYRK план ${p.date.year}-${two(p.date.monthValue)}-${two(p.date.dayOfMonth)} " +
                safe(p.muscleGroup.ifBlank { "Тренировка" }) + ".txt"

    /** План: когда, что и с какой целью. Цели — тем же словами, что в календаре. */
    fun plan(p: PlannedWorkout): String {
        val out = StringBuilder()
        out.append("📅 План тренировки: ").append(p.muscleGroup.ifBlank { "Тренировка" }).append('\n')
        val wd = p.date.dayOfWeek.getDisplayName(TextStyle.FULL, RU)
            .replaceFirstChar { it.uppercase(RU) }
        out.append("$wd, ${p.date.dayOfMonth} ${MONTHS[p.date.monthValue - 1]} ${p.date.year}" +
                " · ${two(p.hour)}:${two(p.minute)}").append('\n')
        out.append('\n')
        if (p.exercises.isEmpty()) {
            out.append("Упражнения — на месте.\n")
        } else {
            p.exercises.forEachIndexed { i, name ->
                val goal = p.targets[name]?.label().orEmpty()
                out.append("${i + 1}. $name").append(if (goal.isNotEmpty()) " — $goal" else "").append('\n')
            }
        }
        out.append('\n').append(FOOTER)
        return out.toString()
    }
}

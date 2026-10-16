package ru.styrk.app.ui.training

import ru.styrk.app.data.model.MeasureType
import ru.styrk.app.data.model.WorkoutSet

/**
 * Подсказка в полях подхода и проверка на опечатку (R194).
 *
 * Чистые функции без Compose — их легко проверить отдельно.
 * Та же логика — в веб-дневнике (`hintSet`, `setDoubt` в app.html).
 */
object SetHints {

    /**
     * Что подставить в поля для следующего подхода (номер `sets.size`).
     *
     *  - `plan` — подходы из плана на сегодня (тренер или свой план);
     *  - `prev` — подходы прошлой тренировки в этом упражнении.
     *
     * «Цель» подхода — он же по плану, а без плана — он же в прошлый раз.
     *
     * Для «вес × повторы»:
     *  1. первый подход — цель;
     *  2. дальше — тяжелее из двух: только что сделанный подход или цель.
     *     Сделал 30 × 10, а по плану дальше 28 × 10 — подсказка 30 × 10:
     *     назад по весу не шагаем. Тяжёлые подходы, до которых ещё
     *     не дошли, на подсказку не влияют (до R194 подставлялся самый
     *     тяжёлый подход вообще);
     *  3. цель тяжелее сделанного — подставляется цель, но только если
     *     она не больше чем на 20 % выше уже известного веса: самого
     *     тяжёлого из сегодняшних и прошлой тренировки. Именно так,
     *     а не «20 % от прошлого подхода»: иначе после разминки 20 кг
     *     рабочие 60 кг по плану не подставились бы никогда — каждый
     *     следующий подход упирался бы в предыдущий. А вот 100 кг
     *     по плану при известных 60 — похоже на ошибку в плане,
     *     и подсказка остаётся на сделанном.
     *     Прошлой тренировки с весом нет (упражнение впервые) — плану
     *     верим целиком: сравнивать не с чем.
     *
     * Для остального (свой вес, время, дистанция, кардио) «тяжелее»
     * не определено: цель → последний подход сегодня → последний
     * подход прошлой тренировки.
     */
    fun pick(
        type: MeasureType,
        sets: List<WorkoutSet>,
        plan: List<WorkoutSet>,
        prev: List<WorkoutSet>
    ): WorkoutSet? {
        val i = sets.size
        val target = plan.getOrNull(i) ?: prev.getOrNull(i)
        val cur = sets.lastOrNull()
        if (type != MeasureType.WEIGHT_REPS) return target ?: cur ?: prev.lastOrNull()
        if (cur == null) return target ?: prev.lastOrNull()
        if (target == null || target.weight < cur.weight) return cur
        val history = prev.maxOfOrNull { it.weight } ?: 0f
        if (history <= 0f) return target
        val known = maxOf(history, sets.maxOf { it.weight })
        return if (target.weight <= known * 1.2f + 0.001f) target else cur
    }

    /**
     * Похоже на опечатку — короткое пояснение, иначе null.
     *
     * Ловит то, что случается на самом деле: 70 × 70 вместо 70 × 10
     * (повторы набрали весом), лишний ноль (600 вместо 60), перепутанные
     * поля. Сравнивает с `refs` — сегодняшними подходами (без правимого)
     * и прошлой тренировкой; без истории — только явные крайности.
     * Записать всё равно можно — это вопрос, а не запрет.
     */
    fun doubt(type: MeasureType, s: WorkoutSet, refs: List<WorkoutSet>): String? {
        if (type != MeasureType.WEIGHT_REPS && type != MeasureType.REPS) return null
        val w = if (type == MeasureType.WEIGHT_REPS) s.weight else 0f
        val r = s.reps
        val maxW = refs.maxOfOrNull { it.weight } ?: 0f
        val maxR = refs.maxOfOrNull { it.reps } ?: 0
        if (w > 0f) {
            if (maxW > 0f && w >= maxW * 1.5f && w - maxW >= 10f)
                return "${num(w)} кг — в полтора раза больше обычного (до ${num(maxW)} кг)"
            if (maxW <= 0f && w >= 500f)
                return "${num(w)} кг — очень большой вес"
            if (r >= 30 && (maxR <= 0 || r >= maxR * 2))
                return if (maxR > 0) "$r повторов — больше обычного (до $maxR)"
                       else "$r повторов с весом — очень много"
        } else {
            if (r > 100 || (maxR > 0 && r >= 30 && r >= maxR * 3))
                return if (maxR > 0) "$r повторов — больше обычного (до $maxR)"
                       else "$r повторов — очень много"
        }
        return null
    }

    private fun num(f: Float): String =
        if (f == f.toInt().toFloat()) f.toInt().toString()
        else "%.2f".format(java.util.Locale.US, f).trimEnd('0').trimEnd('.').replace('.', ',')
}

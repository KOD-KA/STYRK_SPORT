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
     * Похоже на опечатку — короткое пояснение, иначе null (R194, R195).
     *
     * Ловит то, что случается на самом деле: 70 × 70 вместо 70 × 10
     * (повторы набрали весом), лишний ноль (600 вместо 60), перепутанные
     * поля. Записать всё равно можно — это вопрос, а не запрет.
     *
     *  - `history` — все подходы этого упражнения из журнала (без
     *    сегодняшней тренировки);
     *  - `today` — сегодняшние подходы в этой карточке (без правимого).
     *
     * Главное правило: НЕ СПРАШИВАТЬ О ПРИВЫЧНОМ. Сегодняшние подходы
     * для сравнения не годятся: разминка 20 → 40 → 80 — это рост
     * за тренировку, а не опечатка (первая версия R194 спрашивала
     * на каждом шаге). Сегодняшнее только гасит вопросы: что уже
     * записали или подтвердили, второй раз не спрашивается.
     *
     * Вес:
     *  1. есть история — вопрос, если вес вдвое больше и на 30 кг больше
     *     известного. Вдвое, а не в полтора раза: лишний ноль и двойной
     *     ввод дают ×10 и ×2, а честный рост — меньше;
     *  2. потолок упражнения — для всех, включая первый раз. Сравнивается
     *     расчётный разовый максимум (вес × (1 + повторы / 30), до 30
     *     повторов): 400 × 10 в становой — вопрос, 400 × 1 — нет.
     *     Потолки щедрые, с запасом на сильных людей: см. [cap].
     * Повторы с весом: есть история — 30+ и вдвое больше известного;
     * первый раз — 50+ или 30+ с весом от 40 кг (70 × 70).
     * Свой вес: есть история — 30+ и втрое больше; первый раз — больше 150.
     */
    fun doubt(
        type: MeasureType,
        s: WorkoutSet,
        name: String,
        group: String?,
        history: List<WorkoutSet>,
        today: List<WorkoutSet>
    ): String? {
        if (type != MeasureType.WEIGHT_REPS && type != MeasureType.REPS) return null
        val w = if (type == MeasureType.WEIGHT_REPS) s.weight else 0f
        val r = s.reps
        val all = history + today
        if (w > 0f) {
            val weighted = all.filter { it.weight > 0f }
            val baseW = weighted.maxOfOrNull { it.weight } ?: 0f
            if (history.any { it.weight > 0f } && w >= baseW * 2f && w - baseW >= 30f)
                return "Вдвое больше, чем вы поднимали в этом упражнении (до ${num(baseW)} кг)"
            val e1 = e1rm(w, r)
            if (e1 > cap(name, group) && e1 > (weighted.maxOfOrNull { e1rm(it.weight, it.reps) } ?: 0f))
                return "Очень много для этого упражнения"
            val baseR = weighted.maxOfOrNull { it.reps } ?: 0
            if (r > baseR) {
                if (history.any { it.weight > 0f && it.reps > 0 }) {
                    if (r >= 30 && r >= baseR * 2) return "$r повторов — больше обычного (до $baseR)"
                } else if (r >= 50 || (r >= 30 && w >= 40f)) {
                    return "$r повторов с весом — очень много"
                }
            }
        } else {
            val baseR = all.maxOfOrNull { it.reps } ?: 0
            if (r > baseR) {
                if (history.any { it.reps > 0 }) {
                    if (r >= 30 && r >= baseR * 3) return "$r повторов — больше обычного (до $baseR)"
                } else if (r > 150) {
                    return "$r повторов — очень много"
                }
            }
        }
        return null
    }

    /** Расчётный разовый максимум (формула Эпли), повторы — до 30. */
    private fun e1rm(w: Float, r: Int): Float = w * (1f + r.coerceIn(1, 30) / 30f)

    /**
     * Потолок упражнения — расчётный разовый максимум, выше которого
     * у живого человека уже вопрос. С запасом на сильных: жим лёжа 300
     * (250 × 5 проходит, 280 × 5 — уже вопрос), становая и присед 500
     * (400 × 10 — вопрос), жим ногами 750. По названию — то, что в нём
     * однозначно; иначе по группе мышц. Свои упражнения человека —
     * по группе.
     */
    private fun cap(name: String, group: String?): Float {
        val n = name.lowercase()
        return when {
            "гантел" in n -> 150f                                   // вес одной гантели
            "жим ногами" in n || "одной ногой" in n || "гакк" in n || "носки" in n -> 750f
            "станов" in n || "присед" in n || "ягодичн" in n || "румынск" in n || "шраги" in n -> 500f
            group?.uppercase() in setOf("BICEPS", "TRICEPS", "FOREARMS", "NECK", "ABS") -> 200f
            group?.uppercase() == "SHOULDERS" -> 250f
            else -> 300f
        }
    }

    private fun num(f: Float): String =
        if (f == f.toInt().toFloat()) f.toInt().toString()
        else "%.2f".format(java.util.Locale.US, f).trimEnd('0').trimEnd('.').replace('.', ',')
}

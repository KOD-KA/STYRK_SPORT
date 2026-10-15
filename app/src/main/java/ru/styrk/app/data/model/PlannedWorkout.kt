package ru.styrk.app.data.model

import java.time.LocalDate

/**
 * Цель на упражнение в плане: сколько подходов, повторов и с каким весом.
 * Любое поле может быть пустым — заполнять их не обязательно.
 */
data class ExerciseTarget(
    val sets: Int? = null,
    val reps: Int? = null,
    val weight: Float? = null,
    /**
     * План по подходам — когда тренер расписал каждый подход отдельно.
     * Пусто — плана по подходам нет, действует общая цель выше.
     */
    val plan: List<WorkoutSet> = emptyList()
) {
    val isEmpty: Boolean get() = sets == null && reps == null && weight == null && plan.isEmpty()

    /** Строка вида «4 x 8 x 82,5 кг». Пустая, если ничего не задано. */
    fun label(): String {
        if (isEmpty) return ""
        // План по подходам говорит сам за себя: «40×10 · 42,5×8 · 45×6».
        if (plan.isNotEmpty()) return plan.joinToString(" · ") { s ->
            (if (s.weight > 0f) fmt(s.weight) else "без веса") + "×" + s.reps
        }
        val parts = buildList {
            if (sets != null) add("$sets подх.")
            if (reps != null) add("$reps повт.")
            if (weight != null) add(fmt(weight) + " кг")
        }
        return parts.joinToString(" x ")
    }

    private fun fmt(v: Float): String =
        if (v == v.toInt().toFloat()) v.toInt().toString() else "%.1f".format(v)

    companion object {
        /** План по подходам → JSON для базы и сервера: [{"w":40,"r":10}]. */
        fun planToJson(plan: List<WorkoutSet>): org.json.JSONArray {
            val a = org.json.JSONArray()
            plan.forEach { s ->
                a.put(org.json.JSONObject().put("w", s.weight.toDouble()).put("r", s.reps))
            }
            return a
        }

        /** Обратно. Негодные элементы пропускаются; не больше 20 подходов. */
        fun planFromJson(a: org.json.JSONArray?): List<WorkoutSet> {
            if (a == null) return emptyList()
            val out = ArrayList<WorkoutSet>()
            for (i in 0 until minOf(a.length(), 20)) {
                val o = a.optJSONObject(i) ?: continue
                val w = o.optDouble("w", 0.0).takeIf { !it.isNaN() && it >= 0 } ?: 0.0
                val r = o.optInt("r", 0)
                if (r > 0) out += WorkoutSet(weight = w.toFloat(), reps = r)
            }
            return out
        }

        fun planFromString(s: String?): List<WorkoutSet> =
            if (s.isNullOrBlank()) emptyList()
            else try { planFromJson(org.json.JSONArray(s)) } catch (e: Exception) { emptyList() }
    }
}

data class PlannedWorkout(
    val id: Long = 0,
    val date: LocalDate,
    val hour: Int,
    val minute: Int,
    /**
     * Группы мышц одной строкой через запятую: «Грудь, Трицепс».
     *
     * Не список — намеренно. Это же поле читают сервер и веб-версия,
     * и превратить его в список значило бы сломать разбор им обоим.
     * Разложить обратно помогает [groupTitles].
     */
    val muscleGroup: String,
    val exercises: List<String> = emptyList(),
    /** Цели по упражнениям. Ключ — название упражнения. */
    val targets: Map<String, ExerciseTarget> = emptyMap(),
    /**
     * Когда план был создан или последний раз изменён.
     *
     * Нужно, чтобы отличить «тренировка уже была, а план я составил
     * после» от настоящего выполнения. Без этого план, записанный
     * вечером на сегодня, сразу считался бы выполненным утренней
     * тренировкой, которая к нему отношения не имеет.
     *
     * Ноль означает «неизвестно» — тогда проверка по времени
     * не применяется.
     */
    val createdAt: Long = 0L
) {
    /** Группы по отдельности: «Грудь, Трицепс» -> [Грудь, Трицепс]. */
    val groupTitles: List<String>
        get() = muscleGroup.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}
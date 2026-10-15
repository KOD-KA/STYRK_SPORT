package ru.styrk.app.data.model

import java.time.LocalDate
import java.time.ZoneId

/**
 * Какие планы можно считать выполненными.
 *
 * Считается не по одному плану, а сразу по всем: иначе одна тренировка
 * закрывает каждый подходящий план, и два плана на один день — на 19:01
 * и 19:02 — оба оказываются выполненными после одной тренировки.
 *
 * Поэтому здесь сопоставление один к одному: каждая тренировка
 * закрывает НЕ БОЛЬШЕ ОДНОГО плана.
 *
 * Как выбирается, какой именно:
 *   1. тот же день;
 *   2. хотя бы одна группа мышц совпадает;
 *   3. тренировка закончилась позже, чем план был составлен — иначе
 *      план, записанный вечером на сегодня, засчитывался бы утренней
 *      тренировкой, к нему отношения не имеющей;
 *   4. из оставшихся берётся тот, чьё время ближе к началу тренировки.
 *      Планировали на 19:00 и на 08:00, тренировались утром —
 *      закроется утренний.
 *
 * Тренировки разбираются по порядку, от ранней к поздней: так первая
 * забирает свой план, а не самый удобный.
 *
 * Правило иногда ошибается: если в день «ноги» вы сделали спину, план
 * останется несделанным. Это честнее обратного — считать выполненным
 * что угодно, лишь бы в этот день была тренировка.
 *
 * Живёт отдельным файлом, потому что нужно двум экранам сразу —
 * календарю и стартовому. Две копии одного правила неизбежно
 * разъезжаются: в этом проекте так уже было с окном упражнения.
 */
fun donePlanIds(
    plans: List<PlannedWorkout>,
    workoutsByDate: Map<LocalDate, List<CompletedWorkout>>
): Set<Long> {
    val done = mutableSetOf<Long>()

    plans.groupBy { it.date }.forEach { (date, dayPlans) ->
        val workouts = (workoutsByDate[date] ?: emptyList()).sortedBy { it.dateTime }
        val free = dayPlans.toMutableList()

        workouts.forEach { w ->
            val finished = w.dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            // Сравниваем с НАЧАЛОМ тренировки: dateTime — это момент
            // окончания, и часовая тренировка иначе выглядела бы
            // назначенной на час позже, чем была.
            val started = w.dateTime.minusMinutes(w.durationMinutes.toLong())
            val startedMin = started.hour * 60 + started.minute

            val candidates = free.filter { p ->
                val notEarlier = p.createdAt <= 0L || finished + 60_000 >= p.createdAt
                if (!notEarlier) return@filter false
                val want = p.groupTitles.map { it.lowercase() }
                if (want.isEmpty()) return@filter true
                val got = w.muscleGroup.split(",").map { it.trim().lowercase() }
                got.any { it in want }
            }

            val best = candidates.minByOrNull { p ->
                kotlin.math.abs((p.hour * 60 + p.minute) - startedMin)
            }
            if (best != null) {
                done.add(best.id)
                free.remove(best)
            }
        }
    }
    return done
}

/**
 * План на сегодня, который ещё не выполнен. Если их несколько —
 * тот, что раньше по времени: к нему человек и готовился.
 */
fun todaysPlan(
    plans: List<PlannedWorkout>,
    workoutsByDate: Map<LocalDate, List<CompletedWorkout>>
): PlannedWorkout? {
    val today = LocalDate.now()
    val done = donePlanIds(plans, workoutsByDate)
    return plans
        .filter { it.date == today && it.id !in done }
        .minWithOrNull(compareBy({ it.hour }, { it.minute }))
}
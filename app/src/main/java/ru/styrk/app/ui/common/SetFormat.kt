package ru.styrk.app.ui.common

import ru.styrk.app.data.model.MeasureType
import ru.styrk.app.data.model.WorkoutSet

/**
 * Единая точка форматирования подхода в строку для ВСЕХ экранов
 * (тренировка, архив, итоги). Как выглядит подход каждого типа:
 *
 *  WEIGHT_REPS: "80 × 12"  или  "без веса × 15"
 *  REPS:        "25 повт."
 *  TIME:        "1:30"
 *  DISTANCE:    "5 км · 27:30"
 *  CARDIO:      "нагр. 8 · 12 км/ч · 30:00"
 */
object SetFormat {

    /** Число без лишних нулей: 80 → "80", 82.5 → "82.5" */
    fun num(v: Float): String =
        if (v == v.toInt().toFloat()) v.toInt().toString() else "%.1f".format(v)

    /** Секунды → "мин:сек" */
    fun time(totalSec: Int): String = "%d:%02d".format(totalSec / 60, totalSec % 60)

    fun value(type: MeasureType, set: WorkoutSet): String = when (type) {
        MeasureType.WEIGHT_REPS ->
            if (set.weight <= 0f) "без веса × ${set.reps}"
            else "${num(set.weight)} × ${set.reps}"

        MeasureType.REPS -> "${set.reps} повт."

        MeasureType.TIME -> time(set.seconds ?: 0)

        MeasureType.DISTANCE ->
            "${num(set.distanceKm ?: 0f)} км · ${time(set.seconds ?: 0)}"

        MeasureType.CARDIO -> listOfNotNull(
            set.load?.let { "нагр. ${num(it)}" },
            set.speed?.let { "${num(it)} км/ч" },
            set.seconds?.let { time(it) }
        ).joinToString(" · ").ifEmpty { "—" }
    }
}
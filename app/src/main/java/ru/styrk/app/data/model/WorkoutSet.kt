package ru.styrk.app.data.model

/**
 * Один подход. Какие поля заполнены — зависит от MeasureType упражнения:
 *
 *  WEIGHT_REPS: weight (0 = «без доп. веса») + reps
 *  REPS:        только reps
 *  TIME:        только seconds
 *  DISTANCE:    distanceKm + seconds
 *  CARDIO:      load + speed + seconds
 *
 * Поля с дефолтами — чтобы весь существующий код WorkoutSet(weight, reps)
 * продолжал работать без изменений.
 */
data class WorkoutSet(

    val weight: Float = 0f,

    val reps: Int = 0,

    // Время выполнения/удержания в секундах (TIME / DISTANCE / CARDIO)
    val seconds: Int? = null,

    // Дистанция в километрах (DISTANCE)
    val distanceKm: Float? = null,

    // Уровень нагрузки тренажёра (CARDIO)
    val load: Float? = null,

    // Скорость км/ч (CARDIO)
    val speed: Float? = null
)
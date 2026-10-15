package ru.styrk.app.data.model

import java.time.LocalDateTime

data class ExerciseHistory(
    val exerciseName: String,
    val maxWeight: Float = 0f,
    val maxWeightReps: Int = 0,
    val maxReps: Int = 0,
    val bestVolume: Float = 0f,
    val recordDate: LocalDateTime? = null,
    // Вес атлета в момент рекорда
    val athleteWeight: Float? = null,
    // Подход, давший лучший объём (weight × reps)
    val bestVolumeWeight: Float? = null,
    val bestVolumeReps: Int? = null,

    // ДОБАВЛЕНО (модель v2): тип упражнения и рекорды для новых типов
    val measureType: MeasureType = MeasureType.WEIGHT_REPS,
    val bestSeconds: Int? = null,      // лучшее время (TIME)
    val bestDistanceKm: Float? = null  // лучшая дистанция (DISTANCE)
)
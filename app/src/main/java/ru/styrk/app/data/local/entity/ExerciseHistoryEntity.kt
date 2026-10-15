package ru.styrk.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exercise_history")
data class ExerciseHistoryEntity(

    @PrimaryKey
    val exerciseName: String,

    val maxWeight: Float,
    val maxWeightReps: Int,
    val maxReps: Int,
    val bestVolume: Float,

    val recordDate: Long? = null,
    val athleteWeight: Float? = null,
    // Подход, давший лучший объём
    val bestVolumeWeight: Float? = null,
    val bestVolumeReps: Int? = null,

    // ДОБАВЛЕНО (модель v2, миграция 14->15) — рекорды для новых типов
    // упражнений (заполняются со следующего под-этапа). Null = WEIGHT_REPS.
    val measureType: String? = null,
    val bestSeconds: Int? = null,      // лучшее время (TIME)
    val bestDistanceKm: Float? = null  // лучшая дистанция (DISTANCE)
)
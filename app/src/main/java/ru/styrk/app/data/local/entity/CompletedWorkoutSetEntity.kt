package ru.styrk.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "completed_workout_sets")
data class CompletedWorkoutSetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val workoutId: Long,
    val exerciseName: String,

    val weight: Float,
    val reps: Int,

    // ДОБАВЛЕНО (модель v2, миграция 14->15) — поля для новых типов
    // упражнений. Null для классических вес×повторы.
    val seconds: Int? = null,      // время (TIME / DISTANCE / CARDIO)
    val distanceKm: Float? = null, // дистанция (DISTANCE)
    val load: Float? = null,       // нагрузка тренажёра (CARDIO)
    val speed: Float? = null       // скорость км/ч (CARDIO)
)
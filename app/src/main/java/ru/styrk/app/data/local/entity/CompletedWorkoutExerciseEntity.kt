package ru.styrk.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "completed_workout_exercises")
data class CompletedWorkoutExerciseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val workoutId: Long,

    val exerciseName: String,

    // Группа мышц, к которой относилось упражнение (MuscleGroup.name).
    // Нужно для восстановления группировки после перезапуска приложения
    // и для функции "повторить группу из прошлой тренировки".
    val muscleGroup: String? = null,

    // ДОБАВЛЕНО (модель v2, миграция 14->15): MeasureType.name.
    // Null = WEIGHT_REPS (все старые записи архива).
    val measureType: String? = null
)
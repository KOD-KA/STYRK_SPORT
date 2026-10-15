package ru.styrk.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// Черновик текущей (незавершённой) тренировки — одна строка (id всегда 1).
// Сохраняется при каждом изменении и удаляется при завершении/сбросе
// тренировки, чтобы прогресс не терялся при закрытии приложения.
@Entity(tableName = "in_progress_workout")
data class InProgressWorkoutEntity(
    @PrimaryKey val id: Int = 1,
    val currentScreen: String,
    val athleteWeight: Float?,
    val muscleGroup: String?,
    val workoutStartTime: Long,
    // Список упражнений с подходами, закодированный в простой JSON
    // (через org.json — он есть в самой Android-платформе, без
    // дополнительных зависимостей)
    val exercisesJson: String,
    // Длительность, заданная вручную, — у записи задним числом.
    // Хранить обязательно: без этого прерванная запись прошлой
    // тренировки восстановилась бы как обычная и легла в журнал
    // сегодняшним числом.
    val manualDurationMinutes: Int? = null
)
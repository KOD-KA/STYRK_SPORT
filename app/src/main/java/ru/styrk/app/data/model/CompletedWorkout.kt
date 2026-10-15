package ru.styrk.app.data.model

import java.time.LocalDateTime

data class CompletedWorkout(

    val id: Long = 0,

    val dateTime: LocalDateTime,

    val athleteWeight: Float?,

    val muscleGroup: String,

    val exercises: List<WorkoutExercise>,

    val durationMinutes: Long = 0,

    val notes: String? = null,

    /**
     * Опознавательный знак записи — тот же, что в базе и в обмене.
     *
     * Нужен, чтобы привязать к тренировке заметку тренера: она приходит
     * с сервера отдельно и знает запись только по нему. Номер строки
     * для этого не годится — он у каждого устройства свой.
     */
    val uuid: String = ""
)
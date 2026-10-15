package ru.styrk.app.data.model

import java.time.LocalDateTime

data class CurrentWorkoutState(

    val workoutStartTime: LocalDateTime =
        LocalDateTime.now(),

    val athleteWeight: Float? = null,

    val selectedMuscleGroup: MuscleGroup? = null,

    val selectedExercises: List<WorkoutExercise> = emptyList(),

    val trainingStarted: Boolean = false,

    val currentScreen: String = "start",

    /**
     * Длительность, заданная человеком вручную. Только у тренировки,
     * записанной ЗАДНИМ ЧИСЛОМ.
     *
     * У обычной тренировки длительность считается сама — от нажатия
     * «Начать» до «Завершить». У прошедшей считать нечего: человек
     * вспоминает её, а не проживает. Поэтому он называет и время
     * начала, и сколько занимался.
     *
     * Это же поле служит признаком «задним числом»: не пусто — значит
     * запись о прошлом. Отдельного признака заводить не стал — два
     * поля об одном и том же однажды разойдутся.
     */
    val manualDurationMinutes: Int? = null

) {
    /** Записываем прошедшую тренировку, а не проводим сейчас. */
    val isBackdated: Boolean get() = manualDurationMinutes != null
}
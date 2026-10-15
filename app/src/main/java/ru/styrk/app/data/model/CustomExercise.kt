package ru.styrk.app.data.model

data class CustomExercise(
    val id: Long = 0,
    val name: String,
    val muscleGroup: MuscleGroup,
    // ДОБАВЛЕНО (модель v2): как считается упражнение (выбирается в
    // выпадающем меню при создании). Старые свои упражнения — WEIGHT_REPS.
    val measureType: MeasureType = MeasureType.WEIGHT_REPS,
    /** Своё пояснение к упражнению. Пусто — обычное дело. */
    val description: String? = null,
    /** Путь к снимку во внутренней памяти. */
    val photoPath: String? = null
)
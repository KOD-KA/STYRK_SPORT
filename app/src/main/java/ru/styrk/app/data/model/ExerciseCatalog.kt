package ru.styrk.app.data.model

data class ExerciseCatalog(
    val name: String,
    val muscleGroup: MuscleGroup,
    // ДОБАВЛЕНО (модель v2): как считается упражнение.
    // По умолчанию классика — вес × повторы.
    val measureType: MeasureType = MeasureType.WEIGHT_REPS
)
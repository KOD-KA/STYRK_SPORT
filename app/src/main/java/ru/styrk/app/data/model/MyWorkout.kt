package ru.styrk.app.data.model

/**
 * Своя тренировка в том виде, в каком её показывает и правит экран:
 * название, группы мышц строкой и список названий упражнений
 * по порядку.
 *
 * Разбор на две таблицы остаётся внутри слоя хранения — экрану
 * незачем знать, что упражнения лежат отдельно.
 */
data class MyWorkout(
    val id: Long = 0,
    val title: String,
    val muscleGroup: String,
    val exercises: List<String> = emptyList(),
    val uuid: String = "",
    val updatedAt: Long = 0L
)
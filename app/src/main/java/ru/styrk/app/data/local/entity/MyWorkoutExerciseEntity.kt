package ru.styrk.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Упражнение внутри своей тренировки.
 *
 * Отдельной таблицей, а не строкой через запятую в родителе, —
 * по той же причине, что и у плановых тренировок: названия упражнений
 * задаёт человек, и в них может оказаться любой знак, включая тот,
 * который выбрали разделителем. Отдельные строки не ломаются никогда.
 *
 * Порядок важен: первым идёт самое тяжёлое движение, и менять его
 * местами при чтении нельзя. Поэтому есть номер по порядку —
 * полагаться на порядок выдачи строк базой нельзя.
 */
@Entity(tableName = "my_workout_exercises")
data class MyWorkoutExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val myWorkoutId: Long,
    val exerciseName: String,
    val position: Int = 0
)
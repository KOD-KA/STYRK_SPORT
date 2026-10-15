package ru.styrk.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Упражнение внутри запланированной тренировки.
 *
 * Три поля цели добавлены в версии базы 2. Все три необязательные:
 * план без цифр — это по-прежнему нормальный план, человек просто
 * записал, что собирается делать. Цифры нужны тем, кто ведёт
 * прогрессию и хочет видеть в зале, что перекрыть.
 *
 * Хранится именно то, что человек задумал, а не то, что вышло:
 * сделанное пишется в журнал тренировки отдельно.
 */
@Entity(tableName = "planned_exercises")
data class PlannedExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val exerciseName: String,

    // ═══ ЦЕЛЬ НА ПОДХОД (версия базы 2) ═══
    // null = не задано. Ноль тоже считается «не задано»: подход
    // из нуля повторов смысла не имеет, и отличать его не от чего.
    val targetSets: Int? = null,
    val targetReps: Int? = null,
    val targetWeight: Float? = null,

    // ═══ ПЛАН ПО ПОДХОДАМ (версия базы 8) ═══
    // Что расписал тренер на каждый подход: JSON-массив
    // [{"w":40,"r":10},{"w":42.5,"r":8}]. null — плана по подходам нет,
    // есть только общая цель выше (или ничего).
    val targetPlan: String? = null
)
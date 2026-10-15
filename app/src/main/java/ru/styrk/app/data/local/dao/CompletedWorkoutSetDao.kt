package ru.styrk.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import ru.styrk.app.data.local.entity.CompletedWorkoutSetEntity

@Dao
interface CompletedWorkoutSetDao {

    @Insert
    suspend fun insert(
        set: CompletedWorkoutSetEntity
    )

    // Порядок задан явно. Без ORDER BY SQLite вправе вернуть строки
    // в любом порядке — обычно возвращает по порядку добавления, но
    // не обязан. А порядок входит в отпечаток, по которому тренировки
    // признаются одинаковыми: сдвинется он — и телефон посчитает свою
    // же тренировку чужой, начнёт спрашивать о расхождении и заведёт
    // второй экземпляр. Такое всплывает не сразу и не у всех.
    @Query(
        "SELECT * FROM completed_workout_sets WHERE workoutId = :workoutId ORDER BY id ASC"
    )
    suspend fun getByWorkout(
        workoutId: Long
    ): List<CompletedWorkoutSetEntity>

    @Query("DELETE FROM completed_workout_sets WHERE workoutId = :workoutId")
    suspend fun deleteByWorkout(workoutId: Long)

    @Query("UPDATE completed_workout_sets SET exerciseName = :newName WHERE exerciseName = :oldName")
    suspend fun renameExercise(oldName: String, newName: String)
}
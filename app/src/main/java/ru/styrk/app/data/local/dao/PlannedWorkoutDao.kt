package ru.styrk.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ru.styrk.app.data.local.entity.PlannedExerciseEntity
import ru.styrk.app.data.local.entity.PlannedWorkoutEntity

@Dao
interface PlannedWorkoutDao {

    // ── Тренировки ──────────────────────────────────────────────────
    @Insert
    suspend fun insert(entity: PlannedWorkoutEntity): Long

    @Query("SELECT * FROM planned_workouts ORDER BY date ASC, hour ASC, minute ASC")
    suspend fun getAll(): List<PlannedWorkoutEntity>

    @Query("DELETE FROM planned_workouts WHERE id = :id")
    suspend fun deleteById(id: Long)

    // ── Упражнения ──────────────────────────────────────────────────
    @Insert
    suspend fun insertExercise(entity: PlannedExerciseEntity)

    @Query("SELECT * FROM planned_exercises WHERE workoutId = :workoutId")
    suspend fun getExercises(workoutId: Long): List<PlannedExerciseEntity>

    @Query("DELETE FROM planned_exercises WHERE workoutId = :workoutId")
    suspend fun deleteExercisesByWorkout(workoutId: Long)

    @Query("UPDATE planned_exercises SET exerciseName = :newName WHERE exerciseName = :oldName")
    suspend fun renameExercise(oldName: String, newName: String)

    // ── для синхронизации ────────────────────────────────────────────
    // Список упражнений — часть плановой тренировки, отдельно он не
    // синхронизируется. Поэтому после переименования упражнения
    // помечаем все плановые тренировки как изменённые: их содержимое
    // стало другим.
    @Query("UPDATE planned_workouts SET updatedAt = :now, dirty = 1")
    suspend fun markAllDirty(now: Long)

    @Query("SELECT uuid FROM planned_workouts WHERE id = :id")
    suspend fun getUuid(id: Long): String?

    @Query("SELECT * FROM planned_workouts WHERE uuid = :uuid")
    suspend fun getByUuid(uuid: String): PlannedWorkoutEntity?

    @Query("SELECT * FROM planned_workouts WHERE dirty = 1")
    suspend fun getDirty(): List<PlannedWorkoutEntity>

    @Query("UPDATE planned_workouts SET dirty = 0 WHERE uuid IN (:uuids)")
    suspend fun clearDirty(uuids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PlannedWorkoutEntity): Long
}

package ru.styrk.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ru.styrk.app.data.local.entity.CompletedWorkoutEntity

@Dao
interface WorkoutHistoryDao {

    @Insert
    suspend fun insertWorkout(
        workout: CompletedWorkoutEntity
    ): Long

    @Query(
        "SELECT * FROM completed_workouts ORDER BY date DESC"
    )
    suspend fun getAllWorkouts():
            List<CompletedWorkoutEntity>

    // Правка заметок — это изменение записи, поэтому здесь же обновляем
    // время правки и помечаем строку неотправленной.
    @Query("UPDATE completed_workouts SET notes = :notes, updatedAt = :now, dirty = 1 WHERE id = :id")
    suspend fun updateNotes(id: Long, notes: String?, now: Long)

    @Query("DELETE FROM completed_workouts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query(
        "DELETE FROM completed_workouts"
    )
    suspend fun clear()

    // ── для синхронизации ────────────────────────────────────────────
    @Query("SELECT uuid FROM completed_workouts WHERE id = :id")
    suspend fun getUuid(id: Long): String?

    @Query("SELECT * FROM completed_workouts WHERE uuid = :uuid")
    suspend fun getByUuid(uuid: String): CompletedWorkoutEntity?

    @Query("SELECT * FROM completed_workouts WHERE dirty = 1")
    suspend fun getDirty(): List<CompletedWorkoutEntity>

    @Query("UPDATE completed_workouts SET dirty = 0 WHERE uuid IN (:uuids)")
    suspend fun clearDirty(uuids: List<String>)

    // Приём записи с сервера: строка с таким uuid заменяется целиком.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(workout: CompletedWorkoutEntity): Long
}

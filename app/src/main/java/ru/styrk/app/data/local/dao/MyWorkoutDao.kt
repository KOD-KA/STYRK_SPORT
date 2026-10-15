package ru.styrk.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ru.styrk.app.data.local.entity.MyWorkoutEntity
import ru.styrk.app.data.local.entity.MyWorkoutExerciseEntity

/**
 * Свои тренировки-образцы.
 *
 * Набор запросов повторяет PlannedWorkoutDao: родитель и дети,
 * плюс шесть запросов для обмена. Повторяет не по недосмотру —
 * так у всех синхронизируемых записей одинаковая форма, и человеку,
 * который будет читать это после нас, не придётся выяснять,
 * чем эта таблица особенная. Она не особенная.
 */
@Dao
interface MyWorkoutDao {

    @Insert
    suspend fun insert(entity: MyWorkoutEntity): Long

    @Query("SELECT * FROM my_workouts ORDER BY updatedAt DESC")
    suspend fun getAll(): List<MyWorkoutEntity>

    @Query("SELECT * FROM my_workouts WHERE id = :id")
    suspend fun getById(id: Long): MyWorkoutEntity?

    @Query("DELETE FROM my_workouts WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Нужен для способа «заменить данные телефона данными с сервера». */
    @Query("DELETE FROM my_workouts")
    suspend fun clear()

    // ── упражнения внутри ────────────────────────────────────────────
    @Insert
    suspend fun insertExercise(entity: MyWorkoutExerciseEntity)

    @Query("SELECT * FROM my_workout_exercises WHERE myWorkoutId = :id ORDER BY position ASC")
    suspend fun getExercises(id: Long): List<MyWorkoutExerciseEntity>

    @Query("DELETE FROM my_workout_exercises WHERE myWorkoutId = :id")
    suspend fun deleteExercises(id: Long)

    /**
     * Переименовали своё упражнение — переименовать и в образцах.
     *
     * Без этого образец продолжал бы ссылаться на несуществующее имя,
     * и при запуске тренировки упражнение молча пропадало бы из набора.
     */
    @Query("UPDATE my_workout_exercises SET exerciseName = :newName WHERE exerciseName = :oldName")
    suspend fun renameExercise(oldName: String, newName: String)

    // ── для синхронизации ────────────────────────────────────────────
    @Query("SELECT uuid FROM my_workouts WHERE id = :id")
    suspend fun getUuid(id: Long): String?

    @Query("SELECT * FROM my_workouts WHERE uuid = :uuid")
    suspend fun getByUuid(uuid: String): MyWorkoutEntity?

    @Query("SELECT * FROM my_workouts WHERE dirty = 1")
    suspend fun getDirty(): List<MyWorkoutEntity>

    @Query("UPDATE my_workouts SET dirty = 0 WHERE uuid IN (:uuids)")
    suspend fun clearDirty(uuids: List<String>)

    /** После входа в аккаунт всё помечается как неотправленное. */
    @Query("UPDATE my_workouts SET updatedAt = :now, dirty = 1")
    suspend fun markAllDirty(now: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MyWorkoutEntity): Long
}
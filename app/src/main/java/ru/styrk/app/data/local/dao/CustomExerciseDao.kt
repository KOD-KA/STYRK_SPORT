package ru.styrk.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ru.styrk.app.data.local.entity.CustomExerciseEntity

@Dao
interface CustomExerciseDao {

    @Insert
    suspend fun insert(entity: CustomExerciseEntity): Long

    @Query("SELECT * FROM custom_exercises ORDER BY name ASC")
    suspend fun getAll(): List<CustomExerciseEntity>

    // Нужен для способа «заменить данные телефона данными с сервера»
    @Query("DELETE FROM custom_exercises")
    suspend fun clear()

    /**
     * Убрать одну запись по номеру.
     *
     * Человеку удаление своих упражнений не даётся нарочно: на них
     * ссылаются прошлые тренировки. Этот способ существует только для
     * склейки двойников — когда одно и то же упражнение приехало
     * с двух устройств под разными опознавательными знаками.
     */
    @Query("DELETE FROM custom_exercises WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE custom_exercises SET name = :newName, updatedAt = :now, dirty = 1 WHERE id = :id")
    suspend fun rename(id: Long, newName: String, now: Long)

    /**
     * Описание и снимок правятся вместе — они в одном окне и меняются
     * одним нажатием «Сохранить». Двумя запросами запись пометилась бы
     * изменённой дважды и уехала на сервер дважды.
     */
    @Query("UPDATE custom_exercises SET description = :description, photoPath = :photoPath, " +
           "updatedAt = :now, dirty = 1 WHERE id = :id")
    suspend fun setDetails(id: Long, description: String?, photoPath: String?, now: Long)

    // ── для синхронизации ────────────────────────────────────────────
    @Query("SELECT uuid FROM custom_exercises WHERE id = :id")
    suspend fun getUuid(id: Long): String?

    @Query("SELECT * FROM custom_exercises WHERE uuid = :uuid")
    suspend fun getByUuid(uuid: String): CustomExerciseEntity?

    @Query("SELECT * FROM custom_exercises WHERE dirty = 1")
    suspend fun getDirty(): List<CustomExerciseEntity>

    @Query("UPDATE custom_exercises SET dirty = 0 WHERE uuid IN (:uuids)")
    suspend fun clearDirty(uuids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CustomExerciseEntity): Long
}
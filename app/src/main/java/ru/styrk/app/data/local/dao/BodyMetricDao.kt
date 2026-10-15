package ru.styrk.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ru.styrk.app.data.local.entity.BodyMetricEntity

@Dao
interface BodyMetricDao {

    @Insert
    suspend fun insert(entity: BodyMetricEntity): Long

    @Query("SELECT * FROM body_metrics ORDER BY date ASC")
    suspend fun getAll(): List<BodyMetricEntity>

    @Query("DELETE FROM body_metrics WHERE id = :id")
    suspend fun deleteById(id: Long)

    // ── для синхронизации ────────────────────────────────────────────
    @Query("SELECT uuid FROM body_metrics WHERE id = :id")
    suspend fun getUuid(id: Long): String?

    @Query("SELECT * FROM body_metrics WHERE uuid = :uuid")
    suspend fun getByUuid(uuid: String): BodyMetricEntity?

    @Query("SELECT * FROM body_metrics WHERE dirty = 1")
    suspend fun getDirty(): List<BodyMetricEntity>

    @Query("UPDATE body_metrics SET dirty = 0 WHERE uuid IN (:uuids)")
    suspend fun clearDirty(uuids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BodyMetricEntity): Long
}

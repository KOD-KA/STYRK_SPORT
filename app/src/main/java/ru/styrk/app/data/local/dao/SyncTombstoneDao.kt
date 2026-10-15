package ru.styrk.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ru.styrk.app.data.local.entity.SyncTombstoneEntity

@Dao
interface SyncTombstoneDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: SyncTombstoneEntity)

    @Query("SELECT * FROM sync_tombstones")
    suspend fun getAll(): List<SyncTombstoneEntity>

    @Query("DELETE FROM sync_tombstones WHERE kind = :kind AND uuid = :uuid")
    suspend fun remove(kind: String, uuid: String)

    @Query("DELETE FROM sync_tombstones")
    suspend fun clear()
}

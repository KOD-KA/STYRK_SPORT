package ru.styrk.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ru.styrk.app.data.local.entity.UserProfileEntity

@Dao
interface UserProfileDao {

    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun get(): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: UserProfileEntity)

    // ── для синхронизации ────────────────────────────────────────────
    @Query("SELECT * FROM user_profile WHERE dirty = 1")
    suspend fun getDirty(): List<UserProfileEntity>

    @Query("UPDATE user_profile SET dirty = 0 WHERE id = 1")
    suspend fun clearDirty()
}

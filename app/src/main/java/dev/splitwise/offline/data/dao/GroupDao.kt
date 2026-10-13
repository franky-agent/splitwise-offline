package dev.splitwise.offline.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import dev.splitwise.offline.data.entity.GroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {

    @Query("SELECT * FROM groups ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups WHERE id = :id")
    fun observeById(id: String): Flow<GroupEntity?>

    @Query("SELECT * FROM groups WHERE id = :id")
    suspend fun getById(id: String): GroupEntity?

    @Query("SELECT id, contentHash FROM groups")
    suspend fun allHashes(): List<HashRow>

    @Upsert
    suspend fun upsert(entity: GroupEntity)

    @Query("DELETE FROM groups WHERE id = :id")
    suspend fun delete(id: String)

    /** Lightweight projection used for sync preflight ("do we already agree?"). */
    data class HashRow(val id: String, val contentHash: String)
}
package com.wenbo.habitrecord.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RewardDao {

    @Query("SELECT * FROM rewards WHERE archived = 0 ORDER BY pointsCost, id")
    fun observeActive(): Flow<List<RewardEntity>>

    @Query("SELECT * FROM rewards ORDER BY archived, pointsCost, id")
    fun observeAll(): Flow<List<RewardEntity>>

    @Query("SELECT * FROM rewards WHERE id = :id")
    suspend fun getById(id: Long): RewardEntity?

    @Query("SELECT * FROM rewards")
    suspend fun getAll(): List<RewardEntity>

    @Query("SELECT COUNT(*) FROM rewards")
    suspend fun count(): Int

    @Insert
    suspend fun insert(reward: RewardEntity): Long

    @Update
    suspend fun update(reward: RewardEntity)

    @Delete
    suspend fun delete(reward: RewardEntity)

    @Query("DELETE FROM rewards")
    suspend fun deleteAll()

    suspend fun insertAll(rewards: List<RewardEntity>) {
        rewards.forEach { insert(it) }
    }
}

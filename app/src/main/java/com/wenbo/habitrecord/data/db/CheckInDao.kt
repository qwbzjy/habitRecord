package com.wenbo.habitrecord.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CheckInDao {

    @Query("SELECT * FROM check_ins WHERE habitId = :habitId AND date = :date")
    suspend fun get(habitId: Long, date: Long): CheckInEntity?

    @Query("SELECT * FROM check_ins WHERE habitId = :habitId")
    suspend fun getByHabit(habitId: Long): List<CheckInEntity>

    @Query("SELECT * FROM check_ins WHERE habitId = :habitId AND date BETWEEN :from AND :to")
    suspend fun getByHabitBetween(habitId: Long, from: Long, to: Long): List<CheckInEntity>

    @Query("SELECT * FROM check_ins WHERE habitId = :habitId AND status = :status AND date < :beforeDate")
    suspend fun getByHabitStatusBefore(habitId: Long, beforeDate: Long, status: Int): List<CheckInEntity>

    @Query("SELECT * FROM check_ins")
    fun observeAll(): Flow<List<CheckInEntity>>

    @Query("SELECT * FROM check_ins")
    suspend fun getAll(): List<CheckInEntity>

    @Query("SELECT COUNT(*) FROM check_ins WHERE status = :status")
    suspend fun countByStatus(status: Int): Int

    /** 唯一索引 (habitId, date) 冲突时静默忽略，用于惰性补建 PENDING 行 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoring(checkIn: CheckInEntity): Long

    @Insert
    suspend fun insert(checkIn: CheckInEntity): Long

    @Update
    suspend fun update(checkIn: CheckInEntity)

    @Delete
    suspend fun delete(checkIn: CheckInEntity)

    @Query("DELETE FROM check_ins")
    suspend fun deleteAll()

    suspend fun insertAll(checkIns: List<CheckInEntity>) {
        checkIns.forEach { insert(it) }
    }
}

package com.wenbo.habitrecord.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {

    @Query("SELECT * FROM points_ledger ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<PointsLedgerEntity>>

    @Query("SELECT * FROM points_ledger WHERE reason = :reason ORDER BY createdAt DESC, id DESC")
    fun observeByReason(reason: String): Flow<List<PointsLedgerEntity>>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM points_ledger")
    fun observeBalance(): Flow<Int>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM points_ledger")
    suspend fun balance(): Int

    /** 幂等去重键：(habitId, date, reason) */
    @Query("SELECT * FROM points_ledger WHERE habitId = :habitId AND date = :date AND reason = :reason LIMIT 1")
    suspend fun find(habitId: Long, date: Long, reason: String): PointsLedgerEntity?

    @Query("SELECT * FROM points_ledger")
    suspend fun getAll(): List<PointsLedgerEntity>

    @Insert
    suspend fun insert(entry: PointsLedgerEntity): Long

    @Delete
    suspend fun delete(entry: PointsLedgerEntity)

    @Query("DELETE FROM points_ledger")
    suspend fun deleteAll()

    suspend fun insertAll(entries: List<PointsLedgerEntity>) {
        entries.forEach { insert(it) }
    }
}

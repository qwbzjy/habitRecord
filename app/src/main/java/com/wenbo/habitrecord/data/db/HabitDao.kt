package com.wenbo.habitrecord.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {

    @Query("SELECT * FROM habits WHERE archived = 0 ORDER BY sortOrder, id")
    fun observeActive(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits ORDER BY archived, sortOrder, id")
    fun observeAll(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun getById(id: Long): HabitEntity?

    @Query("SELECT * FROM habits WHERE archived = 0 ORDER BY sortOrder, id")
    suspend fun getActive(): List<HabitEntity>

    @Query("SELECT * FROM habits")
    suspend fun getAll(): List<HabitEntity>

    @Query("SELECT MAX(id) FROM habits")
    suspend fun maxId(): Long?

    @Query("SELECT COUNT(*) FROM habits")
    suspend fun count(): Int

    /** 仅用于新建（id == 0）。更新一律走 [update]，避免 REPLACE 触发级联删除。 */
    @Insert
    suspend fun insert(habit: HabitEntity): Long

    @Update
    suspend fun update(habit: HabitEntity)

    @Delete
    suspend fun delete(habit: HabitEntity)

    @Query("UPDATE habits SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    @Query("DELETE FROM habits")
    suspend fun deleteAll()

    suspend fun insertAll(habits: List<HabitEntity>) {
        habits.forEach { insert(it) }
    }
}

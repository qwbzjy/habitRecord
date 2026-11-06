package com.wenbo.habitrecord.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wenbo.habitrecord.domain.model.FrequencyType

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val emoji: String = "✅",
    val frequencyType: FrequencyType = FrequencyType.DAILY,
    /** WEEKDAYS 类型用："1,3,5"（1=周一 … 7=周日） */
    val weekdays: String = "",
    /** TIMES_PER_WEEK 类型用：每周目标次数 */
    val timesPerWeek: Int = 3,
    /** 完成一次得分 */
    val pointsOnComplete: Int = 10,
    /** 逾期未完成扣分 */
    val pointsOnMiss: Int = 5,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

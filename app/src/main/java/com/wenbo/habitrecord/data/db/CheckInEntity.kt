package com.wenbo.habitrecord.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 每个应打卡日一行；当天的记录在 App 打开时惰性创建（PENDING）。 */
@Entity(
    tableName = "check_ins",
    indices = [Index(value = ["habitId", "date"], unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class CheckInEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val habitId: Long,
    /** epoch day（LocalDate.toEpochDay()） */
    val date: Long,
    /** 取值见 [com.wenbo.habitrecord.domain.model.CheckInStatus] */
    val status: Int,
)

package com.wenbo.habitrecord.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rewards")
data class RewardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val emoji: String = "🎁",
    val pointsCost: Int = 50,
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

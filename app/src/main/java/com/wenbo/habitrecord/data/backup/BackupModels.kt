package com.wenbo.habitrecord.data.backup

import kotlinx.serialization.Serializable

/** 备份文件 DTO（与 Room 实体解耦，保留 id 以便完整恢复关联关系）。 */
@Serializable
data class BackupV1(
    val version: Int = BACKUP_VERSION,
    val exportedAt: Long,
    val habits: List<HabitBackup> = emptyList(),
    val checkIns: List<CheckInBackup> = emptyList(),
    val ledger: List<LedgerBackup> = emptyList(),
    val rewards: List<RewardBackup> = emptyList(),
) {
    companion object {
        const val BACKUP_VERSION = 1
    }
}

@Serializable
data class HabitBackup(
    val id: Long,
    val name: String,
    val emoji: String = "✅",
    val frequencyType: String = "DAILY",
    val weekdays: String = "",
    val timesPerWeek: Int = 3,
    val pointsOnComplete: Int = 10,
    val pointsOnMiss: Int = 5,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = 0L,
)

@Serializable
data class CheckInBackup(
    val id: Long,
    val habitId: Long,
    val date: Long,
    val status: Int,
)

@Serializable
data class LedgerBackup(
    val id: Long,
    val amount: Int,
    val reason: String,
    val habitId: Long? = null,
    val rewardId: Long? = null,
    val date: Long? = null,
    val note: String? = null,
    val createdAt: Long = 0L,
)

@Serializable
data class RewardBackup(
    val id: Long,
    val name: String,
    val emoji: String = "🎁",
    val pointsCost: Int = 50,
    val archived: Boolean = false,
    val createdAt: Long = 0L,
)

package com.wenbo.habitrecord.data.backup

import androidx.room.withTransaction
import com.wenbo.habitrecord.data.db.AppDatabase
import com.wenbo.habitrecord.data.db.CheckInEntity
import com.wenbo.habitrecord.data.db.HabitEntity
import com.wenbo.habitrecord.data.db.PointsLedgerEntity
import com.wenbo.habitrecord.data.db.RewardEntity
import com.wenbo.habitrecord.domain.model.FrequencyType

/** 备份的导出与两种导入（整体替换 / 名称合并）。 */
class BackupManager(private val db: AppDatabase) {

    private val habitDao = db.habitDao()
    private val checkInDao = db.checkInDao()
    private val ledgerDao = db.ledgerDao()
    private val rewardDao = db.rewardDao()

    suspend fun export(): BackupV1 = BackupV1(
        exportedAt = System.currentTimeMillis(),
        habits = habitDao.getAll().map { it.toBackup() },
        checkIns = checkInDao.getAll().map { it.toBackup() },
        ledger = ledgerDao.getAll().map { it.toBackup() },
        rewards = rewardDao.getAll().map { it.toBackup() },
    )

    /** 整体替换：清空本地全部数据后按备份原样恢复（保留原 id 与关联）。 */
    suspend fun importReplace(backup: BackupV1) = db.withTransaction {
        checkInDao.deleteAll()
        ledgerDao.deleteAll()
        rewardDao.deleteAll()
        habitDao.deleteAll()
        habitDao.insertAll(backup.habits.map { it.toEntity() })
        checkInDao.insertAll(backup.checkIns.map { it.toEntity() })
        ledgerDao.insertAll(backup.ledger.map { it.toEntity() })
        rewardDao.insertAll(backup.rewards.map { it.toEntity() })
    }

    /** 合并导入：按 BackupMerger 规划执行插入。返回实际插入的条数摘要。 */
    suspend fun importMerge(backup: BackupV1): String = db.withTransaction {
        val plan = BackupMerger.plan(
            existingHabits = habitDao.getAll(),
            existingRewards = rewardDao.getAll(),
            existingCheckIns = checkInDao.getAll(),
            existingLedger = ledgerDao.getAll(),
            backup = backup,
        )
        habitDao.insertAll(plan.habitsToInsert)
        rewardDao.insertAll(plan.rewardsToInsert)
        checkInDao.insertAll(plan.checkInsToInsert)
        ledgerDao.insertAll(plan.ledgerToInsert)
        plan.adjustEntry?.let { ledgerDao.insert(it) }
        "合并完成：新增习惯 ${plan.habitsToInsert.size}、奖励 ${plan.rewardsToInsert.size}、" +
            "打卡 ${plan.checkInsToInsert.size} 条"
    }

    private fun HabitEntity.toBackup() = HabitBackup(
        id = id, name = name, emoji = emoji,
        frequencyType = frequencyType.name,
        weekdays = weekdays, timesPerWeek = timesPerWeek,
        pointsOnComplete = pointsOnComplete, pointsOnMiss = pointsOnMiss,
        archived = archived, sortOrder = sortOrder, createdAt = createdAt,
    )

    private fun CheckInEntity.toBackup() = CheckInBackup(id = id, habitId = habitId, date = date, status = status)

    private fun PointsLedgerEntity.toBackup() = LedgerBackup(
        id = id, amount = amount, reason = reason,
        habitId = habitId, rewardId = rewardId, date = date,
        note = note, createdAt = createdAt,
    )

    private fun RewardEntity.toBackup() = RewardBackup(
        id = id, name = name, emoji = emoji,
        pointsCost = pointsCost, archived = archived, createdAt = createdAt,
    )

    private fun HabitBackup.toEntity() = HabitEntity(
        id = id, name = name, emoji = emoji,
        frequencyType = FrequencyType.entries.firstOrNull { it.name == frequencyType } ?: FrequencyType.DAILY,
        weekdays = weekdays, timesPerWeek = timesPerWeek,
        pointsOnComplete = pointsOnComplete, pointsOnMiss = pointsOnMiss,
        archived = archived, sortOrder = sortOrder, createdAt = createdAt,
    )

    private fun CheckInBackup.toEntity() = CheckInEntity(id = id, habitId = habitId, date = date, status = status)

    private fun LedgerBackup.toEntity() = PointsLedgerEntity(
        id = id, amount = amount, reason = reason,
        habitId = habitId, rewardId = rewardId, date = date,
        note = note, createdAt = createdAt,
    )

    private fun RewardBackup.toEntity() = RewardEntity(
        id = id, name = name, emoji = emoji,
        pointsCost = pointsCost, archived = archived, createdAt = createdAt,
    )
}

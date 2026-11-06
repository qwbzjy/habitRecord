package com.wenbo.habitrecord.data.backup

import com.wenbo.habitrecord.data.db.CheckInEntity
import com.wenbo.habitrecord.data.db.HabitEntity
import com.wenbo.habitrecord.data.db.PointsLedgerEntity
import com.wenbo.habitrecord.data.db.RewardEntity
import com.wenbo.habitrecord.domain.model.FrequencyType
import com.wenbo.habitrecord.domain.model.LedgerReason

/** 合并导入计划：给出应插入的行与主键重映射，执行交给 BackupManager。 */
data class MergePlan(
    val habitsToInsert: List<HabitEntity>,
    val rewardsToInsert: List<RewardEntity>,
    val checkInsToInsert: List<CheckInEntity>,
    val ledgerToInsert: List<PointsLedgerEntity>,
    /** 差额校准流水（补齐 REDEEM 等未合并行带来的余额差），amount==0 时为 null */
    val adjustEntry: PointsLedgerEntity?,
)

/**
 * 纯函数：规划合并导入（不碰数据库，便于单测）。
 *
 * 规则：
 * - 习惯/奖励按名称匹配：重名视为同一条（保留本地 id），否则作为新条目插入；
 * - 打卡按 (重映射后的 habitId, date) 去重，本地已有则跳过；
 * - CHECKIN/MISS 流水按 (重映射 habitId, date, reason) 去重；REDEEM/ADJUST 不合并；
 * - 最后追加一笔 ADJUST 校准流水，使合并后余额等于备份文件中的余额。
 */
object BackupMerger {

    fun plan(
        existingHabits: List<HabitEntity>,
        existingRewards: List<RewardEntity>,
        existingCheckIns: List<CheckInEntity>,
        existingLedger: List<PointsLedgerEntity>,
        backup: BackupV1,
    ): MergePlan {
        val localHabitIds = existingHabits.map { it.id }.toSet()
        var nextHabitId = (localHabitIds.maxOrNull() ?: 0L) + 1L
        val habitIdMap = mutableMapOf<Long, Long>()
        val habitsToInsert = mutableListOf<HabitEntity>()
        for (hb in backup.habits) {
            val matched = existingHabits.firstOrNull { it.name.trim() == hb.name.trim() }
            if (matched != null) {
                habitIdMap[hb.id] = matched.id
            } else {
                habitIdMap[hb.id] = nextHabitId
                habitsToInsert += hb.toEntity(nextHabitId)
                nextHabitId++
            }
        }

        val localRewardIds = existingRewards.map { it.id }.toSet()
        var nextRewardId = (localRewardIds.maxOrNull() ?: 0L) + 1L
        val rewardIdMap = mutableMapOf<Long, Long>()
        val rewardsToInsert = mutableListOf<RewardEntity>()
        for (rb in backup.rewards) {
            val matched = existingRewards.firstOrNull { it.name.trim() == rb.name.trim() }
            if (matched != null) {
                rewardIdMap[rb.id] = matched.id
            } else {
                rewardIdMap[rb.id] = nextRewardId
                rewardsToInsert += rb.toEntity(nextRewardId)
                nextRewardId++
            }
        }

        val seenCheckIn = existingCheckIns.map { it.habitId to it.date }.toMutableSet()
        val checkInsToInsert = mutableListOf<CheckInEntity>()
        for (cb in backup.checkIns) {
            val habitId = habitIdMap[cb.habitId] ?: continue // 引用了备份外/无法映射的习惯，丢弃
            val key = habitId to cb.date
            if (key in seenCheckIn) continue
            seenCheckIn += key
            checkInsToInsert += CheckInEntity(habitId = habitId, date = cb.date, status = cb.status)
        }

        val seenLedger = existingLedger
            .filter { it.habitId != null && it.date != null }
            .map { Triple(it.habitId!!, it.date!!, it.reason) }
            .toMutableSet()
        val ledgerToInsert = mutableListOf<PointsLedgerEntity>()
        for (lb in backup.ledger) {
            when (reasonOrNull(lb.reason)) {
                LedgerReason.CHECKIN, LedgerReason.MISS -> {
                    val habitId = lb.habitId?.let { habitIdMap[it] } ?: continue
                    val date = lb.date ?: continue
                    val key = Triple(habitId, date, lb.reason)
                    if (key in seenLedger) continue
                    seenLedger += key
                    ledgerToInsert += lb.toEntity(habitId = habitId)
                }
                else -> Unit // REDEEM/ADJUST 不合并，用校准流水补齐余额
            }
        }

        val backupBalance = backup.ledger.sumOf { it.amount }
        val mergedBalance = existingLedger.sumOf { it.amount } + ledgerToInsert.sumOf { it.amount }
        val adjust = backupBalance - mergedBalance
        val adjustEntry = if (adjust != 0) {
            PointsLedgerEntity(
                amount = adjust.toInt(),
                reason = LedgerReason.ADJUST.name,
                note = "合并导入差额校准",
            )
        } else {
            null
        }

        return MergePlan(habitsToInsert, rewardsToInsert, checkInsToInsert, ledgerToInsert, adjustEntry)
    }

    private fun reasonOrNull(name: String): LedgerReason? =
        LedgerReason.entries.firstOrNull { it.name == name }

    private fun HabitBackup.toEntity(id: Long) = HabitEntity(
        id = id,
        name = name,
        emoji = emoji,
        frequencyType = FrequencyType.entries.firstOrNull { it.name == frequencyType } ?: FrequencyType.DAILY,
        weekdays = weekdays,
        timesPerWeek = timesPerWeek,
        pointsOnComplete = pointsOnComplete,
        pointsOnMiss = pointsOnMiss,
        archived = archived,
        sortOrder = sortOrder,
        createdAt = createdAt,
    )

    private fun RewardBackup.toEntity(id: Long) = RewardEntity(
        id = id,
        name = name,
        emoji = emoji,
        pointsCost = pointsCost,
        archived = archived,
        createdAt = createdAt,
    )

    private fun LedgerBackup.toEntity(habitId: Long?) = PointsLedgerEntity(
        amount = amount,
        reason = reason,
        habitId = habitId,
        rewardId = null,
        date = date,
        note = note,
        createdAt = createdAt,
    )
}

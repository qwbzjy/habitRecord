package com.wenbo.habitrecord.data.backup

import com.wenbo.habitrecord.data.db.CheckInEntity
import com.wenbo.habitrecord.data.db.HabitEntity
import com.wenbo.habitrecord.data.db.PointsLedgerEntity
import com.wenbo.habitrecord.data.db.RewardEntity
import com.wenbo.habitrecord.domain.model.CheckInStatus
import com.wenbo.habitrecord.domain.model.LedgerReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupMergerTest {

    private val existingHabit = HabitEntity(id = 10, name = "阅读", emoji = "📚")
    private val existingReward = RewardEntity(id = 20, name = "电影", pointsCost = 50)

    @Test
    fun `matching names reuse local ids, others inserted`() {
        val backup = BackupV1(
            exportedAt = 1L,
            habits = listOf(
                HabitBackup(id = 5, name = "阅读", emoji = "📖"),
                HabitBackup(id = 6, name = "冥想"),
            ),
            rewards = listOf(
                RewardBackup(id = 7, name = "电影"),
                RewardBackup(id = 8, name = "奶茶"),
            ),
        )
        val plan = BackupMerger.plan(
            existingHabits = listOf(existingHabit),
            existingRewards = listOf(existingReward),
            existingCheckIns = emptyList(),
            existingLedger = emptyList(),
            backup = backup,
        )
        // 重名的沿用本地 id；「冥想」作为新条目插入
        assertEquals(1, plan.habitsToInsert.size)
        assertEquals(1, plan.rewardsToInsert.size)
        assertEquals("奶茶", plan.rewardsToInsert[0].name)
        // 新条目分配在现有最大 id 之后：「冥想」插入为 11
        assertEquals(1, plan.habitsToInsert.size)
        assertEquals("冥想", plan.habitsToInsert[0].name)
        assertEquals(11L, plan.habitsToInsert[0].id)
        assertEquals(21L, plan.rewardsToInsert[0].id)
        // 重名映射：备份习惯 5 → 本地 10
        val checkInPlan = BackupMerger.plan(
            existingHabits = listOf(existingHabit),
            existingRewards = listOf(existingReward),
            existingCheckIns = emptyList(),
            existingLedger = emptyList(),
            backup = backup.copy(
                checkIns = listOf(CheckInBackup(id = 1, habitId = 5, date = 100, status = 1)),
            ),
        )
        assertEquals(10L, checkInPlan.checkInsToInsert.single().habitId)
    }

    @Test
    fun `check ins and ledger dedup on existing rows`() {
        val backup = BackupV1(
            exportedAt = 1L,
            habits = listOf(HabitBackup(id = 5, name = "阅读")),
            checkIns = listOf(
                CheckInBackup(id = 1, habitId = 5, date = 100, status = CheckInStatus.DONE.value),
                CheckInBackup(id = 2, habitId = 5, date = 101, status = CheckInStatus.DONE.value),
            ),
            ledger = listOf(
                LedgerBackup(id = 1, amount = 10, reason = LedgerReason.CHECKIN.name, habitId = 5, date = 100),
                LedgerBackup(id = 2, amount = 10, reason = LedgerReason.CHECKIN.name, habitId = 5, date = 101),
                LedgerBackup(id = 3, amount = -50, reason = LedgerReason.REDEEM.name, rewardId = 7),
            ),
        )
        val plan = BackupMerger.plan(
            existingHabits = listOf(existingHabit),
            existingRewards = listOf(existingReward),
            existingCheckIns = listOf(CheckInEntity(habitId = 10, date = 100, status = CheckInStatus.DONE.value)),
            existingLedger = listOf(
                PointsLedgerEntity(id = 1, amount = 10, reason = LedgerReason.CHECKIN.name, habitId = 10, date = 100),
            ),
            backup = backup,
        )
        // date=100 已存在被跳过，date=101 插入
        assertEquals(1, plan.checkInsToInsert.size)
        assertEquals(101L, plan.checkInsToInsert[0].date)
        // date=100 的 CHECKIN 已存在被跳过；REDEEM 不合并
        assertEquals(1, plan.ledgerToInsert.size)
        assertEquals(101L, plan.ledgerToInsert[0].date)
        // 余额校准：备份余额 = 10+10-50 = -30；合并后 = 10(本地) + 10(新) = 20；差额 = -50
        assertEquals(-50, plan.adjustEntry?.amount)
        assertEquals(LedgerReason.ADJUST.name, plan.adjustEntry?.reason)
    }

    @Test
    fun `no adjust entry when balances already match`() {
        val backup = BackupV1(
            exportedAt = 1L,
            habits = listOf(HabitBackup(id = 5, name = "阅读")),
            ledger = listOf(
                LedgerBackup(id = 1, amount = 10, reason = LedgerReason.CHECKIN.name, habitId = 5, date = 100),
            ),
        )
        val plan = BackupMerger.plan(
            existingHabits = listOf(existingHabit),
            existingRewards = listOf(existingReward),
            existingCheckIns = emptyList(),
            existingLedger = listOf(
                PointsLedgerEntity(id = 1, amount = 10, reason = LedgerReason.CHECKIN.name, habitId = 10, date = 100),
            ),
            backup = backup,
        )
        // date=100 已存在跳过，本地余额 10 == 备份余额 10
        assertEquals(0, plan.ledgerToInsert.size)
        assertNull(plan.adjustEntry)
    }

    @Test
    fun `check ins referencing unknown habit are dropped`() {
        val backup = BackupV1(
            exportedAt = 1L,
            checkIns = listOf(CheckInBackup(id = 1, habitId = 999, date = 100, status = 1)),
        )
        val plan = BackupMerger.plan(
            existingHabits = listOf(existingHabit),
            existingRewards = listOf(existingReward),
            existingCheckIns = emptyList(),
            existingLedger = emptyList(),
            backup = backup,
        )
        assertEquals(0, plan.checkInsToInsert.size)
    }
}

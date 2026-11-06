package com.wenbo.habitrecord.data.repository

import androidx.room.withTransaction
import com.wenbo.habitrecord.data.db.AppDatabase
import com.wenbo.habitrecord.data.db.CheckInEntity
import com.wenbo.habitrecord.data.db.HabitEntity
import com.wenbo.habitrecord.data.db.PointsLedgerEntity
import com.wenbo.habitrecord.data.db.RewardEntity
import com.wenbo.habitrecord.domain.FrequencyPolicy
import com.wenbo.habitrecord.domain.createdOn
import com.wenbo.habitrecord.domain.model.CheckInStatus
import com.wenbo.habitrecord.domain.model.FrequencyType
import com.wenbo.habitrecord.domain.model.LedgerReason
import java.time.LocalDate

/** 兑换结果。 */
sealed interface RedeemResult {
    data object Success : RedeemResult
    data object Insufficient : RedeemResult
}

/**
 * 打卡相关的一切写路径：打卡/补卡、取消、跳过、惰性结算（PENDING→MISS 扣分、
 * 周结算）、兑换奖励。所有积分变动都经由这里，保证流水幂等。
 */
class CheckInService(private val db: AppDatabase) {

    private val habitDao = db.habitDao()
    private val checkInDao = db.checkInDao()
    private val ledgerDao = db.ledgerDao()

    /**
     * App 打开时执行：补建 PENDING 行 + 把过期的 PENDING 结算为 MISS 扣分 +
     * 每周 N 次习惯的已结束周结算。全程幂等，可重复调用。
     *
     * @param lookbackDays 最多回溯多少天（用户多日未打开 App 也只追溯这么多天）
     */
    suspend fun runDailySettlement(today: LocalDate, lookbackDays: Long = 60) = db.withTransaction {
        ensurePendingRows(today, lookbackDays)
        settlePerDateHabits(today)
        settleWeeklyHabits(today, lookbackDays)
    }

    /** 为按日打卡的习惯补建从回溯起点到今天的 PENDING 行（已有行自动忽略）。 */
    private suspend fun ensurePendingRows(today: LocalDate, lookbackDays: Long) {
        val lookbackStart = today.minusDays(lookbackDays)
        for (habit in habitDao.getActive()) {
            if (habit.frequencyType == FrequencyType.TIMES_PER_WEEK) continue
            var d = maxOf(lookbackStart, habit.createdOn())
            while (!d.isAfter(today)) {
                if (FrequencyPolicy.isScheduled(habit, d)) {
                    checkInDao.insertIgnoring(
                        CheckInEntity(
                            habitId = habit.id,
                            date = d.toEpochDay(),
                            status = CheckInStatus.PENDING.value,
                        ),
                    )
                }
                d = d.plusDays(1)
            }
        }
    }

    /** DAILY / WEEKDAYS：过期 PENDING → MISS 并扣分；已不再被计划或早于创建日的残留行直接删除。 */
    private suspend fun settlePerDateHabits(today: LocalDate) {
        val todayEpochDay = today.toEpochDay()
        for (habit in habitDao.getActive()) {
            if (habit.frequencyType == FrequencyType.TIMES_PER_WEEK) continue
            val pendings = checkInDao.getByHabitStatusBefore(habit.id, todayEpochDay, CheckInStatus.PENDING.value)
            for (row in pendings) {
                val date = LocalDate.ofEpochDay(row.date)
                if (!FrequencyPolicy.isScheduled(habit, date) || date.isBefore(habit.createdOn())) {
                    checkInDao.delete(row)
                    continue
                }
                if (ledgerDao.find(habit.id, row.date, LedgerReason.MISS.name) == null) {
                    ledgerDao.insert(missEntry(habit, row.date))
                }
                checkInDao.update(row.copy(status = CheckInStatus.MISS.value))
            }
        }
    }

    /** TIMES_PER_WEEK：对已结束的周做达标结算（从创建周次一周开始）。 */
    private suspend fun settleWeeklyHabits(today: LocalDate, lookbackDays: Long) {
        val lookbackWeekStart = FrequencyPolicy.weekStart(today.minusDays(lookbackDays))
        for (habit in habitDao.getActive()) {
            if (habit.frequencyType != FrequencyType.TIMES_PER_WEEK) continue
            // 创建所在的那一周不足整周，不结算；从下一周开始
            var ws = FrequencyPolicy.weekStart(habit.createdOn()).plusWeeks(1)
            if (ws.isBefore(lookbackWeekStart)) ws = lookbackWeekStart
            while (ws.plusDays(6).isBefore(today)) { // 周日已过 → 该周结束
                settleWeek(habit, ws.toEpochDay(), today)
                ws = ws.plusWeeks(1)
            }
        }
    }

    /**
     * 同步某一「已结束」周的周结算扣分：缺口 = max(0, 目标次数 - 已完成次数)，
     * 扣分 = 缺口 × pointsOnMiss。补卡/取消后重算会自动修正或撤销。
     */
    private suspend fun settleWeek(habit: HabitEntity, weekStartEpochDay: Long, today: LocalDate) {
        if (!FrequencyPolicy.weekStart(LocalDate.ofEpochDay(weekStartEpochDay)).plusDays(6).isBefore(today)) {
            return // 周未结束不结算
        }
        val weekEnd = weekStartEpochDay + 6
        val doneCount = checkInDao.getByHabitBetween(habit.id, weekStartEpochDay, weekEnd)
            .count { it.status == CheckInStatus.DONE.value }
        val deficit = (habit.timesPerWeek - doneCount).coerceAtLeast(0)
        val charge = deficit * habit.pointsOnMiss
        val existing = ledgerDao.find(habit.id, weekStartEpochDay, LedgerReason.MISS.name)
        when {
            charge > 0 && existing == null ->
                ledgerDao.insert(missEntry(habit, weekStartEpochDay, note = "${habit.name}·周目标差 $deficit 次"))
            charge > 0 && existing != null && existing.amount != -charge -> {
                ledgerDao.delete(existing)
                ledgerDao.insert(missEntry(habit, weekStartEpochDay, note = "${habit.name}·周目标差 $deficit 次"))
            }
            charge <= 0 && existing != null -> ledgerDao.delete(existing)
        }
    }

    private fun missEntry(habit: HabitEntity, date: Long, note: String? = null) = PointsLedgerEntity(
        amount = -habit.pointsOnMiss,
        reason = LedgerReason.MISS.name,
        habitId = habit.id,
        date = date,
        note = note ?: habit.name,
    )

    /** 打卡/补卡：置为完成、回冲该日 MISS 扣分、记完成得分。只允许今天及过去。 */
    suspend fun markDone(habitId: Long, epochDay: Long, todayEpochDay: Long = LocalDate.now().toEpochDay()) {
        if (epochDay > todayEpochDay) return
        db.withTransaction {
            val habit = habitDao.getById(habitId) ?: return@withTransaction
            val existing = checkInDao.get(habitId, epochDay)
            if (existing?.status == CheckInStatus.DONE.value) return@withTransaction
            val row = (existing ?: CheckInEntity(habitId = habitId, date = epochDay, status = CheckInStatus.PENDING.value))
                .copy(status = CheckInStatus.DONE.value)
            if (existing == null) checkInDao.insert(row) else checkInDao.update(row)
            ledgerDao.find(habitId, epochDay, LedgerReason.MISS.name)?.let { ledgerDao.delete(it) }
            if (ledgerDao.find(habitId, epochDay, LedgerReason.CHECKIN.name) == null) {
                ledgerDao.insert(
                    PointsLedgerEntity(
                        amount = habit.pointsOnComplete,
                        reason = LedgerReason.CHECKIN.name,
                        habitId = habitId,
                        date = epochDay,
                        note = habit.name,
                    ),
                )
            }
            if (habit.frequencyType == FrequencyType.TIMES_PER_WEEK) {
                settleWeek(habit, FrequencyPolicy.weekStart(LocalDate.ofEpochDay(epochDay)).toEpochDay(), LocalDate.ofEpochDay(todayEpochDay))
            }
        }
    }

    /** 取消打卡：今天回 PENDING；过去日期回 MISS 并立即补扣分（周目标习惯重算该周）。 */
    suspend fun unmarkDone(habitId: Long, epochDay: Long, todayEpochDay: Long = LocalDate.now().toEpochDay()) {
        db.withTransaction {
            val habit = habitDao.getById(habitId) ?: return@withTransaction
            val existing = checkInDao.get(habitId, epochDay) ?: return@withTransaction
            if (existing.status != CheckInStatus.DONE.value) return@withTransaction
            ledgerDao.find(habitId, epochDay, LedgerReason.CHECKIN.name)?.let { ledgerDao.delete(it) }
            when {
                habit.frequencyType == FrequencyType.TIMES_PER_WEEK -> checkInDao.delete(existing)
                epochDay < todayEpochDay -> {
                    checkInDao.update(existing.copy(status = CheckInStatus.MISS.value))
                    if (ledgerDao.find(habitId, epochDay, LedgerReason.MISS.name) == null) {
                        ledgerDao.insert(missEntry(habit, epochDay))
                    }
                }
                else -> checkInDao.update(existing.copy(status = CheckInStatus.PENDING.value))
            }
            if (habit.frequencyType == FrequencyType.TIMES_PER_WEEK) {
                settleWeek(habit, FrequencyPolicy.weekStart(LocalDate.ofEpochDay(epochDay)).toEpochDay(), LocalDate.ofEpochDay(todayEpochDay))
            }
        }
    }

    /** 跳过/取消跳过（仅限今天）：跳过 = 主动休息，不产生扣分。 */
    suspend fun setSkip(habitId: Long, epochDay: Long, skip: Boolean, todayEpochDay: Long = LocalDate.now().toEpochDay()) {
        if (epochDay > todayEpochDay) return
        db.withTransaction {
            val habit = habitDao.getById(habitId) ?: return@withTransaction
            val existing = checkInDao.get(habitId, epochDay)
            if (skip) {
                if (existing?.status == CheckInStatus.DONE.value) return@withTransaction
                val row = (existing ?: CheckInEntity(habitId = habitId, date = epochDay, status = CheckInStatus.PENDING.value))
                    .copy(status = CheckInStatus.SKIP.value)
                if (existing == null) checkInDao.insert(row) else checkInDao.update(row)
            } else if (existing?.status == CheckInStatus.SKIP.value) {
                if (epochDay < todayEpochDay) {
                    checkInDao.update(existing.copy(status = CheckInStatus.MISS.value))
                    if (ledgerDao.find(habitId, epochDay, LedgerReason.MISS.name) == null) {
                        ledgerDao.insert(missEntry(habit, epochDay))
                    }
                } else {
                    checkInDao.update(existing.copy(status = CheckInStatus.PENDING.value))
                }
            }
        }
    }

    /** 兑换奖励：余额足够则记一笔 REDEEM 负流水。 */
    suspend fun redeem(reward: RewardEntity): RedeemResult = db.withTransaction {
        if (ledgerDao.balance() < reward.pointsCost) {
            RedeemResult.Insufficient
        } else {
            ledgerDao.insert(
                PointsLedgerEntity(
                    amount = -reward.pointsCost,
                    reason = LedgerReason.REDEEM.name,
                    rewardId = reward.id,
                    note = reward.name,
                ),
            )
            RedeemResult.Success
        }
    }

    /** 撤销兑换（删除该笔流水，积分返还）。 */
    suspend fun undoRedeem(entry: PointsLedgerEntity) {
        if (entry.reason != LedgerReason.REDEEM.name) return
        db.withTransaction { ledgerDao.delete(entry) }
    }
}

package com.wenbo.habitrecord.domain

import com.wenbo.habitrecord.data.db.HabitEntity
import com.wenbo.habitrecord.domain.model.CheckInStatus
import com.wenbo.habitrecord.domain.model.FrequencyType
import java.time.LocalDate

/** 连续打卡天数与周进度计算（纯函数）。 */
object StreakCalculator {

    /**
     * 连续完成天数：从今天沿「应打卡日」序列回溯。
     * 规则：今天应打卡但未完成不打断已有连续；主动跳过（SKIP）不打断；MISS 或过去未打卡则打断。
     * TIMES_PER_WEEK 无连续概念，返回 0。
     *
     * @param statusByDate epochDay -> CheckInStatus.value
     */
    fun streak(
        habit: HabitEntity,
        today: LocalDate,
        createdOn: LocalDate,
        statusByDate: Map<Long, Int>,
    ): Int {
        if (habit.frequencyType == FrequencyType.TIMES_PER_WEEK) return 0
        if (today.isBefore(createdOn)) return 0

        var count = 0
        var cursor = today
        if (FrequencyPolicy.isScheduled(habit, cursor)) {
            if (statusByDate[cursor.toEpochDay()] == CheckInStatus.DONE.value) count++
            cursor = cursor.minusDays(1)
        }
        while (!cursor.isBefore(createdOn)) {
            if (FrequencyPolicy.isScheduled(habit, cursor)) {
                when (statusByDate[cursor.toEpochDay()]) {
                    CheckInStatus.DONE.value -> count++
                    CheckInStatus.SKIP.value -> Unit // 跳过不打断
                    else -> break // MISS / 过去未打卡
                }
            }
            cursor = cursor.minusDays(1)
        }
        return count
    }

    /** 本周（周一~周日）已完成的打卡次数。 */
    fun weekDoneCount(statusByDate: Map<Long, Int>, weekStart: LocalDate): Int {
        var count = 0
        var cursor = weekStart
        for (i in 0 until 7) {
            if (statusByDate[cursor.toEpochDay()] == CheckInStatus.DONE.value) count++
            cursor = cursor.plusDays(1)
        }
        return count
    }
}

/** 习惯创建日期（按系统时区）。 */
fun HabitEntity.createdOn(): LocalDate =
    java.time.Instant.ofEpochMilli(createdAt)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDate()

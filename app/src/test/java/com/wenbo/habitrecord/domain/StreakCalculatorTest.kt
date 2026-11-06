package com.wenbo.habitrecord.domain

import com.wenbo.habitrecord.data.db.HabitEntity
import com.wenbo.habitrecord.domain.model.CheckInStatus
import com.wenbo.habitrecord.domain.model.FrequencyType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class StreakCalculatorTest {

    private val createdOn = LocalDate.of(2026, 11, 1)
    private val createdMillis =
        createdOn.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun dailyHabit() = HabitEntity(
        name = "阅读",
        frequencyType = FrequencyType.DAILY,
        createdAt = createdMillis,
    )

    private fun weekdaysHabit(days: String) = HabitEntity(
        name = "跑步",
        frequencyType = FrequencyType.WEEKDAYS,
        weekdays = days,
        createdAt = createdMillis,
    )

    private fun today() = LocalDate.of(2026, 11, 6) // 周五

    private fun mapOf(vararg entries: Pair<LocalDate, CheckInStatus>): Map<Long, Int> =
        entries.associate { (d, s) -> d.toEpochDay() to s.value }

    @Test
    fun `all done days count as streak`() {
        val status = mapOf(
            LocalDate.of(2026, 11, 1) to CheckInStatus.DONE,
            LocalDate.of(2026, 11, 2) to CheckInStatus.DONE,
            LocalDate.of(2026, 11, 3) to CheckInStatus.DONE,
            LocalDate.of(2026, 11, 4) to CheckInStatus.DONE,
            LocalDate.of(2026, 11, 5) to CheckInStatus.DONE,
            LocalDate.of(2026, 11, 6) to CheckInStatus.DONE,
        )
        assertEquals(6, StreakCalculator.streak(dailyHabit(), today(), createdOn, status))
    }

    @Test
    fun `yesterday miss breaks streak but today done counts`() {
        val status = mapOf(
            LocalDate.of(2026, 11, 4) to CheckInStatus.DONE,
            LocalDate.of(2026, 11, 5) to CheckInStatus.MISS,
            LocalDate.of(2026, 11, 6) to CheckInStatus.DONE,
        )
        assertEquals(1, StreakCalculator.streak(dailyHabit(), today(), createdOn, status))
    }

    @Test
    fun `today not done yet does not break streak`() {
        val status = mapOf(
            LocalDate.of(2026, 11, 3) to CheckInStatus.DONE,
            LocalDate.of(2026, 11, 4) to CheckInStatus.DONE,
            LocalDate.of(2026, 11, 5) to CheckInStatus.DONE,
            // 今天(6日)尚未打卡
        )
        assertEquals(3, StreakCalculator.streak(dailyHabit(), today(), createdOn, status))
    }

    @Test
    fun `skip does not break streak`() {
        val status = mapOf(
            LocalDate.of(2026, 11, 3) to CheckInStatus.DONE,
            LocalDate.of(2026, 11, 4) to CheckInStatus.SKIP,
            LocalDate.of(2026, 11, 5) to CheckInStatus.DONE,
            LocalDate.of(2026, 11, 6) to CheckInStatus.DONE,
        )
        assertEquals(3, StreakCalculator.streak(dailyHabit(), today(), createdOn, status))
    }

    @Test
    fun `weekdays habit ignores non scheduled gaps`() {
        // 每周一三五：周一、周三完成，周二周四缺席不算中断
        val status = mapOf(
            LocalDate.of(2026, 11, 2) to CheckInStatus.DONE, // 周一
            LocalDate.of(2026, 11, 4) to CheckInStatus.DONE, // 周三
            LocalDate.of(2026, 11, 6) to CheckInStatus.DONE, // 周五(今天)
        )
        assertEquals(3, StreakCalculator.streak(weekdaysHabit("1,3,5"), today(), createdOn, status))
    }

    @Test
    fun `times per week has no streak`() {
        val status = mapOf(
            LocalDate.of(2026, 11, 5) to CheckInStatus.DONE,
            LocalDate.of(2026, 11, 6) to CheckInStatus.DONE,
        )
        assertEquals(
            0,
            StreakCalculator.streak(
                HabitEntity(name = "x", frequencyType = FrequencyType.TIMES_PER_WEEK, createdAt = createdMillis),
                today(),
                createdOn,
                status,
            ),
        )
    }

    @Test
    fun `week done count covers monday to sunday`() {
        val weekStart = LocalDate.of(2026, 11, 2)
        val status = mapOf(
            LocalDate.of(2026, 11, 2) to CheckInStatus.DONE,
            LocalDate.of(2026, 11, 5) to CheckInStatus.DONE,
            LocalDate.of(2026, 11, 8) to CheckInStatus.DONE,
            LocalDate.of(2026, 11, 9) to CheckInStatus.DONE, // 下周一，不计入
        )
        assertEquals(3, StreakCalculator.weekDoneCount(status, weekStart))
    }
}

package com.wenbo.habitrecord.domain

import com.wenbo.habitrecord.data.db.HabitEntity
import com.wenbo.habitrecord.domain.model.FrequencyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class FrequencyPolicyTest {

    private val monday = LocalDate.of(2026, 11, 2) // 周一
    private val wednesday = LocalDate.of(2026, 11, 4)

    @Test
    fun `daily is always scheduled`() {
        val habit = HabitEntity(name = "喝水", frequencyType = FrequencyType.DAILY)
        assertTrue(FrequencyPolicy.isScheduled(habit, monday))
        assertTrue(FrequencyPolicy.isScheduled(habit, wednesday))
    }

    @Test
    fun `weekdays respects configured days`() {
        val habit = HabitEntity(name = "跑步", frequencyType = FrequencyType.WEEKDAYS, weekdays = "1,3,5")
        assertTrue(FrequencyPolicy.isScheduled(habit, monday))
        assertTrue(FrequencyPolicy.isScheduled(habit, wednesday))
        assertFalse(FrequencyPolicy.isScheduled(habit, LocalDate.of(2026, 11, 3))) // 周二
    }

    @Test
    fun `times per week is scheduled any day`() {
        val habit = HabitEntity(name = "拉伸", frequencyType = FrequencyType.TIMES_PER_WEEK, timesPerWeek = 3)
        assertTrue(FrequencyPolicy.isScheduled(habit, monday))
        assertTrue(FrequencyPolicy.isScheduled(habit, LocalDate.of(2026, 11, 8))) // 周日
    }

    @Test
    fun `weekday codec round trip and filters invalid`() {
        val days = setOf(5, 1, 3)
        val encoded = FrequencyPolicy.formatWeekdays(days)
        assertEquals("1,3,5", encoded)
        assertEquals(setOf(1, 3, 5), FrequencyPolicy.parseWeekdays(encoded))
        assertEquals(emptySet<Int>(), FrequencyPolicy.parseWeekdays("9,0,abc,"))
    }

    @Test
    fun `weekStart maps any day to its Monday`() {
        assertEquals(LocalDate.of(2026, 11, 2), FrequencyPolicy.weekStart(wednesday))
        assertEquals(DayOfWeek.MONDAY, FrequencyPolicy.weekStart(wednesday).dayOfWeek)
        // 周日仍属于同一周
        assertEquals(LocalDate.of(2026, 11, 2), FrequencyPolicy.weekStart(LocalDate.of(2026, 11, 8)))
    }

    @Test
    fun `frequency label renders chinese`() {
        assertEquals(
            "每天",
            FrequencyPolicy.frequencyLabel(HabitEntity(name = "x", frequencyType = FrequencyType.DAILY)),
        )
        assertEquals(
            "每周 3 次",
            FrequencyPolicy.frequencyLabel(
                HabitEntity(name = "x", frequencyType = FrequencyType.TIMES_PER_WEEK, timesPerWeek = 3),
            ),
        )
    }
}

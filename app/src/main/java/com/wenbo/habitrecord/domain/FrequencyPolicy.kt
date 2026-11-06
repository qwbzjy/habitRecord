package com.wenbo.habitrecord.domain

import com.wenbo.habitrecord.data.db.HabitEntity
import com.wenbo.habitrecord.domain.model.FrequencyType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** 打卡频率相关的纯函数：某天是否应打卡、星期编解码、周起点。 */
object FrequencyPolicy {

    /** 判断某天是否「应打卡」（TIMES_PER_WEEK 任何一天都可以打）。 */
    fun isScheduled(habit: HabitEntity, date: LocalDate): Boolean = when (habit.frequencyType) {
        FrequencyType.DAILY -> true
        FrequencyType.WEEKDAYS -> date.dayOfWeek.value in parseWeekdays(habit.weekdays)
        FrequencyType.TIMES_PER_WEEK -> true
    }

    fun parseWeekdays(raw: String): Set<Int> =
        raw.split(',')
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in DayOfWeek.MONDAY.value..DayOfWeek.SUNDAY.value }
            .toSet()

    fun formatWeekdays(days: Collection<Int>): String =
        days.toSortedSet().joinToString(",")

    /** 该周（周一~周日）的周一。 */
    fun weekStart(date: LocalDate): LocalDate =
        date.minusDays((date.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())

    /** 频率的中文描述，如「每天」「周一、周三、周五」「每周 3 次」。 */
    fun frequencyLabel(habit: HabitEntity): String = when (habit.frequencyType) {
        FrequencyType.DAILY -> "每天"
        FrequencyType.TIMES_PER_WEEK -> "每周 ${habit.timesPerWeek} 次"
        FrequencyType.WEEKDAYS ->
            parseWeekdays(habit.weekdays).sorted().joinToString("、") { day ->
                DayOfWeek.of(day).getDisplayName(TextStyle.SHORT, Locale.CHINESE)
            }
    }
}

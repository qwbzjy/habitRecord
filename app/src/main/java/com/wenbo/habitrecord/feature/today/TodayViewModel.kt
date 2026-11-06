package com.wenbo.habitrecord.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wenbo.habitrecord.data.db.HabitEntity
import com.wenbo.habitrecord.di.AppContainer
import com.wenbo.habitrecord.domain.FrequencyPolicy
import com.wenbo.habitrecord.domain.StreakCalculator
import com.wenbo.habitrecord.domain.createdOn
import com.wenbo.habitrecord.domain.model.CheckInStatus
import com.wenbo.habitrecord.domain.model.FrequencyType
import java.time.LocalDate
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TodayHabitUi(
    val habit: HabitEntity,
    val status: CheckInStatus,
    val streak: Int,
    /** TIMES_PER_WEEK 专属：本周已完成次数 */
    val weekDone: Int?,
)

data class TodayUiState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val items: List<TodayHabitUi> = emptyList(),
    val balance: Int = 0,
)

class TodayViewModel(private val container: AppContainer) : ViewModel() {

    private val service = container.checkInService
    private var settledOn: Long = -1L

    private val _state = MutableStateFlow(TodayUiState())
    val state = _state.asStateFlow()

    private val _events = Channel<String>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            combine(
                container.database.habitDao().observeActive(),
                container.database.checkInDao().observeAll(),
                container.database.ledgerDao().observeBalance(),
            ) { habits, checkIns, balance ->
                Triple(habits, checkIns, balance)
            }.collect { (habits, checkIns, balance) ->
                val today = LocalDate.now()
                val todayEpochDay = today.toEpochDay()
                val statusByHabitToday = checkIns
                    .filter { it.date == todayEpochDay }
                    .associateBy { it.habitId }
                val statusByHabitAll = checkIns.groupBy { it.habitId }
                val items = habits.map { habit ->
                    val statusMap = statusByHabitAll[habit.id].orEmpty()
                        .associate { it.date to it.status }
                    TodayHabitUi(
                        habit = habit,
                        status = CheckInStatus.from(
                            statusByHabitToday[habit.id]?.status ?: CheckInStatus.PENDING.value,
                        ),
                        streak = StreakCalculator.streak(habit, today, habit.createdOn(), statusMap),
                        weekDone = if (habit.frequencyType == FrequencyType.TIMES_PER_WEEK) {
                            StreakCalculator.weekDoneCount(statusMap, FrequencyPolicy.weekStart(today))
                        } else {
                            null
                        },
                    )
                }
                _state.update {
                    it.copy(loading = false, today = today, items = items, balance = balance)
                }
            }
        }
    }

    /** 每次回到今日页调用：跨天后重新执行惰性结算。 */
    fun onScreenShown() {
        val todayEpochDay = LocalDate.now().toEpochDay()
        if (todayEpochDay != settledOn) {
            settledOn = todayEpochDay
            viewModelScope.launch {
                service.runDailySettlement(LocalDate.ofEpochDay(todayEpochDay))
            }
        }
    }

    /** 点击卡片：完成 ⇄ 取消完成。 */
    fun toggle(habitId: Long) {
        val item = _state.value.items.firstOrNull { it.habit.id == habitId } ?: return
        val today = LocalDate.now().toEpochDay()
        viewModelScope.launch {
            if (item.status == CheckInStatus.DONE) {
                service.unmarkDone(habitId, today)
            } else {
                service.markDone(habitId, today)
            }
        }
    }

    /** 跳过/取消跳过（仅当天）。 */
    fun toggleSkip(habitId: Long) {
        val item = _state.value.items.firstOrNull { it.habit.id == habitId } ?: return
        val today = LocalDate.now().toEpochDay()
        viewModelScope.launch {
            service.setSkip(habitId, today, skip = item.status != CheckInStatus.SKIP)
        }
    }
}

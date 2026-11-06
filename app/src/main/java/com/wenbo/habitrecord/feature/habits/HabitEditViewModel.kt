package com.wenbo.habitrecord.feature.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wenbo.habitrecord.data.db.HabitEntity
import com.wenbo.habitrecord.di.AppContainer
import com.wenbo.habitrecord.domain.FrequencyPolicy
import com.wenbo.habitrecord.domain.model.FrequencyType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HabitEditUiState(
    val isNew: Boolean = true,
    val name: String = "",
    val emoji: String = "✅",
    val frequencyType: FrequencyType = FrequencyType.DAILY,
    val selectedWeekdays: Set<Int> = emptySet(),
    val timesPerWeek: Int = 3,
    val pointsOnComplete: Int = 10,
    val pointsOnMiss: Int = 5,
    val archived: Boolean = false,
    val saved: Boolean = false,
)

class HabitEditViewModel(
    private val container: AppContainer,
    private val habitId: Long?,
) : ViewModel() {

    private val habitDao = container.database.habitDao()

    private val _state = MutableStateFlow(HabitEditUiState())
    val state = _state.asStateFlow()

    init {
        if (habitId != null) {
            viewModelScope.launch {
                val habit = habitDao.getById(habitId) ?: return@launch
                _state.update {
                    HabitEditUiState(
                        isNew = false,
                        name = habit.name,
                        emoji = habit.emoji,
                        frequencyType = habit.frequencyType,
                        selectedWeekdays = FrequencyPolicy.parseWeekdays(habit.weekdays),
                        timesPerWeek = habit.timesPerWeek,
                        pointsOnComplete = habit.pointsOnComplete,
                        pointsOnMiss = habit.pointsOnMiss,
                        archived = habit.archived,
                    )
                }
            }
        }
    }

    fun updateName(name: String) = _state.update { it.copy(name = name.take(20)) }
    fun updateEmoji(emoji: String) = _state.update { it.copy(emoji = emoji) }
    fun updateFrequencyType(type: FrequencyType) = _state.update { it.copy(frequencyType = type) }
    fun updatePointsOnComplete(points: Int) =
        _state.update { it.copy(pointsOnComplete = points.coerceIn(0, 999)) }

    fun updatePointsOnMiss(points: Int) =
        _state.update { it.copy(pointsOnMiss = points.coerceIn(0, 999)) }

    fun toggleWeekday(day: Int) = _state.update {
        val next = it.selectedWeekdays.toMutableSet()
        if (!next.add(day)) next.remove(day)
        it.copy(selectedWeekdays = next)
    }

    fun updateTimesPerWeek(times: Int) =
        _state.update { it.copy(timesPerWeek = times.coerceIn(1, 7)) }

    fun save() {
        val s = _state.value
        if (s.name.isBlank()) return
        if (s.frequencyType == FrequencyType.WEEKDAYS && s.selectedWeekdays.isEmpty()) return
        val existingId = habitId
        viewModelScope.launch {
            if (existingId == null) {
                val maxOrder = container.database.habitDao().getAll().maxOfOrNull { it.sortOrder } ?: 0
                habitDao.insert(
                    HabitEntity(
                        name = s.name.trim(),
                        emoji = s.emoji,
                        frequencyType = s.frequencyType,
                        weekdays = FrequencyPolicy.formatWeekdays(s.selectedWeekdays),
                        timesPerWeek = s.timesPerWeek,
                        pointsOnComplete = s.pointsOnComplete,
                        pointsOnMiss = s.pointsOnMiss,
                        sortOrder = maxOrder + 1,
                    ),
                )
            } else {
                val habit = habitDao.getById(existingId) ?: return@launch
                habitDao.update(
                    habit.copy(
                        name = s.name.trim(),
                        emoji = s.emoji,
                        frequencyType = s.frequencyType,
                        weekdays = FrequencyPolicy.formatWeekdays(s.selectedWeekdays),
                        timesPerWeek = s.timesPerWeek,
                        pointsOnComplete = s.pointsOnComplete,
                        pointsOnMiss = s.pointsOnMiss,
                    ),
                )
            }
            _state.update { it.copy(saved = true) }
        }
    }
}

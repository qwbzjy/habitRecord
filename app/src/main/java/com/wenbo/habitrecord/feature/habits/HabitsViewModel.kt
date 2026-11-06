package com.wenbo.habitrecord.feature.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wenbo.habitrecord.data.db.HabitEntity
import com.wenbo.habitrecord.di.AppContainer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HabitsViewModel(private val container: AppContainer) : ViewModel() {

    val habits = container.database.habitDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _events = Channel<String>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val _confirmDelete = MutableStateFlow<HabitEntity?>(null)
    val confirmDelete = _confirmDelete.asStateFlow()

    fun requestDelete(habit: HabitEntity) {
        _confirmDelete.value = habit
    }

    fun dismissDelete() {
        _confirmDelete.value = null
    }

    /** 删除习惯及其全部打卡与流水（级联）。 */
    fun confirmDelete() {
        val habit = _confirmDelete.value ?: return
        _confirmDelete.value = null
        viewModelScope.launch {
            container.database.habitDao().delete(habit)
            _events.send("已删除「${habit.name}」")
        }
    }

    fun setArchived(habit: HabitEntity, archived: Boolean) {
        viewModelScope.launch {
            container.database.habitDao().setArchived(habit.id, archived)
        }
    }
}

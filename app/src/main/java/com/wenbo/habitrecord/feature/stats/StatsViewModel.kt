package com.wenbo.habitrecord.feature.stats

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wenbo.habitrecord.data.backup.BackupCodec
import com.wenbo.habitrecord.data.db.HabitEntity
import com.wenbo.habitrecord.data.db.PointsLedgerEntity
import com.wenbo.habitrecord.di.AppContainer
import com.wenbo.habitrecord.domain.StreakCalculator
import com.wenbo.habitrecord.domain.createdOn
import com.wenbo.habitrecord.domain.model.CheckInStatus
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class StreakUi(val habit: HabitEntity, val streak: Int)

data class StatsUiState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val balance: Int = 0,
    val totalDone: Int = 0,
    val habits: List<HabitEntity> = emptyList(),
    val selectedHabitId: Long? = null,
    val month: YearMonth = YearMonth.now(),
    /** 选中习惯的 epochDay -> status */
    val statusByDate: Map<Long, Int> = emptyMap(),
    val streakRanking: List<StreakUi> = emptyList(),
    val ledger: List<PointsLedgerEntity> = emptyList(),
)

enum class ImportMode { REPLACE, MERGE }

class StatsViewModel(private val container: AppContainer) : ViewModel() {

    private val db = container.database
    private val service = container.checkInService
    private val backupManager = container.backupManager

    private val _state = MutableStateFlow(StatsUiState())
    val state = _state.asStateFlow()

    private val _events = Channel<String>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val selectedHabitId = MutableStateFlow<Long?>(null)
    private val month = MutableStateFlow(YearMonth.now())

    init {
        viewModelScope.launch {
            combine(
                db.ledgerDao().observeBalance(),
                db.checkInDao().observeAll(),
                db.habitDao().observeAll(),
                db.ledgerDao().observeAll(),
            ) { balance, checkIns, habits, ledger ->
                StatsInputs(balance, checkIns, habits, ledger)
            }.combine(selectedHabitId) { inputs, selected ->
                inputs to selected
            }.combine(month) { (inputs, selected), currentMonth ->
                Triple(inputs, selected, currentMonth)
            }.collect { (inputs, selected, currentMonth) ->
                val habitId = selected
                    ?: inputs.habits.firstOrNull { !it.archived }?.id
                    ?: inputs.habits.firstOrNull()?.id
                val statusMap = inputs.checkIns
                    .filter { it.habitId == habitId }
                    .associate { it.date to it.status }
                val today = LocalDate.now()
                val ranking = inputs.habits.filter { !it.archived }.map { habit ->
                    val map = inputs.checkIns
                        .filter { it.habitId == habit.id }
                        .associate { it.date to it.status }
                    StreakUi(habit, StreakCalculator.streak(habit, today, habit.createdOn(), map))
                }.sortedByDescending { it.streak }
                _state.update {
                    it.copy(
                        loading = false,
                        balance = inputs.balance,
                        totalDone = inputs.checkIns.count { c -> c.status == CheckInStatus.DONE.value },
                        habits = inputs.habits,
                        selectedHabitId = habitId,
                        month = currentMonth,
                        statusByDate = statusMap,
                        streakRanking = ranking,
                        ledger = inputs.ledger.take(50),
                    )
                }
            }
        }
    }

    private data class StatsInputs(
        val balance: Int,
        val checkIns: List<com.wenbo.habitrecord.data.db.CheckInEntity>,
        val habits: List<HabitEntity>,
        val ledger: List<PointsLedgerEntity>,
    )

    fun selectHabit(habitId: Long) {
        selectedHabitId.value = habitId
    }

    fun shiftMonth(delta: Long) {
        month.value = month.value.plusMonths(delta)
    }

    /** 热力图点按某天：完成 ⇄ 取消完成（仅限今天及过去，即补卡）。 */
    fun toggleDate(epochDay: Long) {
        val habitId = _state.value.selectedHabitId ?: return
        viewModelScope.launch {
            val status = _state.value.statusByDate[epochDay]
            if (status == CheckInStatus.DONE.value) {
                service.unmarkDone(habitId, epochDay)
            } else {
                service.markDone(habitId, epochDay)
            }
        }
    }

    /** 导出备份到用户选择的位置。 */
    fun exportTo(uri: Uri) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                val backup = backupManager.export()
                val json = BackupCodec.encode(backup)
                withContext(Dispatchers.IO) {
                    container.openBackupOutput(uri)?.use { os ->
                        os.write(json.toByteArray(Charsets.UTF_8))
                    } ?: throw IOException("无法打开目标文件")
                }
                _events.send(
                    "导出成功：习惯 ${backup.habits.size}、打卡 ${backup.checkIns.size}、" +
                        "奖励 ${backup.rewards.size}、流水 ${backup.ledger.size}",
                )
            } catch (e: Exception) {
                _events.send("导出失败：${e.message}")
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    /** 从用户选择的文件导入。 */
    fun importFrom(uri: Uri, mode: ImportMode) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                val text = withContext(Dispatchers.IO) {
                    container.openBackupInput(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                        ?: throw IOException("无法读取所选文件")
                }
                val backup = BackupCodec.decode(text)
                    ?: throw IOException("不是有效的备份文件（或由更高版本导出）")
                val message = when (mode) {
                    ImportMode.REPLACE -> {
                        backupManager.importReplace(backup)
                        "恢复完成：习惯 ${backup.habits.size}、打卡 ${backup.checkIns.size}、奖励 ${backup.rewards.size}"
                    }
                    ImportMode.MERGE -> backupManager.importMerge(backup)
                }
                _events.send(message)
            } catch (e: Exception) {
                _events.send("导入失败：${e.message}")
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }
}

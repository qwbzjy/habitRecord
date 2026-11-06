package com.wenbo.habitrecord.feature.rewards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wenbo.habitrecord.data.db.PointsLedgerEntity
import com.wenbo.habitrecord.data.db.RewardEntity
import com.wenbo.habitrecord.data.repository.RedeemResult
import com.wenbo.habitrecord.di.AppContainer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RewardUi(
    val reward: RewardEntity,
    val affordable: Boolean,
)

data class RewardsUiState(
    val loading: Boolean = true,
    val balance: Int = 0,
    val rewards: List<RewardUi> = emptyList(),
    val redemptions: List<PointsLedgerEntity> = emptyList(),
    /** 正在编辑的奖励（null=未打开；RewardEntity() 带空 id 表示新建） */
    val editing: RewardEntity? = null,
)

class RewardsViewModel(private val container: AppContainer) : ViewModel() {

    private val db = container.database
    private val service = container.checkInService

    private val _state = MutableStateFlow(RewardsUiState())
    val state = _state.asStateFlow()

    private val _events = Channel<String>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            combine(
                db.ledgerDao().observeBalance(),
                db.rewardDao().observeAll(),
                db.ledgerDao().observeByReason(com.wenbo.habitrecord.domain.model.LedgerReason.REDEEM.name),
            ) { balance, rewards, redemptions ->
                Triple(balance, rewards, redemptions)
            }.collect { (balance, rewards, redemptions) ->
                _state.update {
                    it.copy(
                        loading = false,
                        balance = balance,
                        rewards = rewards.map { r -> RewardUi(r, balance >= r.pointsCost) },
                        redemptions = redemptions,
                    )
                }
            }
        }
    }

    fun openEditor(reward: RewardEntity?) =
        _state.update { it.copy(editing = reward ?: RewardEntity(name = "")) }

    fun closeEditor() = _state.update { it.copy(editing = null) }

    fun saveReward(name: String, emoji: String, pointsCost: Int) {
        val editing = _state.value.editing ?: return
        if (name.isBlank()) return
        viewModelScope.launch {
            if (editing.id == 0L) {
                db.rewardDao().insert(editing.copy(name = name.trim(), emoji = emoji, pointsCost = pointsCost))
            } else {
                db.rewardDao().update(editing.copy(name = name.trim(), emoji = emoji, pointsCost = pointsCost))
            }
            closeEditor()
        }
    }

    fun deleteReward(reward: RewardEntity) {
        viewModelScope.launch {
            db.rewardDao().delete(reward)
            closeEditor()
            _events.send("已删除「${reward.name}」")
        }
    }

    fun redeem(reward: RewardEntity) {
        viewModelScope.launch {
            when (service.redeem(reward)) {
                RedeemResult.Success -> _events.send("🎉 已兑换「${reward.name}」，快去兑现吧！")
                RedeemResult.Insufficient -> _events.send("积分不足，还差 ${reward.pointsCost - _state.value.balance} 分")
            }
        }
    }

    fun undoRedeem(entry: PointsLedgerEntity) {
        viewModelScope.launch {
            service.undoRedeem(entry)
            _events.send("已撤销兑换，积分已返还")
        }
    }
}

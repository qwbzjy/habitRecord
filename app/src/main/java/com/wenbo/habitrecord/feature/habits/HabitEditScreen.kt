package com.wenbo.habitrecord.feature.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wenbo.habitrecord.di.LocalAppContainer
import com.wenbo.habitrecord.domain.model.FrequencyType

private val EMOJI_CHOICES = listOf(
    "✅", "🏃", "📚", "💧", "🧘", "🌱", "💪", "🛏️", "🦷", "🍎",
    "☀️", "🌙", "📝", "🎸", "🎨", "💻", "🧹", "🚭", "💰", "📵",
    "🚶", "🚴", "🏊", "🥗", "🍵", "💊", "🧠", "⏰", "🗣️", "🙏",
)

private val WEEKDAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitEditScreen(habitId: Long?, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: HabitEditViewModel = viewModel(key = "habit_edit_$habitId") {
        HabitEditViewModel(container, habitId)
    }
    val state by vm.state.collectAsState()

    LaunchedEffect(state.saved) { if (state.saved) onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "新建习惯" else "编辑习惯") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = vm::updateName,
                label = { Text("习惯名称") },
                placeholder = { Text("如：每天阅读 30 分钟") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("图标", style = MaterialTheme.typography.titleSmall)
            Box(Modifier.height(160.dp)) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(10),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(EMOJI_CHOICES) { emoji ->
                        EmojiCell(
                            emoji = emoji,
                            selected = emoji == state.emoji,
                            onClick = { vm.updateEmoji(emoji) },
                        )
                    }
                }
            }

            Text("打卡频率", style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val types = listOf(
                    FrequencyType.DAILY to "每天",
                    FrequencyType.WEEKDAYS to "指定星期",
                    FrequencyType.TIMES_PER_WEEK to "每周 N 次",
                )
                types.forEachIndexed { index, (type, label) ->
                    SegmentedButton(
                        selected = state.frequencyType == type,
                        onClick = { vm.updateFrequencyType(type) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = types.size),
                    ) { Text(label) }
                }
            }

            when (state.frequencyType) {
                FrequencyType.WEEKDAYS -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        WEEKDAY_LABELS.forEachIndexed { index, label ->
                            val day = index + 1
                            FilterChip(
                                selected = day in state.selectedWeekdays,
                                onClick = { vm.toggleWeekday(day) },
                                label = { Text(label) },
                            )
                        }
                    }
                    if (state.selectedWeekdays.isEmpty()) {
                        Text(
                            "请至少选择一天",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                FrequencyType.TIMES_PER_WEEK -> {
                    StepperRow(
                        label = "每周目标次数",
                        value = state.timesPerWeek,
                        onMinus = { vm.updateTimesPerWeek(state.timesPerWeek - 1) },
                        onPlus = { vm.updateTimesPerWeek(state.timesPerWeek + 1) },
                    )
                }
                FrequencyType.DAILY -> Unit
            }

            Text("奖惩积分", style = MaterialTheme.typography.titleSmall)
            StepperRow(
                label = "完成一次得分",
                value = state.pointsOnComplete,
                onMinus = { vm.updatePointsOnComplete(state.pointsOnComplete - 1) },
                onPlus = { vm.updatePointsOnComplete(state.pointsOnComplete + 1) },
            )
            StepperRow(
                label = "未完成扣分",
                value = state.pointsOnMiss,
                onMinus = { vm.updatePointsOnMiss(state.pointsOnMiss - 1) },
                onPlus = { vm.updatePointsOnMiss(state.pointsOnMiss + 1) },
            )
            Text(
                "未完成扣分在次日凌晨结算时生效；主动跳过不扣分。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = vm::save,
                enabled = state.name.isNotBlank() &&
                    (state.frequencyType != FrequencyType.WEEKDAYS || state.selectedWeekdays.isNotEmpty()),
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
            ) { Text(if (state.isNew) "创建习惯" else "保存修改") }
        }
    }
}

@Composable
private fun EmojiCell(emoji: String, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .width(34.dp)
                .height(34.dp)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                emoji,
                fontSize = 20.sp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
        if (selected) {
            Box(
                Modifier
                    .width(20.dp)
                    .height(3.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
            )
        }
    }
}

@Composable
private fun StepperRow(
    label: String,
    value: Int,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = onMinus, enabled = value > 0) { Text("－") }
        Text(
            "$value",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        OutlinedButton(onClick = onPlus, enabled = value < 999) { Text("＋") }
    }
}

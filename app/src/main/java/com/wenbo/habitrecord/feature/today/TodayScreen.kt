package com.wenbo.habitrecord.feature.today

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wenbo.habitrecord.di.LocalAppContainer
import com.wenbo.habitrecord.domain.FrequencyPolicy
import com.wenbo.habitrecord.domain.model.CheckInStatus
import com.wenbo.habitrecord.ui.theme.MissRed
import com.wenbo.habitrecord.ui.theme.SkipGray
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    onEditHabit: (Long?) -> Unit,
    onManageHabits: () -> Unit,
) {
    val container = LocalAppContainer.current
    val vm: TodayViewModel = viewModel { TodayViewModel(container) }
    val state by vm.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.onScreenShown() }
    LaunchedEffect(Unit) {
        vm.events.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("今日打卡")
                        Text(
                            state.today.format(
                                DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    AssistChip(
                        onClick = {},
                        label = { Text("${state.balance} 分") },
                        leadingIcon = {
                            Text("🪙", fontSize = 14.sp)
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        ),
                    )
                    Spacer(Modifier.width(12.dp))
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.items.isEmpty() -> EmptyToday(
                onManageHabits = onManageHabits,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            else -> {
                val doneCount = state.items.count { it.status == CheckInStatus.DONE }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Text(
                            "已完成 $doneCount / ${state.items.size}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(state.items, key = { it.habit.id }) { item ->
                        TodayHabitCard(
                            item = item,
                            onToggle = { vm.toggle(item.habit.id) },
                            onToggleSkip = { vm.toggleSkip(item.habit.id) },
                            onEdit = { onEditHabit(item.habit.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayHabitCard(
    item: TodayHabitUi,
    onToggle: () -> Unit,
    onToggleSkip: () -> Unit,
    onEdit: () -> Unit,
) {
    val habit = item.habit
    val done = item.status == CheckInStatus.DONE
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (done) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
        border = when (item.status) {
            CheckInStatus.MISS -> BorderStroke(1.dp, MissRed)
            CheckInStatus.SKIP -> BorderStroke(1.dp, SkipGray)
            else -> null
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(habit.emoji, fontSize = 30.sp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(habit.name, style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        FrequencyPolicy.frequencyLabel(habit),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (item.weekDone != null) {
                        Text(
                            " · 本周 ${item.weekDone}/${habit.timesPerWeek}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (item.streak > 0) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Filled.LocalFireDepartment,
                            contentDescription = "连续",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            "${item.streak}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Icon(
                    when (item.status) {
                        CheckInStatus.DONE -> Icons.Filled.CheckCircle
                        CheckInStatus.SKIP -> Icons.Outlined.RemoveCircleOutline
                        else -> Icons.Outlined.RadioButtonUnchecked
                    },
                    contentDescription = null,
                    tint = when (item.status) {
                        CheckInStatus.DONE -> MaterialTheme.colorScheme.primary
                        CheckInStatus.SKIP -> SkipGray
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(28.dp),
                )
                Text(
                    when (item.status) {
                        CheckInStatus.DONE -> "+${habit.pointsOnComplete}"
                        else -> "完成 +${habit.pointsOnComplete}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 4.dp)) {
            TextButton(onClick = onEdit) { Text("编辑", style = MaterialTheme.typography.labelMedium) }
            Spacer(Modifier.weight(1f))
            if (!done) {
                TextButton(onClick = onToggleSkip) {
                    Text(
                        if (item.status == CheckInStatus.SKIP) "取消跳过" else "今天跳过",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyToday(onManageHabits: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("🌱", fontSize = 56.sp)
        Spacer(Modifier.height(16.dp))
        Text("还没有习惯", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "去创建第一个想养成的习惯吧",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onManageHabits) { Text("去添加习惯") }
    }
}

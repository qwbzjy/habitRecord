package com.wenbo.habitrecord.feature.habits

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wenbo.habitrecord.data.db.HabitEntity
import com.wenbo.habitrecord.di.LocalAppContainer
import com.wenbo.habitrecord.domain.FrequencyPolicy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsScreen(onEditHabit: (Long?) -> Unit) {
    val container = LocalAppContainer.current
    val vm: HabitsViewModel = viewModel { HabitsViewModel(container) }
    val habits by vm.habits.collectAsState()
    val confirmDelete by vm.confirmDelete.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        vm.events.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("我的习惯") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEditHabit(null) }) {
                Icon(Icons.Filled.Add, contentDescription = "添加习惯")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val active = habits.filter { !it.archived }
        val archived = habits.filter { it.archived }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(active, key = { it.id }) { habit ->
                HabitRow(
                    habit = habit,
                    onClick = { onEditHabit(habit.id) },
                    onArchive = { vm.setArchived(habit, true) },
                    onDelete = { vm.requestDelete(habit) },
                )
            }
            if (archived.isNotEmpty()) {
                item {
                    Text(
                        "已归档",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                items(archived, key = { it.id }) { habit ->
                    HabitRow(
                        habit = habit,
                        onClick = { onEditHabit(habit.id) },
                        onArchive = { vm.setArchived(habit, false) },
                        onDelete = { vm.requestDelete(habit) },
                    )
                }
            }
        }
    }

    confirmDelete?.let { habit ->
        AlertDialog(
            onDismissRequest = { vm.dismissDelete() },
            title = { Text("删除习惯") },
            text = { Text("确定删除「${habit.name}」？其全部打卡记录与积分流水将一并删除，且无法恢复。") },
            confirmButton = {
                TextButton(onClick = { vm.confirmDelete() }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { vm.dismissDelete() }) { Text("取消") } },
        )
    }
}

@Composable
private fun HabitRow(
    habit: HabitEntity,
    onClick: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (habit.archived) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(habit.emoji, fontSize = 26.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(habit.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${FrequencyPolicy.frequencyLabel(habit)} · 完成 +${habit.pointsOnComplete}" +
                        if (habit.pointsOnMiss > 0) " / 未完成 -${habit.pointsOnMiss}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onArchive) {
                Icon(
                    if (habit.archived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                    contentDescription = if (habit.archived) "取消归档" else "归档",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

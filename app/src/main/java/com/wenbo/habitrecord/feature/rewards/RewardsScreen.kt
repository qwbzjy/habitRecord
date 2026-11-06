package com.wenbo.habitrecord.feature.rewards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wenbo.habitrecord.data.db.RewardEntity
import com.wenbo.habitrecord.di.LocalAppContainer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardsScreen() {
    val container = LocalAppContainer.current
    val vm: RewardsViewModel = viewModel { RewardsViewModel(container) }
    val state by vm.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.events.collect { snackbarHostState.showSnackbar(it) } }

    Scaffold(
        topBar = { TopAppBar(title = { Text("奖励商店") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { vm.openEditor(null) }) {
                Icon(Icons.Filled.Add, contentDescription = "添加奖励")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                ) {
                    Column(Modifier.fillMaxWidth().padding(20.dp)) {
                        Text("当前积分", style = MaterialTheme.typography.bodyMedium)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("🪙", fontSize = 28.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${state.balance}",
                                style = MaterialTheme.typography.displaySmall,
                            )
                        }
                    }
                }
            }
            if (state.rewards.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("🎁", fontSize = 48.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "添加心仪的奖励，用积分兑换",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(state.rewards, key = { it.reward.id }) { ui ->
                RewardRow(ui = ui, onRedeem = { vm.redeem(ui.reward) }, onEdit = { vm.openEditor(ui.reward) })
            }
            if (state.redemptions.isNotEmpty()) {
                item {
                    Text(
                        "兑换记录",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                items(state.redemptions, key = { it.id }) { entry ->
                    RedemptionRow(entry = entry, onUndo = { vm.undoRedeem(entry) })
                }
            }
        }
    }

    state.editing?.let { editing ->
        RewardEditorDialog(
            initial = editing,
            onDismiss = { vm.closeEditor() },
            onSave = { name, emoji, cost -> vm.saveReward(name, emoji, cost) },
            onDelete = if (editing.id != 0L) {
                { vm.deleteReward(editing) }
            } else {
                null
            },
        )
    }
}

@Composable
private fun RewardRow(ui: RewardUi, onRedeem: () -> Unit, onEdit: () -> Unit) {
    val reward = ui.reward
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(reward.emoji, fontSize = 26.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(reward.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "🪙 ${reward.pointsCost} 分",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onRedeem, enabled = ui.affordable) {
                Text(if (ui.affordable) "兑换" else "积分不足")
            }
        }
    }
}

@Composable
private fun RedemptionRow(entry: com.wenbo.habitrecord.data.db.PointsLedgerEntity, onUndo: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(entry.note ?: "兑换", style = MaterialTheme.typography.bodyMedium)
            Text(
                formatLedgerTime(entry.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "${entry.amount}",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.titleMedium,
        )
        TextButton(onClick = onUndo) { Text("撤销") }
    }
}

private fun formatLedgerTime(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))

@Composable
private fun RewardEditorDialog(
    initial: RewardEntity,
    onDismiss: () -> Unit,
    onSave: (name: String, emoji: String, cost: Int) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(initial.name) }
    var emoji by remember { mutableStateOf(initial.emoji) }
    var cost by remember { mutableStateOf(initial.pointsCost.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "添加奖励" else "编辑奖励") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(20) },
                    label = { Text("奖励名称") },
                    placeholder = { Text("如：看一场电影") },
                    singleLine = true,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    listOf("🎁", "🎬", "🍰", "🛍️", "🎮").forEach { e ->
                        Text(
                            e,
                            fontSize = 22.sp,
                            modifier = Modifier
                                .clickable { emoji = e }
                                .padding(4.dp),
                            color = if (emoji == e) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                OutlinedTextField(
                    value = cost,
                    onValueChange = { cost = it.filter(Char::isDigit).take(4) },
                    label = { Text("所需积分") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, emoji, cost.toIntOrNull() ?: 0) },
                enabled = name.isNotBlank() && (cost.toIntOrNull() ?: 0) > 0,
            ) { Text("保存") }
        },
        dismissButton = {
            Row {
                onDelete?.let {
                    TextButton(onClick = it) { Text("删除", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

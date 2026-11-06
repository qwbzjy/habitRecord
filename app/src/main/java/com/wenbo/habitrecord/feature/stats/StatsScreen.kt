package com.wenbo.habitrecord.feature.stats

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wenbo.habitrecord.data.db.PointsLedgerEntity
import com.wenbo.habitrecord.di.LocalAppContainer
import com.wenbo.habitrecord.domain.model.CheckInStatus
import com.wenbo.habitrecord.ui.theme.MissRedContainer
import com.wenbo.habitrecord.ui.theme.SkipGray
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen() {
    val container = LocalAppContainer.current
    val vm: StatsViewModel = viewModel { StatsViewModel(container) }
    val state by vm.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var pendingImportMode by remember { mutableStateOf(ImportMode.REPLACE) }
    var showImportDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(vm::exportTo) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { vm.importFrom(it, pendingImportMode) } }

    LaunchedEffect(Unit) {
        vm.events.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("统计") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SummaryCard("积分余额", "🪙 ${state.balance}", Modifier.weight(1f))
                    SummaryCard("累计完成", "✅ ${state.totalDone}", Modifier.weight(1f))
                    SummaryCard(
                        "最长连续",
                        "🔥 ${state.streakRanking.maxOfOrNull { it.streak } ?: 0}",
                        Modifier.weight(1f),
                    )
                }
            }

            if (state.habits.isNotEmpty()) {
                item { HeatmapSection(vm = vm, state = state) }

                item { Text("连续打卡排行", style = MaterialTheme.typography.titleSmall) }
                items(state.streakRanking, key = { it.habit.id }) { entry ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(entry.habit.emoji, fontSize = 20.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(entry.habit.name, Modifier.weight(1f))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${entry.streak}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(" 天", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else {
                item {
                    Text(
                        "暂无数据，先去创建习惯并打卡吧",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    )
                }
            }

            if (state.ledger.isNotEmpty()) {
                item { Text("积分流水", style = MaterialTheme.typography.titleSmall) }
                items(state.ledger, key = { it.id }) { entry ->
                    LedgerRow(entry)
                }
            }

            item {
                Text("数据管理", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { exportLauncher.launch(defaultExportName()) },
                        enabled = !state.busy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("导出备份")
                    }
                    OutlinedButton(
                        onClick = { showImportDialog = true },
                        enabled = !state.busy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("导入备份")
                    }
                }
                if (state.busy) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("导入备份") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ImportModeRow(
                        title = "替换恢复（推荐）",
                        desc = "清空当前全部数据，按备份文件完整恢复",
                        selected = pendingImportMode == ImportMode.REPLACE,
                        onClick = { pendingImportMode = ImportMode.REPLACE },
                    )
                    ImportModeRow(
                        title = "合并导入",
                        desc = "保留现有数据，重名习惯/奖励视为同一条",
                        selected = pendingImportMode == ImportMode.MERGE,
                        onClick = { pendingImportMode = ImportMode.MERGE },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showImportDialog = false
                    importLauncher.launch(arrayOf("application/json"))
                }) { Text("选择文件") }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun LedgerRow(entry: PointsLedgerEntity) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(entry.note ?: ledgerReasonLabel(entry.reason), style = MaterialTheme.typography.bodyMedium)
            Text(
                formatLedgerTime(entry.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            if (entry.amount > 0) "+${entry.amount}" else "${entry.amount}",
            color = if (entry.amount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun ImportModeRow(title: String, desc: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SummaryCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(2.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HeatmapSection(vm: StatsViewModel, state: StatsUiState) {
    var toggling by remember { mutableStateOf<Long?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("打卡日历", style = MaterialTheme.typography.titleSmall)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            state.habits.forEach { habit ->
                FilterChip(
                    selected = habit.id == state.selectedHabitId,
                    onClick = { vm.selectHabit(habit.id) },
                    label = { Text(habit.emoji + habit.name) },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = { vm.shiftMonth(-1) }) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "上月")
            }
            Text(
                state.month.format(DateTimeFormatter.ofPattern("yyyy年M月")),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            IconButton(
                onClick = { vm.shiftMonth(1) },
                enabled = !state.month.atEndOfMonth().isAfter(LocalDate.now()),
            ) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "下月")
            }
        }

        val firstDay = state.month.atDay(1)
        val leadingBlanks = (firstDay.dayOfWeek.value + 6) % 7 // 周一为第一列
        val weeks = (leadingBlanks + state.month.lengthOfMonth() + 6) / 7

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        var day = firstDay.minusDays(leadingBlanks.toLong())
        repeat(weeks) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(7) {
                    val date = day
                    val inMonth = date.monthValue == state.month.monthValue && date.year == state.month.year
                    val isFuture = date.isAfter(LocalDate.now())
                    val status = state.statusByDate[date.toEpochDay()]
                    DayCell(
                        date = date,
                        enabled = inMonth && !isFuture && status != null,
                        status = status,
                        onClick = { toggling = date.toEpochDay() },
                        modifier = Modifier.weight(1f),
                    )
                    day = day.plusDays(1)
                }
            }
        }
    }

    toggling?.let { epochDay ->
        val date = LocalDate.ofEpochDay(epochDay)
        val status = state.statusByDate[epochDay]
        AlertDialog(
            onDismissRequest = { toggling = null },
            title = { Text(date.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA))) },
            text = {
                Text(
                    when (status) {
                        CheckInStatus.DONE.value -> "已完成。要取消这次的打卡吗？取消将返还已得积分并按规则补扣。"
                        else -> "标记这一天为已完成（补卡），将撤销该日扣分并记为完成。"
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.toggleDate(epochDay); toggling = null }) {
                    Text(if (status == CheckInStatus.DONE.value) "取消打卡" else "标记完成")
                }
            },
            dismissButton = { TextButton(onClick = { toggling = null }) { Text("关闭") } },
        )
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    enabled: Boolean,
    status: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = when (status) {
        CheckInStatus.DONE.value -> MaterialTheme.colorScheme.primary
        CheckInStatus.MISS.value -> MissRedContainer
        CheckInStatus.SKIP.value -> SkipGray.copy(alpha = 0.3f)
        CheckInStatus.PENDING.value -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surface
    }
    val fg = when {
        status == CheckInStatus.DONE.value -> Color.White
        status == CheckInStatus.MISS.value -> MaterialTheme.colorScheme.error
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = modifier
            .height(34.dp)
            .background(bg, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("${date.dayOfMonth}", color = fg, style = MaterialTheme.typography.labelMedium)
    }
}

private fun ledgerReasonLabel(reason: String): String = when (reason) {
    "CHECKIN" -> "打卡完成"
    "MISS" -> "未完成扣分"
    "REDEEM" -> "兑换奖励"
    "ADJUST" -> "积分校准"
    else -> reason
}

private fun formatLedgerTime(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))

private fun defaultExportName(): String =
    "habitrecord_backup_" + DateTimeFormatter.ofPattern("yyyyMMdd_HHmm").format(LocalDateTime.now()) + ".json"

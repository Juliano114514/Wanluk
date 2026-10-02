package com.wanluk.ui.studio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wanluk.foundation.survey.*
import com.wanluk.libcomposeui.ActionIcon
import com.wanluk.libcomposeui.ActionSymbol
import com.wanluk.libcomposeui.AppDialog
import com.wanluk.libcomposeui.AppTextButton
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun RecordingProgressDialog(session: SessionSummary, enabled: Boolean,
  onDismiss: () -> Unit, onPosition: (Int) -> Unit, onExport: (List<Int>) -> Unit, onClear: (List<Int>) -> Unit,
  viewModel: RecordingProgressViewModel = koinViewModel()) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val selection = rememberMultiSelection(state.page.entries.map { it.step.position.toString() }, enabled, pruneMissing = false)
  var menu by remember { mutableStateOf(false) }
  var clearing by remember { mutableStateOf<List<Int>?>(null) }
  var number by rememberSaveable(session.id) { mutableStateOf("") }
  LaunchedEffect(session.id, session.contentRevision) { viewModel.open(session.id) }
  LaunchedEffect(session.id, state.status) { selection.clear() }
  val available = enabled && !state.loading
  AppDialog(onDismissRequest = { if (enabled) { if (selection.active) selection.exit() else onDismiss() } },
    symbol = ActionSymbol.RECORDINGS, title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("录制进度", Modifier.weight(1f))
        Box {
          MoreButton(available) { menu = true }
          DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem(text = { Text("多选") }, leadingIcon = { ActionIcon(ActionSymbol.MULTISELECT) },
              enabled = !selection.active, onClick = { menu = false; selection.enter() })
          }
        }
      }
    }, text = {
      Column {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          listOf("" to "全部", "pending" to "待录", "recorded" to "已录", "skipped" to "跳过").forEach { (value, label) ->
            FilterChip(state.status == value, { viewModel.filter(value) }, enabled = available, label = { Text(label) })
          }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          OutlinedTextField(number, { number = it.filter(Char::isDigit).take(6) }, Modifier.weight(1f), singleLine = true,
            label = { Text("题号 1–${session.totalSteps}") }, enabled = available)
          AppTextButton(onClick = { onPosition(requireNotNull(number.toIntOrNull()) - 1); onDismiss() },
            enabled = available && (number.toIntOrNull() ?: 0) in 1..session.totalSteps) { Text("跳转") }
        }
        if (selection.active) SelectionToolbar(selection, available) { viewModel.all(selection::all) }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
        state.error?.let { error -> AppTextButton(onClick = viewModel::load, enabled = available) { Text("$error · 重试") } }
        if (!state.loading && state.error == null && state.page.entries.isEmpty()) Text("没有匹配的题目", Modifier.padding(vertical = 12.dp))
        LazyColumn(Modifier.heightIn(max = 300.dp)) {
          items(state.page.entries, key = { it.step.position }) { entry ->
            val step = entry.step
            val id = step.position.toString()
            var rowMenu by remember { mutableStateOf(false) }
            Surface(onClick = {
              if (selection.active) selection.toggle(id) else { onPosition(step.position); onDismiss() }
            }, enabled = available, color = MaterialTheme.colorScheme.surface) {
              Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                if (selection.active) Checkbox(id in selection.selected, null, enabled = available)
                Text("${step.position + 1}. ${entry.text} · 第${step.repetition}次 · ${when (step.status) {
                  "recorded" -> "已录"; "skipped" -> "已跳过"; else -> "待录"
                }}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                if (!selection.active) Box {
                  MoreButton(available) { rowMenu = true }
                  DropdownMenu(rowMenu, { rowMenu = false }) {
                    DropdownMenuItem(text = { Text("清空本题录音") }, leadingIcon = { ActionIcon(ActionSymbol.TRASH) },
                      onClick = { rowMenu = false; clearing = listOf(step.position) })
                  }
                }
              }
            }
          }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          AppTextButton(onClick = viewModel::previous, enabled = available && state.pageNumber > 1) { Text("上一页") }
          Text("第 ${state.pageNumber} 页 · ${state.page.total} 题", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
          AppTextButton(onClick = viewModel::next, enabled = available && state.hasNext) { Text("下一页") }
        }
        if (selection.active) SelectionActions {
          AppTextButton(onClick = { onExport(selection.ids.map(String::toInt)) }, enabled = available && selection.ids.isNotEmpty()) { Text("导出所选") }
          AppTextButton(onClick = { clearing = selection.ids.map(String::toInt) }, enabled = available && selection.ids.isNotEmpty()) { Text("清空录音", color = MaterialTheme.colorScheme.error) }
        }
      }
    }, confirmButton = { AppTextButton(onClick = onDismiss, enabled = enabled) { Text("关闭") } })
  clearing?.let { positions -> DeleteSelectionDialog(if (positions.size == 1) "清空第 ${positions.single() + 1} 题录音？" else "清空 ${positions.size} 项录音？",
    "删除这些题目的全部录音历史，并恢复待录。题目和备注保留，音频无法恢复。", enabled,
    onDismiss = { clearing = null }, onDelete = { clearing = null; selection.clear(); onClear(positions) }) }
}

@Composable
internal fun RecordingHistoryDialog(takes: List<RecordingTake>, selectedTakeId: String?, enabled: Boolean,
  onDismiss: () -> Unit, onPlay: (String) -> Unit, onAdopt: (String) -> Unit,
  onExport: (List<String>) -> Unit, onDelete: (List<String>) -> Unit, onReview: (String, ReviewStatus) -> Unit) {
  val selection = rememberMultiSelection(takes.map { it.id }, enabled)
  var menu by remember { mutableStateOf(false) }
  var deleting by remember { mutableStateOf<List<String>?>(null) }
  val exportable = takes.filter { it.id in selection.selected && it.state == RecordingTake.SAVED }.map { it.id }
  AppDialog(onDismissRequest = { if (enabled) { if (selection.active) selection.exit() else onDismiss() } },
    symbol = ActionSymbol.HISTORY, title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("本条录音历史", Modifier.weight(1f))
        Box {
          MoreButton(enabled) { menu = true }
          DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem(text = { Text("多选") }, leadingIcon = { ActionIcon(ActionSymbol.MULTISELECT) },
              enabled = !selection.active && takes.isNotEmpty(), onClick = { menu = false; selection.enter() })
          }
        }
      }
    }, text = {
      Column {
        if (selection.active) SelectionToolbar(selection, enabled) { selection.all(takes.map { it.id }) }
        if (takes.isEmpty()) Text("暂无录音历史")
        LazyColumn(Modifier.heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          items(takes, key = { it.id }) { take ->
            var rowMenu by remember { mutableStateOf(false) }
            Surface(onClick = { if (selection.active) selection.toggle(take.id) else if (take.state == RecordingTake.SAVED) onPlay(take.id) },
              enabled = enabled, color = MaterialTheme.colorScheme.surface) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                if (selection.active) Checkbox(take.id in selection.selected, null, enabled = enabled)
                Column(Modifier.weight(1f)) {
                  Text(SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(take.createdAt)) + " · " +
                    if (take.state == RecordingTake.SAVED) String.format(Locale.ROOT, "%.1f 秒", take.durationMs / 1000.0) else "录制中断")
                  if (take.id == selectedTakeId) Text("当前采用", color = MaterialTheme.colorScheme.primary)
                  if (take.state == RecordingTake.SAVED) Text("复核：${take.review.label}", style = MaterialTheme.typography.labelSmall)
                  if (take.warning.isNotBlank()) Text(take.warning, style = MaterialTheme.typography.bodySmall)
                  if (!selection.active && take.state == RecordingTake.SAVED) Row {
                    AppTextButton(onClick = { onPlay(take.id) }, enabled = enabled) { Text("回听") }
                    AppTextButton(onClick = { onAdopt(take.id) }, enabled = enabled && take.id != selectedTakeId) { Text("采用此版") }
                  }
                }
                if (!selection.active) Box {
                  MoreButton(enabled) { rowMenu = true }
                  DropdownMenu(rowMenu, { rowMenu = false }) {
                    if (take.state == RecordingTake.SAVED) {
                      ReviewStatus.entries.forEach { status -> DropdownMenuItem(text = { Text("复核 · ${status.label}") },
                        enabled = status.value != take.reviewStatus, onClick = { rowMenu = false; onReview(take.id, status) }) }
                      DropdownMenuItem(text = { Text("导出此版") }, onClick = { rowMenu = false; onExport(listOf(take.id)) })
                    }
                    DropdownMenuItem(text = { Text("删除此版", color = MaterialTheme.colorScheme.error) },
                      leadingIcon = { ActionIcon(ActionSymbol.TRASH) }, enabled = take.state != RecordingTake.RECORDING,
                      onClick = { rowMenu = false; deleting = listOf(take.id) })
                  }
                }
              }
            }
          }
        }
        if (selection.active) SelectionActions {
          AppTextButton(onClick = { onExport(exportable) }, enabled = enabled && exportable.isNotEmpty()) { Text("导出 ${exportable.size} 条") }
          AppTextButton(onClick = { deleting = selection.ids }, enabled = enabled && selection.ids.isNotEmpty()) { Text("删除", color = MaterialTheme.colorScheme.error) }
        }
      }
    }, confirmButton = { AppTextButton(onClick = onDismiss, enabled = enabled) { Text("关闭") } })
  deleting?.let { ids -> DeleteSelectionDialog(if (ids.size == 1) "删除这个录音版本？" else "删除 ${ids.size} 个版本？",
    "删除后无法恢复。当前采用版本被删除时，该题恢复待录；其余历史保留。", enabled,
    onDismiss = { deleting = null }, onDelete = { deleting = null; selection.clear(); onDelete(ids) }) }
}

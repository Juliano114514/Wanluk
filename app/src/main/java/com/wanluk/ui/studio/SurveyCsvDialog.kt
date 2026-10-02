package com.wanluk.ui.studio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wanluk.foundation.survey.CsvField
import com.wanluk.foundation.survey.SurveyPackage
import com.wanluk.foundation.survey.RecordingPlanType
import androidx.compose.ui.text.style.TextOverflow
import com.wanluk.libcomposeui.AppDialog
import com.wanluk.libcomposeui.ActionSymbol
import com.wanluk.libcomposeui.AppButton
import com.wanluk.libcomposeui.AppTextButton

@Composable
internal fun SurveyCsvDialog(state: SurveyCsvState, viewModel: SurveyCsvViewModel, onDraft: (SurveyPackage) -> Unit) {
  if (!state.open) return
  var page by remember { mutableIntStateOf(0) }
  LaunchedEffect(state.rows?.size) { page = page.coerceAtMost(((state.rows?.size ?: 1) - 1).coerceAtLeast(0) / 80) }
  AppDialog(onDismissRequest = viewModel::close, symbol = ActionSymbol.IMPORT,
    title = { Text(if (state.rows == null) "CSV 字段映射" else "CSV 导入预览") }, text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        val table = state.table
        val rows = state.rows
        if (table != null && rows == null) {
          Text("方案分类", style = MaterialTheme.typography.labelLarge)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RecordingPlanType.entries.forEach { type -> FilterChip(state.planType == type,
              { viewModel.planType(type) }, enabled = !state.busy, label = { Text(type.label) }) }
          }
          Text("${table.records.size} 行 · 空值继承默认设置；隐藏提示请填写 hide_instruction=true。", style = MaterialTheme.typography.bodySmall)
          Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
            CsvField.entries.forEach { field ->
              var menu by remember { mutableStateOf(false) }
              Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(field.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Box {
                  AppTextButton(onClick = { menu = true }, enabled = !state.busy) {
                    Text(state.mapping[field]?.let { "${it + 1}. ${table.columns[it].take(80)}" } ?: "未映射")
                  }
                  DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("未映射") }, onClick = { viewModel.mapping(field, null); menu = false })
                    table.columns.forEachIndexed { index, column -> DropdownMenuItem(text = { Text("${index + 1}. ${column.take(80)}") },
                      onClick = { viewModel.mapping(field, index); menu = false }) }
                  }
                }
              }
            }
          }
        } else if (rows != null) {
          OutlinedTextField(state.title, viewModel::title, Modifier.fillMaxWidth(), label = { Text("调查包名称") }, singleLine = true, enabled = !state.busy)
          Text("有效 ${rows.count { it.item != null }} 行 · 错误 ${rows.count { it.error != null }} 行 · 已选 ${state.selected.size}", style = MaterialTheme.typography.bodySmall)
          Row {
            AppTextButton(onClick = viewModel::all, enabled = !state.busy) { Text("全选有效行") }
            AppTextButton(onClick = viewModel::clear, enabled = !state.busy) { Text("清空选择") }
          }
          LazyColumn(Modifier.heightIn(max = 300.dp)) {
            items(rows.drop(page * 80).take(80), key = { it.id }) { row ->
              var menu by remember { mutableStateOf(false) }
              Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(row.id in state.selected, { viewModel.toggle(row.id) }, enabled = !state.busy && row.item != null)
                Column(Modifier.weight(1f)) {
                  Text("第 ${row.line} 行 · ${row.item?.text ?: row.text}", style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                  Text(row.error ?: "${row.item?.type} · ${row.item?.repetitions} 次", style = MaterialTheme.typography.bodySmall,
                    color = if (row.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                  MoreButton(!state.busy) { menu = true }
                  DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("移出预览") }, onClick = { menu = false; viewModel.remove(row.id) })
                  }
                }
              }
            }
          }
          Row(verticalAlignment = Alignment.CenterVertically) {
            AppTextButton(onClick = { page-- }, enabled = !state.busy && page > 0) { Text("上一页") }
            Text("第 ${page + 1} 页", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
            AppTextButton(onClick = { page++ }, enabled = !state.busy && (page + 1) * 80 < rows.size) { Text("下一页") }
          }
          AppTextButton(onClick = viewModel::remap, enabled = !state.busy) { Text("调整字段映射") }
        }
      }
    }, confirmButton = {
      if (state.table != null && state.rows == null) AppButton(onClick = viewModel::preview, enabled = !state.busy) { Text("预览") }
      else if (state.rows != null) AppButton(onClick = { viewModel.draft(onDraft) }, enabled = !state.busy && state.title.isNotBlank() && state.selected.isNotEmpty()) { Text("生成草稿") }
    }, dismissButton = { AppTextButton(onClick = viewModel::close, enabled = !state.busy) { Text("关闭") } })
}

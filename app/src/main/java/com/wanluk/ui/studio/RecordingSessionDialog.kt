package com.wanluk.ui.studio

import com.wanluk.libcomposeui.AppDialog
import com.wanluk.libcomposeui.ActionIcon
import com.wanluk.libcomposeui.ActionSymbol

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wanluk.libroom.entity.SurveySessionEntity

@Composable
internal fun RecordingSessionDialog(session: SurveySessionEntity, enabled: Boolean, onDismiss: () -> Unit,
  onSave: (String, String, String) -> Unit, onDelete: () -> Unit, onExport: () -> Unit) {
  var page by rememberSaveable(session.id) { mutableStateOf("menu") }
  var title by rememberSaveable(session.id) { mutableStateOf(session.title) }
  var alias by rememberSaveable(session.id) { mutableStateOf(session.speakerAlias) }
  var dialect by rememberSaveable(session.id) { mutableStateOf(session.dialect) }
  AppDialog(onDismissRequest = { if (enabled) onDismiss() },
    symbol = if (page == "delete") ActionSymbol.TRASH else ActionSymbol.RECORDINGS, title = {
    Text(when (page) { "edit" -> "编辑录制信息"; "delete" -> "删除这条录制？"; else -> session.title })
  }, text = {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      when (page) {
        "edit" -> {
          OutlinedTextField(title, { title = it.take(120) }, Modifier.fillMaxWidth(), enabled = enabled,
            label = { Text("录制名称") }, singleLine = true)
          OutlinedTextField(alias, { alias = it.take(80) }, Modifier.fillMaxWidth(), enabled = enabled,
            label = { Text("怎么称呼您？") }, singleLine = true)
          OutlinedTextField(dialect, { dialect = it.take(160) }, Modifier.fillMaxWidth(), enabled = enabled,
            label = { Text("方言") }, singleLine = true)
          Text("只修改这条录制的信息。", style = MaterialTheme.typography.bodySmall)
        }
        "delete" -> Text("将删除《${session.title}》的录制进度和全部本地录音，无法恢复。已导出的文件和录制方案仍保留。")
        else -> {
          FilledTonalButton(onClick = onExport, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            ActionIcon(ActionSymbol.EXPORT); Spacer(Modifier.width(8.dp)); Text("导出录音 ZIP")
          }
          OutlinedButton(onClick = { page = "edit" }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            ActionIcon(ActionSymbol.EDIT); Spacer(Modifier.width(8.dp)); Text("编辑信息")
          }
          TextButton(onClick = { page = "delete" }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text("删除录制", color = MaterialTheme.colorScheme.error)
          }
        }
      }
    }
  }, confirmButton = {
    when (page) {
      "edit" -> Button(onClick = { onSave(title, alias, dialect) }, enabled = enabled && title.isNotBlank()) { Text("保存") }
      "delete" -> Button(onClick = onDelete, enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)) { Text("删除") }
      else -> TextButton(onClick = onDismiss, enabled = enabled) { Text("关闭") }
    }
  }, dismissButton = {
    if (page != "menu") TextButton(onClick = { page = "menu" }, enabled = enabled) { Text("取消") }
  })
}

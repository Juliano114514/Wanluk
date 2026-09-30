package com.wanluk.ui.studio

import com.wanluk.libcomposeui.ActionSymbol

import com.wanluk.libcomposeui.ActionIcon

import com.wanluk.libcomposeui.AppDialog
import com.wanluk.libcomposeui.EmptyState

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.gson.GsonBuilder
import com.wanluk.foundation.survey.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SurveyEditor(task: SurveyPackage, enabled: Boolean, onChange: (SurveyPackage) -> Unit,
  onLibrary: () -> Unit, onSave: () -> Unit) {
  var editingItem by remember { mutableStateOf<SurveyItem?>(null) }
  var jsonEditor by remember { mutableStateOf(false) }
  var preview by remember { mutableStateOf(false) }
  var settings by remember { mutableStateOf(false) }
  var menu by remember { mutableStateOf(false) }
  Column(Modifier.fillMaxSize().imePadding().padding(horizontal = 20.dp)) {
    OutlinedTextField(task.title, { onChange(task.copy(title = it.take(120))) }, Modifier.fillMaxWidth(),
      enabled = enabled, label = { Text("名称") }, singleLine = true)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Text("${task.items.size} 字目", style = MaterialTheme.typography.labelLarge)
      Spacer(Modifier.weight(1f))
      TextButton(onClick = onLibrary, enabled = enabled && task.items.size < SurveyPackage.MAX_ITEMS &&
        task.totalSteps < SurveyPackage.MAX_STEPS) {
        ActionIcon(ActionSymbol.ADD, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("选字")
      }
      Box {
        MoreButton(enabled, onClick = { menu = true })
        DropdownMenu(menu, onDismissRequest = { menu = false }) {
          DropdownMenuItem(leadingIcon = { ActionIcon(ActionSymbol.SETTINGS) }, text = { Text("调查包设置") }, onClick = { menu = false; settings = true })
          DropdownMenuItem(leadingIcon = { ActionIcon(ActionSymbol.ADD) }, text = { Text("自定义题目") }, enabled = task.items.size < SurveyPackage.MAX_ITEMS,
            onClick = { menu = false; editingItem = SurveyItem() })
          DropdownMenuItem(leadingIcon = { ActionIcon(ActionSymbol.PLAY) }, text = { Text("预览") }, enabled = task.items.isNotEmpty(),
            onClick = { menu = false; preview = true })
          DropdownMenuItem(leadingIcon = { ActionIcon(ActionSymbol.EDIT) }, text = { Text("JSON 编辑") }, onClick = { menu = false; jsonEditor = true })
        }
      }
    }
    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)) {
      itemsIndexed(task.items, key = { _, item -> item.itemId }) { index, item ->
        var itemMenu by remember { mutableStateOf(false) }
        OutlinedCard(modifier = Modifier.fillMaxWidth().combinedClickable(enabled = enabled,
          onClickLabel = "编辑题目", onClick = { editingItem = item },
          onLongClickLabel = "更多", onLongClick = { itemMenu = true })) {
          Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("${index + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(item.text, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge,
              maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (item.repetitions > 1) Text("×${item.repetitions}", style = MaterialTheme.typography.labelMedium)
            Box {
              MoreButton(enabled, onClick = { itemMenu = true })
              DropdownMenu(itemMenu, onDismissRequest = { itemMenu = false }) {
                DropdownMenuItem(leadingIcon = { ActionIcon(ActionSymbol.UP) }, text = { Text("上移") }, enabled = index > 0, onClick = {
                  itemMenu = false
                  val changed = task.items.toMutableList()
                  java.util.Collections.swap(changed, index, index - 1)
                  onChange(task.copy(items = changed))
                })
                DropdownMenuItem(leadingIcon = { ActionIcon(ActionSymbol.DOWN) }, text = { Text("下移") }, enabled = index < task.items.lastIndex, onClick = {
                  itemMenu = false
                  val changed = task.items.toMutableList()
                  java.util.Collections.swap(changed, index, index + 1)
                  onChange(task.copy(items = changed))
                })
                DropdownMenuItem(leadingIcon = { ActionIcon(ActionSymbol.TRASH) },
                  text = { Text("移除", color = MaterialTheme.colorScheme.error) }, onClick = {
                  itemMenu = false
                  onChange(task.copy(items = task.items.filterNot { it.itemId == item.itemId }))
                })
              }
            }
          }
        }
      }
      if (task.items.isEmpty()) item {
        EmptyState(ActionSymbol.LIBRARY, "先选几个字目吧", "从字库挑选，或在更多中添加词语和句子。")
      }
    }
    Button(onClick = onSave, enabled = enabled && task.items.isNotEmpty() && task.title.isNotBlank(),
      modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).heightIn(min = 56.dp)) { Text("保存") }
  }
  if (settings) AppDialog(onDismissRequest = { settings = false }, symbol = ActionSymbol.SETTINGS, title = { Text("调查包设置") }, text = {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      OutlinedTextField(task.description, { onChange(task.copy(description = it.take(4000))) },
        enabled = enabled, label = { Text("说明") }, minLines = 2, maxLines = 5)
      OutlinedTextField(task.languageTag, { onChange(task.copy(languageTag = it.take(80))) },
        enabled = enabled, label = { Text("语言标记") }, singleLine = true)
      OutlinedTextField(task.dialect, { onChange(task.copy(dialect = it.take(160))) },
        enabled = enabled, label = { Text("方言提示") })
      Text("保存不影响已经开始的录制。", style = MaterialTheme.typography.bodySmall)
    }
  }, confirmButton = { Button(onClick = { settings = false }) { Text("完成") } })
  editingItem?.let { original -> ItemEditor(original, onDismiss = { editingItem = null }) { changed ->
    val exists = task.items.any { it.itemId == changed.itemId }
    onChange(task.copy(items = if (exists) task.items.map { if (it.itemId == changed.itemId) changed else it } else task.items + changed))
    editingItem = null
  } }
  if (jsonEditor) JsonEditor(task, onDismiss = { jsonEditor = false }) { onChange(it); jsonEditor = false }
  if (preview) TaskPreview(task, onDismiss = { preview = false })
}


@Composable
private fun ItemEditor(original: SurveyItem, onDismiss: () -> Unit, onSave: (SurveyItem) -> Unit) {
  var item by remember(original.itemId) { mutableStateOf(original) }
  AppDialog(onDismissRequest = onDismiss, symbol = ActionSymbol.EDIT, title = { Text("编辑题目") }, text = {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SurveyItemType.entries.forEach { type -> FilterChip(selected = item.type == type.value,
          onClick = { item = item.copy(type = type.value) }, label = { Text(type.label) }) }
      }
      OutlinedTextField(item.text, { item = item.copy(text = it.take(500), phonology = if (it == original.text) original.phonology else null) },
        label = { Text("实际要录的字／词／句") }, minLines = 1, maxLines = 5)
      OutlinedTextField(item.instruction, { item = item.copy(instruction = it.take(2000)) }, label = { Text("给发音人的提示") }, minLines = 2, maxLines = 5)
      Text("明确写出录单字还是整个词语。改动采集文本时会清除原音韵标注，避免错配。", style = MaterialTheme.typography.bodySmall)
      Row {
        TextButton(onClick = { item = item.copy(repetitions = item.repetitions - 1) }, enabled = item.repetitions > 1) { Text("减少") }
        Text("录 ${item.repetitions} 次", modifier = Modifier.padding(top = 12.dp))
        TextButton(onClick = { item = item.copy(repetitions = item.repetitions + 1) }, enabled = item.repetitions < 5) { Text("增加") }
      }
      Row { Checkbox(item.allowSkip, { item = item.copy(allowSkip = it) }); Text("允许跳过，原因可稍后填写", modifier = Modifier.padding(top = 12.dp)) }
      if (item.phonology != null) Text("已附带汉语音韵标注，可在 JSON 编辑器中查看。", style = MaterialTheme.typography.bodySmall)
    }
  }, confirmButton = { Button(onClick = { onSave(item) }, enabled = item.text.isNotBlank()) { Text("确定") } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
private fun JsonEditor(task: SurveyPackage, onDismiss: () -> Unit, onApply: (SurveyPackage) -> Unit) {
  var json by remember { mutableStateOf(GsonBuilder().setPrettyPrinting().create().toJson(task)) }
  var error by remember { mutableStateOf<String?>(null) }
  var checking by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  AppDialog(onDismissRequest = { if (!checking) onDismiss() }, symbol = ActionSymbol.EDIT, title = { Text("任务 JSON · 协议 v1") }, text = {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("仅编辑任务数据。应用前会校验题型、长度、版本与重复 ID。", style = MaterialTheme.typography.bodySmall)
      OutlinedTextField(json, { if (it.length <= SurveyPackageCodec.MAX_BYTES) json = it },
        Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 360.dp), enabled = !checking, minLines = 8, maxLines = 16)
      error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
      if (checking) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
  }, confirmButton = { Button(enabled = !checking, onClick = {
    checking = true
    scope.launch {
      try {
        val parsed = withContext(Dispatchers.Default) { SurveyPackageCodec.decode(json) }
        onApply(parsed)
      } catch (exception: Exception) {
        if (exception is kotlinx.coroutines.CancellationException) throw exception
        error = (exception.message ?: "JSON 格式不正确").take(240)
      } finally { checking = false }
    }
  }) { Text("校验并应用") } }, dismissButton = { TextButton(onClick = onDismiss, enabled = !checking) { Text("取消") } })
}

@Composable
private fun TaskPreview(task: SurveyPackage, onDismiss: () -> Unit) {
  var position by rememberSaveable { mutableIntStateOf(0) }
  val item = task.items[position]
  AppDialog(onDismissRequest = onDismiss, title = { Text("发音人提示预览 ${position + 1}/${task.items.size}") }, text = {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
      Text(SurveyItemType.entries.first { it.value == item.type }.label, style = MaterialTheme.typography.labelLarge)
      Text(item.text, style = MaterialTheme.typography.headlineLarge)
      Text(item.instruction)
      Text("录制 ${item.repetitions} 次 · ${if (item.allowSkip) "允许跳过" else "需要录制"}")
      Row {
        TextButton(onClick = { position-- }, enabled = position > 0) { Text("上一题") }
        TextButton(onClick = { position++ }, enabled = position < task.items.lastIndex) { Text("下一题") }
      }
    }
  }, confirmButton = { Button(onClick = onDismiss) { Text("关闭预览") } })
}

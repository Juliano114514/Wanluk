package com.wanluk.ui.studio

import com.wanluk.libcomposeui.AppButton as Button
import com.wanluk.libcomposeui.AppTextButton as TextButton

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
import com.wanluk.foundation.survey.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SurveyEditor(task: SurveyPackage, enabled: Boolean, onChange: (SurveyPackage) -> Unit,
  onLibrary: () -> Unit, onSave: () -> Unit, onResolveItem: (SurveyItem) -> SurveyItem, onExport: (SurveyPackage) -> Unit) {
  var editingItem by remember { mutableStateOf<SurveyItem?>(null) }
  var jsonEditor by remember { mutableStateOf(false) }
  var preview by remember { mutableStateOf(false) }
  var settings by remember { mutableStateOf(false) }
  var menu by remember { mutableStateOf(false) }
  val selection = rememberMultiSelection(task.items.map { it.itemId }, enabled)
  val selectedItems = task.items.filter { it.itemId in selection.selected }
  var removing by remember { mutableStateOf(false) }
  Column(Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp)) {
    OutlinedTextField(task.title, { onChange(task.copy(title = it.take(120))) }, Modifier.fillMaxWidth(),
      enabled = enabled, label = { Text("名称") }, singleLine = true)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      RecordingPlanType.entries.forEach { type -> FilterChip(task.planType == type,
        { onChange(task.copy(schemaVersion = 3, planType = type)) }, enabled = enabled, label = { Text(type.label) }) }
    }
    Text("每条最多 ${task.planType.maxSeconds / 60} 分钟", style = MaterialTheme.typography.bodySmall)
    val oversized = task.items.indexOfFirst { it.text.length > task.planType.maxTextLength }
    if (oversized >= 0) Text("第 ${oversized + 1} 题超过 ${task.planType.maxTextLength} 字符，请编辑内容或调整分类。",
      color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Text("${task.items.size} 题", style = MaterialTheme.typography.labelLarge)
      Spacer(Modifier.weight(1f))
      TextButton(onClick = onLibrary, enabled = enabled && task.items.size < SurveyPackage.MAX_ITEMS &&
        task.totalSteps < SurveyPackage.MAX_STEPS) {
        ActionIcon(ActionSymbol.ADD, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("选字")
      }
      Box {
        MoreButton(enabled, onClick = { menu = true })
        DropdownMenu(menu, onDismissRequest = { menu = false }) {
          DropdownMenuItem(leadingIcon = { ActionIcon(ActionSymbol.MULTISELECT) }, text = { Text("多选") },
            enabled = !selection.active && task.items.isNotEmpty(), onClick = { menu = false; selection.enter() })
          DropdownMenuItem(leadingIcon = { ActionIcon(ActionSymbol.SETTINGS) }, text = { Text("调查包设置") }, onClick = { menu = false; settings = true })
          DropdownMenuItem(leadingIcon = { ActionIcon(ActionSymbol.ADD) }, text = { Text("自定义题目") }, enabled = task.items.size < SurveyPackage.MAX_ITEMS,
            onClick = {
              menu = false
              editingItem = onResolveItem(if (task.planType == RecordingPlanType.PASSAGE)
                SurveyItem(type = SurveyItemType.SENTENCE.value, instruction = BuiltinSurveys.PASSAGE_PROMPT,
                  extInfo = ItemExtInfo(instruction = BuiltinSurveys.PASSAGE_PROMPT)) else SurveyItem())
            })
          DropdownMenuItem(leadingIcon = { ActionIcon(ActionSymbol.PLAY) }, text = { Text("预览") }, enabled = task.items.isNotEmpty(),
            onClick = { menu = false; preview = true })
          DropdownMenuItem(leadingIcon = { ActionIcon(ActionSymbol.EDIT) }, text = { Text("JSON 编辑") }, onClick = { menu = false; jsonEditor = true })
        }
      }
    }
    if (selection.active) SelectionToolbar(selection, enabled) { selection.all(task.items.map { it.itemId }) }
    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 12.dp)) {
      itemsIndexed(task.items, key = { _, item -> item.itemId }) { index, item ->
        var itemMenu by remember { mutableStateOf(false) }
        Surface(color = MaterialTheme.colorScheme.background,
          modifier = Modifier.fillMaxWidth().combinedClickable(enabled = enabled,
          onClickLabel = if (selection.active) "选择题目" else "编辑题目", onClick = { if (selection.active) selection.toggle(item.itemId) else editingItem = item },
          onLongClickLabel = "更多", onLongClick = { if (selection.active) selection.toggle(item.itemId) else itemMenu = true })) {
          Row(Modifier.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("${index + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(item.text, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
              maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (item.repetitions > 1) Text("×${item.repetitions}", style = MaterialTheme.typography.labelMedium)
            if (selection.active) Checkbox(item.itemId in selection.selected, { selection.toggle(item.itemId) }, enabled = enabled)
            else Box {
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
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      }
      if (task.items.isEmpty()) item {
        EmptyState(ActionSymbol.LIBRARY, "暂无题目", "从字库选字，或在更多菜单中添加")
      }
    }
    if (selection.active) SelectionActions {
      TextButton(onClick = { onExport(task.copy(packageId = UUID.randomUUID().toString(), revision = 1,
        title = task.title.ifBlank { "所选题目" }.take(116) + "（选录）", items = selectedItems)) }, enabled = enabled && selectedItems.isNotEmpty()) { Text("导出方案") }
      TextButton(onClick = { onChange(task.copy(items = task.items + selectedItems.map { it.duplicate() })) },
        enabled = enabled && selectedItems.isNotEmpty() && task.items.size + selectedItems.size <= SurveyPackage.MAX_ITEMS &&
          task.totalSteps + selectedItems.sumOf { it.repetitions } <= SurveyPackage.MAX_STEPS) { Text("复制") }
      TextButton(onClick = { removing = true }, enabled = enabled && selectedItems.isNotEmpty()) { Text("移除", color = MaterialTheme.colorScheme.error) }
    }
    else Button(onClick = onSave, enabled = enabled && task.items.isNotEmpty() && task.title.isNotBlank() && oversized < 0,
      modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).heightIn(min = 48.dp)) { Text("保存") }
  }
  if (removing) DeleteSelectionDialog("移除 ${selectedItems.size} 道题目？", "只修改当前草稿，保存后生效；已有录制保留。", enabled,
    onDismiss = { removing = false }, onDelete = {
      removing = false
      onChange(task.copy(items = task.items.filterNot { it.itemId in selection.selected }))
      selection.clear()
    })
  if (settings) AppDialog(onDismissRequest = { settings = false }, symbol = ActionSymbol.SETTINGS, title = { Text("调查包设置") }, text = {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      val defaults = requireNotNull(task.defaults)
      Text("公用录制设置", style = MaterialTheme.typography.titleSmall)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(SurveyItemType.CHARACTER, SurveyItemType.WORD).forEach { type ->
          FilterChip(defaults.displayType == type.value,
            { onChange(task.copy(defaults = defaults.copy(displayType = type.value))) },
            enabled = enabled, label = { Text(type.label) })
        }
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(defaults.instruction == null, { inherit ->
          onChange(task.copy(defaults = defaults.copy(instruction = if (inherit) null else "")))
        }, enabled = enabled)
        Text("使用默认录制提示")
      }
      defaults.instruction?.let { instruction ->
        OutlinedTextField(instruction, { onChange(task.copy(defaults = defaults.copy(instruction = it.take(2000)))) },
          modifier = Modifier.fillMaxWidth(), enabled = enabled, label = { Text("公用录制提示") }, maxLines = 5)
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { onChange(task.copy(defaults = defaults.copy(repetitions = defaults.repetitions - 1))) },
          enabled = enabled && defaults.repetitions > 1) { Text("减少") }
        Text("每题录 ${defaults.repetitions} 次")
        TextButton(onClick = { onChange(task.copy(defaults = defaults.copy(repetitions = defaults.repetitions + 1))) },
          enabled = enabled && defaults.repetitions < 5) { Text("增加") }
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(defaults.allowSkip, { onChange(task.copy(defaults = defaults.copy(allowSkip = it))) }, enabled = enabled)
        Text("允许跳过")
      }
      OutlinedTextField(task.description, { onChange(task.copy(description = it.take(4000))) },
        modifier = Modifier.fillMaxWidth(), enabled = enabled, label = { Text("说明") }, minLines = 2, maxLines = 5)
      OutlinedTextField(task.languageTag, { onChange(task.copy(languageTag = it.take(80))) },
        modifier = Modifier.fillMaxWidth(), enabled = enabled, label = { Text("语言标记") }, singleLine = true)
      OutlinedTextField(task.dialect, { onChange(task.copy(dialect = it.take(160))) },
        modifier = Modifier.fillMaxWidth(), enabled = enabled, label = { Text("方言提示") })
      Text("保存不影响已经开始的录制。", style = MaterialTheme.typography.bodySmall)
    }
  }, confirmButton = { Button(onClick = { settings = false }) { Text("完成") } })
  editingItem?.let { original -> ItemEditor(original, task.planType, onResolveItem, onDismiss = { editingItem = null }) { changed ->
    val exists = task.items.any { it.itemId == changed.itemId }
    onChange(task.copy(items = if (exists) task.items.map { if (it.itemId == changed.itemId) changed else it } else task.items + changed))
    editingItem = null
  } }
  if (jsonEditor) JsonEditor(task, onDismiss = { jsonEditor = false }) { onChange(it); jsonEditor = false }
  if (preview) TaskPreview(task, onDismiss = { preview = false })
}


@Composable
private fun ItemEditor(original: SurveyItem, planType: RecordingPlanType, onResolve: (SurveyItem) -> SurveyItem,
  onDismiss: () -> Unit, onSave: (SurveyItem) -> Unit) {
  var item by remember(original.itemId) { mutableStateOf(original) }
  var textError by remember { mutableStateOf<String?>(null) }
  AppDialog(onDismissRequest = onDismiss, symbol = ActionSymbol.EDIT, title = { Text("编辑题目") }, text = {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SurveyItemType.entries.forEach { type -> FilterChip(selected = item.type == type.value,
          onClick = {
            item = if (item.wordId?.toIntOrNull() != null && type != SurveyItemType.SENTENCE) {
              onResolve(item.copy(extInfo = (item.extInfo ?: ItemExtInfo()).copy(displayType = type.value)))
            } else item.copy(type = type.value,
              wordId = if (item.wordId?.toIntOrNull() != null) "ex_${UUID.randomUUID()}" else item.wordId,
              phonology = if (type == SurveyItemType.SENTENCE) null else item.phonology,
              extInfo = item.extInfo?.copy(displayType = null)?.compact())
          }, label = { Text(type.label) }) }
      }
      OutlinedTextField(item.text, { value ->
        if (value.length > planType.maxTextLength) textError = "内容最多 ${planType.maxTextLength} 字符"
        else {
          textError = null
          if (value != item.text) item = item.copy(text = value, phonology = null,
          wordId = if (item.wordId?.toIntOrNull() != null) "ex_${UUID.randomUUID()}" else item.wordId,
          extInfo = item.extInfo?.copy(displayType = null)?.compact())
        }
      },
        modifier = Modifier.fillMaxWidth(), label = { Text("录制内容") }, minLines = 1, maxLines = 8,
        isError = textError != null || item.text.length > planType.maxTextLength,
        supportingText = { Text(textError ?: "${item.text.length}/${planType.maxTextLength} 字符") })
      OutlinedTextField(item.instruction, { item = item.copy(instruction = it.take(2000),
        extInfo = (item.extInfo ?: ItemExtInfo()).copy(instruction = it.take(2000))) },
        modifier = Modifier.fillMaxWidth(), label = { Text("录制提示") }, minLines = 2, maxLines = 5)
      TextButton(onClick = { item = onResolve(item.copy(extInfo = item.extInfo?.copy(instruction = null)?.compact())) },
        enabled = item.extInfo?.instruction != null) { Text("继承方案提示") }
      Text("修改内置内容后成为方案自定义字目，不加入字库。", style = MaterialTheme.typography.bodySmall)
      Row {
        TextButton(onClick = { item = item.copy(repetitions = item.repetitions - 1,
          extInfo = (item.extInfo ?: ItemExtInfo()).copy(repetitions = item.repetitions - 1)) }, enabled = item.repetitions > 1) { Text("减少") }
        Text("录 ${item.repetitions} 次", modifier = Modifier.padding(top = 12.dp))
        TextButton(onClick = { item = item.copy(repetitions = item.repetitions + 1,
          extInfo = (item.extInfo ?: ItemExtInfo()).copy(repetitions = item.repetitions + 1)) }, enabled = item.repetitions < 5) { Text("增加") }
      }
      TextButton(onClick = { item = onResolve(item.copy(extInfo = item.extInfo?.copy(repetitions = null)?.compact())) },
        enabled = item.extInfo?.repetitions != null) { Text("继承方案次数") }
      Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(item.allowSkip, { item = item.copy(allowSkip = it, extInfo = (item.extInfo ?: ItemExtInfo()).copy(allowSkip = it)) }); Text("允许跳过")
      }
      TextButton(onClick = { item = onResolve(item.copy(extInfo = item.extInfo?.copy(allowSkip = null)?.compact())) },
        enabled = item.extInfo?.allowSkip != null) { Text("继承跳过设置") }
      if (item.wordId?.toIntOrNull() != null) TextButton(onClick = {
        item = onResolve(item.copy(extInfo = item.extInfo?.copy(displayType = null)?.compact()))
      }, enabled = item.extInfo?.displayType != null) { Text("继承呈现方式") }
      OutlinedTextField(item.extInfo?.note.orEmpty(), { item = item.copy(extInfo = (item.extInfo ?: ItemExtInfo()).copy(note = it.take(2000)).compact()) },
        Modifier.fillMaxWidth(), label = { Text("补充说明") }, maxLines = 5)
      if (item.phonology != null) Text("包含音韵标注", style = MaterialTheme.typography.bodySmall)
    }
  }, confirmButton = { Button(onClick = { onSave(item) }, enabled = item.text.isNotBlank() && textError == null &&
      item.text.length <= planType.maxTextLength) { Text("确定") } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
private fun JsonEditor(task: SurveyPackage, onDismiss: () -> Unit, onApply: (SurveyPackage) -> Unit) {
  var json by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }
  var checking by remember { mutableStateOf(true) }
  val scope = rememberCoroutineScope()
  LaunchedEffect(Unit) {
    try { json = withContext(Dispatchers.Default) { SurveyPackageCodec.encodeDraft(task) } }
    catch (exception: Exception) {
      if (exception is kotlinx.coroutines.CancellationException) throw exception
      error = (exception.message ?: "本地 JSON 无法生成").take(240)
    } finally { checking = false }
  }
  AppDialog(onDismissRequest = { if (!checking) onDismiss() }, symbol = ActionSymbol.EDIT, title = { Text("本地 JSON 编辑") }, text = {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("公用规则改 defaults，单题例外改 extInfo。", style = MaterialTheme.typography.bodySmall)
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
  AppDialog(onDismissRequest = onDismiss, title = { Text("预览 ${position + 1}/${task.items.size}") }, text = {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
      Text(SurveyItemType.entries.first { it.value == item.type }.label, style = MaterialTheme.typography.labelLarge)
      Text(item.text, style = MaterialTheme.typography.headlineLarge)
      Text(item.instruction)
      item.extInfo?.note?.takeIf { it.isNotBlank() }?.let { Text(it) }
      Text("录制 ${item.repetitions} 次 · ${if (item.allowSkip) "允许跳过" else "需要录制"}")
      Row {
        TextButton(onClick = { position-- }, enabled = position > 0) { Text("上一题") }
        TextButton(onClick = { position++ }, enabled = position < task.items.lastIndex) { Text("下一题") }
      }
    }
  }, confirmButton = { Button(onClick = onDismiss) { Text("关闭预览") } })
}

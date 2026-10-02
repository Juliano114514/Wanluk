package com.wanluk.ui.studio

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wanluk.libcomposeui.ActionSymbol
import com.wanluk.libcomposeui.AppButton
import com.wanluk.libcomposeui.AppDialog
import com.wanluk.libcomposeui.AppTextButton

@Stable
internal class SelectionBackState {
  var exitAction: (() -> Unit)? by mutableStateOf(null)
}

internal val LocalSelectionBack = staticCompositionLocalOf<SelectionBackState?> { null }

@Composable
internal fun SelectionBackEffect(active: Boolean, enabled: Boolean, onExit: () -> Unit) {
  val registry = LocalSelectionBack.current
  val action by rememberUpdatedState(onExit)
  DisposableEffect(active, enabled, registry) {
    val exit: () -> Unit = { action() }
    if (active && enabled) registry?.exitAction = exit
    onDispose { if (registry?.exitAction === exit) registry?.exitAction = null }
  }
}

@Stable
internal class MultiSelection(private val mode: MutableState<Boolean>, private val values: MutableState<List<String>>) {
  val active: Boolean get() = mode.value
  val ids: List<String> get() = values.value
  val selected: Set<String> by derivedStateOf { values.value.toSet() }
  fun enter() { mode.value = true }
  fun exit() { mode.value = false; clear() }
  fun clear() { values.value = emptyList() }
  fun all(ids: List<String>) { values.value = ids.distinct() }
  fun toggle(id: String) { values.value = if (id in selected) ids - id else ids + id }
  fun retain(ids: List<String>) { val available = ids.toSet(); values.value = values.value.filter { it in available } }
}

@Composable
internal fun rememberMultiSelection(keys: List<String>, enabled: Boolean, pageActive: Boolean = true, pruneMissing: Boolean = true): MultiSelection {
  val mode = rememberSaveable { mutableStateOf(false) }
  val values = rememberSaveable { mutableStateOf<List<String>>(arrayListOf()) }
  val selection = remember { MultiSelection(mode, values) }
  LaunchedEffect(keys, pruneMissing) { if (pruneMissing) selection.retain(keys) }
  LaunchedEffect(pageActive) { if (!pageActive) selection.exit() }
  BackHandler(selection.active && pageActive && enabled) { selection.exit() }
  SelectionBackEffect(selection.active && pageActive, enabled, selection::exit)
  return selection
}

@Composable
internal fun SelectionToolbar(selection: MultiSelection, enabled: Boolean, onAll: () -> Unit) {
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text("已选 ${selection.ids.size}", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
    AppTextButton(onClick = onAll, enabled = enabled) { Text("全选") }
    AppTextButton(onClick = selection::clear, enabled = enabled && selection.ids.isNotEmpty()) { Text("清空") }
    AppTextButton(onClick = selection::exit, enabled = enabled) { Text("退出") }
  }
}

@Composable
internal fun SelectionActions(content: @Composable RowScope.() -> Unit) {
  HorizontalDivider()
  Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = content)
}

@Composable
internal fun DeleteSelectionDialog(title: String, description: String, enabled: Boolean,
  onDismiss: () -> Unit, onDelete: () -> Unit) {
  AppDialog(onDismissRequest = { if (enabled) onDismiss() }, symbol = ActionSymbol.TRASH,
    title = { Text(title) }, text = { Text(description) },
    confirmButton = { AppButton(onClick = onDelete, enabled = enabled,
      colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("确认删除") } },
    dismissButton = { AppTextButton(onClick = onDismiss, enabled = enabled) { Text("取消") } })
}

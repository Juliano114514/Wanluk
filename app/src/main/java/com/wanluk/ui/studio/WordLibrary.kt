package com.wanluk.ui.studio

import com.wanluk.libcomposeui.AppButton as Button
import com.wanluk.libcomposeui.AppTextButton as TextButton

import com.wanluk.libcomposeui.ActionSymbol

import com.wanluk.libcomposeui.AppDialog
import com.wanluk.libcomposeui.ActionIcon
import com.wanluk.libcomposeui.EmptyState

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wanluk.foundation.survey.SurveyItemType
import com.wanluk.libroom.entity.WordCaseEntity
import com.wanluk.libroom.repository.WordFilter
import com.wanluk.ui.demo.temp.wordcasedetail.WordCaseDetailContent
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun WordLibrary(picking: Boolean, enabled: Boolean,
  onAdd: (List<Long>, SurveyItemType) -> Unit,
  creating: Boolean = false, maxSelection: Int = 500,
  onCreate: (List<Long>, SurveyItemType, String) -> Unit,
  active: Boolean = true,
  viewModel: WordLibraryViewModel = koinViewModel(key = if (picking) "word-picker" else "word-browser")) {
  var query by rememberSaveable { mutableStateOf("") }
  var she by rememberSaveable { mutableStateOf("") }
  var rarity by rememberSaveable { mutableIntStateOf(-1) }
  var selection by rememberSaveable { mutableStateOf(emptyList<Long>()) }
  var type by rememberSaveable { mutableStateOf(SurveyItemType.CHARACTER) }
  var selectedOnly by rememberSaveable { mutableStateOf(false) }
  var filters by rememberSaveable { mutableStateOf(false) }
  var naming by rememberSaveable { mutableStateOf(false) }
  var name by rememberSaveable { mutableStateOf("") }
  var details by remember { mutableStateOf<WordCaseEntity?>(null) }
  val state by viewModel.state.collectAsStateWithLifecycle()
  val gridState = rememberLazyGridState()
  val selectedIds = remember(selection) { selection.toSet() }
  val filter = remember(query, she, rarity, selectedOnly, selection) {
    WordFilter(query.trim(), she, rarity, selectedOnly, if (selectedOnly) selection else emptyList())
  }
  LaunchedEffect(filter, active) {
    if (active && state.filter != filter) {
      viewModel.search(filter)
      gridState.scrollToItem(0)
    }
  }
  LaunchedEffect(gridState, active, state.words.size, state.loading, state.error, state.total) {
    if (!active) return@LaunchedEffect
    snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
      .distinctUntilChanged().collect { last ->
        if (last >= state.words.size - 16) viewModel.loadMore()
      }
  }
  Column(Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp)) {
    OutlinedTextField(query, { query = it.take(100) }, Modifier.fillMaxWidth().padding(top = 12.dp),
      placeholder = { Text("搜索字目、组词") }, singleLine = true, shape = MaterialTheme.shapes.small,
      leadingIcon = { ActionIcon(ActionSymbol.SEARCH) },
      trailingIcon = if (query.isNotEmpty()) ({
        IconButton(onClick = { query = "" }) { ActionIcon(ActionSymbol.CLOSE, contentDescription = "清除搜索") }
      }) else null)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      if (picking) FilterChip(selectedOnly, { selectedOnly = !selectedOnly },
        label = { Text("已选 ${selection.size}") })
      else Text(if (state.filter == null || state.loading && state.words.isEmpty()) "载入中…" else "${state.total} 字目",
        style = MaterialTheme.typography.labelLarge)
      Spacer(Modifier.weight(1f))
      TextButton(onClick = { filters = true }, enabled = enabled) {
        ActionIcon(ActionSymbol.FILTER, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(if (she.isNotEmpty() || rarity != -1 || type != SurveyItemType.CHARACTER) "筛选 · 已设置" else "筛选")
      }
    }
    if (state.filter != null && state.words.isEmpty() && !state.loading && state.error == null) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
      EmptyState(ActionSymbol.SEARCH, if (selectedOnly && selection.isEmpty()) "尚未选择字目" else "没有匹配的字目",
        if (selectedOnly) "取消「已选」筛选以继续选择" else "更换关键词或调整筛选")
    } else LazyVerticalGrid(GridCells.Adaptive(100.dp), Modifier.weight(1f), state = gridState,
      contentPadding = PaddingValues(bottom = 16.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      items(state.words, key = { it.id }, contentType = { "word" }) { word ->
        val checked = word.id in selectedIds
        Surface(shape = RectangleShape,
          color = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
          contentColor = if (checked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
          border = BorderStroke(if (checked) 2.dp else 1.dp,
            if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
          modifier = Modifier.semantics { selected = checked }.combinedClickable(enabled = enabled,
            onClickLabel = if (picking) "选择字目" else "字目详情", onLongClickLabel = "字目详情",
            onLongClick = { details = word }, onClick = {
              if (!picking) details = word
              else selection = if (checked) selection - word.id else
                if (selection.size < maxSelection) selection + word.id else selection
            })) {
          Box {
          Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(word.coreChar, fontSize = 34.sp, lineHeight = 42.sp)
            Text(word.phrases.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis,
              style = MaterialTheme.typography.bodySmall)
            Text("${word.sheng} · ${word.yun} · ${word.diao}", maxLines = 1, overflow = TextOverflow.Ellipsis,
              style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          if (picking && checked) ActionIcon(ActionSymbol.FINISH,
            Modifier.align(Alignment.TopEnd).padding(6.dp).size(16.dp))
          }
        }
      }
      if (state.filter == null || state.loading) item(key = "loading", span = { GridItemSpan(maxLineSpan) }) {
        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
      }
      state.error?.let { error -> item(key = "error", span = { GridItemSpan(maxLineSpan) }) {
        TextButton(onClick = viewModel::retry, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) { Text("$error · 重试") }
      } }
    }
    if (picking) {
      if (selection.size >= maxSelection) Text("最多可选 $maxSelection 个字目",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Button(onClick = {
        if (creating) naming = true else onAdd(selection, type)
      }, enabled = enabled && selection.isNotEmpty(), modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).heightIn(min = 48.dp)) {
        Text(if (creating) "下一步 · ${selection.size}" else "添加 · ${selection.size}")
      }
    }
  }
  if (naming) AppDialog(onDismissRequest = { if (enabled) naming = false },
    symbol = ActionSymbol.NOTE, title = { Text("调查包名称") }, text = {
      OutlinedTextField(name, { name = it.take(120) }, modifier = Modifier.fillMaxWidth(), enabled = enabled, singleLine = true,
        label = { Text("名称") }, placeholder = { Text("例如：日常用字") })
    }, confirmButton = { Button(onClick = { onCreate(selection, type, name) },
      enabled = enabled && name.isNotBlank()) { Text("保存") } },
    dismissButton = { TextButton(onClick = { naming = false }, enabled = enabled) { Text("继续选字") } })
  if (filters) AppDialog(onDismissRequest = { filters = false }, symbol = ActionSymbol.FILTER, title = { Text("筛选") }, text = {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      if (picking) {
        Text("录制内容", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf(SurveyItemType.CHARACTER, SurveyItemType.WORD).forEach { value ->
            FilterChip(type == value, { type = value }, label = { Text(value.label) })
          }
        }
      }
      Text("摄", style = MaterialTheme.typography.labelLarge)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (listOf("") + state.groups).forEach { group ->
          FilterChip(she == group, { she = group }, label = { Text(group.ifEmpty { "全部" }) })
        }
      }
      Text("罕度", style = MaterialTheme.typography.labelLarge)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (-1..3).forEach { value -> FilterChip(rarity == value, { rarity = value },
          label = { Text(if (value == -1) "全部" else "$value") }) }
      }
    }
  }, confirmButton = { Button(onClick = { filters = false }) { Text("完成") } },
    dismissButton = { TextButton(onClick = { she = ""; rarity = -1; type = SurveyItemType.CHARACTER }) { Text("重置") } })
  details?.let { word -> AppDialog(onDismissRequest = { details = null }, symbol = ActionSymbol.LIBRARY, title = { Text("字目详情") },
    text = { WordCaseDetailContent(word) }, confirmButton = { Button(onClick = { details = null }) { Text("完成") } }) }
}

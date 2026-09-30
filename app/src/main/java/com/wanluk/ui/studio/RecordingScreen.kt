package com.wanluk.ui.studio

import com.wanluk.libcomposeui.ActionSymbol

import com.wanluk.libcomposeui.ActionIcon

import com.wanluk.libcomposeui.AppDialog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import com.wanluk.foundation.survey.SurveyPackage
import com.wanluk.foundation.survey.SurveyItemType
import com.wanluk.librecord.RecordingMeter
import com.wanluk.librecord.WavResult
import com.wanluk.librecordui.RecordingControl
import com.wanluk.libroom.entity.RecordingTakeEntity
import com.wanluk.libroom.entity.SurveySessionDetail
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RecordingScreen(
  detail: SurveySessionDetail, task: SurveyPackage, enabled: Boolean, capture: CaptureState?, meter: StateFlow<RecordingMeter>,
  trial: WavResult?, playing: Boolean,
  onRecord: (held: Boolean) -> Unit, onTrial: () -> Unit, onStop: () -> Unit, onPlay: (String?) -> Unit,
  onPlayTrial: () -> Unit, onStopPlayback: () -> Unit,
  onPosition: (Int) -> Unit, onSkip: () -> Unit, onNote: (String) -> Unit,
  onSkipReason: (SkippedItem, String) -> Unit,
  noteDraft: StepNoteDraft?, onNoteChange: (String, Int, String) -> Unit,
  onAdopt: (String) -> Unit, onExport: () -> Unit, onFinish: () -> Unit,
  holdToRecord: Boolean,
  busy: String? = null, toastHost: @Composable () -> Unit = {},
) {
  val session = detail.session
  val itemsById = remember(task) { task.items.associateBy { it.itemId } }
  val steps = remember(detail.steps) { detail.steps.sortedBy { it.position } }
  val step = steps.firstOrNull { it.position == session.currentPosition } ?: return
  val isLast = step.position == steps.lastIndex
  val item = itemsById.getValue(step.itemId)
  val takes = remember(detail.takes, step.position) {
    detail.takes.filter { it.position == step.position }.sortedByDescending { it.createdAt }
  }
  val selected = takes.firstOrNull { it.id == step.selectedTakeId }
  val recorded = remember(steps) { steps.count { it.selectedTakeId != null } }
  val skipped = remember(steps) { steps.count { it.skipReason != null } }
  var showProgress by remember { mutableStateOf(false) }
  var skipReasonItem by remember { mutableStateOf<SkippedItem?>(null) }
  var showExport by remember { mutableStateOf(false) }
  var showHistory by remember { mutableStateOf(false) }
  val note = noteDraft?.takeIf { it.sessionId == session.id && it.position == step.position }?.text ?: step.note

  var showMore by remember { mutableStateOf(false) }
  var showTrial by remember { mutableStateOf(false) }
  var showNote by remember { mutableStateOf(false) }
  // Older task snapshots already carry these word hints in their instruction.
  // Only split the exact generated format; never look up a different reading by character.
  val hintPrefix = "请用家乡话读出这个字。语义提示："
  val hintSuffix = "。不确定时可以跳过。"
  val hasWordHints = item.type == SurveyItemType.CHARACTER.value &&
    item.instruction.startsWith(hintPrefix) && item.instruction.endsWith(hintSuffix)
  val wordHints = if (hasWordHints) item.instruction.removePrefix(hintPrefix).removeSuffix(hintSuffix).trim() else ""
  val instruction = if (hasWordHints) "请用家乡话读出这个字" else item.instruction

  BoxWithConstraints(Modifier.fillMaxSize()) {
    val pageHeight = maxHeight.coerceAtLeast(if (LocalDensity.current.fontScale > 1.3f) 760.dp else 600.dp)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).height(pageHeight)
      .padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
      Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("${step.position + 1} / ${steps.size}", style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant)
        LinearProgressIndicator(progress = { (recorded + skipped).toFloat() / steps.size.coerceAtLeast(1) },
          modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)),
          trackColor = MaterialTheme.colorScheme.surfaceContainerHighest)
        Box {
          MoreButton(enabled, onClick = { showMore = true })
          DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
            DropdownMenuItem(leadingIcon = { ActionIcon(ActionSymbol.HEADPHONES) }, text = { Text("试音与回听") }, onClick = { showMore = false; showTrial = true })
            DropdownMenuItem(leadingIcon = { ActionIcon(ActionSymbol.RECORDINGS) }, text = { Text("全部进度") }, onClick = { showMore = false; showProgress = true })
            DropdownMenuItem(leadingIcon = { ActionIcon(ActionSymbol.NOTE) }, text = { Text("本条备注") }, onClick = { showMore = false; showNote = true })
            DropdownMenuItem(leadingIcon = { ActionIcon(ActionSymbol.HISTORY) }, text = { Text("录音历史（${takes.size}）") }, enabled = takes.isNotEmpty(),
              onClick = { showMore = false; showHistory = true })
            DropdownMenuItem(leadingIcon = { ActionIcon(ActionSymbol.EXPORT) }, text = { Text("导出成果") }, onClick = { showMore = false; showExport = true })
          }
        }
      }
      OutlinedCard(Modifier.fillMaxWidth().weight(1f), shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Box(Modifier.fillMaxSize()) {
          Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.small) {
              Text(SurveyItemType.entries.first { it.value == item.type }.label +
                if (item.repetitions > 1) " · 第 ${step.repetition}/${item.repetitions} 次" else "",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(28.dp))
            Text(item.text, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
              fontSize = when { item.text.length == 1 -> 128.sp; item.text.length <= 4 -> 72.sp
                item.text.length <= 8 -> 48.sp; else -> 30.sp },
              fontWeight = FontWeight.Medium, lineHeight = when {
                item.text.length == 1 -> 150.sp; item.text.length <= 4 -> 88.sp
                item.text.length <= 8 -> 64.sp; else -> 44.sp })
            if (wordHints.isNotBlank()) {
              Spacer(Modifier.height(28.dp))
              Text(wordHints, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            }
            if (instruction.isNotBlank()) {
              Spacer(Modifier.height(24.dp))
              Text(instruction, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected?.warning?.isNotBlank() == true) {
              Spacer(Modifier.height(12.dp))
              Text(selected.warning, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
          }
          toastHost()
        }
      }
      RecordingFeedback(meter, capture, busy, playing, selected, step.skipReason != null, holdToRecord)
      Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically) {
        RecordingAction("上一个", ActionSymbol.PREVIOUS, enabled && step.position > 0) { onPosition(step.position - 1) }
        RecordingAction(if (playing) "停止播放" else "播放", if (playing) ActionSymbol.STOP else ActionSymbol.PLAY,
          enabled && (selected != null || playing)) { if (playing) onStopPlayback() else onPlay(null) }
        RecordingControl(label = when {
          capture?.stopping == true -> "保存中"
          capture != null -> if (holdToRecord && !capture.trial) "松手停止" else "停止"
          holdToRecord -> "长按录音"
          else -> "录制"
        }, canStart = enabled && selected == null, recording = capture != null, stopping = capture?.stopping == true,
          holdToRecord = holdToRecord && capture?.trial != true, onStart = onRecord, onStop = onStop,
          modifier = Modifier.weight(1.3f), primary = true) {
          ActionIcon(if (capture != null) ActionSymbol.STOP else ActionSymbol.MIC)
        }
        RecordingControl(if (holdToRecord) "长按重录" else "重新录制", canStart = enabled && selected != null,
          recording = false, stopping = false, holdToRecord = holdToRecord,
          onStart = onRecord, onStop = onStop, modifier = Modifier.weight(1f)) { ActionIcon(ActionSymbol.RERECORD) }
        RecordingAction(if (isLast) "结束" else "下一个", if (isLast) ActionSymbol.FINISH else ActionSymbol.NEXT,
          enabled && (selected != null || item.allowSkip), emphasized = isLast || selected != null) {
          when {
            isLast -> onFinish()
            selected != null -> onPosition(step.position + 1)
            else -> onSkip()
          }
        }
      }
      TextButton(onClick = onSkip, enabled = enabled && item.allowSkip,
        modifier = Modifier.padding(top = 4.dp)) { Text("我不会念", color = if (enabled && item.allowSkip)
          MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)) }
      Text(when {
        !item.allowSkip -> "此题需要录制，不确定时可在更多中备注"
        isLast -> "最后一张了 · 结束后可在我的录制中导出"
        else -> ""
      }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(bottom = 8.dp))
    }
  }

  if (showTrial) AppDialog(onDismissRequest = { showTrial = false; onStopPlayback() },
    symbol = ActionSymbol.HEADPHONES, title = { Text("先试一试声音") }, text = {
      Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("找个安静的位置，让手机与嘴保持稳定距离。试录一小段，再听听是否清楚。")
        Text("试音最长 15 秒，不计入正式成果。切到后台会结束录音并尝试保存。",
          style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        trial?.let { result ->
          Text("已试录 ${seconds(result.durationMs)} 秒")
          if (result.warning.isNotBlank()) Text(result.warning, color = MaterialTheme.colorScheme.error)
        }
        Row {
          TextButton(onClick = onPlayTrial, enabled = enabled && trial != null) { Text("播放试音") }
          if (playing) TextButton(onClick = onStopPlayback) { Text("停止播放") }
        }
      }
    }, confirmButton = { Button(onClick = { showTrial = false; onTrial() }, enabled = enabled) { Text("开始试音") } },
    dismissButton = { TextButton(onClick = { showTrial = false; onStopPlayback() }) { Text("关闭") } })
  if (showNote) AppDialog(onDismissRequest = { showNote = false }, symbol = ActionSymbol.NOTE, title = { Text("本条备注") }, text = {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      OutlinedTextField(note, { onNoteChange(session.id, step.position, it) }, Modifier.fillMaxWidth(), enabled = enabled,
        label = { Text("想补充的话") }, minLines = 2, maxLines = 5)
      step.skipReason?.let { reason ->
        Text("跳过原因：${reason.ifBlank { "未填写" }}", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = {
          showNote = false
          skipReasonItem = SkippedItem(session.id, step.position, item.text, reason)
        }, enabled = enabled) { Text(if (reason.isBlank()) "填写原因" else "修改原因") }
      }
      selected?.let { Text("WAV · ${it.sampleRate} Hz · ${it.bitsPerSample}-bit · ${it.audioSource}",
        style = MaterialTheme.typography.bodySmall) }
      Text("切换题目时也会自动保存备注。", style = MaterialTheme.typography.bodySmall)
    }
  }, confirmButton = { Button(onClick = { onNote(note); showNote = false }, enabled = enabled) { Text("保存") } },
    dismissButton = { TextButton(onClick = { showNote = false }) { Text("收起") } })
  skipReasonItem?.let { skipped -> SkipReasonDialog(skipped, enabled, onDismiss = { skipReasonItem = null }) { reason ->
    onSkipReason(skipped, reason)
    skipReasonItem = null
  } }
  if (showProgress) AppDialog(onDismissRequest = { showProgress = false }, symbol = ActionSymbol.RECORDINGS, title = { Text("录制进度") }, text = {
    LazyColumn(Modifier.heightIn(max = 420.dp)) {
      items(steps, key = { it.position }) { entry ->
        val text = itemsById.getValue(entry.itemId).text
        TextButton(onClick = { onPosition(entry.position); showProgress = false }, modifier = Modifier.fillMaxWidth()) {
          Text("${entry.position + 1}. $text · 第${entry.repetition}次 · ${when {
            entry.selectedTakeId != null -> "已录"; entry.skipReason != null -> "已跳过"; else -> "待录"
          }}", modifier = Modifier.fillMaxWidth())
        }
      }
    }
  }, confirmButton = { Button(onClick = { showProgress = false }) { Text("关闭") } })
  if (showHistory) AppDialog(onDismissRequest = { showHistory = false }, symbol = ActionSymbol.HISTORY, title = { Text("本条录音历史") }, text = {
    LazyColumn(Modifier.heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      items(takes, key = { it.id }) { take ->
        Column {
          Text("${timeLabel(take.createdAt)} · ${if (take.state == RecordingTakeEntity.SAVED) "${seconds(take.durationMs)} 秒" else "录制中断"}")
          if (take.id == step.selectedTakeId) Text("当前采用", color = MaterialTheme.colorScheme.primary)
          if (take.warning.isNotBlank()) Text(take.warning, style = MaterialTheme.typography.bodySmall)
          if (take.state == RecordingTakeEntity.SAVED) Row {
            TextButton(onClick = { onPlay(take.id) }) { Text("回听") }
            TextButton(onClick = { onAdopt(take.id); showHistory = false }, enabled = take.id != step.selectedTakeId) { Text("采用此版") }
          }
        }
      }
    }
  }, confirmButton = { Button(onClick = { showHistory = false; onStopPlayback() }) { Text("关闭") } })
  if (showExport) AppDialog(onDismissRequest = { showExport = false }, symbol = ActionSymbol.EXPORT, title = { Text("导出录制成果") }, text = {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text("发音人：${session.speakerAlias.ifBlank { "未填写" }}\n方言背景：${session.dialect.ifBlank { "未填写" }}\n已录 $recorded 条，跳过 $skipped 条，待录 ${steps.size - recorded - skipped} 条。")
      Text("将包含任务快照、全部成功录音版本、录音参数、已保存备注和跳过原因。试音和中断片段不导出。")
      if (note != step.note) Text("当前备注尚未保存，请先取消并保存备注。", color = MaterialTheme.colorScheme.error)
      Text("文件仅写入你选择的位置，不会自动上传或发送。本地原始录音仍保留。")
    }
  }, confirmButton = { Button(onClick = { showExport = false; onExport() }, enabled = note == step.note) { Text("选择保存位置") } },
    dismissButton = { TextButton(onClick = { showExport = false }) { Text("取消") } })
}

@Composable
private fun RowScope.RecordingAction(
  label: String, symbol: ActionSymbol, enabled: Boolean, emphasized: Boolean = false, onClick: () -> Unit,
) {
  Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
    val container = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val foreground = if (emphasized) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Surface(onClick = onClick, enabled = enabled, shape = MaterialTheme.shapes.medium,
      modifier = Modifier.size(48.dp).semantics { contentDescription = label },
      color = if (enabled) container else MaterialTheme.colorScheme.surfaceVariant,
      contentColor = if (enabled) foreground else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
      border = if (emphasized) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
      Box(contentAlignment = Alignment.Center) { ActionIcon(symbol) }
    }
    Text(label, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
      color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
  }
}

/** Only this small region observes the high-frequency microphone meter. */
@Composable
private fun RecordingFeedback(meterFlow: StateFlow<RecordingMeter>, capture: CaptureState?, busy: String?,
  playing: Boolean, selected: RecordingTakeEntity?, skipped: Boolean, holdToRecord: Boolean) {
  val meter by meterFlow.collectAsStateWithLifecycle()
  val status = when {
    capture?.stopping == true -> "正在保存…"
    capture != null -> "${if (capture.trial) "试音中" else "录制中"} · ${seconds(meter.durationMs)} 秒"
    busy != null -> busy
    playing -> "正在播放"
    selected != null -> "已保存 · ${seconds(selected.durationMs)} 秒"
    skipped -> "已跳过"
    else -> if (holdToRecord) "长按录音，松手停止" else "点击录音开始"
  }
  val symbol = when {
    capture != null -> ActionSymbol.MIC
    playing -> ActionSymbol.PLAY
    selected != null -> ActionSymbol.FINISH
    skipped -> ActionSymbol.NEXT
    else -> ActionSymbol.MIC
  }
  Column(Modifier.padding(top = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Surface(shape = MaterialTheme.shapes.small,
      color = if (capture != null) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
      contentColor = if (capture != null) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer) {
      Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ActionIcon(symbol, Modifier.size(16.dp))
        Text(status, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }
    Spacer(Modifier.height(6.dp))
    if (capture != null) LinearProgressIndicator(progress = { meter.peak.coerceIn(0f, 1f) },
      modifier = Modifier.width(112.dp).height(4.dp).clip(MaterialTheme.shapes.small),
      color = MaterialTheme.colorScheme.error, trackColor = MaterialTheme.colorScheme.errorContainer)
    else Spacer(Modifier.height(4.dp))
  }
}

@Composable
internal fun SkipReasonDialog(item: SkippedItem, enabled: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit) {
  var reason by rememberSaveable(item.sessionId, item.position) { mutableStateOf(item.reason) }
  AppDialog(onDismissRequest = onDismiss, symbol = ActionSymbol.NOTE, title = { Text("跳过原因 · ${item.text}") }, text = {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      listOf("不认识这个字／词", "家乡没有这个说法", "不确定怎么说", "暂时不方便录制").forEach { option ->
        FilterChip(selected = reason == option, onClick = { reason = option }, enabled = enabled, label = { Text(option) })
      }
      OutlinedTextField(reason, { reason = it.take(300) }, enabled = enabled, label = { Text("原因，也可自行填写") })
      Text("若本条已经录过，旧录音仍保留在历史中。", style = MaterialTheme.typography.bodySmall)
    }
  }, confirmButton = { Button(onClick = { onSave(reason) }, enabled = enabled && reason.isNotBlank()) { Text("保存原因") } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("暂不填写") } })
}

private fun seconds(ms: Long): String = String.format(Locale.ROOT, "%.1f", ms / 1000.0)
private fun timeLabel(ms: Long): String = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(ms))

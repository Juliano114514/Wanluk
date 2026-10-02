package com.wanluk.ui.studio

import com.wanluk.libcomposeui.AppButton as Button
import com.wanluk.libcomposeui.AppTextButton as TextButton

import com.wanluk.libcomposeui.ActionSymbol

import com.wanluk.libcomposeui.ActionIcon

import com.wanluk.libcomposeui.AppDialog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import com.wanluk.foundation.survey.*
import com.wanluk.foundation.survey.SurveyItemType
import com.wanluk.librecord.RecordingMeter
import com.wanluk.librecord.WavResult
import com.wanluk.librecordui.RecordingControl
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecordingScreen(
  detail: CurrentRecording, enabled: Boolean, capture: CaptureState?, meter: StateFlow<RecordingMeter>,
  trial: WavResult?, playing: Boolean,
  onRecord: (held: Boolean) -> Unit, onTrial: () -> Unit, onStop: () -> Unit, onPlay: (String?) -> Unit,
  onPlayTrial: () -> Unit, onStopPlayback: () -> Unit,
  onPosition: (Int) -> Unit, onSkip: () -> Unit, onNote: (String) -> Unit,
  onSkipReason: (SkippedItem, String) -> Unit,
  noteDraft: StepNoteDraft?, onNoteChange: (String, Int, String) -> Unit,
  onAdopt: (String) -> Unit, onReview: (String, ReviewStatus) -> Unit, onExport: () -> Unit, onFinish: () -> Unit,
  holdToRecord: Boolean,
  busy: String? = null, toastHost: @Composable () -> Unit = {},
  onExportSteps: (List<Int>) -> Unit, onExportTakes: (List<String>) -> Unit,
  onClearSteps: (List<Int>) -> Unit, onDeleteTakes: (List<String>) -> Unit,
) {
  val session = detail.session
  val step = detail.step
  val item = detail.item
  val passage = detail.planType == RecordingPlanType.PASSAGE
  val isLast = step.position == session.totalSteps - 1
  val takes = detail.takes
  val selected = takes.firstOrNull { it.id == step.selectedTakeId }
  val recorded = session.recordedSteps
  val skipped = session.skippedSteps
  var showProgress by rememberSaveable { mutableStateOf(false) }
  var skipReasonItem by remember { mutableStateOf<SkippedItem?>(null) }
  var showExport by remember { mutableStateOf(false) }
  var showHistory by rememberSaveable { mutableStateOf(false) }
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
  val instruction = if (hasWordHints) "用方言读出这个字" else item.instruction

  BoxWithConstraints(Modifier.fillMaxSize()) {
    val controlColumns = if (maxWidth < 360.dp || LocalDensity.current.fontScale > 1.3f) 3 else 5
    val pageHeight = maxHeight.coerceAtLeast(if (LocalDensity.current.fontScale > 1.3f) 760.dp else 600.dp)
    Column(Modifier.fillMaxSize().then(if (passage) Modifier else Modifier.verticalScroll(rememberScrollState()))
      .height(if (passage) maxHeight else pageHeight)
      .padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
      Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("${step.position + 1} / ${session.totalSteps}", style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant)
        LinearProgressIndicator(progress = { (recorded + skipped).toFloat() / session.totalSteps.coerceAtLeast(1) },
          modifier = Modifier.weight(1f).height(3.dp),
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
      Surface(Modifier.fillMaxWidth().weight(1f), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
          Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = if (passage) Arrangement.Top else Arrangement.Center) {
            Text((if (passage) "文段 · 每条最多10分钟" else SurveyItemType.entries.first { it.value == item.type }.label) +
                if (item.repetitions > 1) " · 第 ${step.repetition}/${item.repetitions} 次" else "",
                modifier = Modifier.padding(vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(if (passage) 12.dp else 28.dp))
            Text(item.text, modifier = Modifier.fillMaxWidth(), textAlign = if (passage) TextAlign.Start else TextAlign.Center,
              fontSize = when { passage -> 24.sp; item.text.length == 1 -> 128.sp; item.text.length <= 4 -> 72.sp
                item.text.length <= 8 -> 48.sp; else -> 30.sp },
              fontWeight = if (passage) FontWeight.Normal else FontWeight.Medium, lineHeight = when {
                passage -> 36.sp; item.text.length == 1 -> 150.sp; item.text.length <= 4 -> 88.sp
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
            item.extInfo?.note?.takeIf { it.isNotBlank() }?.let {
              Spacer(Modifier.height(12.dp))
              Text(it, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
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
      RecordingFeedback(meter, capture, busy, playing, selected, step.skipReason != null, holdToRecord, detail.planType.maxSeconds)
      FlowRow(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly,
        verticalArrangement = Arrangement.spacedBy(12.dp), maxItemsInEachRow = controlColumns) {
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
          modifier = Modifier.weight(1.3f).align(Alignment.CenterVertically), primary = true) {
          ActionIcon(if (capture != null) ActionSymbol.STOP else ActionSymbol.MIC)
        }
        RecordingControl(if (holdToRecord) "长按重录" else "重录", canStart = enabled && selected != null,
          recording = false, stopping = false, holdToRecord = holdToRecord,
          onStart = onRecord, onStop = onStop, modifier = Modifier.weight(1f).align(Alignment.CenterVertically)) { ActionIcon(ActionSymbol.RERECORD) }
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
        modifier = Modifier.padding(top = 4.dp)) { Text("不会读，跳过", color = if (enabled && item.allowSkip)
          MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)) }
      if (!item.allowSkip) Text("此项必须录制，可在更多菜单中备注",
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(bottom = 8.dp))
    }
  }

  if (showTrial) AppDialog(onDismissRequest = { showTrial = false; onStopPlayback() },
    symbol = ActionSymbol.HEADPHONES, title = { Text("试音") }, text = {
      Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("最长 15 秒，不计入正式录音。")
        Text("切到后台会结束试音。",
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
        label = { Text("备注") }, minLines = 2, maxLines = 5)
      step.skipReason?.let { reason ->
        Text("跳过原因：${reason.ifBlank { "未填写" }}", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = {
          showNote = false
          skipReasonItem = SkippedItem(session.id, step.position, item.text, reason)
        }, enabled = enabled) { Text(if (reason.isBlank()) "填写原因" else "修改原因") }
      }
      selected?.let { Text("WAV · ${it.sampleRate} Hz · ${it.bitsPerSample}-bit · ${it.audioSource}",
        style = MaterialTheme.typography.bodySmall) }
      Text("切换题目时自动保存", style = MaterialTheme.typography.bodySmall)
    }
  }, confirmButton = { Button(onClick = { onNote(note); showNote = false }, enabled = enabled) { Text("保存") } },
    dismissButton = { TextButton(onClick = { showNote = false }) { Text("收起") } })
  skipReasonItem?.let { skipped -> SkipReasonDialog(skipped, enabled, onDismiss = { skipReasonItem = null }) { reason ->
    onSkipReason(skipped, reason)
    skipReasonItem = null
  } }
  if (showProgress) RecordingProgressDialog(session, enabled,
    onDismiss = { showProgress = false }, onPosition = onPosition, onExport = onExportSteps, onClear = onClearSteps)
  if (showHistory) RecordingHistoryDialog(takes, step.selectedTakeId, enabled,
    onDismiss = { showHistory = false; onStopPlayback() }, onPlay = { onPlay(it) }, onAdopt = onAdopt,
    onExport = onExportTakes, onDelete = onDeleteTakes, onReview = onReview)
  if (showExport) AppDialog(onDismissRequest = { showExport = false }, symbol = ActionSymbol.EXPORT, title = { Text("导出录制成果") }, text = {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text("发音人：${session.speakerAlias.ifBlank { "未填写" }}\n方言背景：${session.dialect.ifBlank { "未填写" }}\n已录 $recorded 条，跳过 $skipped 条，待录 ${session.totalSteps - recorded - skipped} 条。")
      Text("包含题目、录音历史、参数、备注与跳过原因，不含试音及中断片段。")
      if (note != step.note) Text("当前备注尚未保存，请先取消并保存备注。", color = MaterialTheme.colorScheme.error)
      Text("导出为 ZIP，本机录音保留。")
    }
  }, confirmButton = { Button(onClick = { showExport = false; onExport() }, enabled = note == step.note) { Text("选择保存位置") } },
    dismissButton = { TextButton(onClick = { showExport = false }) { Text("取消") } })
}

@Composable
private fun RowScope.RecordingAction(
  label: String, symbol: ActionSymbol, enabled: Boolean, emphasized: Boolean = false, onClick: () -> Unit,
) {
  Column(Modifier.weight(1f).align(Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
    val container = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val foreground = if (emphasized) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Surface(onClick = onClick, enabled = enabled, shape = MaterialTheme.shapes.small,
      modifier = Modifier.size(48.dp).semantics { contentDescription = label },
      color = if (enabled && emphasized) container else MaterialTheme.colorScheme.background,
      contentColor = if (enabled) foreground else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)) {
      Box(contentAlignment = Alignment.Center) { ActionIcon(symbol) }
    }
    Text(label, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
      color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
  }
}

/** Only this small region observes the high-frequency microphone meter. */
@Composable
private fun RecordingFeedback(meterFlow: StateFlow<RecordingMeter>, capture: CaptureState?, busy: String?,
  playing: Boolean, selected: RecordingTake?, skipped: Boolean, holdToRecord: Boolean, maxSeconds: Int) {
  val meter by meterFlow.collectAsStateWithLifecycle()
  val status = when {
    capture?.stopping == true -> "正在保存…"
    capture != null -> "${if (capture.trial) "试音中" else "录制中"} · ${seconds(meter.durationMs)}秒 / ${if (capture.trial) "15秒" else "${maxSeconds / 60}分钟"}"
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
    Surface(color = MaterialTheme.colorScheme.background,
      contentColor = if (capture != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) {
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
    Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      listOf("不认识这个字／词", "家乡没有这个说法", "不确定怎么说", "暂时不方便录制").forEach { option ->
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(reason == option, enabled = enabled,
          role = Role.RadioButton, onClick = { reason = option }), verticalAlignment = Alignment.CenterVertically) {
          RadioButton(reason == option, onClick = null, enabled = enabled)
          Text(option, Modifier.padding(start = 12.dp))
        }
      }
      OutlinedTextField(reason, { reason = it.take(300) }, modifier = Modifier.fillMaxWidth(), enabled = enabled,
        label = { Text("跳过原因") }, maxLines = 3)
      Text("已有录音保留在历史中", style = MaterialTheme.typography.bodySmall)
    }
  }, confirmButton = { Button(onClick = { onSave(reason) }, enabled = enabled && reason.isNotBlank()) { Text("保存原因") } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("暂不填写") } })
}

private fun seconds(ms: Long): String = String.format(Locale.ROOT, "%.1f", ms / 1000.0)

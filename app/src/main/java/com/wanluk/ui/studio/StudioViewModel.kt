package com.wanluk.ui.studio

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wanluk.foundation.recording.RecordingFiles
import com.wanluk.foundation.survey.*
import com.wanluk.libexport.ResultExporter
import com.wanluk.librecord.RecordingPlayer
import com.wanluk.librecord.WavRecorder
import com.wanluk.librecord.WavResult
import com.wanluk.libroom.entity.*
import com.wanluk.libroom.repository.SurveyRepository
import com.wanluk.libroom.repository.WordCaseRepository
import com.wanluk.libsettings.RecorderSettings
import com.wanluk.libsettings.RecorderSettingsStore
import com.wanluk.libsettings.RecordingMode
import com.wanluk.libsettings.ThemeMode
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class StudioScreen { HOME, EDITOR, LIBRARY, SESSION, SETTINGS }
data class StudioState(
  val screen: StudioScreen = StudioScreen.HOME,
  val ready: Boolean = false,
  val busy: String? = null,
  val message: String? = null,
  val draft: SurveyPackage? = null,
  val originalDraft: SurveyPackage? = null,
  val pickingWords: Boolean = false,
  val creatingPackage: Boolean = false,
) {
  val draftChanged: Boolean get() = draft != originalDraft
}
data class CaptureState(val trial: Boolean, val stopping: Boolean = false)
data class ExportedDocument(val uri: Uri, val mime: String)
data class StepNoteDraft(val sessionId: String, val position: Int, val text: String)
data class SkippedItem(val sessionId: String, val position: Int, val text: String, val reason: String = "")
data class RecordingTask(val sessionId: String, val task: SurveyPackage)

@OptIn(ExperimentalCoroutinesApi::class)
class StudioViewModel(
  private val context: Context,
  private val repository: SurveyRepository,
  private val words: WordCaseRepository,
  private val recorder: WavRecorder,
  private val player: RecordingPlayer,
  private val files: RecordingFiles,
  private val exporter: ResultExporter,
  private val settingsStore: RecorderSettingsStore,
) : ViewModel() {
  val recorderSettings = settingsStore.settings
  private val mutableState = MutableStateFlow(StudioState())
  val state = mutableState.asStateFlow()
  private val selectedSession = MutableStateFlow<String?>(null)
  val selectedSessionId = selectedSession.asStateFlow()
  val recordingTask = selectedSession.flatMapLatest { id ->
    if (id == null) flowOf(null) else flow<RecordingTask?> {
      emit(RecordingTask(id, repository.taskSnapshot(id)))
    }.catch { notify("无法读取录制字目，请返回后重试"); emit(null) }
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
  private val mutableSkippedItem = MutableStateFlow<SkippedItem?>(null)
  val skippedItem = mutableSkippedItem.asStateFlow()
  private val mutableNoteDraft = MutableStateFlow<StepNoteDraft?>(null)
  val noteDraft = mutableNoteDraft.asStateFlow()
  val detail = selectedSession.flatMapLatest { id ->
    if (id == null) flowOf(null) else repository.observeSession(id)
  }.catch { notify("无法读取录制任务，请返回后重试") }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
  val packages = repository.packages().map { rows -> rows.map { SurveyPackageCodec.decode(it.json) } }
    .flowOn(Dispatchers.IO).catch { notify("无法读取调查包，请重试初始化") }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
  val sessions = repository.sessions().catch { notify("无法读取录制进度，请重试初始化") }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
  private val mutableCapture = MutableStateFlow<CaptureState?>(null)
  val capture = mutableCapture.asStateFlow()
  val meter = recorder.meter
  val playing = player.playing
  private val mutableTrial = MutableStateFlow<WavResult?>(null)
  val trial = mutableTrial.asStateFlow()
  private val mutableExport = MutableStateFlow<ExportedDocument?>(null)
  val lastExport = mutableExport.asStateFlow()
  private var stopSignal: AtomicBoolean? = null
  private val trialSessionId = UUID.randomUUID().toString()
  private var pendingTaskExport: SurveyPackage? = null
  private var pendingResultExport: String? = null

  init { initialize() }

  fun initialize() = work("准备本地字库和调查包…") {
    withContext(Dispatchers.IO) {
      words.ensureBuiltinWordCasesImported()
      repository.initialize()
    }
    mutableState.update { it.copy(ready = true) }
  }

  fun notify(message: String) { mutableState.update { it.copy(message = message) } }
  fun clearMessage() { mutableState.update { it.copy(message = null) } }
  fun clearExport() { mutableExport.value = null }
  private fun canNavigate() = mutableCapture.value == null && state.value.busy == null

  fun settings() {
    if (canNavigate()) mutableState.update { it.copy(screen = StudioScreen.SETTINGS) }
  }

  fun saveRecorderSettings(settings: RecorderSettings, onSaved: () -> Unit) = work("保存录制者信息…") {
    settingsStore.save(settings)
    onSaved()
    notify("录制者信息已保存在本机")
  }

  fun saveRecordingMode(mode: RecordingMode) = work("保存录音方式…") {
    settingsStore.saveRecordingMode(mode)
    notify("录音方式已保存")
  }

  fun saveThemeMode(mode: ThemeMode) = work("保存外观设置…") {
    settingsStore.saveThemeMode(mode)
  }

  fun home() = work("保存进度…") {
    returnHome()
  }

  fun finishSession(onFinished: () -> Unit) = work("保存进度…") {
    val current = requireCurrent()
    val step = current.steps.first { it.position == current.session.currentPosition }
    require(step.position == current.steps.maxOf { it.position }) { "还未到最后一条" }
    if (step.selectedTakeId == null && step.skipReason == null) {
      repository.skip(current.session.id, step.position)
    }
    returnHome()
    onFinished()
  }

  private fun returnHome() {
    player.stop()
    selectedSession.value = null
    mutableSkippedItem.value = null
    mutableState.update { it.copy(screen = StudioScreen.HOME, draft = null, originalDraft = null,
      pickingWords = false, creatingPackage = false) }
  }

  fun edit(task: SurveyPackage? = null, copy: Boolean = false) {
    if (!canNavigate()) return
    player.stop()
    val source = task ?: SurveyPackage()
    val draft = if (copy) source.copy(packageId = UUID.randomUUID().toString(), revision = 1,
      title = "${source.title}（副本）") else source
    mutableState.update { it.copy(screen = StudioScreen.EDITOR, draft = draft, originalDraft = draft) }
  }

  fun updateDraft(task: SurveyPackage) {
    if (canNavigate()) mutableState.update { it.copy(draft = task) }
  }

  fun applyJson(json: String) = work("校验任务 JSON…") {
    val draft = withContext(Dispatchers.Default) { SurveyPackageCodec.decode(json) }
    mutableState.update { it.copy(draft = draft, message = "JSON 已载入编辑器；保存后生成本地版本") }
  }

  fun saveDraft() = work("保存调查包…") {
    val draft = requireNotNull(state.value.draft)
    val saved = withContext(Dispatchers.IO) { repository.saveRevision(draft) }
    mutableState.update { it.copy(screen = StudioScreen.HOME, draft = null, originalDraft = null,
      message = "已保存《${saved.title}》v${saved.revision}，已有录制任务保持原版本") }
  }

  fun library(picking: Boolean = false) {
    if (!canNavigate()) return
    mutableState.update { it.copy(screen = StudioScreen.LIBRARY, pickingWords = picking) }
  }

  fun newPackage() {
    if (!canNavigate()) return
    mutableState.update { it.copy(screen = StudioScreen.LIBRARY, draft = SurveyPackage(title = ""), originalDraft = null,
      pickingWords = true, creatingPackage = true) }
  }

  fun createPackage(selectedIds: List<Long>, type: SurveyItemType, title: String) = work("保存调查包…") {
    require(state.value.creatingPackage)
    require(selectedIds.isNotEmpty() && selectedIds.size <= SurveyPackage.MAX_ITEMS) { "请选择 1–500 个字目" }
    require(title.trim().isNotEmpty() && title.length <= 120) { "请填写调查包名称" }
    val selected = words.selected(selectedIds)
    val draft = requireNotNull(state.value.draft).copy(title = title.trim(), items = wordItems(selected, type))
    withContext(Dispatchers.IO) { repository.saveRevision(draft) }
    mutableState.update { it.copy(screen = StudioScreen.HOME, draft = null, originalDraft = null, pickingWords = false,
      creatingPackage = false, message = "已保存") }
  }

  fun leaveLibrary() {
    if (!canNavigate()) return
    mutableState.update { it.copy(screen = if (it.pickingWords && !it.creatingPackage) StudioScreen.EDITOR else StudioScreen.HOME,
      draft = if (it.creatingPackage) null else it.draft, pickingWords = false, creatingPackage = false) }
  }

  fun addWords(selectedIds: List<Long>, type: SurveyItemType) = work("添加字目…") {
    val draft = requireNotNull(state.value.draft)
    require(draft.items.size + selectedIds.size <= SurveyPackage.MAX_ITEMS) { "每个调查包最多 500 题" }
    val selected = words.selected(selectedIds)
    val additions = wordItems(selected, type)
    require(draft.totalSteps + additions.size <= SurveyPackage.MAX_STEPS) { "录制次数已达上限" }
    mutableState.update { it.copy(draft = draft.copy(items = draft.items + additions), screen = StudioScreen.EDITOR, pickingWords = false) }
  }

  private fun wordItems(selected: List<WordCaseEntity>, type: SurveyItemType) = selected.map { word ->
      SurveyItem(type = type.value, text = if (type == SurveyItemType.CHARACTER) word.coreChar else
        word.phrases?.takeIf { it.isNotBlank() } ?: word.coreChar,
        instruction = if (type == SurveyItemType.CHARACTER)
          "请用家乡话读出这个字。语义提示：${word.phrases.orEmpty()}。不确定时可以跳过。" else
          "请用家乡话读整个词语。括号中的简体写法仅作提示，无需重复读。",
        phonology = ChinesePhonology(word.sheng, word.hu, word.deng.takeIf { it in 1..4 },
          word.yun, word.diao, word.she, word.zu.orEmpty(), word.sourceId.orEmpty()))
    }

  fun importTask(uri: Uri) = work("导入调查包…") {
    withContext(Dispatchers.IO) {
      val task = requireNotNull(context.contentResolver.openInputStream(uri)) { "无法打开所选文件" }
        .use(SurveyPackageCodec::read)
      repository.importPackage(task)
    }
    notify("调查包已导入；相同版本不会重复添加")
  }

  fun startSession(task: SurveyPackage, settings: RecorderSettings) = work("创建录制任务…") {
    settingsStore.save(settings)
    val id = repository.startSession(task, settings.alias, settings.dialect)
    openSession(id)
  }

  fun startDefaultSession(settings: RecorderSettings) = work("准备完整字库录制…") {
    settingsStore.save(settings)
    val id = withContext(Dispatchers.IO) {
      val task = SurveyPackage(packageId = RecordingTaskSnapshot.LIBRARY_PACKAGE_ID, title = "默认方案 · 完整字库",
        description = "按字库顺序从头到尾逐条录制，同字不同音的字目独立保留。",
        items = wordItems(words.allForRecording(), SurveyItemType.CHARACTER))
      repository.startLibrarySession(task, settings.alias, settings.dialect)
    }
    openSession(id)
  }

  private suspend fun openSession(id: String) {
    selectedSession.value = id
    mutableSkippedItem.value = null
    withContext(Dispatchers.IO) { mutableTrial.value?.file?.delete() }
    mutableTrial.value = null
    mutableState.update { it.copy(screen = StudioScreen.SESSION) }
  }

  fun resume(id: String) = work("载入任务…") {
    player.stop()
    selectedSession.value = id
    mutableState.update { it.copy(screen = StudioScreen.SESSION) }
  }

  fun updateSessionInfo(id: String, title: String, alias: String, dialect: String, onSaved: () -> Unit) = work("保存录制信息…") {
    repository.updateSessionInfo(id, title, alias, dialect)
    onSaved()
    notify("录制信息已保存")
  }

  fun deleteSession(id: String, onDeleted: () -> Unit) = work("删除录制…") {
    require(id != selectedSession.value) { "请先退出这条录制" }
    // Commit the deletion before cleaning files, so interrupted cleanup never leaves live takes pointing at missing audio.
    withContext(NonCancellable + Dispatchers.IO) {
      repository.deleteSession(id)
      try { files.deleteSession(id) }
      catch (_: Exception) { throw IllegalStateException("条目已删除，部分音频未能清理，请再次点击删除重试") }
    }
    onDeleted()
    notify("录制已删除")
  }

  fun position(position: Int) = work("保存位置…") {
    player.stop()
    repository.setPosition(requireNotNull(selectedSession.value), position)
  }

  fun skip() = work("保存进度…") {
    val current = requireCurrent()
    val task = requireNotNull(recordingTask.value?.takeIf { it.sessionId == current.session.id }) { "字目仍在载入" }.task
    val step = current.steps.first { it.position == current.session.currentPosition }
    val item = task.items.first { it.itemId == step.itemId }
    player.stop()
    repository.skip(current.session.id, step.position)
    mutableSkippedItem.value = SkippedItem(current.session.id, step.position, item.text, step.skipReason.orEmpty())
  }

  fun clearSkippedItem(item: SkippedItem) { mutableSkippedItem.compareAndSet(item, null) }

  fun saveSkipReason(item: SkippedItem, reason: String) = work("保存跳过原因…") {
    repository.saveSkipReason(item.sessionId, item.position, reason)
    notify("跳过原因已保存")
  }

  fun note(note: String) = work("保存备注…") {
    val current = requireCurrent()
    repository.saveNote(current.session.id, current.session.currentPosition, note)
  }

  fun changeNote(id: String, position: Int, text: String) {
    if (canNavigate()) mutableNoteDraft.value = StepNoteDraft(id, position, text.take(2000))
  }

  private suspend fun flushNote() {
    val draft = mutableNoteDraft.value ?: return
    repository.saveNote(draft.sessionId, draft.position, draft.text)
    mutableNoteDraft.compareAndSet(draft, null)
  }

  fun adopt(takeId: String) = work("更换采用录音…") {
    val current = requireCurrent()
    val take = current.takes.first { it.id == takeId }
    require(files.take(current.session.id, take.id).isFile) { "录音文件不存在" }
    repository.selectTake(current.session.id, takeId)
  }

  fun startRecording(isTrial: Boolean = false) {
    if (!canNavigate() || !state.value.ready) return
    val current = detail.value?.takeIf { it.session.id == selectedSession.value } ?: return
    player.stop()
    val signal = AtomicBoolean(false)
    stopSignal = signal
    mutableCapture.value = CaptureState(isTrial)
    viewModelScope.launch {
      var take: RecordingTakeEntity? = null
      try {
        flushNote()
        val file = if (isTrial) files.take(trialSessionId, UUID.randomUUID().toString()) else {
          take = repository.beginTake(current.session.id, current.session.currentPosition)
          files.take(current.session.id, requireNotNull(take).id)
        }
        val result = recorder.record(file, signal, if (isTrial) 15 else 120)
        if (isTrial) {
          mutableTrial.value?.file?.delete()
          mutableTrial.value = result
          notify("试音已保存，请先回听确认；试音不会计入调查成果。${result.warning}")
        } else {
          val saved = requireNotNull(take).copy(state = RecordingTakeEntity.SAVED,
            durationMs = result.durationMs, sampleRate = result.sampleRate, audioSource = result.audioSource,
            inputDevice = result.inputDevice, peak = result.peak, rms = result.rms,
            clippedFraction = result.clippedFraction, warning = result.warning)
          repository.finishTake(saved)
          notify(if (result.warning.isBlank()) "已保存。请回听确认，再进入下一条。" else result.warning)
        }
      } catch (error: Exception) {
        take?.let { record -> withContext(NonCancellable) { runCatching { repository.interruptTake(record) } } }
        if (error is CancellationException) throw error
        notify(errorMessage(error))
      } finally {
        stopSignal = null
        mutableCapture.value = null
      }
    }
  }

  fun stopRecording() {
    stopSignal?.set(true)
    mutableCapture.update { it?.copy(stopping = true) }
  }

  fun background() {
    stopRecording()
    player.stop()
    if (mutableNoteDraft.value != null) work("保存备注…") { }
  }
  fun stopPlayback() = player.stop()

  fun play(takeId: String? = null, isTrial: Boolean = false) {
    if (!canNavigate()) return
    try {
      val file = if (isTrial) requireNotNull(trial.value).file else {
        val current = requireCurrent()
        val id = takeId ?: current.steps.first { it.position == current.session.currentPosition }.selectedTakeId
        files.take(current.session.id, requireNotNull(id) { "这道题还没有录音" })
      }
      player.play(file) { notify("无法回听该录音，请检查文件或重新录制") }
    } catch (error: Exception) { notify(errorMessage(error)) }
  }

  fun prepareTaskExport(task: SurveyPackage): String {
    pendingTaskExport = task
    return "wanluk-task-${task.packageId}-v${task.revision}.json"
  }

  fun exportTask(uri: Uri?) {
    val task = pendingTaskExport
    pendingTaskExport = null
    if (uri == null || task == null) return
    work("导出调查任务包…") {
      withContext(Dispatchers.IO) {
        val json = SurveyPackageCodec.encode(task)
        requireNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "无法写入目标文件" }
          .use { it.write(json.toByteArray(Charsets.UTF_8)) }
      }
      mutableExport.value = ExportedDocument(uri, "application/json")
      notify("任务包已导出，可自行分享给装有韵录的发音人")
    }
  }

  fun prepareResultExport(id: String, onReady: (String) -> Unit, onFailure: (String) -> Unit) =
    work("检查录音…", onError = {
      pendingResultExport = null
      onFailure("导出失败：${errorMessage(it)}")
    }) {
      pendingResultExport = null
      val current = withContext(Dispatchers.IO) { repository.session(id) }
      requireExportableAudio(current)
      pendingResultExport = id
      onReady("wanluk-result-$id.zip")
    }

  private suspend fun requireExportableAudio(current: SurveySessionDetail) = withContext(Dispatchers.IO) {
    check(current.takes.any { take ->
      take.state == RecordingTakeEntity.SAVED && take.durationMs > 0 &&
        files.take(current.session.id, take.id).let { it.isFile && it.length() >= 46 }
    }) { "没有可导出的录音" }
  }

  fun exportResult(uri: Uri?, onFailure: (String) -> Unit) {
    val id = pendingResultExport
    pendingResultExport = null
    if (uri == null || id == null) return
    work("正在导出成果包…", onError = { onFailure("导出失败：${errorMessage(it)}") }) {
      withContext(Dispatchers.IO) {
        val current = repository.session(id)
        requireExportableAudio(current)
        val task = repository.taskSnapshot(id)
        val items = task.items.associateBy { it.itemId }
        val session = current.session
        val bundle = SessionExport(sessionId = id, speakerAlias = session.speakerAlias, dialect = session.dialect,
          consentConfirmedAt = session.consentConfirmedAt, createdAt = session.createdAt,
          exportedAt = System.currentTimeMillis(), task = task, title = session.title,
          steps = current.steps.map { step ->
            val item = items.getValue(step.itemId)
            ExportStep(step.position, step.itemId, step.repetition, item.text, item.type,
              step.selectedTakeId, step.skipReason, step.note)
          }, takes = current.takes.map {
            ExportTake(it.id, it.position, it.state, it.createdAt, it.durationMs, it.sampleRate,
              it.channels, it.bitsPerSample, it.audioSource, it.inputDevice, it.peak, it.rms, it.clippedFraction, it.warning)
          })
        requireNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "无法写入目标文件" }.use {
          exporter.write(it, bundle) { done, total -> mutableState.update { state -> state.copy(busy = "导出录音 $done / $total") } }
        }
        repository.markExported(id)
      }
      mutableExport.value = ExportedDocument(uri, "application/zip")
      notify("成果包已导出，本地录音仍保留。请自行转交给研究者。")
    }
  }

  private fun requireCurrent(): SurveySessionDetail = requireNotNull(detail.value?.takeIf {
    it.session.id == selectedSession.value
  }) { "录制任务仍在载入，请稍后重试" }

  private fun work(label: String, onError: ((Exception) -> Unit)? = null, block: suspend () -> Unit) {
    if (!canNavigate()) return
    player.stop()
    mutableState.update { it.copy(busy = label) }
    viewModelScope.launch {
      try { flushNote(); block() }
      catch (error: Exception) {
        if (error is CancellationException) throw error
        if (onError != null) onError(error) else notify(errorMessage(error))
      } finally { mutableState.update { it.copy(busy = null) } }
    }
  }

  private fun errorMessage(error: Exception): String = when (error) {
    is IllegalArgumentException, is IllegalStateException -> error.message?.take(240) ?: "操作未完成，请重试"
    is SecurityException -> "访问被拒绝，请检查麦克风或所选文件的权限"
    is IOException -> "文件读写失败，请检查存储空间和文件权限；未完成的导出文件可能不完整，本地录音仍保留"
    else -> "操作未完成，请确认文件格式、存储空间和权限后重试；本地已有录音仍保留"
  }

  override fun onCleared() { stopSignal?.set(true); player.stop(); super.onCleared() }
}

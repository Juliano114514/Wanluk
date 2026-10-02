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
import com.wanluk.libroom.repository.SurveyRepository
import com.wanluk.libroom.repository.WordCaseRepository
import com.wanluk.libroom.repository.SurveyTransferMapper.Companion.annotation
import com.wanluk.libroom.repository.SurveyTransferMapper.Companion.prompt
import com.wanluk.libroom.repository.SurveyTransferMapper.Companion.sourceNumber
import com.wanluk.libroom.repository.SurveyTransferMapper.Companion.text
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
  val draftStatus: String? = null,
) {
  val draftChanged: Boolean get() = draft != originalDraft
}
data class CaptureState(val trial: Boolean, val stopping: Boolean = false)
data class ExportedDocument(val uri: Uri, val mime: String)
data class StepNoteDraft(val sessionId: String, val position: Int, val text: String)
data class SkippedItem(val sessionId: String, val position: Int, val text: String, val reason: String = "")
data class RecordingCleanup(val sessionId: String, val takeIds: List<String>? = null)
data class BatchOperationResult(val title: String, val succeeded: Int, val failures: List<String> = emptyList(),
  val cleanup: List<RecordingCleanup> = emptyList())
private data class ResultExportRequest(val ids: List<String>, val positions: List<Int>? = null, val takeIds: List<String>? = null)

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
  private val mutableSkippedItem = MutableStateFlow<SkippedItem?>(null)
  val skippedItem = mutableSkippedItem.asStateFlow()
  private val mutableNoteDraft = MutableStateFlow<StepNoteDraft?>(null)
  val noteDraft = mutableNoteDraft.asStateFlow()
  val detail = selectedSession.flatMapLatest { id ->
    if (id == null) flowOf(null) else repository.observeSession(id)
  }.catch { notify("无法读取录制任务，请返回后重试") }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
  val packages = repository.packages().catch { notify("无法读取调查包，请重试初始化") }
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
  private val mutableBatchResult = MutableStateFlow<BatchOperationResult?>(null)
  val batchResult = mutableBatchResult.asStateFlow()
  val drafts = repository.drafts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
  val cleanupCount = repository.cleanupCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
  private val draftLock = Mutex()
  private var activeDraftId: String? = null
  private var draftSave: Job? = null
  private var stopSignal: AtomicBoolean? = null
  private val trialSessionId = UUID.randomUUID().toString()
  private var pendingResultExport: ResultExportRequest? = null

  init { initialize() }

  fun initialize() = work("准备本地字库和调查包…") {
    withContext(Dispatchers.IO) {
      words.ensureBuiltinWordCasesImported()
      repository.initialize()
    }
    mutableState.update { it.copy(ready = true) }
    val pending = withContext(Dispatchers.IO) { runCleanup(repository.cleanupJobs()) }
    if (pending.isNotEmpty()) mutableBatchResult.value = BatchOperationResult("音频尚未清理", 0, cleanup = pending)
  }

  fun notify(message: String) { mutableState.update { it.copy(message = message) } }
  fun clearMessage() { mutableState.update { it.copy(message = null) } }
  fun clearExport() { mutableExport.value = null }
  fun clearBatchResult() { if (state.value.busy == null) mutableBatchResult.value = null }
  fun showCleanup() = work("读取清理记录…") {
    mutableBatchResult.value = BatchOperationResult("音频尚未清理", 0, cleanup = queuedCleanup())
  }
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
    flushDraft()
    returnHome()
  }
  fun discardDraft() = work("丢弃草稿…") {
    draftSave?.cancel()
    draftLock.withLock { activeDraftId?.let { repository.deleteDraft(it) }; activeDraftId = null }
    returnHome()
  }
  fun resumeDraft(id: String) = work("恢复草稿…") {
    flushDraft()
    val saved = repository.readDraft(id)
    activeDraftId = id
    mutableState.update { it.copy(screen = StudioScreen.EDITOR, draft = words.editable(saved.task),
      originalDraft = saved.original?.let(words::editable), draftStatus = "已保存到本机", pickingWords = false, creatingPackage = false) }
  }
  fun deleteLocalDraft(id: String) = work("丢弃草稿…") { repository.deleteDraft(id) }
  fun retryDraft() { if (canNavigate()) scheduleDraft() }
  private fun scheduleDraft() {
    val task = state.value.draft ?: return
    val original = state.value.originalDraft
    val id = activeDraftId ?: UUID.randomUUID().toString().also { activeDraftId = it }
    draftSave?.cancel()
    mutableState.update { it.copy(draftStatus = "等待保存…") }
    draftSave = viewModelScope.launch {
      delay(800)
      try { persistDraft(id, task, original) }
      catch (error: Exception) {
        if (error is CancellationException) throw error
        if (activeDraftId == id) mutableState.update { it.copy(draftStatus = "草稿保存失败 · 重试") }
      }
    }
  }
  private suspend fun persistDraft(id: String, task: SurveyPackage, original: SurveyPackage?) = draftLock.withLock {
    if (activeDraftId != id) return@withLock
    mutableState.update { it.copy(draftStatus = "正在保存…") }
    repository.saveLocalDraft(id, task, original)
    if (activeDraftId == id && state.value.draft == task) mutableState.update { it.copy(draftStatus = "已保存到本机") }
  }
  private suspend fun flushDraft() {
    draftSave?.cancel()
    val task = state.value.draft ?: return
    if (task == state.value.originalDraft && state.value.draftStatus == null) return
    val id = activeDraftId ?: return
    try { persistDraft(id, task, state.value.originalDraft) }
    catch (error: Exception) {
      if (error is CancellationException) throw error
      mutableState.update { it.copy(draftStatus = "草稿保存失败 · 重试") }
      throw error
    }
  }
  fun csvDraft(task: SurveyPackage) {
    if (!canNavigate()) return
    activeDraftId = UUID.randomUUID().toString()
    mutableState.update { it.copy(screen = StudioScreen.EDITOR, draft = task, originalDraft = null,
      pickingWords = false, creatingPackage = false) }
    scheduleDraft()
  }

  fun finishSession(onFinished: (SessionSummary) -> Unit) = work("保存进度…") {
    val current = requireCurrent()
    val step = current.step
    require(step.position == current.session.totalSteps - 1) { "还未到最后一条" }
    if (step.selectedTakeId == null && step.skipReason == null) {
      repository.skip(current.session.id, step.position)
    }
    val summary = repository.sessionInfo(current.session.id)
    returnHome()
    onFinished(summary)
  }

  private fun returnHome() {
    player.stop()
    selectedSession.value = null
    mutableSkippedItem.value = null
    activeDraftId = null
    mutableState.update { it.copy(screen = StudioScreen.HOME, draft = null, originalDraft = null,
      pickingWords = false, creatingPackage = false, draftStatus = null) }
  }

  fun edit(task: SurveyPackage? = null, copy: Boolean = false) {
    if (!canNavigate()) return
    player.stop()
    val source = words.editable(task ?: SurveyPackage())
    val draft = if (copy || BuiltinSurveys.isBuiltin(source.packageId)) source.copy(packageId = UUID.randomUUID().toString(), revision = 1,
      title = (source.title.take(116) + "（副本）")) else source
    activeDraftId = UUID.randomUUID().toString()
    mutableState.update { it.copy(screen = StudioScreen.EDITOR, draft = draft, originalDraft = draft, draftStatus = null) }
  }

  fun updateDraft(task: SurveyPackage) {
    if (!canNavigate()) return
    val previous = state.value.draft
    val resolved = if (previous != null && previous.items === task.items && previous.defaults == task.defaults) task else words.editable(task)
    mutableState.update { it.copy(draft = resolved) }
    scheduleDraft()
  }

  fun resolveItem(item: SurveyItem): SurveyItem {
    val task = requireNotNull(state.value.draft)
    val word = item.wordId?.toIntOrNull()?.let { words.currentBuiltin(it) }
    val normalized = if (word != null) {
      val type = item.extInfo?.displayType ?: requireNotNull(task.defaults).displayType
      item.copy(type = type, text = word.text(type), phonology = word.annotation())
    } else item
    return words.editable(task.copy(items = listOf(normalized))).items.single()
  }

  fun loadPackage(summary: SurveyPackageSummary, onLoaded: (SurveyPackage) -> Unit) {
    if (!canNavigate()) return
    player.stop()
    mutableState.update { it.copy(busy = "载入调查包…") }
    viewModelScope.launch {
      val task = try { withContext(Dispatchers.IO) { words.editable(repository.readPackage(summary.packageId, summary.revision)) } }
      catch (error: Exception) {
        if (error is CancellationException) throw error
        notify(errorMessage(error)); null
      } finally { mutableState.update { it.copy(busy = null) } }
      task?.let(onLoaded)
    }
  }

  fun applyJson(json: String) = work("校验任务 JSON…") {
    val draft = withContext(Dispatchers.Default) { SurveyPackageCodec.decode(json) }
    mutableState.update { it.copy(draft = words.editable(draft), message = "JSON 已载入编辑器；保存后生成本地版本") }
    scheduleDraft()
  }

  fun saveDraft() = work("保存调查包…") {
    val draft = requireNotNull(state.value.draft)
    flushDraft()
    val saved = withContext(Dispatchers.IO) { repository.saveRevision(draft) }
    draftLock.withLock { activeDraftId?.let { repository.deleteDraft(it) }; activeDraftId = null }
    mutableState.update { it.copy(screen = StudioScreen.HOME, draft = null, originalDraft = null,
      draftStatus = null, message = "已保存《${saved.title}》v${saved.revision}，已有录制任务保持原版本") }
  }

  fun library(picking: Boolean = false) = work("保存草稿…") {
    flushDraft()
    mutableState.update { it.copy(screen = StudioScreen.LIBRARY, pickingWords = picking) }
  }

  fun newPackage() {
    edit(SurveyPackage(title = ""))
  }

  fun createPackage(selectedIds: List<Long>, type: SurveyItemType, title: String, planType: RecordingPlanType) = work("保存调查包…") {
    require(selectedIds.isNotEmpty() && selectedIds.size <= SurveyPackage.MAX_ITEMS) { "请选择 1–20000 个字目" }
    require(title.trim().isNotEmpty() && title.length <= 120) { "请填写调查包名称" }
    val selected = words.selected(selectedIds)
    val base = state.value.draft?.takeIf { state.value.creatingPackage } ?: SurveyPackage()
    val defaults = requireNotNull(base.defaults).copy(displayType = type.value)
    val draft = base.copy(title = title.trim(), defaults = defaults, items = wordItems(selected, type, defaults), planType = planType)
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
    require(draft.items.size + selectedIds.size <= SurveyPackage.MAX_ITEMS) { "每个调查包最多 20000 字目" }
    val selected = words.selected(selectedIds)
    val additions = wordItems(selected, type, requireNotNull(draft.defaults))
    val updated = words.editable(draft.copy(items = draft.items + additions))
    require(updated.totalSteps <= SurveyPackage.MAX_STEPS) { "录制次数已达上限" }
    mutableState.update { it.copy(draft = updated, screen = StudioScreen.EDITOR, pickingWords = false) }
    scheduleDraft()
  }

  private fun wordItems(selected: List<WordEntry>, type: SurveyItemType, defaults: SurveyDefaults = SurveyDefaults()) = selected.map { word ->
      SurveyItem(itemId = UUID.randomUUID().toString(), type = type.value, text = word.text(type.value),
        instruction = defaults.instruction ?: word.prompt(type.value), repetitions = defaults.repetitions,
        allowSkip = defaults.allowSkip, phonology = word.annotation(), wordId = word.sourceNumber()?.toString(),
        extInfo = type.value.takeIf { it != defaults.displayType }?.let { ItemExtInfo(displayType = it) })
    }

  fun startSession(task: SurveyPackage, settings: RecorderSettings) = work("创建录制任务…") {
    settingsStore.save(settings)
    val id = repository.startSession(task, settings.alias, settings.dialect)
    openSession(id)
  }

  fun startDefaultSession(settings: RecorderSettings) = work("准备完整字库录制…") {
    settingsStore.save(settings)
    val id = withContext(Dispatchers.IO) {
      val task = repository.libraryPlan()
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

  fun updateSessionInfo(id: String, title: String, alias: String, dialect: String, researchCode: String, location: String, collector: String, onSaved: () -> Unit) = work("保存录制信息…") {
    repository.updateSessionInfo(id, title, alias, dialect, researchCode, location, collector)
    onSaved()
    notify("录制信息已保存")
  }

  fun deleteSession(id: String, onDeleted: () -> Unit) = work("删除录制…") {
    require(id != selectedSession.value) { "请先退出这条录制" }
    // Commit the deletion before cleaning files, so interrupted cleanup never leaves live takes pointing at missing audio.
    withContext(NonCancellable + Dispatchers.IO) {
      repository.deleteSession(id)
      mutableBatchResult.value = BatchOperationResult("删除录制", 1, cleanup = cleanSession(id))
    }
    onDeleted()
  }

  fun copyPackages(packages: List<SurveyPackageSummary>) = work("复制调查包…") {
    var completed = 0
    val failures = mutableListOf<String>()
    withContext(Dispatchers.IO) {
      packages.distinctBy { it.packageId }.forEach { summary ->
        try {
          val source = repository.readPackage(summary.packageId, summary.revision)
          repository.saveRevision(source.copy(packageId = UUID.randomUUID().toString(), revision = 1,
            title = source.title.take(116) + "（副本）"))
          completed++
        } catch (error: Exception) {
          if (error is CancellationException) throw error
          failures += "${summary.title}：${errorMessage(error)}"
        }
      }
    }
    mutableBatchResult.value = BatchOperationResult("复制调查包", completed, failures)
  }

  fun deletePackages(packages: List<SurveyPackageSummary>) = work("删除调查包…") {
    val removable = packages.filterNot { BuiltinSurveys.isBuiltin(it.packageId) }.distinctBy { it.packageId }
    require(removable.isNotEmpty()) { "内置预设不能删除" }
    withContext(Dispatchers.IO) { repository.deletePackages(removable.map { it.packageId }) }
    mutableBatchResult.value = BatchOperationResult("删除调查包", removable.size)
  }

  fun deleteSessions(ids: List<String>) = work("删除录制…") {
    require(ids.isNotEmpty() && selectedSession.value !in ids) { "请先退出这条录制" }
    var completed = 0
    val failures = mutableListOf<String>()
    val pending = mutableListOf<RecordingCleanup>()
    withContext(NonCancellable + Dispatchers.IO) {
      ids.distinct().forEach { id ->
        try {
          repository.deleteSession(id)
          completed++
          pending += cleanSession(id)
        } catch (error: Exception) { failures += "${sessions.value.firstOrNull { it.id == id }?.title ?: id}：${errorMessage(error)}" }
      }
    }
    mutableBatchResult.value = BatchOperationResult("删除录制", completed, failures, pending)
  }

  fun clearSteps(positions: List<Int>) = work("清空所选录音…") {
    val id = requireNotNull(selectedSession.value)
    withContext(NonCancellable + Dispatchers.IO) {
      val takes = repository.clearSteps(id, positions)
      mutableBatchResult.value = BatchOperationResult("清空录音并恢复待录", positions.distinct().size,
        cleanup = cleanTakes(id, takes))
    }
  }

  fun deleteTakes(ids: List<String>) = work("删除所选版本…") {
    val id = requireNotNull(selectedSession.value)
    withContext(NonCancellable + Dispatchers.IO) {
      val deleted = repository.deleteTakes(id, ids)
      val pending = cleanTakes(id, deleted)
      mutableBatchResult.value = BatchOperationResult("删除录音历史", deleted.size, cleanup = pending)
    }
  }

  private suspend fun cleanTakes(sessionId: String, ids: List<String>): List<RecordingCleanup> {
    if (ids.isEmpty()) return emptyList()
    val selected = ids.toSet()
    return runCleanup(repository.cleanupJobs().filter { it.sessionId == sessionId && it.takeId in selected })
  }
  private suspend fun cleanSession(sessionId: String): List<RecordingCleanup> =
    runCleanup(repository.cleanupJobs().filter { it.sessionId == sessionId })
  private fun groupedCleanup(jobs: List<AudioCleanupJob>): List<RecordingCleanup> = jobs.groupBy { it.sessionId }.map { (id, targets) ->
    if (targets.any { it.takeId == null }) RecordingCleanup(id)
    else RecordingCleanup(id, targets.mapNotNull { it.takeId }.distinct())
  }
  private suspend fun queuedCleanup() = groupedCleanup(repository.cleanupJobs())
  private suspend fun runCleanup(jobs: List<AudioCleanupJob>): List<RecordingCleanup> {
    val failed = mutableListOf<AudioCleanupJob>()
    for (job in jobs) {
      try {
        require(repository.canClean(job)) { "清理目标仍被使用" }
        val takeId = job.takeId
        if (takeId == null) files.deleteSession(job.sessionId) else files.deleteTake(job.sessionId, takeId)
        repository.completeCleanup(job.key)
      } catch (error: Exception) {
        if (error is CancellationException) throw error
        repository.cleanupFailed(job.key)
        failed += job
      }
    }
    return groupedCleanup(failed)
  }
  fun retryCleanup() = work("清理音频…") {
    val previous = mutableBatchResult.value ?: BatchOperationResult("清理音频", 0)
    val jobs = repository.cleanupJobs()
    val pending = withContext(NonCancellable + Dispatchers.IO) { runCleanup(jobs) }
    mutableBatchResult.value = BatchOperationResult("清理音频", jobs.size - repository.cleanupJobs().size, previous.failures, pending)
  }

  fun position(position: Int) = work("保存位置…") {
    player.stop()
    repository.setPosition(requireNotNull(selectedSession.value), position)
  }

  fun skip() = work("保存进度…") {
    val current = requireCurrent()
    val step = current.step
    val item = current.item
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

  fun review(takeId: String, status: ReviewStatus) = work("保存复核结果…") {
    repository.reviewTake(requireCurrent().session.id, takeId, status)
  }

  fun startRecording(isTrial: Boolean = false) {
    if (!canNavigate() || !state.value.ready) return
    val current = detail.value?.takeIf { it.session.id == selectedSession.value } ?: return
    player.stop()
    val signal = AtomicBoolean(false)
    stopSignal = signal
    mutableCapture.value = CaptureState(isTrial)
    viewModelScope.launch {
      var take: RecordingTake? = null
      try {
        flushNote()
        val file = if (isTrial) files.take(trialSessionId, UUID.randomUUID().toString()) else {
          take = repository.beginTake(current.session.id, current.session.currentPosition)
          files.take(current.session.id, requireNotNull(take).id)
        }
        val result = recorder.record(file, signal, if (isTrial) 15 else current.planType.maxSeconds)
        if (isTrial) {
          mutableTrial.value?.file?.delete()
          mutableTrial.value = result
          notify("试音已保存，请先回听确认；试音不会计入调查成果。${result.warning}")
        } else {
          val saved = requireNotNull(take).copy(state = RecordingTake.SAVED,
            durationMs = result.durationMs, sampleRate = result.sampleRate, audioSource = result.audioSource,
            inputDevice = result.inputDevice, peak = result.peak, rms = result.rms,
            clippedFraction = result.clippedFraction, warning = result.warning, appliedGain = result.appliedGain)
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
    viewModelScope.launch {
      try { flushDraft(); if (capture.value == null) flushNote() }
      catch (error: Exception) { if (error is CancellationException) throw error; notify(errorMessage(error)) }
    }
  }
  fun stopPlayback() = player.stop()

  fun play(takeId: String? = null, isTrial: Boolean = false) {
    if (!canNavigate()) return
    try {
      val file = if (isTrial) requireNotNull(trial.value).file else {
        val current = requireCurrent()
        val id = takeId ?: current.step.selectedTakeId
        files.take(current.session.id, requireNotNull(id) { "这道题还没有录音" })
      }
      player.play(file) { notify("无法回听该录音，请检查文件或重新录制") }
    } catch (error: Exception) { notify(errorMessage(error)) }
  }

  fun prepareResultExport(id: String, onReady: (String) -> Unit, onFailure: (String) -> Unit) =
    prepareResultsExport(listOf(id), onReady = onReady, onFailure = onFailure)

  fun prepareResultsExport(ids: List<String>, positions: List<Int>? = null, takeIds: List<String>? = null,
    onReady: (String) -> Unit, onFailure: (String) -> Unit) =
    work("检查录音…", onError = {
      pendingResultExport = null
      onFailure("导出失败：${errorMessage(it)}")
    }) {
      pendingResultExport = null
      require(ids.isNotEmpty() && (positions == null && takeIds == null || ids.size == 1)) { "请选择要导出的录制" }
      require(positions == null || takeIds == null) { "导出范围不合法" }
      val request = ResultExportRequest(ids.distinct(), positions?.distinct()?.sorted(), takeIds?.distinct())
      var suggestedName = ResultExportNames.batchFile(request.ids.size, System.currentTimeMillis())
      withContext(Dispatchers.IO) {
        request.ids.forEach { id ->
          val bundle = exportBundle(id, request)
          if (request.ids.size == 1) suggestedName = ResultExportNames.sessionFile(bundle).let {
            if (positions != null || takeIds != null) it.removeSuffix(".zip") + "_选录.zip" else it
          }
          try { exporter.validate(bundle, checkAdopted = request.takeIds == null) }
          catch (error: Exception) {
            if (error is CancellationException) throw error
            throw IllegalStateException("${bundle.title}：${errorMessage(error)}")
          }
        }
      }
      pendingResultExport = request
      onReady(suggestedName)
    }

  private suspend fun exportBundle(id: String, request: ResultExportRequest): SessionExport {
    val current = repository.session(id)
    val task = repository.taskSnapshot(id)
    val items = task.items.associateBy { it.itemId }
    val session = current.session
    val selectedPositions = request.positions?.toSet()
    val selectedTakes = request.takeIds?.toSet()
    request.positions?.let { selected ->
      require(selected.isNotEmpty() && current.steps.mapTo(hashSetOf()) { it.position }.containsAll(selected)) { "所选步骤已变化" }
    }
    request.takeIds?.let { selected ->
      require(selected.isNotEmpty() && current.takes.mapTo(hashSetOf()) { it.id }.containsAll(selected)) { "所选录音已变化" }
    }
    val full = ResultExportNames.attach(SessionExport(sessionId = id, speakerAlias = session.speakerAlias, dialect = session.dialect,
      consentConfirmedAt = session.consentConfirmedAt, createdAt = session.createdAt,
      exportedAt = System.currentTimeMillis(), task = task, title = session.title, contentRevision = session.contentRevision,
      researchCode = session.researchCode, collectionLocation = session.collectionLocation, collector = session.collector,
      steps = current.steps.map { step ->
        val item = items.getValue(step.itemId)
        ExportStep(step.position, step.itemId, step.repetition, item.text, item.type,
          step.selectedTakeId, step.skipReason, step.note)
      }, takes = current.takes.map {
        ExportTake(it.id, it.position, it.state, it.createdAt, it.durationMs, it.sampleRate,
          it.channels, it.bitsPerSample, it.audioSource, it.inputDevice, it.peak, it.rms, it.clippedFraction, it.warning, it.reviewStatus,
          appliedGain = it.appliedGain)
      }))
    return full.copy(steps = full.steps.filter { selectedPositions == null || it.position in selectedPositions },
      takes = full.takes.filter {
        (selectedPositions == null || it.position in selectedPositions) &&
          (selectedTakes == null || it.takeId in selectedTakes && it.state == RecordingTake.SAVED)
      })
  }

  fun exportResult(uri: Uri?, onFailure: (String) -> Unit) {
    val request = pendingResultExport
    pendingResultExport = null
    if (uri == null || request == null) return
    work("正在导出成果包…", onError = { onFailure("导出失败：${errorMessage(it)}") }) {
      withContext(Dispatchers.IO) {
        val progress: (Int, Int) -> Unit = { done, total -> mutableState.update { it.copy(busy = "导出 $done / $total") } }
        val versions = mutableMapOf<String, Long>()
        suspend fun bundle(id: String): SessionExport = exportBundle(id, request).also { versions[id] = it.contentRevision }
        requireNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "无法写入目标文件" }.use {
          if (request.ids.size > 1) exporter.writeBatch(it, request.ids, { id -> bundle(id) }, progress)
          else {
            val bundle = bundle(request.ids.single())
            when {
              request.positions != null -> exporter.writeSteps(it, bundle, request.positions, progress)
              request.takeIds != null -> exporter.writeTakes(it, bundle, request.takeIds, progress)
              else -> exporter.write(it, bundle, progress)
            }
          }
        }
        if (request.positions == null && request.takeIds == null) versions.forEach { (id, version) -> repository.markExported(id, version) }
      }
      mutableExport.value = ExportedDocument(uri, "application/zip")
      notify("已导出，本机录音保留")
    }
  }

  private fun requireCurrent(): CurrentRecording = requireNotNull(detail.value?.takeIf {
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

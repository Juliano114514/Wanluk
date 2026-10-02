package com.wanluk.libroom.repository

import android.content.Context
import androidx.room.withTransaction
import com.wanluk.foundation.survey.*
import com.wanluk.libroom.repository.SurveyTransferMapper.Companion.annotation
import com.wanluk.libroom.repository.SurveyTransferMapper.Companion.prompt
import com.wanluk.libroom.repository.SurveyTransferMapper.Companion.sourceNumber
import com.wanluk.libroom.repository.SurveyTransferMapper.Companion.text
import com.wanluk.libroom.AppDatabase
import com.wanluk.libroom.entity.*
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class PackageImportStatus { ADDED, DUPLICATE }

class SurveyRepository(private val context: Context, private val database: AppDatabase, private val words: WordCaseRepository) {
  private val dao = database.surveyDao()
  private val snapshotLock = Mutex()
  private val itemChunks = linkedMapOf<Pair<String, Int>, SurveyPackage>()
  fun packages() = dao.packages().map { it.sortedBy { task -> BuiltinSurveys.rank(task.packageId) } }
  fun sessions() = dao.sessions()
  fun drafts() = dao.drafts()
  fun cleanupCount() = dao.cleanupCount()

  @OptIn(ExperimentalCoroutinesApi::class)
  fun observeSession(id: String): Flow<CurrentRecording?> = dao.observeSessionSummary(id).distinctUntilChanged().mapLatest { observed ->
    if (observed == null) null else {
      // Read the header, current step and takes from the same committed state.
      val current = database.withTransaction {
        val header = dao.sessionSummary(id) ?: return@withTransaction null
        val step = dao.step(id, header.currentPosition) ?: return@withTransaction null
        Triple(header, step, dao.currentTakes(id, step.position))
      }
      current?.let { (session, step, takes) ->
        val (item, planType) = recordingItemSnapshot(id, step.itemId)
        CurrentRecording(session, step, item, takes, planType)
      }
    }
  }.flowOn(Dispatchers.IO)

  suspend fun session(id: String): SessionContents = database.withTransaction {
    SessionContents(requireNotNull(dao.sessionSummary(id)) { "录制任务不存在" }, dao.allSteps(id), dao.allTakes(id))
  }
  suspend fun sessionInfo(id: String) = requireNotNull(dao.sessionSummary(id)) { "录制任务不存在" }

  suspend fun updateSessionInfo(id: String, title: String, alias: String, dialect: String,
    researchCode: String = "", location: String = "", collector: String = "") = database.withTransaction {
    require(title.isNotBlank() && title.length <= 120) { "请填写 1–120 字符的录制名称" }
    require(alias.length <= 80 && dialect.length <= 160) { "称呼最多 80 字符，方言最多 160 字符" }
    require(researchCode.length <= 120 && location.length <= 160 && collector.length <= 80) { "研究编号最多 120、采集地点最多 160、采集者最多 80 字符" }
    val current = sessionInfo(id)
    if (listOf(current.title, current.speakerAlias, current.dialect, current.researchCode, current.collectionLocation, current.collector) ==
      listOf(title.trim(), alias.trim(), dialect.trim(), researchCode.trim(), location.trim(), collector.trim())) return@withTransaction
    check(dao.updateSessionInfo(id, title.trim(), alias.trim(), dialect.trim(), researchCode.trim(), location.trim(), collector.trim(), System.currentTimeMillis()) == 1) { "录制条目不存在" }
  }

  suspend fun deleteSession(id: String) = database.withTransaction {
    require(dao.activeTakes(id) == 0) { "请先停止录音" }
    require(dao.sessionSummary(id) != null) { "录制任务不存在" }
    dao.queueCleanup(listOf(RecordingCleanupJob(id, id)))
    check(dao.deleteSession(id) == 1)
  }

  suspend fun readPackage(id: String, revision: Int): SurveyPackage = withContext(Dispatchers.IO) {
    if (id == RecordingTaskSnapshot.LIBRARY_PACKAGE_ID) libraryPlan(revision)
    else SurveyPackageCodec.decode(readParts(requireNotNull(dao.taskHeader(id, revision)) { "调查包不存在" }).joinToString(""))
  }
  private suspend fun readParts(header: SurveyPackageHeader): List<String> {
    require(header.jsonChars in 1..SurveyPackageCodec.MAX_BYTES) { "本地调查包大小不合法" }
    return (1..header.jsonChars step SLICE_CHARS).map { offset ->
      requireNotNull(dao.taskSlice(header.packageId, header.revision, offset, SLICE_CHARS)) { "调查包不存在" }
    }.also { require(it.sumOf { part -> part.toByteArray(Charsets.UTF_8).size.toLong() } <= SurveyPackageCodec.MAX_BYTES) }
  }
  private suspend fun firstSnapshot(id: String): String {
    val header = requireNotNull(dao.snapshotHeader(id)) { "录制任务不存在" }
    require(header.jsonChars in 1..SurveyPackageCodec.MAX_BYTES)
    return (1..header.jsonChars step SLICE_CHARS).map { offset ->
      requireNotNull(dao.snapshotSlice(id, offset, SLICE_CHARS)) { "录制快照不存在" }
    }.joinToString("").also { require(it.toByteArray(Charsets.UTF_8).size <= SurveyPackageCodec.MAX_BYTES) }
  }
  suspend fun taskSnapshot(id: String): SurveyPackage = withContext(Dispatchers.IO) {
    val header = requireNotNull(dao.snapshotHeader(id)) { "录制任务不存在" }
    val chunks = dao.taskChunkHeaders(id)
    require(chunks.map { it.chunkIndex } == (1..chunks.size).toList()) { "字目快照不完整" }
    val first = firstSnapshot(id)
    var bytes = first.toByteArray(Charsets.UTF_8).size.toLong()
    val remaining = chunks.map { chunk -> readChunk(id, chunk.chunkIndex, chunk.jsonChars).also {
      bytes += it.toByteArray(Charsets.UTF_8).size; require(bytes <= SurveyPackageCodec.MAX_BYTES) { "录制快照超过 16 MB" }
    } }
    RecordingTaskSnapshot.restore(first, remaining).also { require(it.totalSteps == header.totalSteps) { "录制字目快照不完整" } }
  }
  private suspend fun readChunk(id: String, index: Int, knownChars: Int? = null): String {
    val chars = knownChars ?: requireNotNull(dao.taskChunkLength(id, index)) { "快照分块不存在" }
    require(chars in 1..SurveyPackageCodec.MAX_BYTES) { "快照分块大小不合法" }
    return (1..chars step SLICE_CHARS).map { offset ->
      requireNotNull(dao.taskChunkSlice(id, index, offset, SLICE_CHARS)) { "快照分块不存在" }
    }.joinToString("").also { require(it.toByteArray(Charsets.UTF_8).size <= SurveyPackageCodec.MAX_BYTES) }
  }
  suspend fun itemSnapshot(id: String, itemId: String): SurveyItem = recordingItemSnapshot(id, itemId).first

  private suspend fun recordingItemSnapshot(id: String, itemId: String): Pair<SurveyItem, RecordingPlanType> = withContext(Dispatchers.IO) {
    snapshotLock.withLock {
      val location = dao.location(id, itemId) ?: run {
        // Legacy snapshots are split without looking up or reinterpreting the current catalog.
        val task = taskSnapshot(id)
        val chunks = RecordingTaskSnapshot.chunks(task)
        database.withTransaction {
          require(dao.sessionSummary(id) != null) { "录制任务不存在" }
          dao.replaceSnapshotHeader(id, chunks.first())
          dao.deleteTaskChunks(id)
          dao.insertTaskChunks(chunks.drop(1).mapIndexed { index, json -> SessionTaskChunkEntity(id, index + 1, json) })
          dao.deleteLocations(id)
          task.items.mapIndexed { index, item -> SessionItemLocation(id, item.itemId, index / RecordingTaskSnapshot.CHUNK_SIZE, index % RecordingTaskSnapshot.CHUNK_SIZE) }
            .chunked(1000).forEach { dao.insertLocations(it) }
        }
        requireNotNull(dao.location(id, itemId)) { "快照字目不存在" }
      }
      val key = id to location.chunkIndex
      val chunk = itemChunks[key] ?: SurveyPackageCodec.decode(if (location.chunkIndex == 0) firstSnapshot(id)
        else readChunk(id, location.chunkIndex)).also {
        if (itemChunks.size >= 8) itemChunks.remove(itemChunks.keys.first())
        itemChunks[key] = it
      }
      requireNotNull(chunk.items.getOrNull(location.itemIndex)?.takeIf { it.itemId == itemId }) { "快照定位不一致" } to chunk.planType
    }
  }

  suspend fun initialize() = withContext(Dispatchers.IO) {
    database.withTransaction { dao.touchInterruptedSessions(System.currentTimeMillis()); dao.recoverInterruptedTakes() }
    for (asset in listOf("surveys/starter-zh.json", "surveys/swadesh-100-zh.json")) {
      val legacy = context.assets.open(asset).use(SurveyPackageCodec::read)
      importPackage(legacy)
      for (revision in 2..BuiltinSurveys.revision(legacy.packageId)) {
        importPackage(words.editable(legacy).copy(revision = revision,
          title = BuiltinSurveys.title(legacy.packageId, revision = revision), planType = RecordingPlanType.WORD))
      }
    }
    for (id in listOf(BuiltinSurveys.SHANCUN_ID, BuiltinSurveys.GUO_ID)) {
      for (revision in 1..BuiltinSurveys.revision(id)) importPackage(BuiltinSurveys.passage(id, revision))
    }
    val dong = words.builtinCatalog().words.entries.filter { it.value.yun == "東" }.sortedBy { it.key }
    for (revision in 1..BuiltinSurveys.revision(BuiltinSurveys.DONG_ID)) {
      importPackage(SurveyPackage(packageId = BuiltinSurveys.DONG_ID, revision = revision,
        title = BuiltinSurveys.title(BuiltinSurveys.DONG_ID, itemCount = dong.size, revision = revision),
        description = "字库韻＝東的全部字目，同字不同音独立保留。", items = dong.map { (source, word) ->
          SurveyItem(itemId = "i_dong_$source", type = "character", text = word.text("character"),
            instruction = word.prompt("character"), phonology = word.annotation(), wordId = source.toString())
        }))
    }
    dao.missingSummaries().forEach { header ->
      val summary = SurveyPackageCodec.summary(readParts(header))
      dao.saveSummary(header.packageId, header.revision, summary.title, summary.itemCount, summary.totalSteps, summary.description, summary.dialect)
    }
    migrateCleanup()
  }

  suspend fun libraryPlan(revision: Int = BuiltinSurveys.revision(RecordingTaskSnapshot.LIBRARY_PACKAGE_ID)): SurveyPackage = withContext(Dispatchers.IO) {
    require(revision in 1..BuiltinSurveys.revision(RecordingTaskSnapshot.LIBRARY_PACKAGE_ID)) { "全量录制版本不受支持" }
    val items = words.allForRecording().map { word -> SurveyItem(itemId = "i_library_${word.id}", type = "character",
      text = word.text("character"), instruction = word.prompt("character"), phonology = word.annotation(), wordId = word.sourceNumber()?.toString()) }
    SurveyPackage(schemaVersion = if (revision == 1) 2 else 3, packageId = RecordingTaskSnapshot.LIBRARY_PACKAGE_ID, revision = revision,
      title = BuiltinSurveys.title(RecordingTaskSnapshot.LIBRARY_PACKAGE_ID, itemCount = items.size, revision = revision),
      description = "按字库顺序从头到尾逐条录制，同字不同音的字目独立保留。",
      items = items)
  }

  suspend fun deletePackages(ids: List<String>) = database.withTransaction {
    require(ids.isNotEmpty() && ids.none(BuiltinSurveys::isBuiltin)) { "内置预设不能删除" }
    ids.distinct().forEach { dao.deletePackage(it) }
  }
  suspend fun clearSteps(sessionId: String, positions: List<Int>): List<String> = database.withTransaction {
    val selected = positions.distinct()
    require(selected.isNotEmpty()) { "请选择步骤" }
    val steps = selected.chunked(900).flatMap { dao.stepsByPositions(sessionId, it) }
    require(steps.size == selected.size) { "所选步骤已变化" }
    val takes = selected.chunked(900).flatMap { dao.takesByPositions(sessionId, it) }
    require(takes.none { it.state == RecordingTake.RECORDING }) { "请先停止录音" }
    val ids = takes.map { it.id }
    queueTakes(sessionId, ids)
    selected.chunked(900).forEach { dao.clearSteps(sessionId, it) }
    ids.chunked(900).forEach { dao.deleteTakes(sessionId, it) }
    dao.changeProgress(sessionId, -steps.count { it.selectedTakeId != null }, -steps.count { it.selectedTakeId == null && it.skipReason != null }, System.currentTimeMillis())
    ids
  }
  suspend fun deleteTakes(sessionId: String, ids: List<String>): List<String> = database.withTransaction {
    val selected = ids.distinct()
    require(selected.isNotEmpty()) { "请选择录音版本" }
    val takes = selected.chunked(900).flatMap { dao.takesByIds(sessionId, it) }
    require(takes.size == selected.size) { "所选录音已变化" }
    require(takes.none { it.state == RecordingTake.RECORDING }) { "请先停止录音" }
    val steps = selected.chunked(900).flatMap { dao.stepsAdopting(sessionId, it) }
    queueTakes(sessionId, selected)
    selected.chunked(900).forEach { dao.clearAdoptedTakes(sessionId, it); dao.deleteTakes(sessionId, it) }
    dao.changeProgress(sessionId, -steps.size, 0, System.currentTimeMillis())
    selected
  }
  private suspend fun queueTakes(id: String, ids: List<String>) {
    ids.chunked(1000).forEach { chunk -> dao.queueCleanup(chunk.map { RecordingCleanupJob("$id/$it", id, it) }) }
  }
  suspend fun importPackage(task: SurveyPackage): SurveyPackage = database.withTransaction {
    val json = SurveyPackageCodec.encode(task)
    val existing = dao.taskHeader(task.packageId, task.revision)
    require(existing == null || words.transferMapper().equivalent(readPackage(task.packageId, task.revision), task)) {
      "相同调查包编号和版本已存在，但内容不同；请在编辑器中另存为新调查包"
    }
    if (existing == null) dao.insertPackage(SurveyPackageEntity(task.packageId, task.revision,
      task.title, json, System.currentTimeMillis(), task.items.size, task.totalSteps, task.description, task.dialect))
    task
  }

  suspend fun inspectImport(task: SurveyPackage): PackageImportStatus = withContext(Dispatchers.IO) {
    SurveyPackageCodec.validate(task)
    RecordingTaskSnapshot.chunks(task)
    if (BuiltinSurveys.isBuiltin(task.packageId)) {
      require(task.revision in 1..BuiltinSurveys.revision(task.packageId) &&
        words.transferMapper().equivalent(readPackage(task.packageId, task.revision), task)) { "内置预设内容冲突，未覆盖本机方案" }
      return@withContext PackageImportStatus.DUPLICATE
    }
    dao.taskHeader(task.packageId, task.revision) ?: return@withContext PackageImportStatus.ADDED
    require(words.transferMapper().equivalent(readPackage(task.packageId, task.revision), task)) {
      "相同调查包编号和版本的内容冲突，未覆盖本机方案"
    }
    PackageImportStatus.DUPLICATE
  }

  suspend fun importTransferredPackage(task: SurveyPackage): PackageImportStatus = database.withTransaction {
    val status = inspectImport(task)
    if (status == PackageImportStatus.ADDED) importPackage(task)
    status
  }

  suspend fun saveRevision(draft: SurveyPackage): SurveyPackage = database.withTransaction {
    require(!BuiltinSurveys.isBuiltin(draft.packageId)) { "请先复制内置预设，再编辑副本" }
    val task = words.editable(draft).copy(revision = (dao.latestRevision(draft.packageId) ?: 0) + 1)
    RecordingTaskSnapshot.chunks(task)
    importPackage(task)
  }

  suspend fun startSession(task: SurveyPackage, alias: String, dialect: String): String = withContext(Dispatchers.IO) {
    val resolved = words.editable(task)
    createSession(resolved, alias, dialect, RecordingTaskSnapshot.chunks(resolved))
  }

  suspend fun startLibrarySession(task: SurveyPackage, alias: String, dialect: String): String = withContext(Dispatchers.IO) {
    startSession(task, alias, dialect)
  }

  private suspend fun createSession(task: SurveyPackage, alias: String, dialect: String,
    snapshots: List<String>): String = database.withTransaction {
    require(alias.length <= 80) { "称呼不能超过 80 字符" }
    require(dialect.length <= 160) { "方言不能超过 160 字符" }
    val now = System.currentTimeMillis()
    val id = UUID.randomUUID().toString()
    dao.insertSession(SurveySessionEntity(id, task.packageId, task.revision, task.title, snapshots.first(),
      alias.trim(), dialect.trim(), now, now, now, totalSteps = task.totalSteps))
    dao.insertTaskChunks(snapshots.drop(1).mapIndexed { index, json -> SessionTaskChunkEntity(id, index + 1, json) })
    task.items.mapIndexed { index, item -> SessionItemLocation(id, item.itemId, index / RecordingTaskSnapshot.CHUNK_SIZE, index % RecordingTaskSnapshot.CHUNK_SIZE) }.chunked(1000).forEach { dao.insertLocations(it) }
    val steps = task.items.flatMap { item -> (1..item.repetitions).map { item.itemId to it } }
      .mapIndexed { index, (itemId, repetition) -> SurveyStepEntity(id, index, itemId, repetition) }
    steps.chunked(1000).forEach { dao.insertSteps(it) }
    id
  }


  suspend fun beginTake(sessionId: String, position: Int): RecordingTake = database.withTransaction {
    require(sessionInfo(sessionId).currentPosition == position) { "题目已切换" }
    requireNotNull(dao.step(sessionId, position)) { "题目不存在" }
    require(dao.activeTakes(sessionId) == 0) { "请先停止录音" }
    val take = RecordingTake(UUID.randomUUID().toString(), sessionId, position, createdAt = System.currentTimeMillis())
    dao.insertTake(take.entity())
    dao.touchContent(sessionId, System.currentTimeMillis())
    take
  }
  suspend fun finishTake(take: RecordingTake) = database.withTransaction {
    require(take.state == RecordingTake.SAVED && take.durationMs > 0)
    require(dao.take(take.sessionId, take.id)?.state == RecordingTake.RECORDING) { "录音尝试已变化" }
    val step = requireNotNull(dao.step(take.sessionId, take.position)) { "题目不存在" }
    dao.updateTake(take.entity())
    changeStep(step, step.copy(selectedTakeId = take.id, skipReason = null))
  }
  suspend fun interruptTake(take: RecordingTake) = database.withTransaction {
    if (dao.interruptTake(take.id) > 0) dao.touchContent(take.sessionId, System.currentTimeMillis())
  }
  private suspend fun changeStep(old: RecordingStep, next: RecordingStep) {
    if (old == next) return
    dao.updateStep(next.entity())
    fun recorded(step: RecordingStep) = if (step.selectedTakeId != null) 1 else 0
    fun skipped(step: RecordingStep) = if (step.selectedTakeId == null && step.skipReason != null) 1 else 0
    dao.changeProgress(old.sessionId, recorded(next) - recorded(old), skipped(next) - skipped(old), System.currentTimeMillis())
  }
  suspend fun skip(id: String, position: Int) {
    val step = requireNotNull(dao.step(id, position)) { "题目不存在" }
    require(itemSnapshot(id, step.itemId).allowSkip) { "这道题不允许跳过" }
    database.withTransaction {
      val header = sessionInfo(id)
      require(header.currentPosition == position) { "题目已切换，请稍后重试" }
      val current = requireNotNull(dao.step(id, position))
      changeStep(current, current.copy(skipReason = current.skipReason.orEmpty(), selectedTakeId = null))
      dao.setPosition(id, (position + 1).coerceAtMost(header.totalSteps - 1), System.currentTimeMillis())
    }
  }
  suspend fun saveSkipReason(id: String, position: Int, reason: String) = database.withTransaction {
    require(reason.isNotBlank() && reason.length <= 300) { "请填写 1–300 字符的跳过原因" }
    val step = requireNotNull(dao.step(id, position)) { "题目不存在" }
    require(step.skipReason != null && step.selectedTakeId == null) { "本条已恢复录制，无需填写跳过原因" }
    changeStep(step, step.copy(skipReason = reason.trim()))
  }
  suspend fun saveNote(id: String, position: Int, note: String) = database.withTransaction {
    require(note.length <= 2000) { "备注不能超过 2000 字符" }
    val step = requireNotNull(dao.step(id, position)) { "题目不存在" }
    if (step.note != note) { dao.updateStep(step.copy(note = note).entity()); dao.touchContent(id, System.currentTimeMillis()) }
  }
  suspend fun selectTake(id: String, takeId: String) = database.withTransaction {
    val take = requireNotNull(dao.take(id, takeId)?.takeIf { it.state == RecordingTake.SAVED }) { "录音不可采用" }
    val step = requireNotNull(dao.step(id, take.position))
    changeStep(step, step.copy(selectedTakeId = take.id, skipReason = null))
  }
  suspend fun reviewTake(id: String, takeId: String, review: ReviewStatus) = database.withTransaction {
    val take = requireNotNull(dao.take(id, takeId)?.takeIf { it.state == RecordingTake.SAVED }) { "只能复核已保存录音" }
    if (take.reviewStatus != review.value) { check(dao.setReview(id, takeId, review.value) == 1); dao.touchContent(id, System.currentTimeMillis()) }
  }
  suspend fun setPosition(id: String, position: Int) {
    requireNotNull(dao.step(id, position)) { "题目不存在" }
    dao.setPosition(id, position, System.currentTimeMillis())
  }
  suspend fun markExported(id: String, version: Long) = dao.markExported(id, version, System.currentTimeMillis())

  suspend fun progress(id: String, status: String, after: Int): ProgressPage = withContext(Dispatchers.IO) {
    require(status in listOf("", "pending", "recorded", "skipped"))
    val header = sessionInfo(id)
    val total = when (status) { "pending" -> header.pendingSteps; "recorded" -> header.recordedSteps; "skipped" -> header.skippedSteps; else -> header.totalSteps }
    ProgressPage(dao.progressPage(id, status, after, 80).map { ProgressEntry(it, itemSnapshot(id, it.itemId).text) }, total)
  }
  suspend fun progressPositions(id: String, status: String): List<Int> {
    require(status in listOf("", "pending", "recorded", "skipped"))
    return dao.progressPositions(id, status)
  }
  suspend fun saveLocalDraft(id: String, task: SurveyPackage, original: SurveyPackage?) = withContext(Dispatchers.IO) {
    require(UUID.fromString(id).toString() == id)
    val json = SurveyDraftCodec.encode(task, original)
    database.withTransaction {
      dao.deleteDraft(id)
      dao.putDraft(SurveyDraftEntity(id, task.title, System.currentTimeMillis()))
      dao.putDraftChunks(safeChunks(json).mapIndexed { index, text -> SurveyDraftChunk(id, index, text) })
    }
  }
  suspend fun readDraft(id: String): SavedSurveyDraft = withContext(Dispatchers.IO) {
    val parts = dao.draftChunks(id)
    require(parts.isNotEmpty() && parts.map { it.chunkIndex } == parts.indices.toList()) { "草稿不存在或不完整" }
    require(parts.sumOf { it.json.toByteArray(Charsets.UTF_8).size.toLong() } <= SurveyDraftCodec.MAX_BYTES)
    SurveyDraftCodec.decode(parts.joinToString("") { it.json })
  }
  suspend fun deleteDraft(id: String) = dao.deleteDraft(id)
  suspend fun cleanupJobs() = dao.cleanupJobs().map { it.model() }
  suspend fun completeCleanup(key: String) = dao.completeCleanup(key)
  suspend fun cleanupFailed(key: String) = dao.cleanupFailed(key, "文件清理失败，可重试")
  suspend fun canClean(job: AudioCleanupJob): Boolean {
    val takeId = job.takeId
    return if (takeId == null) dao.sessionSummary(job.sessionId) == null else dao.take(job.sessionId, takeId) == null
  }
  private suspend fun migrateCleanup() {
    val store = context.getSharedPreferences("recording_cleanup", Context.MODE_PRIVATE)
    val keys = store.getStringSet("pending", emptySet()).orEmpty().toList()
    if (keys.isEmpty()) return
    val jobs = keys.mapNotNull { key ->
      val ids = key.split('/')
      if (ids.size !in 1..2 || ids.any { runCatching { UUID.fromString(it).toString() == it }.getOrDefault(false).not() }) null
      else RecordingCleanupJob(key, ids[0], ids.getOrNull(1))
    }
    database.withTransaction { dao.queueCleanup(jobs.filter { canClean(it.model()) }) }
    store.edit().remove("pending").commit()
  }
  private fun safeChunks(json: String): List<String> {
    val chunks = mutableListOf<String>()
    var start = 0
    while (start < json.length) {
      var end = (start + SLICE_CHARS).coerceAtMost(json.length)
      if (end < json.length && json[end - 1].isHighSurrogate() && json[end].isLowSurrogate()) end--
      chunks += json.substring(start, end)
      start = end
    }
    return chunks
  }
  companion object { private const val SLICE_CHARS = 256 * 1024 }
}

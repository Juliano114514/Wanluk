package com.wanluk.libroom.repository

import android.content.Context
import androidx.room.withTransaction
import com.wanluk.foundation.survey.SurveyPackage
import com.wanluk.foundation.survey.SurveyPackageCodec
import com.wanluk.foundation.survey.RecordingTaskSnapshot
import com.wanluk.libroom.AppDatabase
import com.wanluk.libroom.entity.*
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SurveyRepository(private val context: Context, private val database: AppDatabase) {
  private val dao = database.surveyDao()
  fun packages() = dao.packages()
  fun sessions() = dao.sessions()
  fun observeSession(id: String) = dao.observeSession(id)
  suspend fun session(id: String) = requireNotNull(dao.session(id)) { "录制任务不存在" }

  suspend fun updateSessionInfo(id: String, title: String, alias: String, dialect: String) {
    require(title.isNotBlank() && title.length <= 120) { "请填写 1–120 字符的录制名称" }
    require(alias.length <= 80 && dialect.length <= 160) { "称呼最多 80 字符，方言最多 160 字符" }
    check(dao.updateSessionInfo(id, title.trim(), alias.trim(), dialect.trim(), System.currentTimeMillis()) == 1) {
      "录制条目不存在"
    }
  }

  /** Foreign keys cascade to steps, takes and snapshot chunks; packages remain untouched. */
  suspend fun deleteSession(id: String) = dao.deleteSession(id)

  suspend fun taskSnapshot(id: String): SurveyPackage = withContext(Dispatchers.IO) {
    val header = requireNotNull(dao.sessionHeader(id)) { "录制任务不存在" }
    val chunks = dao.taskChunks(id)
    require(chunks.map { it.chunkIndex } == (1..chunks.size).toList()) { "字库快照不完整" }
    RecordingTaskSnapshot.restore(header.packageJson, chunks.map { it.json }).also {
      require(it.totalSteps == header.totalSteps) { "录制字目快照不完整" }
    }
  }

  suspend fun initialize() = withContext(Dispatchers.IO) {
    dao.recoverInterruptedTakes()
    val builtin = context.assets.open("surveys/starter-zh.json").use(SurveyPackageCodec::read)
    importPackage(builtin)
  }

  suspend fun importPackage(task: SurveyPackage): SurveyPackage = database.withTransaction {
    val json = SurveyPackageCodec.encode(task)
    val existing = dao.task(task.packageId, task.revision)
    require(existing == null || existing.json == json) {
      "相同调查包编号和版本已存在，但内容不同；请在编辑器中另存为新调查包"
    }
    if (existing == null) dao.insertPackage(SurveyPackageEntity(task.packageId, task.revision,
      task.title, json, System.currentTimeMillis()))
    task
  }

  suspend fun saveRevision(draft: SurveyPackage): SurveyPackage = database.withTransaction {
    val task = draft.copy(revision = (dao.latestRevision(draft.packageId) ?: 0) + 1)
    importPackage(task)
  }

  suspend fun startSession(task: SurveyPackage, alias: String, dialect: String): String = withContext(Dispatchers.IO) {
    createSession(task, alias, dialect, listOf(SurveyPackageCodec.encode(task)))
  }

  suspend fun startLibrarySession(task: SurveyPackage, alias: String, dialect: String): String = withContext(Dispatchers.IO) {
    createSession(task, alias, dialect, RecordingTaskSnapshot.chunks(task))
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
    val steps = task.items.flatMap { item -> (1..item.repetitions).map { item.itemId to it } }
      .mapIndexed { index, (itemId, repetition) -> SurveyStepEntity(id, index, itemId, repetition) }
    dao.insertSteps(steps)
    id
  }

  suspend fun beginTake(sessionId: String, position: Int): RecordingTakeEntity {
    requireNotNull(dao.step(sessionId, position)) { "题目不存在" }
    val take = RecordingTakeEntity(UUID.randomUUID().toString(), sessionId, position,
      createdAt = System.currentTimeMillis())
    dao.insertTake(take)
    return take
  }

  suspend fun finishTake(take: RecordingTakeEntity) = database.withTransaction {
    require(take.state == RecordingTakeEntity.SAVED && take.durationMs > 0)
    val step = requireNotNull(dao.step(take.sessionId, take.position))
    dao.updateTake(take)
    dao.updateStep(step.copy(selectedTakeId = take.id, skipReason = null))
    dao.refreshProgress(take.sessionId, System.currentTimeMillis())
  }

  suspend fun interruptTake(take: RecordingTakeEntity) {
    dao.interruptTake(take.id)
  }

  suspend fun skip(id: String, position: Int) = database.withTransaction {
    val header = requireNotNull(dao.sessionHeader(id)) { "录制任务不存在" }
    require(header.currentPosition == position) { "题目已切换，请稍后重试" }
    val step = requireNotNull(dao.step(id, position))
    val chunk = if (header.packageId == RecordingTaskSnapshot.LIBRARY_PACKAGE_ID)
      dao.taskChunk(id, position / RecordingTaskSnapshot.CHUNK_SIZE) else null
    val task = SurveyPackageCodec.decode(chunk ?: header.packageJson)
    require(task.items.first { it.itemId == step.itemId }.allowSkip) { "这道题不允许跳过" }
    // Empty means skipped without a supplied reason; null still means not skipped.
    dao.updateStep(step.copy(skipReason = step.skipReason.orEmpty(), selectedTakeId = null))
    dao.setPosition(id, (position + 1).coerceAtMost(header.totalSteps - 1), System.currentTimeMillis())
    dao.refreshProgress(id, System.currentTimeMillis())
  }

  suspend fun saveSkipReason(id: String, position: Int, reason: String) = database.withTransaction {
    require(reason.isNotBlank() && reason.length <= 300) { "请填写 1–300 字符的跳过原因" }
    val step = requireNotNull(dao.step(id, position)) { "题目不存在" }
    require(step.skipReason != null && step.selectedTakeId == null) { "本条已恢复录制，无需填写跳过原因" }
    dao.updateStep(step.copy(skipReason = reason.trim()))
    dao.refreshProgress(id, System.currentTimeMillis())
  }

  suspend fun saveNote(id: String, position: Int, note: String) = database.withTransaction {
    require(note.length <= 2000) { "备注不能超过 2000 字符" }
    dao.updateStep(requireNotNull(dao.step(id, position)).copy(note = note))
    dao.refreshProgress(id, System.currentTimeMillis())
  }

  suspend fun selectTake(id: String, takeId: String) = database.withTransaction {
    val take = session(id).takes.first { it.id == takeId && it.state == RecordingTakeEntity.SAVED }
    val step = requireNotNull(dao.step(id, take.position))
    dao.updateStep(step.copy(selectedTakeId = take.id, skipReason = null))
    dao.refreshProgress(id, System.currentTimeMillis())
  }

  suspend fun setPosition(id: String, position: Int) {
    requireNotNull(dao.step(id, position)) { "题目不存在" }
    dao.setPosition(id, position, System.currentTimeMillis())
  }

  suspend fun markExported(id: String) = dao.markExported(id, System.currentTimeMillis())
}

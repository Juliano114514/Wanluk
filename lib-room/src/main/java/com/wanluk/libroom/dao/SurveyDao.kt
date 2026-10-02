package com.wanluk.libroom.dao

import androidx.room.*
import com.wanluk.libroom.entity.*
import com.wanluk.foundation.survey.*
import kotlinx.coroutines.flow.Flow

private const val SESSION_COLUMNS = "id, packageId, revision, title, speakerAlias, dialect, consentConfirmedAt, createdAt, updatedAt, currentPosition, completedSteps, totalSteps, exportedAt, recordedSteps, skippedSteps, contentRevision, exportedContentRevision, researchCode, collectionLocation, collector"
private const val STEP_FILTER = " WHERE sessionId = :id AND (:status = '' OR (:status = 'recorded' AND selectedTakeId IS NOT NULL) OR (:status = 'skipped' AND selectedTakeId IS NULL AND skipReason IS NOT NULL) OR (:status = 'pending' AND selectedTakeId IS NULL AND skipReason IS NULL))"

@Dao
interface SurveyDao {
  @Query("SELECT * FROM builtin_assets WHERE name = :name") suspend fun asset(name: String): BuiltinAssetEntity?
  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveAsset(asset: BuiltinAssetEntity)
  @Query("SELECT packageId, revision, title, itemCount, totalSteps, description, dialect FROM survey_packages p WHERE itemCount IS NOT NULL AND totalSteps IS NOT NULL AND revision = (SELECT MAX(revision) FROM survey_packages q WHERE q.packageId = p.packageId) ORDER BY createdAt DESC")
  fun packages(): Flow<List<SurveyPackageSummary>>
  @Query("SELECT packageId, revision, length(json) AS jsonChars FROM survey_packages WHERE itemCount IS NULL OR totalSteps IS NULL")
  suspend fun missingSummaries(): List<SurveyPackageHeader>
  @Query("UPDATE survey_packages SET title = :title, itemCount = :count, totalSteps = :steps, description = :description, dialect = :dialect WHERE packageId = :id AND revision = :revision")
  suspend fun saveSummary(id: String, revision: Int, title: String, count: Int, steps: Int, description: String, dialect: String)
  @Query("SELECT packageId, revision, length(json) AS jsonChars FROM survey_packages WHERE packageId = :id AND revision = :revision") suspend fun taskHeader(id: String, revision: Int): SurveyPackageHeader?
  @Query("SELECT substr(json, :offset, :length) FROM survey_packages WHERE packageId = :id AND revision = :revision") suspend fun taskSlice(id: String, revision: Int, offset: Int, length: Int): String?
  @Query("SELECT MAX(revision) FROM survey_packages WHERE packageId = :id") suspend fun latestRevision(id: String): Int?
  @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertPackage(task: SurveyPackageEntity)
  @Query("DELETE FROM survey_packages WHERE packageId = :id") suspend fun deletePackage(id: String)

  @Query("SELECT " + SESSION_COLUMNS + " FROM survey_sessions ORDER BY updatedAt DESC") fun sessions(): Flow<List<SessionSummary>>
  @Query("SELECT " + SESSION_COLUMNS + " FROM survey_sessions WHERE id = :id") fun observeSessionSummary(id: String): Flow<SessionSummary?>
  @Query("SELECT " + SESSION_COLUMNS + " FROM survey_sessions WHERE id = :id") suspend fun sessionSummary(id: String): SessionSummary?
  @Insert suspend fun insertSession(session: SurveySessionEntity)
  @Query("SELECT id, length(packageJson) AS jsonChars, totalSteps FROM survey_sessions WHERE id = :id") suspend fun snapshotHeader(id: String): SessionSnapshotHeader?
  @Query("SELECT substr(packageJson, :offset, :length) FROM survey_sessions WHERE id = :id") suspend fun snapshotSlice(id: String, offset: Int, length: Int): String?
  @Query("UPDATE survey_sessions SET packageJson = :json WHERE id = :id") suspend fun replaceSnapshotHeader(id: String, json: String)
  @Insert suspend fun insertTaskChunks(chunks: List<SessionTaskChunkEntity>)
  @Query("SELECT chunkIndex, length(json) AS jsonChars FROM session_task_chunks WHERE sessionId = :id ORDER BY chunkIndex") suspend fun taskChunkHeaders(id: String): List<SessionTaskChunkHeader>
  @Query("SELECT length(json) FROM session_task_chunks WHERE sessionId = :id AND chunkIndex = :index") suspend fun taskChunkLength(id: String, index: Int): Int?
  @Query("SELECT substr(json, :offset, :length) FROM session_task_chunks WHERE sessionId = :id AND chunkIndex = :index") suspend fun taskChunkSlice(id: String, index: Int, offset: Int, length: Int): String?
  @Query("DELETE FROM session_task_chunks WHERE sessionId = :id") suspend fun deleteTaskChunks(id: String)
  @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertLocations(rows: List<SessionItemLocation>)
  @Query("SELECT * FROM session_item_locations WHERE sessionId = :id AND itemId = :itemId") suspend fun location(id: String, itemId: String): SessionItemLocation?
  @Query("DELETE FROM session_item_locations WHERE sessionId = :id") suspend fun deleteLocations(id: String)
  @Query("UPDATE survey_sessions SET title = :title, speakerAlias = :alias, dialect = :dialect, researchCode = :researchCode, collectionLocation = :location, collector = :collector, updatedAt = :now, contentRevision = contentRevision + 1, exportedAt = NULL, exportedContentRevision = NULL WHERE id = :id")
  suspend fun updateSessionInfo(id: String, title: String, alias: String, dialect: String, researchCode: String, location: String, collector: String, now: Long): Int
  @Query("DELETE FROM survey_sessions WHERE id = :id") suspend fun deleteSession(id: String): Int
  @Query("UPDATE survey_sessions SET completedSteps = completedSteps + :recorded + :skipped, recordedSteps = recordedSteps + :recorded, skippedSteps = skippedSteps + :skipped, updatedAt = :now, contentRevision = contentRevision + 1, exportedAt = NULL, exportedContentRevision = NULL WHERE id = :id")
  suspend fun changeProgress(id: String, recorded: Int, skipped: Int, now: Long)
  @Query("UPDATE survey_sessions SET updatedAt = :now, contentRevision = contentRevision + 1, exportedAt = NULL, exportedContentRevision = NULL WHERE id = :id") suspend fun touchContent(id: String, now: Long)
  @Query("UPDATE survey_sessions SET currentPosition = :position, updatedAt = :now WHERE id = :id") suspend fun setPosition(id: String, position: Int, now: Long)
  @Query("UPDATE survey_sessions SET exportedAt = :now, exportedContentRevision = :version WHERE id = :id AND contentRevision = :version") suspend fun markExported(id: String, version: Long, now: Long): Int

  @Insert suspend fun insertSteps(steps: List<SurveyStepEntity>)
  @Query("SELECT * FROM survey_steps WHERE sessionId = :sessionId AND position = :position") suspend fun step(sessionId: String, position: Int): RecordingStep?
  @Query("SELECT * FROM survey_steps WHERE sessionId = :sessionId AND position = :position") fun observeStep(sessionId: String, position: Int): Flow<RecordingStep?>
  @Query("SELECT * FROM survey_steps WHERE sessionId = :id ORDER BY position") suspend fun allSteps(id: String): List<RecordingStep>
  @Query("SELECT * FROM survey_steps" + STEP_FILTER + " AND position > :after ORDER BY position LIMIT :limit") suspend fun progressPage(id: String, status: String, after: Int, limit: Int): List<RecordingStep>
  @Query("SELECT COUNT(*) FROM survey_steps" + STEP_FILTER) suspend fun progressCount(id: String, status: String): Int
  @Query("SELECT position FROM survey_steps" + STEP_FILTER + " ORDER BY position") suspend fun progressPositions(id: String, status: String): List<Int>
  @Query("SELECT * FROM survey_steps WHERE sessionId = :id AND position IN (:positions)") suspend fun stepsByPositions(id: String, positions: List<Int>): List<RecordingStep>
  @Query("SELECT * FROM survey_steps WHERE sessionId = :id AND selectedTakeId IN (:ids)") suspend fun stepsAdopting(id: String, ids: List<String>): List<RecordingStep>
  @Update suspend fun updateStep(step: SurveyStepEntity)
  @Insert suspend fun insertTake(take: RecordingTakeEntity)
  @Update suspend fun updateTake(take: RecordingTakeEntity)
  @Query("SELECT * FROM recording_takes WHERE sessionId = :id AND position = :position ORDER BY createdAt DESC") suspend fun currentTakes(id: String, position: Int): List<RecordingTake>
  @Query("SELECT * FROM recording_takes WHERE sessionId = :id ORDER BY position, createdAt") suspend fun allTakes(id: String): List<RecordingTake>
  @Query("SELECT * FROM recording_takes WHERE sessionId = :id AND id = :takeId") suspend fun take(id: String, takeId: String): RecordingTake?
  @Query("SELECT * FROM recording_takes WHERE sessionId = :id AND id IN (:ids)") suspend fun takesByIds(id: String, ids: List<String>): List<RecordingTake>
  @Query("SELECT * FROM recording_takes WHERE sessionId = :id AND position IN (:positions)") suspend fun takesByPositions(id: String, positions: List<Int>): List<RecordingTake>
  @Query("SELECT COUNT(*) FROM recording_takes WHERE sessionId = :id AND state = 'recording'") suspend fun activeTakes(id: String): Int
  @Query("DELETE FROM recording_takes WHERE sessionId = :sessionId AND id IN (:ids)") suspend fun deleteTakes(sessionId: String, ids: List<String>)
  @Query("UPDATE survey_steps SET selectedTakeId = NULL, skipReason = NULL WHERE sessionId = :sessionId AND position IN (:positions)") suspend fun clearSteps(sessionId: String, positions: List<Int>)
  @Query("UPDATE survey_steps SET selectedTakeId = NULL, skipReason = NULL WHERE sessionId = :sessionId AND selectedTakeId IN (:ids)") suspend fun clearAdoptedTakes(sessionId: String, ids: List<String>)
  @Query("UPDATE recording_takes SET state = 'interrupted', warning = '本次录制未完成，原有录音仍保留' WHERE id = :id AND state = 'recording'") suspend fun interruptTake(id: String): Int
  @Query("UPDATE survey_sessions SET updatedAt = :now, contentRevision = contentRevision + 1, exportedAt = NULL, exportedContentRevision = NULL WHERE id IN (SELECT sessionId FROM recording_takes WHERE state = 'recording')") suspend fun touchInterruptedSessions(now: Long)
  @Query("UPDATE recording_takes SET state = 'interrupted', warning = '上次录制中断，未作为完成录音采用' WHERE state = 'recording'") suspend fun recoverInterruptedTakes()
  @Query("UPDATE recording_takes SET reviewStatus = :review WHERE sessionId = :id AND id = :takeId AND state = 'saved'") suspend fun setReview(id: String, takeId: String, review: String): Int

  @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun queueCleanup(jobs: List<RecordingCleanupJob>)
  @Query("SELECT * FROM recording_cleanup_jobs ORDER BY `key`") suspend fun cleanupJobs(): List<RecordingCleanupJob>
  @Query("SELECT COUNT(*) FROM recording_cleanup_jobs") fun cleanupCount(): Flow<Int>
  @Query("DELETE FROM recording_cleanup_jobs WHERE `key` = :key") suspend fun completeCleanup(key: String)
  @Query("UPDATE recording_cleanup_jobs SET attempts = attempts + 1, lastError = :error WHERE `key` = :key") suspend fun cleanupFailed(key: String, error: String)

  @Query("SELECT id, title, updatedAt FROM survey_drafts ORDER BY updatedAt DESC") fun drafts(): Flow<List<DraftSummary>>
  @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putDraft(draft: SurveyDraftEntity)
  @Insert suspend fun putDraftChunks(chunks: List<SurveyDraftChunk>)
  @Query("SELECT * FROM survey_draft_chunks WHERE draftId = :id ORDER BY chunkIndex") suspend fun draftChunks(id: String): List<SurveyDraftChunk>
  @Query("DELETE FROM survey_drafts WHERE id = :id") suspend fun deleteDraft(id: String)
}

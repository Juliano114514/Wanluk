package com.wanluk.libroom.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.wanluk.libroom.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SurveyDao {
  @Query("SELECT * FROM builtin_assets WHERE name = :name")
  suspend fun asset(name: String): BuiltinAssetEntity?
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun saveAsset(asset: BuiltinAssetEntity)

  @Query("SELECT p.* FROM survey_packages p WHERE p.revision = (SELECT MAX(q.revision) FROM survey_packages q WHERE q.packageId = p.packageId) ORDER BY p.createdAt DESC")
  fun packages(): Flow<List<SurveyPackageEntity>>
  @Query("SELECT * FROM survey_packages WHERE packageId = :id AND revision = :revision")
  suspend fun task(id: String, revision: Int): SurveyPackageEntity?
  @Query("SELECT MAX(revision) FROM survey_packages WHERE packageId = :id")
  suspend fun latestRevision(id: String): Int?
  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertPackage(task: SurveyPackageEntity)

  @Query("SELECT * FROM survey_sessions ORDER BY updatedAt DESC")
  fun sessions(): Flow<List<SurveySessionEntity>>
  @Transaction
  @Query("SELECT * FROM survey_sessions WHERE id = :id")
  fun observeSession(id: String): Flow<SurveySessionDetail?>
  @Transaction
  @Query("SELECT * FROM survey_sessions WHERE id = :id")
  suspend fun session(id: String): SurveySessionDetail?
  @Insert
  suspend fun insertSession(session: SurveySessionEntity)
  @Insert
  suspend fun insertTaskChunks(chunks: List<SessionTaskChunkEntity>)
  @Query("SELECT * FROM session_task_chunks WHERE sessionId = :id ORDER BY chunkIndex")
  suspend fun taskChunks(id: String): List<SessionTaskChunkEntity>
  @Query("SELECT json FROM session_task_chunks WHERE sessionId = :id AND chunkIndex = :index")
  suspend fun taskChunk(id: String, index: Int): String?
  @Query("SELECT * FROM survey_sessions WHERE id = :id")
  suspend fun sessionHeader(id: String): SurveySessionEntity?
  @Query("UPDATE survey_sessions SET title = :title, speakerAlias = :alias, dialect = :dialect, updatedAt = :now, exportedAt = NULL WHERE id = :id")
  suspend fun updateSessionInfo(id: String, title: String, alias: String, dialect: String, now: Long): Int
  @Query("DELETE FROM survey_sessions WHERE id = :id")
  suspend fun deleteSession(id: String)
  @Insert
  suspend fun insertSteps(steps: List<SurveyStepEntity>)
  @Query("SELECT * FROM survey_steps WHERE sessionId = :sessionId AND position = :position")
  suspend fun step(sessionId: String, position: Int): SurveyStepEntity?
  @Update
  suspend fun updateStep(step: SurveyStepEntity)
  @Insert
  suspend fun insertTake(take: RecordingTakeEntity)
  @Update
  suspend fun updateTake(take: RecordingTakeEntity)

  @Query("UPDATE recording_takes SET state = 'interrupted', warning = '本次录制未完成，原有录音仍保留' WHERE id = :id AND state = 'recording'")
  suspend fun interruptTake(id: String)

  @Query("UPDATE recording_takes SET state = 'interrupted', warning = '上次录制中断，未作为完成录音采用' WHERE state = 'recording'")
  suspend fun recoverInterruptedTakes()
  @Query("UPDATE survey_sessions SET completedSteps = (SELECT COUNT(*) FROM survey_steps WHERE sessionId = :id AND (selectedTakeId IS NOT NULL OR skipReason IS NOT NULL)), updatedAt = :now, exportedAt = NULL WHERE id = :id")
  suspend fun refreshProgress(id: String, now: Long)
  @Query("UPDATE survey_sessions SET currentPosition = :position, updatedAt = :now WHERE id = :id")
  suspend fun setPosition(id: String, position: Int, now: Long)
  @Query("UPDATE survey_sessions SET exportedAt = :now WHERE id = :id")
  suspend fun markExported(id: String, now: Long)
}

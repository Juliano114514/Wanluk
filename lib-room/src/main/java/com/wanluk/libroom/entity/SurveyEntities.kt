package com.wanluk.libroom.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import androidx.room.ColumnInfo
import com.wanluk.foundation.survey.*

@Entity(tableName = "builtin_assets")
data class BuiltinAssetEntity(@PrimaryKey val name: String, val sha256: String)

@Entity(tableName = "survey_packages", primaryKeys = ["packageId", "revision"])
data class SurveyPackageEntity(
  val packageId: String,
  val revision: Int,
  val title: String,
  val json: String,
  val createdAt: Long,
  val itemCount: Int? = null,
  val totalSteps: Int? = null,
  @ColumnInfo(defaultValue = "''") val description: String = "",
  @ColumnInfo(defaultValue = "''") val dialect: String = "",
)

/** Small query projection; large JSON values are read in bounded slices. */
data class SurveyPackageHeader(val packageId: String, val revision: Int, val jsonChars: Int)

@Entity(tableName = "survey_sessions", indices = [Index("updatedAt")])
data class SurveySessionEntity(
  @PrimaryKey val id: String,
  val packageId: String,
  val revision: Int,
  val title: String,
  val packageJson: String,
  val speakerAlias: String,
  val dialect: String,
  val consentConfirmedAt: Long,
  val createdAt: Long,
  val updatedAt: Long,
  val currentPosition: Int = 0,
  val completedSteps: Int = 0,
  val totalSteps: Int,
  val exportedAt: Long? = null,
  @ColumnInfo(defaultValue = "0") val recordedSteps: Int = 0,
  @ColumnInfo(defaultValue = "0") val skippedSteps: Int = 0,
  @ColumnInfo(defaultValue = "0") val contentRevision: Long = 0,
  val exportedContentRevision: Long? = null,
  @ColumnInfo(defaultValue = "''") val researchCode: String = "",
  @ColumnInfo(defaultValue = "''") val collectionLocation: String = "",
  @ColumnInfo(defaultValue = "''") val collector: String = "",
)

@Entity(
  tableName = "survey_steps", primaryKeys = ["sessionId", "position"],
  foreignKeys = [ForeignKey(entity = SurveySessionEntity::class, parentColumns = ["id"],
    childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)],
  indices = [Index("sessionId")],
)
data class SurveyStepEntity(
  val sessionId: String,
  val position: Int,
  val itemId: String,
  val repetition: Int,
  val selectedTakeId: String? = null,
  val skipReason: String? = null,
  val note: String = "",
)

@Entity(
  tableName = "recording_takes",
  foreignKeys = [ForeignKey(entity = SurveySessionEntity::class, parentColumns = ["id"],
    childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)],
  indices = [Index(value = ["sessionId", "position"]), Index(value = ["sessionId", "position", "createdAt"]), Index(value = ["sessionId", "state"])],
)
data class RecordingTakeEntity(
  @PrimaryKey val id: String,
  val sessionId: String,
  val position: Int,
  val state: String = RECORDING,
  val createdAt: Long,
  val durationMs: Long = 0,
  val sampleRate: Int = 48000,
  val channels: Int = 1,
  val bitsPerSample: Int = 16,
  val audioSource: String = "",
  val inputDevice: String = "",
  val peak: Double = 0.0,
  val rms: Double = 0.0,
  val clippedFraction: Double = 0.0,
  val warning: String = "",
  @ColumnInfo(defaultValue = "'unreviewed'") val reviewStatus: String = "unreviewed",
  @ColumnInfo(defaultValue = "1.0") val appliedGain: Double = 1.0,
) {
  companion object {
    const val RECORDING = "recording"
    const val SAVED = "saved"
    const val INTERRUPTED = "interrupted"
  }
}

data class SurveySessionDetail(
  @Embedded val session: SurveySessionEntity,
  @Relation(parentColumn = "id", entityColumn = "sessionId") val steps: List<SurveyStepEntity>,
  @Relation(parentColumn = "id", entityColumn = "sessionId") val takes: List<RecordingTakeEntity>,
)

@Entity(
  tableName = "session_task_chunks", primaryKeys = ["sessionId", "chunkIndex"],
  foreignKeys = [ForeignKey(entity = SurveySessionEntity::class, parentColumns = ["id"],
    childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)],
  indices = [Index("sessionId")],
)
data class SessionTaskChunkEntity(val sessionId: String, val chunkIndex: Int, val json: String)

@Entity(tableName = "session_item_locations", primaryKeys = ["sessionId", "itemId"],
  foreignKeys = [ForeignKey(entity = SurveySessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)],
  indices = [Index("sessionId")])
data class SessionItemLocation(val sessionId: String, val itemId: String, val chunkIndex: Int, val itemIndex: Int)

@Entity(tableName = "recording_cleanup_jobs")
data class RecordingCleanupJob(@PrimaryKey val key: String, val sessionId: String, val takeId: String? = null,
  val attempts: Int = 0, val lastError: String = "")

@Entity(tableName = "survey_drafts")
data class SurveyDraftEntity(@PrimaryKey val id: String, val title: String, val updatedAt: Long)

@Entity(tableName = "survey_draft_chunks", primaryKeys = ["draftId", "chunkIndex"],
  foreignKeys = [ForeignKey(entity = SurveyDraftEntity::class, parentColumns = ["id"], childColumns = ["draftId"], onDelete = ForeignKey.CASCADE)],
  indices = [Index("draftId")])
data class SurveyDraftChunk(val draftId: String, val chunkIndex: Int, val json: String)

@Entity(tableName = "word_favorites")
data class WordFavoriteEntity(@PrimaryKey val sourceKey: String, val createdAt: Long)

data class SessionTaskChunkHeader(val chunkIndex: Int, val jsonChars: Int)

data class SessionSnapshotHeader(val id: String, val jsonChars: Int, val totalSteps: Int)

internal fun RecordingTakeEntity.model() = RecordingTake(id, sessionId, position, state, createdAt, durationMs,
  sampleRate, channels, bitsPerSample, audioSource, inputDevice, peak, rms, clippedFraction, warning, reviewStatus, appliedGain)
internal fun RecordingTake.entity() = RecordingTakeEntity(id, sessionId, position, state, createdAt, durationMs,
  sampleRate, channels, bitsPerSample, audioSource, inputDevice, peak, rms, clippedFraction, warning, reviewStatus, appliedGain)
internal fun RecordingStep.entity() = SurveyStepEntity(sessionId, position, itemId, repetition, selectedTakeId, skipReason, note)

internal fun RecordingCleanupJob.model() = AudioCleanupJob(key, sessionId, takeId, attempts, lastError)

package com.wanluk.libroom.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "builtin_assets")
data class BuiltinAssetEntity(@PrimaryKey val name: String, val sha256: String)

@Entity(tableName = "survey_packages", primaryKeys = ["packageId", "revision"])
data class SurveyPackageEntity(
  val packageId: String,
  val revision: Int,
  val title: String,
  val json: String,
  val createdAt: Long,
)

@Entity(tableName = "survey_sessions")
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
  indices = [Index(value = ["sessionId", "position"])],
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

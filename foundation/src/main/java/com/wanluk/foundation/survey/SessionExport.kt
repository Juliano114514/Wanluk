package com.wanluk.foundation.survey

data class SessionExport(
  val exportSchemaVersion: Int = 3,
  val sessionId: String,
  val speakerAlias: String,
  val dialect: String,
  val consentConfirmedAt: Long,
  val createdAt: Long,
  val exportedAt: Long,
  val task: SurveyPackage,
  val steps: List<ExportStep>,
  val takes: List<ExportTake>,
  val title: String = task.title,
  val contentRevision: Long = 0,
  val researchCode: String = "",
  val collectionLocation: String = "",
  val collector: String = "",
  val planType: RecordingPlanType = task.planType,
)

data class ExportStep(
  val position: Int,
  val itemId: String,
  val repetition: Int,
  val text: String,
  val type: String,
  val selectedTakeId: String?,
  val skipReason: String?,
  val note: String,
)

data class ExportTake(
  val takeId: String,
  val position: Int,
  val state: String,
  val createdAt: Long,
  val durationMs: Long,
  val sampleRate: Int,
  val channels: Int,
  val bitsPerSample: Int,
  val audioSource: String,
  val inputDevice: String,
  val peak: Double,
  val rms: Double,
  val clippedFraction: Double,
  val warning: String,
  val reviewStatus: String = ReviewStatus.UNREVIEWED.value,
  val audioFile: String = "",
  val appliedGain: Double = 1.0,
)

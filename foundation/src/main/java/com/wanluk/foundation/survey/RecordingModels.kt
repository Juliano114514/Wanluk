package com.wanluk.foundation.survey

/** List/current-recording projections intentionally contain no snapshot JSON. */
data class SessionSummary(
  val id: String, val packageId: String, val revision: Int, val title: String,
  val speakerAlias: String, val dialect: String, val consentConfirmedAt: Long,
  val createdAt: Long, val updatedAt: Long, val currentPosition: Int,
  val completedSteps: Int, val totalSteps: Int, val exportedAt: Long?,
  val recordedSteps: Int = 0, val skippedSteps: Int = 0,
  val contentRevision: Long = 0, val exportedContentRevision: Long? = null,
  val researchCode: String = "", val collectionLocation: String = "", val collector: String = "",
) {
  val pendingSteps: Int get() = (totalSteps - recordedSteps - skippedSteps).coerceAtLeast(0)
  val needsExport: Boolean get() = exportedContentRevision != contentRevision
}

data class RecordingStep(
  val sessionId: String, val position: Int, val itemId: String, val repetition: Int,
  val selectedTakeId: String? = null, val skipReason: String? = null, val note: String = "",
) {
  val status: String get() = when { selectedTakeId != null -> "recorded"; skipReason != null -> "skipped"; else -> "pending" }
}

enum class ReviewStatus(val value: String, val label: String) {
  UNREVIEWED("unreviewed", "未复核"), ACCEPTED("accepted", "通过"), NEEDS_RERECORD("needs_rerecord", "需重录");
}

data class RecordingTake(
  val id: String, val sessionId: String, val position: Int, val state: String = RECORDING,
  val createdAt: Long, val durationMs: Long = 0, val sampleRate: Int = 48000,
  val channels: Int = 1, val bitsPerSample: Int = 16, val audioSource: String = "",
  val inputDevice: String = "", val peak: Double = 0.0, val rms: Double = 0.0,
  val clippedFraction: Double = 0.0, val warning: String = "",
  val reviewStatus: String = ReviewStatus.UNREVIEWED.value,
  val appliedGain: Double = 1.0,
) {
  val review: ReviewStatus get() = ReviewStatus.entries.firstOrNull { it.value == reviewStatus } ?: ReviewStatus.UNREVIEWED
  companion object { const val RECORDING = "recording"; const val SAVED = "saved"; const val INTERRUPTED = "interrupted" }
}

data class CurrentRecording(val session: SessionSummary, val step: RecordingStep, val item: SurveyItem, val takes: List<RecordingTake>,
  val planType: RecordingPlanType = RecordingPlanType.CHARACTER)
data class SessionContents(val session: SessionSummary, val steps: List<RecordingStep>, val takes: List<RecordingTake>)
data class ProgressEntry(val step: RecordingStep, val text: String)
data class ProgressPage(val entries: List<ProgressEntry> = emptyList(), val total: Int = 0)
data class DraftSummary(val id: String, val title: String, val updatedAt: Long)
data class SavedSurveyDraft(val task: SurveyPackage, val original: SurveyPackage?)

data class WordEntry(
  val id: Long, val sheng: String, val hu: String, val deng: Int, val yun: String,
  val diao: String, val zu: String?, val she: String, val coreChar: String,
  val phrases: String?, val remark: String?, val rarity: Int, val sourceId: String?,
  val polyphonic: Boolean? = null, val favorite: Boolean = false,
)

data class WordFacet(val field: String, val value: String)

/** Durable cleanup projection; UI never writes database job entities. */
data class AudioCleanupJob(val key: String, val sessionId: String, val takeId: String?, val attempts: Int, val lastError: String)

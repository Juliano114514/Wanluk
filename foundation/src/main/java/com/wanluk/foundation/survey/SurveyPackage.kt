package com.wanluk.foundation.survey

import java.util.UUID
import com.google.gson.annotations.SerializedName

/** Portable task content; no local database IDs or executable expressions. */
data class SurveyPackage(
  val schemaVersion: Int = 3,
  val packageId: String = UUID.randomUUID().toString(),
  val revision: Int = 1,
  val title: String = "新调查包",
  val description: String = "",
  val languageTag: String = "zh",
  val dialect: String = "",
  val recordingProfile: String = RECORDING_PROFILE,
  val items: List<SurveyItem> = emptyList(),
  val defaults: SurveyDefaults? = SurveyDefaults(),
  val planType: RecordingPlanType = RecordingPlanType.CHARACTER,
) {
  val totalSteps: Int get() = items.sumOf { it.extInfo?.repetitions ?: defaults?.repetitions ?: it.repetitions }

  companion object {
    const val RECORDING_PROFILE = "wav-pcm16-48000-mono"
    const val MAX_ITEMS = 20_000
    const val MAX_STEPS = 100_000
  }
}

enum class RecordingPlanType(val value: String, val label: String, val maxSeconds: Int, val maxTextLength: Int) {
  @SerializedName("character") CHARACTER("character", "字目", 120, 500),
  @SerializedName("word") WORD("word", "词汇", 120, 500),
  @SerializedName("passage") PASSAGE("passage", "文段", 600, 10_000);

  companion object {
    fun fromValue(value: String): RecordingPlanType =
      requireNotNull(entries.firstOrNull { it.value == value }) { "方案分类须为字目、词汇或文段" }

    fun legacy(packageId: String, displayType: String?): RecordingPlanType =
      if (packageId in listOf(BuiltinSurveys.STARTER_ID, BuiltinSurveys.SIMPLE_ID) || displayType == "word") WORD else CHARACTER
  }
}

enum class SurveyItemType(val value: String, val label: String) {
  CHARACTER("character", "单字"),
  WORD("word", "词语"),
  SENTENCE("sentence", "句子");
}

data class SurveyItem(
  val itemId: String = "ex_${UUID.randomUUID()}",
  val type: String = SurveyItemType.WORD.value,
  val text: String = "",
  val instruction: String = "请用你平时的家乡话说出屏幕上的内容。",
  val repetitions: Int = 1,
  val allowSkip: Boolean = true,
  val phonology: ChinesePhonology? = null,
  /** Portable word identity, distinct from the recording question's instance ID. */
  val wordId: String? = null,
  val extInfo: ItemExtInfo? = null,
)

data class SurveyDefaults(
  val displayType: String = SurveyItemType.CHARACTER.value,
  /** null inherits the protocol's type-specific prompt; empty explicitly hides the prompt. */
  val instruction: String? = null,
  val repetitions: Int = 1,
  val allowSkip: Boolean = true,
)

data class ItemExtInfo(
  val displayType: String? = null,
  val instruction: String? = null,
  val repetitions: Int? = null,
  val allowSkip: Boolean? = null,
  val note: String = "",
) {
  fun compact(): ItemExtInfo? = takeUnless { it == ItemExtInfo() }
}

data class SurveyPackageSummary(val packageId: String, val revision: Int, val title: String,
  val itemCount: Int, val totalSteps: Int, val description: String, val dialect: String)

/** Optional historical Chinese annotations; other languages need not supply them. */
data class ChinesePhonology(
  val sheng: String = "",
  val hu: String = "",
  val deng: Int? = null,
  val yun: String = "",
  val diao: String = "",
  val she: String = "",
  val zu: String = "",
  val sourceId: String = "",
)

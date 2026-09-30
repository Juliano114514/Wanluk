package com.wanluk.foundation.survey

import java.util.UUID

/** Portable task content; no local database IDs or executable expressions. */
data class SurveyPackage(
  val schemaVersion: Int = 1,
  val packageId: String = UUID.randomUUID().toString(),
  val revision: Int = 1,
  val title: String = "新调查包",
  val description: String = "",
  val languageTag: String = "zh",
  val dialect: String = "",
  val recordingProfile: String = RECORDING_PROFILE,
  val items: List<SurveyItem> = emptyList(),
) {
  val totalSteps: Int get() = items.sumOf { it.repetitions }

  companion object {
    const val RECORDING_PROFILE = "wav-pcm16-48000-mono"
    const val MAX_ITEMS = 500
    const val MAX_STEPS = 1500
  }
}

enum class SurveyItemType(val value: String, val label: String) {
  CHARACTER("character", "单字"),
  WORD("word", "词语"),
  SENTENCE("sentence", "句子");
}

data class SurveyItem(
  val itemId: String = UUID.randomUUID().toString(),
  val type: String = SurveyItemType.WORD.value,
  val text: String = "",
  val instruction: String = "请用你平时的家乡话说出屏幕上的内容。",
  val repetitions: Int = 1,
  val allowSkip: Boolean = true,
  val phonology: ChinesePhonology? = null,
)

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

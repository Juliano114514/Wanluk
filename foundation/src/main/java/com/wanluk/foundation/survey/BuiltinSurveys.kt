package com.wanluk.foundation.survey

import java.util.UUID

object BuiltinSurveys {
  const val SIMPLE_ID = "wanluk-simple-recording"
  const val SIMPLE_PROMPT = "用你的方言怎么说？"
  const val STARTER_ID = "wanluk-starter-zh"
  const val SHANCUN_ID = "wanluk-shancun-yonghuai"
  const val GUO_ID = "wanluk-guo-moruo-mei-chuxi"
  const val DONG_ID = "wanluk-dong-rime-characters"
  const val PASSAGE_PROMPT = "请用方言读出下面的文段。"

  val orderedIds = listOf(SHANCUN_ID, STARTER_ID, SIMPLE_ID, GUO_ID, DONG_ID, RecordingTaskSnapshot.LIBRARY_PACKAGE_ID)
  fun isBuiltin(id: String): Boolean = id in orderedIds
  fun rank(id: String): Int = orderedIds.indexOf(id).takeIf { it >= 0 } ?: orderedIds.size
  fun revision(id: String): Int = if (id in listOf(STARTER_ID, SIMPLE_ID, RecordingTaskSnapshot.LIBRARY_PACKAGE_ID)) 3 else 2

  fun title(id: String, itemCount: Int? = null, revision: Int = revision(id)): String {
    if (revision < revision(id)) return when (id) {
      SHANCUN_ID -> "山村咏怀 · 约${hanziCount(POEM)}字"
      STARTER_ID -> "日常生活30词"
      SIMPLE_ID -> "斯瓦迪士核心词"
      GUO_ID -> "郭沫若没出息 · 约${hanziCount(GUO_PASSAGE)}字"
      DONG_ID -> "所有东韵的字"
      RecordingTaskSnapshot.LIBRARY_PACKAGE_ID -> if (revision == 1) "默认方案 · 完整字库" else "全量录制"
      else -> error("不是内置预设")
    }
    return when (id) {
      SHANCUN_ID -> "山村咏怀·${hanziCount(POEM)}字"
      STARTER_ID -> "日常用词·30词"
      SIMPLE_ID -> "斯瓦迪士核心词·100词"
      GUO_ID -> "郭沫若没出息·约${hanziCount(GUO_PASSAGE)}字"
      DONG_ID -> "平水韵·东韵·${itemCount ?: 392}字"
      RecordingTaskSnapshot.LIBRARY_PACKAGE_ID -> "全量录制·约${itemCount ?: 10291}字"
      else -> error("不是内置预设")
    }
  }

  fun subtitle(id: String, itemCount: Int): String? = when (id) {
    SHANCUN_ID -> "文段 · 整篇录制 · ${hanziCount(POEM)}字"
    STARTER_ID -> "词汇 · 30词"
    SIMPLE_ID -> "$SIMPLE_PROMPT · 100词"
    GUO_ID -> "文段 · $itemCount 句 · 约${hanziCount(GUO_PASSAGE)}字"
    DONG_ID -> "字目 · $itemCount 个东韵字目"
    RecordingTaskSnapshot.LIBRARY_PACKAGE_ID -> "字目 · 全量字库"
    else -> null
  }

  fun passage(id: String, revision: Int = revision(id)): SurveyPackage {
    val texts = when (id) {
      SHANCUN_ID -> listOf(POEM)
      GUO_ID -> Regex("[^。]+。?").findAll(GUO_PASSAGE).map { it.value }.toList()
      else -> error("不是文段预设")
    }
    return SurveyPackage(packageId = id, revision = revision, title = title(id, revision = revision), planType = RecordingPlanType.PASSAGE,
      description = if (id == SHANCUN_ID) "整篇录制。" else "逐句录制，保留全文及标点。",
      defaults = SurveyDefaults(instruction = PASSAGE_PROMPT), items = texts.mapIndexed { index, text ->
        val itemId = "ex_${if (id == SHANCUN_ID) "shancun" else "guo"}_${index + 1}"
        SurveyItem(itemId = itemId, wordId = itemId, type = SurveyItemType.SENTENCE.value, text = text, instruction = PASSAGE_PROMPT)
      })
  }

  fun hanziCount(text: String): Int {
    var count = 0
    var index = 0
    while (index < text.length) {
      val code = Character.codePointAt(text, index)
      if (Character.UnicodeScript.of(code) == Character.UnicodeScript.HAN) count++
      index += Character.charCount(code)
    }
    return count
  }

  private const val POEM = "一去二三里，烟村四五家。亭台六七座，八九十枝花。"
  private const val GUO_PASSAGE = "郭沫若没出息，不积极阅读学习热力学学术力作，读硕却不续读博。浙北谷穴贼作，郭踯躅不决，击敌不力，罚禄失职，实属屈辱。食龌龊的垃圾食物却不食六畜熟肉及绿色麦谷，喝血及蜜却不喝白色的雪碧。不服佛法，日日月月极作孽，掠获鹿鸭鹤雀，直接握铁戟杀戮，切赤舌，凿白骨，拔黑发，斫肉末，确实毒辣刻薄，触及法律。宅植苜蓿不植菊，客室角落没竹没木亦没石。特别渴，悒郁寂寞得哭，纳秃发妾入屋，热不沐浴，熄灭蜡烛，急摸席侧的雪白玉足。食色蚀力，弱不敌疾，却一直觉得不值得吃药。一夕，忽卒。"
}

/** Copies within one task need fresh custom identities, while builtin source identities remain stable. */
fun SurveyItem.duplicate(): SurveyItem {
  val id = "ex_${UUID.randomUUID()}"
  return copy(itemId = id, wordId = if (wordId?.toIntOrNull() != null) wordId else id)
}

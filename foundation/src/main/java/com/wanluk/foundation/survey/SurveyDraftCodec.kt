package com.wanluk.foundation.survey

import com.google.gson.GsonBuilder

/** Private draft storage; incomplete titles/items never weaken the public package validator. */
object SurveyDraftCodec {
  const val MAX_BYTES = SurveyPackageCodec.MAX_BYTES * 4 + 1024 * 1024
  private val gson = GsonBuilder().disableHtmlEscaping().create()
  private data class Part(val json: String, val blankTitle: String?, val blankLanguage: String?, val emptyItems: Boolean)
  private data class Document(val version: Int, val task: Part, val original: Part?)

  fun encode(task: SurveyPackage, original: SurveyPackage?): String = gson.toJson(
    Document(1, part(task), original?.let(::part))).also {
      require(it.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "草稿超过本地存储上限" }
    }

  fun decode(json: String): SavedSurveyDraft {
    require(json.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "草稿超过本地存储上限" }
    val document = requireNotNull(gson.fromJson(json, Document::class.java)) { "草稿不完整" }
    require(document.version == 1) { "草稿版本不支持" }
    return SavedSurveyDraft(restore(requireNotNull(document.task)), document.original?.let(::restore))
  }

  private fun part(task: SurveyPackage): Part {
    val safe = task.copy(schemaVersion = if (task.defaults != null) 3 else task.schemaVersion,
      title = task.title.ifBlank { "草稿" }, languageTag = task.languageTag.ifBlank { "und" }, items = task.items.ifEmpty {
      listOf(SurveyItem(itemId = "ex_draft_placeholder", text = "草稿", repetitions = task.defaults?.repetitions ?: 1))
    })
    return Part(SurveyPackageCodec.encode(safe), task.title.takeIf { it.isBlank() }, task.languageTag.takeIf { it.isBlank() }, task.items.isEmpty())
  }

  private fun restore(part: Part): SurveyPackage {
    require(part.blankTitle == null || part.blankTitle.isBlank() && part.blankTitle.length <= 120) { "草稿名称不合法" }
    require(part.blankLanguage == null || part.blankLanguage.isBlank() && part.blankLanguage.length <= 80) { "草稿语言标记不合法" }
    val task = SurveyPackageCodec.decode(requireNotNull(part.json))
    if (part.emptyItems) require(task.items.size == 1 && task.items.single().itemId == "ex_draft_placeholder") { "草稿结构不完整" }
    return task.copy(title = part.blankTitle ?: task.title, languageTag = part.blankLanguage ?: task.languageTag, items = if (part.emptyItems) emptyList() else task.items)
  }
}

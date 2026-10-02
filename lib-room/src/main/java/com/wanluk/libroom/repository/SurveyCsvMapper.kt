package com.wanluk.libroom.repository

import com.wanluk.foundation.survey.*
import com.wanluk.libroom.repository.SurveyTransferMapper.Companion.annotation
import com.wanluk.libroom.repository.SurveyTransferMapper.Companion.prompt
import com.wanluk.libroom.repository.SurveyTransferMapper.Companion.text

data class CsvPreviewRow(val id: String, val line: Int, val text: String, val item: SurveyItem?, val error: String? = null)

class SurveyCsvMapper(private val catalog: BuiltinWordCatalog) {
  fun preview(table: SurveyCsvTable, mapping: Map<CsvField, Int>, defaults: SurveyDefaults,
    planType: RecordingPlanType = RecordingPlanType.CHARACTER): List<CsvPreviewRow> {
    require(mapping.values.all { it in table.columns.indices } && mapping.values.distinct().size == mapping.size) { "同一列不能映射到多个字段" }
    require(CsvField.TEXT in mapping || CsvField.BUILTIN_ID in mapping) { "请映射内容或内置字目 ID" }
    return table.records.map { row ->
      val text = mapping[CsvField.TEXT]?.let { row.cells[it] }.orEmpty()
      try { CsvPreviewRow(row.id, row.line, text.take(500), item(row, mapping, defaults, planType)) }
      catch (error: IllegalArgumentException) { CsvPreviewRow(row.id, row.line, text.take(500), null, error.message?.take(180) ?: "字段不合法") }
    }
  }
  private fun item(row: CsvRecord, mapping: Map<CsvField, Int>, defaults: SurveyDefaults, planType: RecordingPlanType): SurveyItem {
    fun value(field: CsvField): String = mapping[field]?.let { row.cells[it].trim() }.orEmpty()
    fun bool(field: CsvField): Boolean? = value(field).takeIf { it.isNotEmpty() }?.let {
      when (it.lowercase()) { "true", "1", "是" -> true; "false", "0", "否" -> false; else -> errorValue(field, "须为 true/false、1/0 或 是/否") }
    }
    val declared = value(CsvField.BUILTIN_ID)
    val builtin = if (declared.isEmpty()) null else {
      val id = declared.toIntOrNull()
      require(id != null && id > 0 && id.toString() == declared) { "内置字目 ID 须为正整数" }
      requireNotNull(catalog.words[id]) { "内置字目 ID $id 不存在" }
    }
    val type = when (val raw = value(CsvField.TYPE)) {
      "" -> if (planType == RecordingPlanType.PASSAGE) "sentence" else defaults.displayType
      "单字" -> "character"; "词语" -> "word"; "句子", "文段" -> "sentence"
      "character", "word", "sentence" -> raw; else -> errorValue(CsvField.TYPE, "须为 character/word/sentence")
    }
    require(builtin == null || type in listOf("character", "word")) { "内置字目只支持单字或词语" }
    val text = value(CsvField.TEXT).ifEmpty { builtin?.text(type).orEmpty() }
    require(text.isNotBlank()) { "内容不能为空" }
    require(builtin == null || text == builtin.text(type)) { "内容与声明的内置字目 ID 不一致" }
    val instruction = value(CsvField.INSTRUCTION).takeIf { it.isNotEmpty() }
    val hidden = bool(CsvField.HIDE_INSTRUCTION) == true
    require(!hidden || instruction == null) { "隐藏提示与非空录制提示冲突" }
    val repetitions = value(CsvField.REPETITIONS).takeIf { it.isNotEmpty() }?.let { raw ->
      requireNotNull(raw.toIntOrNull()?.takeIf { it in 1..5 }) { "重复次数须为 1–5" }
    }
    val allowSkip = bool(CsvField.ALLOW_SKIP)
    val deng = value(CsvField.DENG).takeIf { it.isNotEmpty() }?.let { raw ->
      when (raw) { "一" -> 1; "二" -> 2; "三" -> 3; "四" -> 4; else -> raw.toIntOrNull() }.also { require(it in 1..4) { "等须为 1–4 或 一二三四" } }
    }
    val annotationFields = listOf(CsvField.SHENG, CsvField.HU, CsvField.DENG, CsvField.YUN, CsvField.DIAO, CsvField.SHE, CsvField.ZU)
    val supplied = ChinesePhonology(value(CsvField.SHENG), value(CsvField.HU), deng, value(CsvField.YUN),
      value(CsvField.DIAO), value(CsvField.SHE), value(CsvField.ZU))
    val annotation = builtin?.annotation() ?: supplied.takeIf { annotationFields.any { field -> value(field).isNotEmpty() } }
    if (builtin != null) {
      val expected = builtin.annotation()
      val expectedFields = mapOf(CsvField.SHENG to expected.sheng, CsvField.HU to expected.hu, CsvField.YUN to expected.yun,
        CsvField.DIAO to expected.diao, CsvField.SHE to expected.she, CsvField.ZU to expected.zu)
      require(expectedFields.all { (field, expectedValue) -> value(field).isEmpty() || value(field) == expectedValue } &&
        (deng == null || deng == expected.deng)) { "音韵字段与声明的内置字目 ID 不一致" }
    }
    val ext = ItemExtInfo(type.takeIf { builtin != null && it != defaults.displayType },
      if (hidden) "" else instruction, repetitions, allowSkip, value(CsvField.NOTE)).compact()
    return SurveyItem("ex_${row.id}", type, text, ext?.instruction ?: defaults.instruction ?: builtin?.prompt(type) ?: SurveyTransferMapper.CUSTOM_PROMPT,
      repetitions ?: defaults.repetitions, allowSkip ?: defaults.allowSkip, annotation, declared.ifEmpty { "ex_${row.id}" }, ext).also {
      SurveyPackageCodec.validate(SurveyPackage(title = "CSV", defaults = defaults, items = listOf(it), planType = planType))
    }
  }
  private fun errorValue(field: CsvField, message: String): Nothing = throw IllegalArgumentException("${field.label}$message")
}

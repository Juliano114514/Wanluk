package com.wanluk.foundation.survey

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.UUID

data class CsvRecord(val line: Int, val cells: List<String>, val id: String = UUID.randomUUID().toString())
data class SurveyCsvTable(val columns: List<String>, val records: List<CsvRecord>)

enum class CsvField(val label: String, vararg val aliases: String) {
  TEXT("内容", "text", "内容", "题目", "单字", "單字"),
  TYPE("题型", "type", "题型"), BUILTIN_ID("内置字目 ID", "builtin_id", "字目id", "内置id"),
  INSTRUCTION("录制提示", "instruction", "提示", "录制提示"),
  HIDE_INSTRUCTION("隐藏提示", "hide_instruction", "隐藏提示"),
  REPETITIONS("重复次数", "repetitions", "重复次数", "次数"),
  ALLOW_SKIP("允许跳过", "allow_skip", "允许跳过"), NOTE("说明", "note", "说明", "备注"),
  SHENG("声母", "sheng", "声", "聲", "声母"), HU("呼", "hu", "呼"), DENG("等", "deng", "等"),
  YUN("韵", "yun", "韵", "韻"), DIAO("调", "diao", "调", "調"),
  SHE("摄", "she", "摄", "攝"), ZU("组", "zu", "组", "組"),
}

/** UTF-8 and RFC-style quoting only. Broken structure stops the whole import. */
object SurveyCsv {
  const val MAX_BYTES = 16 * 1024 * 1024
  const val MAX_COLUMNS = 64
  fun read(input: InputStream): SurveyCsvTable {
    val bytes = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
      val count = input.read(buffer)
      if (count < 0) break
      require(bytes.size().toLong() + count <= MAX_BYTES) { "CSV 超过 16 MB" }
      bytes.write(buffer, 0, count)
    }
    val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
    return parse(decoder.decode(ByteBuffer.wrap(bytes.toByteArray())).toString().removePrefix("\uFEFF"))
  }
  fun parse(text: String): SurveyCsvTable {
    require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "CSV 超过 16 MB" }
    val rows = mutableListOf<CsvRecord>()
    val fields = mutableListOf<String>()
    val cell = StringBuilder()
    var quoted = false
    var closedQuote = false
    var rowHasSyntax = false
    var line = 1
    var startLine = 1
    var index = 0
    fun field() {
      require(fields.size < MAX_COLUMNS) { "CSV 每行最多 64 列" }
      fields += cell.toString(); cell.setLength(0); closedQuote = false
    }
    fun record() {
      field()
      if (rowHasSyntax || fields.any { it.isNotBlank() }) {
        require(rows.size <= SurveyPackage.MAX_ITEMS) { "CSV 最多 20000 行题目" }
        rows += CsvRecord(startLine, fields.toList())
      }
      fields.clear(); rowHasSyntax = false
    }
    while (index < text.length) {
      val char = text[index]
      if (quoted) {
        if (char == '"') {
          if (index + 1 < text.length && text[index + 1] == '"') { cell.append('"'); index++ }
          else { quoted = false; closedQuote = true }
        } else {
          cell.append(char)
          if (char == '\n' || char == '\r' && (index + 1 == text.length || text[index + 1] != '\n')) line++
        }
      } else when (char) {
        '"' -> { require(cell.isEmpty() && !closedQuote) { "第 $line 行引号位置不合法" }; quoted = true; rowHasSyntax = true }
        ',' -> { rowHasSyntax = true; field() }
        '\n', '\r' -> {
          record()
          if (char == '\r' && index + 1 < text.length && text[index + 1] == '\n') index++
          line++; startLine = line
        }
        else -> { require(!closedQuote) { "第 $line 行引号后存在多余字符" }; cell.append(char) }
      }
      index++
    }
    require(!quoted) { "第 $startLine 行引号未闭合" }
    if (cell.isNotEmpty() || fields.isNotEmpty() || closedQuote) record()
    require(rows.size >= 2) { "CSV 需要表头和至少一行题目" }
    val columns = rows.first().cells.map { it.trim().removePrefix("\uFEFF") }
    require(rows.drop(1).all { it.cells.size == columns.size }) { "CSV 行列数量不一致，未载入任何题目" }
    return SurveyCsvTable(columns, rows.drop(1))
  }
  fun mapping(table: SurveyCsvTable): Map<CsvField, Int> = CsvField.entries.mapNotNull { field ->
    table.columns.indexOfFirst { name -> field.aliases.any { it.equals(name, ignoreCase = true) } }
      .takeIf { it >= 0 }?.let { field to it }
  }.toMap()
  val template: String = "\uFEFF" + CsvField.entries.joinToString(",") { it.aliases.first() } +
    "\r\n苹果,word,,,false,1,true,日常用语,,,,,,,\r\n你好,sentence,,,true,1,false,,,,,,,,\r\n"
}

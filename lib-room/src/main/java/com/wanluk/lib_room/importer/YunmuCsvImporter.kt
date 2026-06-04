package com.wanluk.lib_room.importer

import com.wanluk.lib_room.entities.WordCaseEntity
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/**
 * 内置 [韵目表.csv] 解析器：续行继承、等→Int 转换，仅映射 README §2.1.3 落库列。
 */
object YunmuCsvImporter {

  private const val COL_SHENG = 0
  private const val COL_HU = 1
  private const val COL_DENG = 2
  private const val COL_YUN = 3
  private const val COL_DIAO = 4
  private const val COL_ZU = 5
  private const val COL_SHE = 6
  private const val COL_CORE_CHAR = 7
  private const val COL_PHRASES = 9
  private const val COL_REMARK = 10
  private const val MIN_COLUMNS = 8

  fun parse(inputStream: InputStream): List<WordCaseEntity> {
    val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
    reader.use { buffered ->
      buffered.readLine() // 跳过表头
      val entities = mutableListOf<WordCaseEntity>()
      var lastCoords: PhoneticCoords? = null

      buffered.forEachLine { line ->
        if (line.isBlank()) return@forEachLine
        val tokens = line.split(',')
        if (tokens.size < MIN_COLUMNS) return@forEachLine

        val coords = when {
          areCoordsEmpty(tokens) -> lastCoords
          hasAnyCoord(tokens) -> parseCoords(tokens).also { lastCoords = it }
          else -> null
        } ?: return@forEachLine

        val coreChar = tokens[COL_CORE_CHAR].trim()
        if (coreChar.isEmpty()) return@forEachLine

        entities.add(
          WordCaseEntity(
            sheng = coords.sheng,
            hu = coords.hu,
            deng = coords.deng,
            yun = coords.yun,
            diao = coords.diao,
            zu = coords.zu,
            she = coords.she,
            coreChar = coreChar,
            phrases = tokens.getOrNull(COL_PHRASES)?.trim()?.takeIf { it.isNotEmpty() },
            remark = tokens.getOrNull(COL_REMARK)?.trim()?.takeIf { it.isNotEmpty() },
          )
        )
      }
      return entities
    }
  }

  private fun areCoordsEmpty(tokens: List<String>): Boolean =
    (COL_SHENG..COL_SHE).all { tokens.getOrElse(it) { "" }.trim().isEmpty() }

  private fun hasAnyCoord(tokens: List<String>): Boolean = !areCoordsEmpty(tokens)

  private fun parseCoords(tokens: List<String>): PhoneticCoords =
    PhoneticCoords(
      sheng = tokens[COL_SHENG].trim(),
      hu = tokens[COL_HU].trim(),
      deng = parseDeng(tokens[COL_DENG]),
      yun = tokens[COL_YUN].trim(),
      diao = tokens[COL_DIAO].trim(),
      zu = tokens.getOrNull(COL_ZU)?.trim()?.takeIf { it.isNotEmpty() },
      she = tokens[COL_SHE].trim(),
    )

  private fun parseDeng(raw: String): Int = when (raw.trim()) {
    "一" -> 1
    "二" -> 2
    "三" -> 3
    "四" -> 4
    else -> 0
  }

  private data class PhoneticCoords(
    val sheng: String,
    val hu: String,
    val deng: Int,
    val yun: String,
    val diao: String,
    val zu: String?,
    val she: String,
  )
}

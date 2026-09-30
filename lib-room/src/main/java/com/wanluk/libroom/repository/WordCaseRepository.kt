package com.wanluk.libroom.repository

import android.content.Context
import androidx.room.withTransaction
import com.wanluk.libroom.AppDatabase
import com.wanluk.libroom.entity.BuiltinAssetEntity
import com.wanluk.libroom.entity.WordCaseEntity
import com.wanluk.libroom.importer.YunmuCsvImporter
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

data class WordFilter(val search: String = "", val she: String = "", val rarity: Int = -1,
  val selectedOnly: Boolean = false, val selectedIds: List<Long> = emptyList())

class WordCaseRepository(
  private val context: Context,
  private val database: AppDatabase,
) {
  private val wordCaseDao = database.wordCaseDao()

  fun getAllWordCases(): Flow<List<WordCaseEntity>> = wordCaseDao.getAllWordCases()

  fun getWordCasesByShe(she: String): Flow<List<WordCaseEntity>> =
    wordCaseDao.getWordCasesByShe(she)

  suspend fun page(filter: WordFilter, afterId: Long = 0): List<WordCaseEntity> =
    wordCaseDao.page(filter.search, filter.she, filter.rarity, filter.selectedOnly,
      filter.selectedIds.ifEmpty { listOf(-1L) }, afterId, 80)

  suspend fun count(filter: WordFilter): Int = wordCaseDao.filteredCount(filter.search, filter.she,
    filter.rarity, filter.selectedOnly, filter.selectedIds.ifEmpty { listOf(-1L) })

  suspend fun groups(): List<String> = wordCaseDao.groups()

  suspend fun selected(ids: List<Long>): List<WordCaseEntity> {
    require(ids.size <= 500)
    val byId = wordCaseDao.byIds(ids).associateBy { it.id }
    return ids.map { requireNotNull(byId[it]) { "字目已变化，请重新选择" } }
  }

  suspend fun allForRecording(): List<WordCaseEntity> {
    require(wordCaseDao.getWordCaseCount() in 1..com.wanluk.foundation.survey.RecordingTaskSnapshot.MAX_LIBRARY_ITEMS) {
      "字库为空或超过本地录制上限"
    }
    return wordCaseDao.getAllWordCasesOnce()
  }

  /** 按资产内容更新，整个更新事务化；保留既有 ID，不清空用户条目。 */
  suspend fun ensureBuiltinWordCasesImported() = withContext(Dispatchers.IO) {
    val bytes = context.assets.open(BUILTIN_CSV_ASSET).use { it.readBytes() }
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    val assets = database.surveyDao()
    if (assets.asset(BUILTIN_CSV_ASSET)?.sha256 == digest) return@withContext
    val imported = YunmuCsvImporter.parse(bytes.inputStream())
    check(imported.isNotEmpty() && imported.map { it.sourceId }.distinct().size == imported.size) {
      "内置字表为空或来源 ID 重复"
    }
    database.withTransaction {
      if (assets.asset(BUILTIN_CSV_ASSET)?.sha256 == digest) return@withTransaction
      val old = wordCaseDao.getAllWordCasesOnce()
      val bySource = old.filter { it.sourceId != null }.associateBy { it.sourceId }
      // v2 没有来源 ID：匹配完整记录内容，按队列保留同字同音但不同义项的独立 ID。
      val legacy = old.filter { it.sourceId == null }.groupBy { it.copy(id = 0) }
        .mapValues { (_, values) -> java.util.ArrayDeque(values) }
      val updated = imported.map { item ->
        val previous = bySource[item.sourceId] ?: legacy[item.copy(id = 0, sourceId = null)]?.pollFirst()
        item.copy(id = previous?.id ?: 0)
      }
      wordCaseDao.insertWordCases(updated)
      assets.saveAsset(BuiltinAssetEntity(BUILTIN_CSV_ASSET, digest))
    }
  }

  companion object {
    const val BUILTIN_CSV_ASSET = "WordCaseList.csv"
  }
}

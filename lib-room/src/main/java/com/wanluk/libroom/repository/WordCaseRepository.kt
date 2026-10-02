package com.wanluk.libroom.repository

import android.content.Context
import androidx.room.withTransaction
import com.wanluk.libroom.AppDatabase
import com.wanluk.libroom.entity.BuiltinAssetEntity
import com.wanluk.libroom.entity.WordCaseEntity
import com.wanluk.libroom.importer.YunmuCsvImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.wanluk.foundation.survey.WordEntry
import com.wanluk.libroom.entity.WordFavoriteEntity
import com.wanluk.foundation.survey.SurveyPackage
import com.wanluk.foundation.survey.SurveyTransferCodec

data class WordFilter(val search: String = "", val she: String = "", val rarity: Int = -1,
  val selectedOnly: Boolean = false, val selectedIds: List<Long> = emptyList(),
  val sheng: String = "", val yun: String = "", val diao: String = "", val deng: Int = -1, val hu: String = "",
  val polyphonic: Int = -1, val favoritesOnly: Boolean = false)

class WordCaseRepository(
  private val context: Context,
  private val database: AppDatabase,
) {
  private val wordCaseDao = database.wordCaseDao()
  private val catalogLock = Mutex()
  private val selectionLock = Mutex()
  @Volatile private var cachedCatalog: BuiltinWordCatalog? = null
  private var selectedCache: Pair<WordFilter, List<WordEntry>>? = null

  fun getAllWordCases(): Flow<List<WordCaseEntity>> = wordCaseDao.getAllWordCases()

  fun getWordCasesByShe(she: String): Flow<List<WordCaseEntity>> =
    wordCaseDao.getWordCasesByShe(she)

  suspend fun page(filter: WordFilter, afterId: Long = 0): List<WordEntry> = withContext(Dispatchers.IO) {
    if (filter.selectedOnly) selectedPage(filter).filter { it.id > afterId }.take(80)
    else with(filter) { wordCaseDao.page(search, she, rarity, sheng, yun, diao, deng, hu, polyphonic,
      favoritesOnly, false, listOf(-1L), afterId, 80) }
  }
  suspend fun count(filter: WordFilter): Int = withContext(Dispatchers.IO) {
    if (filter.selectedOnly) selectedPage(filter).size
    else with(filter) { wordCaseDao.filteredCount(search, she, rarity, sheng, yun, diao, deng, hu, polyphonic,
      favoritesOnly, false, listOf(-1L)) }
  }
  private suspend fun selectedPage(filter: WordFilter): List<WordEntry> = selectionLock.withLock {
    selectedCache?.takeIf { it.first == filter }?.let { return@withLock it.second }
    require(filter.selectedIds.size <= SurveyPackage.MAX_ITEMS)
    val rows = with(filter) { selectedIds.distinct().chunked(900).flatMap {
      wordCaseDao.filteredSelection(search, she, rarity, sheng, yun, diao, deng, hu, polyphonic,
        favoritesOnly, true, it)
    }.sortedBy { it.id } }
    selectedCache = filter to rows
    rows
  }
  suspend fun matchingIds(filter: WordFilter): List<Long> = withContext(Dispatchers.IO) {
    if (filter.selectedOnly) selectedPage(filter).map { it.id }
    else with(filter) { wordCaseDao.matchingIds(search, she, rarity, sheng, yun, diao, deng, hu, polyphonic,
      favoritesOnly, false, listOf(-1L), SurveyPackage.MAX_ITEMS + 1) }
  }
  suspend fun groups(): List<String> = wordCaseDao.groups()
  suspend fun facets() = wordCaseDao.facets()
  suspend fun setFavorite(word: WordEntry, enabled: Boolean) = selectionLock.withLock {
    val key = word.sourceId ?: "legacy:${word.id}"
    if (enabled) wordCaseDao.favorite(WordFavoriteEntity(key, System.currentTimeMillis())) else wordCaseDao.unfavorite(key)
    selectedCache = null
  }
  suspend fun selected(ids: List<Long>): List<WordEntry> = withContext(Dispatchers.IO) {
    require(ids.size <= SurveyPackage.MAX_ITEMS)
    val byId = ids.distinct().chunked(900).flatMap { wordCaseDao.byIds(it) }.associateBy { it.id }
    ids.map { requireNotNull(byId[it]) { "字目已变化，请重新选择" } }
  }

  suspend fun allForRecording(): List<WordCaseEntity> {
    require(wordCaseDao.getWordCaseCount() in 1..com.wanluk.foundation.survey.RecordingTaskSnapshot.MAX_LIBRARY_ITEMS) {
      "字库为空或超过本地录制上限"
    }
    return wordCaseDao.getAllWordCasesOnce()
  }

  suspend fun builtinCatalog(): BuiltinWordCatalog = withContext(Dispatchers.IO) {
    cachedCatalog ?: catalogLock.withLock {
      cachedCatalog ?: run {
        val bytes = context.assets.open(BUILTIN_CSV_ASSET).use { it.readBytes() }
        val rows = YunmuCsvImporter.parse(bytes.inputStream())
        val pairs = rows.map { requireNotNull(it.sourceId?.removePrefix("builtin:wordcase:")?.toIntOrNull()) to it }
        check(pairs.isNotEmpty() && pairs.map { it.first }.distinct().size == pairs.size && pairs.all { it.first > 0 })
        BuiltinWordCatalog(SurveyTransferCodec.hex(SurveyTransferCodec.sha256(bytes)), pairs.toMap()).also { cachedCatalog = it }
      }
    }
  }

  suspend fun transferMapper(): SurveyTransferMapper = SurveyTransferMapper(builtinCatalog())
  fun editable(task: SurveyPackage): SurveyPackage = SurveyTransferMapper(requireNotNull(cachedCatalog)).editable(task)
  fun currentBuiltin(id: Int): WordCaseEntity? = requireNotNull(cachedCatalog).words[id]

  /** 按资产内容更新，整个更新事务化；保留既有 ID，不清空用户条目。 */
  suspend fun ensureBuiltinWordCasesImported() = withContext(Dispatchers.IO) {
    val catalog = builtinCatalog()
    val digest = catalog.sha256
    val assets = database.surveyDao()
    if (assets.asset(BUILTIN_CSV_ASSET)?.sha256 == digest) {
      backfillPolyphonic(catalog)
      return@withContext
    }
    val imported = catalog.words.values.toList()
    check(imported.isNotEmpty() && imported.map { it.sourceId }.distinct().size == imported.size) {
      "内置字表为空或来源 ID 重复"
    }
    database.withTransaction {
      if (assets.asset(BUILTIN_CSV_ASSET)?.sha256 == digest) return@withTransaction
      val old = wordCaseDao.getAllWordCasesOnce()
      val bySource = old.filter { it.sourceId != null }.associateBy { it.sourceId }
      // v2 没有来源 ID：匹配完整记录内容，按队列保留同字同音但不同义项的独立 ID。
      val legacy = old.filter { it.sourceId == null }.groupBy { it.copy(id = 0, polyphonic = null) }
        .mapValues { (_, values) -> java.util.ArrayDeque(values) }
      val updated = imported.map { item ->
        val previous = bySource[item.sourceId] ?: legacy[item.copy(id = 0, sourceId = null, polyphonic = null)]?.pollFirst()
        item.copy(id = previous?.id ?: 0)
      }
      wordCaseDao.insertWordCases(updated)
      assets.saveAsset(BuiltinAssetEntity(BUILTIN_CSV_ASSET, digest))
      assets.saveAsset(BuiltinAssetEntity(POLYPHONIC_MAPPING, digest))
    }
  }

  private suspend fun backfillPolyphonic(catalog: BuiltinWordCatalog) {
    val assets = database.surveyDao()
    if (assets.asset(POLYPHONIC_MAPPING)?.sha256 == catalog.sha256) return
    database.withTransaction {
      catalog.words.values.forEach { word -> wordCaseDao.updatePolyphonic(requireNotNull(word.sourceId), word.polyphonic) }
      assets.saveAsset(BuiltinAssetEntity(POLYPHONIC_MAPPING, catalog.sha256))
    }
  }
  companion object {
    private const val POLYPHONIC_MAPPING = "wordcase-polyphonic-v1"
    const val BUILTIN_CSV_ASSET = "WordCaseList.csv"
  }
}

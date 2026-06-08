package com.wanluk.libroom.repository

import android.content.Context
import com.wanluk.libroom.dao.WordCaseDao
import com.wanluk.libroom.entity.WordCaseEntity
import com.wanluk.libroom.importer.YunmuCsvImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class WordCaseRepository(
  private val context: Context,
  private val wordCaseDao: WordCaseDao,
) {

  fun getAllWordCases(): Flow<List<WordCaseEntity>> = wordCaseDao.getAllWordCases()

  fun getWordCasesByShe(she: String): Flow<List<WordCaseEntity>> =
    wordCaseDao.getWordCasesByShe(she)

  /** 表为空或行数与内置字表不一致时重新灌入，避免 schema/字表升级后数据陈旧。 */
  suspend fun ensureBuiltinWordCasesImported() = withContext(Dispatchers.IO) {
    val count = wordCaseDao.getWordCaseCount()
    if (count == BUILTIN_ROW_COUNT) return@withContext
    if (count > 0) wordCaseDao.deleteAllWordCases()
    importBuiltinCsv()
  }

  private suspend fun importBuiltinCsv() {
    context.assets.open(BUILTIN_CSV_ASSET).use { stream ->
      wordCaseDao.insertWordCases(YunmuCsvImporter.parse(stream))
    }
  }

  companion object {
    const val BUILTIN_CSV_ASSET = "WordCaseList.csv"
    const val BUILTIN_ROW_COUNT = 10_292
  }
}

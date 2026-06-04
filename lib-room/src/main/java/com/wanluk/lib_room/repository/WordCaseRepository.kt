package com.wanluk.lib_room.repository

import android.content.Context
import com.wanluk.lib_room.dao.WordCaseDao
import com.wanluk.lib_room.entities.WordCaseEntity
import com.wanluk.lib_room.importer.YunmuCsvImporter
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

  /** 表为空时从 assets 灌入内置韵目表，避免重复导入。 */
  suspend fun ensureBuiltinWordCasesImported() = withContext(Dispatchers.IO) {
    if (wordCaseDao.getWordCaseCount() > 0) return@withContext
    context.assets.open(BUILTIN_CSV_ASSET).use { stream ->
      val entities = YunmuCsvImporter.parse(stream)
      wordCaseDao.insertWordCases(entities)
    }
  }

  companion object {
    const val BUILTIN_CSV_ASSET = "韵目表.csv"
  }
}

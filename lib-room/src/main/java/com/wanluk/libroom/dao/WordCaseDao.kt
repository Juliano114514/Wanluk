package com.wanluk.libroom.dao

import androidx.room.*
import com.wanluk.foundation.survey.WordEntry
import com.wanluk.foundation.survey.WordFacet
import com.wanluk.libroom.entity.WordCaseEntity
import com.wanluk.libroom.entity.WordFavoriteEntity
import kotlinx.coroutines.flow.Flow

private const val FAVORITE = "EXISTS (SELECT 1 FROM word_favorites f WHERE f.sourceKey = COALESCE(w.source_id, 'legacy:' || w.id))"
private const val WORD_COLUMNS = "w.id, w.sheng, w.hu, w.deng, w.yun, w.diao, w.zu, w.she, w.core_char AS coreChar," +
  " w.phrases, w.remark, w.rarity, w.source_id AS sourceId, w.polyphonic, " + FAVORITE + " AS favorite"
private const val WORD_FILTER = " WHERE (:she = '' OR w.she = :she) AND (:rarity = -1 OR w.rarity = :rarity)" +
  " AND (:sheng = '' OR w.sheng = :sheng) AND (:yun = '' OR w.yun = :yun) AND (:diao = '' OR w.diao = :diao)" +
  " AND (:deng = -1 OR w.deng = :deng) AND (:hu = '' OR w.hu = :hu)" +
  " AND (:polyphonic = -1 OR w.polyphonic = :polyphonic) AND (:favoritesOnly = 0 OR " + FAVORITE + ")" +
  " AND (:selectedOnly = 0 OR w.id IN (:selectedIds))" +
  " AND (:search = '' OR instr(lower(w.core_char), lower(:search)) > 0" +
  " OR instr(lower(COALESCE(w.phrases, '')), lower(:search)) > 0 OR instr(lower(w.sheng), lower(:search)) > 0" +
  " OR instr(lower(w.yun), lower(:search)) > 0 OR instr(lower(w.diao), lower(:search)) > 0)"

@Dao
interface WordCaseDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertWordCases(wordCases: List<WordCaseEntity>)
  @Query("SELECT * FROM word_cases")
  fun getAllWordCases(): Flow<List<WordCaseEntity>>
  @Query("SELECT * FROM word_cases ORDER BY id")
  suspend fun getAllWordCasesOnce(): List<WordCaseEntity>
  @Query("SELECT * FROM word_cases WHERE she = :sheInput")
  fun getWordCasesByShe(sheInput: String): Flow<List<WordCaseEntity>>
  @Query("SELECT * FROM word_cases WHERE yun = :yunInput")
  fun getWordCasesByYun(yunInput: String): Flow<List<WordCaseEntity>>
  @Query("SELECT COUNT(*) FROM word_cases")
  suspend fun getWordCaseCount(): Int
  @Query("SELECT " + WORD_COLUMNS + " FROM word_cases w" + WORD_FILTER + " AND w.id > :afterId ORDER BY w.id LIMIT :limit")
  suspend fun page(search: String, she: String, rarity: Int, sheng: String, yun: String, diao: String, deng: Int, hu: String, polyphonic: Int, favoritesOnly: Boolean, selectedOnly: Boolean, selectedIds: List<Long>, afterId: Long, limit: Int): List<WordEntry>
  @Query("SELECT COUNT(*) FROM word_cases w" + WORD_FILTER)
  suspend fun filteredCount(search: String, she: String, rarity: Int, sheng: String, yun: String, diao: String, deng: Int, hu: String, polyphonic: Int, favoritesOnly: Boolean, selectedOnly: Boolean, selectedIds: List<Long>): Int
  @Query("SELECT " + WORD_COLUMNS + " FROM word_cases w" + WORD_FILTER + " ORDER BY w.id")
  suspend fun filteredSelection(search: String, she: String, rarity: Int, sheng: String, yun: String, diao: String, deng: Int, hu: String, polyphonic: Int, favoritesOnly: Boolean, selectedOnly: Boolean, selectedIds: List<Long>): List<WordEntry>
  @Query("SELECT w.id FROM word_cases w" + WORD_FILTER + " ORDER BY w.id LIMIT :limit")
  suspend fun matchingIds(search: String, she: String, rarity: Int, sheng: String, yun: String, diao: String, deng: Int, hu: String, polyphonic: Int, favoritesOnly: Boolean, selectedOnly: Boolean, selectedIds: List<Long>, limit: Int): List<Long>
  @Query("SELECT " + WORD_COLUMNS + " FROM word_cases w WHERE w.id IN (:ids)")
  suspend fun byIds(ids: List<Long>): List<WordEntry>
  @Query("SELECT she FROM word_cases WHERE she != '' GROUP BY she ORDER BY MIN(id)")
  suspend fun groups(): List<String>
  @Query("SELECT 'sheng' AS field, sheng AS value FROM word_cases WHERE sheng != '' GROUP BY sheng" +
    " UNION ALL SELECT 'yun', yun FROM word_cases WHERE yun != '' GROUP BY yun" +
    " UNION ALL SELECT 'diao', diao FROM word_cases WHERE diao != '' GROUP BY diao" +
    " UNION ALL SELECT 'hu', hu FROM word_cases WHERE hu != '' GROUP BY hu")
  suspend fun facets(): List<WordFacet>
  @Insert(onConflict = OnConflictStrategy.IGNORE)
  suspend fun favorite(row: WordFavoriteEntity)
  @Query("DELETE FROM word_favorites WHERE sourceKey = :key")
  suspend fun unfavorite(key: String)
  @Query("UPDATE word_cases SET polyphonic = :value WHERE source_id = :source")
  suspend fun updatePolyphonic(source: String, value: Boolean?)
  @Query("DELETE FROM word_cases")
  suspend fun deleteAllWordCases()
}

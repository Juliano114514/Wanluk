package com.wanluk.libroom.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wanluk.libroom.entity.WordCaseEntity
import kotlinx.coroutines.flow.Flow

private const val WORD_FILTER = " WHERE (:she = '' OR she = :she) AND (:rarity = -1 OR rarity = :rarity)" +
  " AND (:selectedOnly = 0 OR id IN (:selectedIds))" +
  " AND (:search = '' OR instr(lower(core_char), lower(:search)) > 0" +
  " OR instr(lower(COALESCE(phrases, '')), lower(:search)) > 0 OR instr(lower(sheng), lower(:search)) > 0" +
  " OR instr(lower(yun), lower(:search)) > 0 OR instr(lower(diao), lower(:search)) > 0)"

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

  @Query("SELECT * FROM word_cases" + WORD_FILTER + " AND id > :afterId ORDER BY id LIMIT :limit")
  suspend fun page(search: String, she: String, rarity: Int, selectedOnly: Boolean,
    selectedIds: List<Long>, afterId: Long, limit: Int): List<WordCaseEntity>

  @Query("SELECT COUNT(*) FROM word_cases" + WORD_FILTER)
  suspend fun filteredCount(search: String, she: String, rarity: Int, selectedOnly: Boolean,
    selectedIds: List<Long>): Int

  @Query("SELECT she FROM word_cases WHERE she != '' GROUP BY she ORDER BY MIN(id)")
  suspend fun groups(): List<String>

  @Query("SELECT * FROM word_cases WHERE id IN (:ids)")
  suspend fun byIds(ids: List<Long>): List<WordCaseEntity>

  @Query("DELETE FROM word_cases")
  suspend fun deleteAllWordCases()
}

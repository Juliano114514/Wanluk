package com.wanluk.lib_room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wanluk.lib_room.entities.WordCaseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WordCaseDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertWordCases(wordCases: List<WordCaseEntity>)

  @Query("SELECT * FROM word_cases")
  fun getAllWordCases(): Flow<List<WordCaseEntity>>

  @Query("SELECT * FROM word_cases WHERE she = :sheInput")
  fun getWordCasesByShe(sheInput: String): Flow<List<WordCaseEntity>>

  @Query("SELECT * FROM word_cases WHERE yun = :yunInput")
  fun getWordCasesByYun(yunInput: String): Flow<List<WordCaseEntity>>

  @Query("SELECT COUNT(*) FROM word_cases")
  suspend fun getWordCaseCount(): Int

  @Query("DELETE FROM word_cases")
  suspend fun deleteAllWordCases()
}

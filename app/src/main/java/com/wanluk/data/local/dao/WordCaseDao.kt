package com.wanluk.data.local.dao

/**
 * @Author: JULIANO
 * @CreateDate: 2026/3/22 17:35
 * @Description:
 */

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import com.wanluk.data.local.entities.WordCaseEntity

@Dao
interface WordCaseDao {
  // 批量导入字例（用于方案中的一键导入功能）
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertWordCases(wordCases: List<WordCaseEntity>)

  // 默认展示全部字例，返回 Flow 以便 UI 自动响应数据变化
  @Query("SELECT * FROM word_cases")
  fun getAllWordCases(): Flow<List<WordCaseEntity>>

  // 核心筛选：按「摄」分类展示
  @Query("SELECT * FROM word_cases WHERE she = :sheInput")
  fun getWordCasesByShe(sheInput: String): Flow<List<WordCaseEntity>>

  // 拓展能力：按「韵」进行二次筛选
  @Query("SELECT * FROM word_cases WHERE yun = :yunInput")
  fun getWordCasesByYun(yunInput: String): Flow<List<WordCaseEntity>>
}
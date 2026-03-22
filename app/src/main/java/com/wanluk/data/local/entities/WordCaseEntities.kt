package com.wanluk.data.local.entities

/**
 * @Author: JULIANO
 * @CreateDate: 2026/3/22 17:31
 * @Description:
 */
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "word_cases")
data class WordCaseEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,

  // 核心音韵字段
  @ColumnInfo(name = "sheng") val sheng: String,       // 聲 (如：端)
  @ColumnInfo(name = "hu") val hu: String,             // 呼 (开口/合口等)
  @ColumnInfo(name = "deng") val deng: Int,            // 等 (一/二/三/四)
  @ColumnInfo(name = "yun") val yun: String,           // 韻 (如：东)
  @ColumnInfo(name = "diao") val diao: String,         // 調 (平/上/去/入)
  @ColumnInfo(name = "zu") val zu: String?,            // 組 (辅助分类，可选)
  @ColumnInfo(name = "she") val she: String,           // 攝 (如：通)

  // 基础内容字段
  @ColumnInfo(name = "core_char") val coreChar: String,// 核心例字
  // 例子组词，暂定方案为:合并为一个特定分隔符(如逗号)的字符串存入
  @ColumnInfo(name = "phrases") val phrases: String?,
  @ColumnInfo(name = "remark") val remark: String?     // 备注
)
package com.wanluk.lib_room.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "word_cases")
data class WordCaseEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,

  @ColumnInfo(name = "sheng") val sheng: String,
  @ColumnInfo(name = "hu") val hu: String,
  @ColumnInfo(name = "deng") val deng: Int,
  @ColumnInfo(name = "yun") val yun: String,
  @ColumnInfo(name = "diao") val diao: String,
  @ColumnInfo(name = "zu") val zu: String?,
  @ColumnInfo(name = "she") val she: String,

  @ColumnInfo(name = "core_char") val coreChar: String,
  @ColumnInfo(name = "phrases") val phrases: String?,
  @ColumnInfo(name = "remark") val remark: String?,
)

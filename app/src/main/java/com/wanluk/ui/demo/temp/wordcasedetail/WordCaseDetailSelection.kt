package com.wanluk.ui.demo.temp.wordcasedetail

import com.wanluk.lib_room.entities.WordCaseEntity
import androidx.compose.ui.geometry.Rect

/**
 * 演示用详情浮层选中态（临时）。
 */
data class WordCaseDetailSelection(
  val item: WordCaseEntity,
  val anchorBounds: Rect,
)

package com.wanluk.ui.demo.temp.displaymode

import com.wanluk.libroom.entity.WordCaseEntity

/**
 * 卡片主显示内容模式。
 *
 * - [CHAR]   显示字目（coreChar）
 * - [PHRASES] 显示组词（phrases）；无组词时自动回退到字目
 */
enum class DisplayMode(val label: String) {
  CHAR("字目"),
  PHRASES("组词"),
}

/** 剥离所有括号及其内容，仅保留括号外的繁体文本。 */
private val PAREN_CONTENT = Regex("[\\(（][^\\)）]*[\\)）]")

private fun stripParens(text: String): String =
  text.replace(PAREN_CONTENT, "").trim()

/**
 * 根据当前模式解析卡片主显示文本。
 *
 * 逻辑集中于此，卡片只调用此函数，切换细节与 UI 完全解耦。
 * 有括号的组词仅保留括号外的繁体部分。
 */
fun DisplayMode.resolveMainText(item: WordCaseEntity): String =
  when (this) {
    DisplayMode.CHAR -> item.coreChar
    DisplayMode.PHRASES -> {
      val raw = item.phrases?.takeIf { it.isNotBlank() } ?: return item.coreChar
      stripParens(raw).ifEmpty { item.coreChar }
    }
  }

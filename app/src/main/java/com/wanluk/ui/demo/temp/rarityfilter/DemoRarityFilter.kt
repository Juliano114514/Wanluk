package com.wanluk.ui.demo.temp.rarityfilter

import com.wanluk.lib_room.entities.WordCaseEntity

/**
 * 演示用罕度筛选（临时）。
 *
 * 正式版发布前删除整个 `temp.rarityfilter` 包及相关 ViewModel / Screen 引用。
 */
enum class DemoRarityFilter(val label: String, val rarity: Int?) {
  ALL("全部", null),
  LEVEL_0("罕度 0", 0),
  LEVEL_1("罕度 1", 1),
  LEVEL_2("罕度 2", 2),
  LEVEL_3("罕度 3", 3),
}

fun List<WordCaseEntity>.filterByDemoRarity(filter: DemoRarityFilter): List<WordCaseEntity> {
  val target = filter.rarity ?: return this
  return filter { it.rarity == target }
}

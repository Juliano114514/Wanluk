package com.wanluk.ui.demo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wanluk.lib_room.entities.WordCaseEntity
import com.wanluk.lib_room.repository.WordCaseRepository
import com.wanluk.ui.demo.temp.rarityfilter.DemoRarityFilter
import com.wanluk.ui.demo.temp.rarityfilter.filterByDemoRarity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WordCaseDemoViewModel(
  private val repository: WordCaseRepository,
) : ViewModel() {

  // TODO(demo): 发布前删除 demoRarityFilter 及相关 combine 逻辑
  private val demoRarityFilter = MutableStateFlow(DemoRarityFilter.ALL)

  val demoRarityFilterState: StateFlow<DemoRarityFilter> = demoRarityFilter

  val wordCases: StateFlow<List<WordCaseEntity>> =
    combine(repository.getAllWordCases(), demoRarityFilter) { cases, filter ->
      cases.filterByDemoRarity(filter)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  init {
    viewModelScope.launch {
      repository.ensureBuiltinWordCasesImported()
    }
  }

  fun onDemoRarityFilterSelected(filter: DemoRarityFilter) {
    demoRarityFilter.value = filter
  }
}

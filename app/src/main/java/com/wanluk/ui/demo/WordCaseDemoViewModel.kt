package com.wanluk.ui.demo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wanluk.lib_room.entities.WordCaseEntity
import com.wanluk.lib_room.repository.WordCaseRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WordCaseDemoViewModel(
  private val repository: WordCaseRepository,
) : ViewModel() {

  val wordCases: StateFlow<List<WordCaseEntity>> =
    repository.getAllWordCases()
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  init {
    viewModelScope.launch {
      repository.ensureBuiltinWordCasesImported()
    }
  }
}

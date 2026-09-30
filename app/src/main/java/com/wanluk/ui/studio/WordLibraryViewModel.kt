package com.wanluk.ui.studio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wanluk.libroom.entity.WordCaseEntity
import com.wanluk.libroom.repository.WordCaseRepository
import com.wanluk.libroom.repository.WordFilter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WordLibraryState(
  val filter: WordFilter? = null,
  val words: List<WordCaseEntity> = emptyList(),
  val total: Int = 0,
  val groups: List<String> = emptyList(),
  val loading: Boolean = false,
  val error: String? = null,
)

/** No database query runs until a library page is actually composed. */
class WordLibraryViewModel(private val repository: WordCaseRepository) : ViewModel() {
  private val mutableState = MutableStateFlow(WordLibraryState())
  val state = mutableState.asStateFlow()
  private var request: Job? = null

  fun search(filter: WordFilter) {
    if (state.value.filter == filter) return
    val debounce = state.value.filter?.search != filter.search && filter.search.isNotEmpty()
    request?.cancel()
    mutableState.value = WordLibraryState(filter = filter, groups = state.value.groups, loading = true)
    load(reset = true, debounce = debounce)
  }

  fun loadMore() {
    val current = state.value
    if (current.loading || current.error != null || current.words.size >= current.total) return
    load(reset = false)
  }

  fun retry() = load(reset = state.value.words.isEmpty())

  private fun load(reset: Boolean, debounce: Boolean = false) {
    val current = state.value
    val filter = current.filter ?: return
    mutableState.update { it.copy(loading = true, error = null) }
    request = viewModelScope.launch {
      try {
        if (debounce) delay(250)
        val groups = if (current.groups.isEmpty()) repository.groups() else current.groups
        val total = if (reset) repository.count(filter) else current.total
        val page = repository.page(filter, if (reset) 0 else current.words.last().id)
        mutableState.value = current.copy(words = if (reset) page else current.words + page,
          total = total, groups = groups, loading = false, error = null)
      } catch (error: Exception) {
        if (error is CancellationException) throw error
        mutableState.update { it.copy(loading = false, error = "字库读取失败，请重试") }
      }
    }
  }
}

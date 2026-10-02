package com.wanluk.ui.studio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wanluk.foundation.survey.WordEntry
import com.wanluk.foundation.survey.WordFacet
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
  val words: List<WordEntry> = emptyList(),
  val total: Int = 0,
  val groups: List<String> = emptyList(),
  val facets: List<WordFacet> = emptyList(),
  val loading: Boolean = false,
  val selecting: Boolean = false,
  val error: String? = null,
)

/** No database query runs until a library page is actually composed. */
class WordLibraryViewModel(private val repository: WordCaseRepository) : ViewModel() {
  private val mutableState = MutableStateFlow(WordLibraryState())
  val state = mutableState.asStateFlow()
  private var request: Job? = null
  private var selectionRequest: Job? = null
  private var selectionVersion = 0

  fun search(filter: WordFilter) {
    if (state.value.filter == filter) return
    val debounce = state.value.filter?.search != filter.search && filter.search.isNotEmpty()
    request?.cancel()
    selectionRequest?.cancel()
    selectionVersion++
    mutableState.value = WordLibraryState(filter = filter, groups = state.value.groups, facets = state.value.facets, loading = true)
    load(reset = true, debounce = debounce)
  }

  fun favorite(word: WordEntry, enabled: Boolean, onSaved: (WordEntry) -> Unit) {
    viewModelScope.launch {
      try {
        repository.setFavorite(word, enabled)
        onSaved(word.copy(favorite = enabled))
        request?.cancel()
        load(reset = true)
      } catch (error: Exception) {
        if (error is CancellationException) throw error
        mutableState.update { it.copy(error = "收藏未保存，请重试") }
      }
    }
  }

  fun loadMore() {
    val current = state.value
    if (current.loading || current.error != null || current.words.size >= current.total) return
    load(reset = false)
  }

  fun retry() = load(reset = state.value.words.isEmpty())

  fun selectAll(existing: List<Long>, maximum: Int, onSelected: (List<Long>) -> Unit) {
    val filter = state.value.filter ?: return
    if (state.value.selecting) return
    val version = ++selectionVersion
    mutableState.update { it.copy(selecting = true, error = null) }
    selectionRequest = viewModelScope.launch {
      try {
        val ids = repository.matchingIds(filter)
        val selected = (existing + ids).distinct()
        require(selected.size <= maximum) { "当前筛选超过可选上限 $maximum，请缩小筛选范围" }
        if (selectionVersion == version && state.value.filter == filter) onSelected(selected)
      } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (selectionVersion == version) mutableState.update { it.copy(error = error.message?.take(160) ?: "选字失败，请重试") }
      } finally {
        if (selectionVersion == version) mutableState.update { it.copy(selecting = false) }
      }
    }
  }

  private fun load(reset: Boolean, debounce: Boolean = false) {
    val current = state.value
    val filter = current.filter ?: return
    mutableState.update { it.copy(loading = true, error = null) }
    request = viewModelScope.launch {
      try {
        if (debounce) delay(250)
        val groups = if (current.groups.isEmpty()) repository.groups() else current.groups
        val facets = if (current.facets.isEmpty()) repository.facets() else current.facets
        val total = if (reset) repository.count(filter) else current.total
        val page = repository.page(filter, if (reset) 0 else current.words.last().id)
        mutableState.update { latest -> latest.copy(words = if (reset) page else current.words + page,
          total = total, groups = groups, facets = facets, loading = false) }
      } catch (error: Exception) {
        if (error is CancellationException) throw error
        mutableState.update { it.copy(loading = false, error = "字库读取失败，请重试") }
      }
    }
  }
}

package com.wanluk.ui.studio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wanluk.foundation.survey.ProgressPage
import com.wanluk.libroom.repository.SurveyRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecordingProgressState(
  val sessionId: String = "", val status: String = "", val page: ProgressPage = ProgressPage(),
  val anchors: List<Int> = listOf(-1), val loading: Boolean = false, val error: String? = null,
) {
  val pageNumber get() = anchors.size
  val hasNext get() = pageNumber * 80 < page.total && page.entries.isNotEmpty()
}

class RecordingProgressViewModel(private val repository: SurveyRepository) : ViewModel() {
  private val mutable = MutableStateFlow(RecordingProgressState())
  val state = mutable.asStateFlow()
  private var request: Job? = null
  private var generation = 0
  fun open(id: String) {
    if (mutable.value.sessionId != id) mutable.value = RecordingProgressState(sessionId = id)
    load()
  }
  fun filter(status: String) { mutable.update { it.copy(status = status, anchors = listOf(-1)) }; load() }
  fun next() {
    val current = mutable.value
    if (current.loading || !current.hasNext) return
    mutable.update { it.copy(anchors = it.anchors + current.page.entries.last().step.position) }
    load()
  }
  fun previous() {
    if (mutable.value.loading || mutable.value.anchors.size <= 1) return
    mutable.update { it.copy(anchors = it.anchors.dropLast(1)) }; load()
  }
  fun all(onSelected: (List<String>) -> Unit) {
    val current = mutable.value
    if (current.loading) return
    val version = ++generation
    mutable.update { it.copy(loading = true, error = null) }
    request = viewModelScope.launch {
      try {
        val ids = repository.progressPositions(current.sessionId, current.status)
        if (generation == version && mutable.value.sessionId == current.sessionId && mutable.value.status == current.status) onSelected(ids.map(Int::toString))
      } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (generation == version) mutable.update { it.copy(error = "选择失败，请重试") }
      } finally { if (generation == version) mutable.update { it.copy(loading = false) } }
    }
  }
  fun load() {
    val version = ++generation
    request?.cancel()
    val current = mutable.value
    if (current.sessionId.isEmpty()) return
    mutable.update { it.copy(loading = true, error = null) }
    request = viewModelScope.launch {
      try {
        val page = repository.progress(current.sessionId, current.status, current.anchors.last())
        if (generation == version) mutable.update { it.copy(page = page, loading = false) }
      } catch (error: Exception) {
        if (error is CancellationException) throw error
        if (generation == version) mutable.update { it.copy(loading = false, error = "进度读取失败，请重试") }
      }
    }
  }
}

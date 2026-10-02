package com.wanluk.ui.studio

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wanluk.foundation.survey.*
import com.wanluk.libroom.repository.CsvPreviewRow
import com.wanluk.libroom.repository.SurveyCsvMapper
import com.wanluk.libroom.repository.WordCaseRepository
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SurveyCsvState(val open: Boolean = false, val busy: Boolean = false, val title: String = "CSV 调查包",
  val table: SurveyCsvTable? = null, val mapping: Map<CsvField, Int> = emptyMap(),
  val rows: List<CsvPreviewRow>? = null, val selected: Set<String> = emptySet(), val message: String? = null,
  val planType: RecordingPlanType = RecordingPlanType.CHARACTER)

class SurveyCsvViewModel(context: Context, private val words: WordCaseRepository) : ViewModel() {
  private val resolver = context.applicationContext.contentResolver
  private val mutable = MutableStateFlow(SurveyCsvState())
  val state = mutable.asStateFlow()
  fun close() { if (!mutable.value.busy) mutable.value = SurveyCsvState() }
  fun title(value: String) { mutable.update { it.copy(title = value.take(120)) } }
  fun planType(value: RecordingPlanType) {
    if (!mutable.value.busy) mutable.update { it.copy(planType = value, rows = null, selected = emptySet(), message = null) }
  }
  private fun defaults(type: RecordingPlanType) = SurveyDefaults(displayType = if (type == RecordingPlanType.WORD) "word" else "character",
    instruction = if (type == RecordingPlanType.PASSAGE) "请用方言读出下面的文段。" else null)
  fun mapping(field: CsvField, index: Int?) {
    if (mutable.value.busy) return
    mutable.update { it.copy(mapping = if (index == null) it.mapping - field else it.mapping + (field to index), message = null) }
  }
  fun remap() { if (!mutable.value.busy) mutable.update { it.copy(rows = null, message = null) } }
  fun toggle(id: String) {
    if (!mutable.value.busy && mutable.value.rows?.any { it.id == id && it.item != null } == true)
      mutable.update { it.copy(selected = if (id in it.selected) it.selected - id else it.selected + id) }
  }
  fun all() { if (!mutable.value.busy) mutable.update { it.copy(selected = it.rows.orEmpty().filter { row -> row.item != null }.mapTo(hashSetOf()) { row -> row.id }) } }
  fun clear() { if (!mutable.value.busy) mutable.update { it.copy(selected = emptySet()) } }
  fun remove(id: String) {
    if (!mutable.value.busy) mutable.update { it.copy(table = it.table?.copy(records = it.table.records.filter { row -> row.id != id }),
      rows = it.rows?.filter { row -> row.id != id }, selected = it.selected - id) }
  }
  fun read(uri: Uri) = work {
    mutable.value = SurveyCsvState(open = true, busy = true)
    val loaded = withContext(Dispatchers.IO) {
      val name = runCatching { resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst() && cursor.columnCount > 0) cursor.getString(0) else null
      } }.getOrNull()?.substringBeforeLast('.')?.take(120)?.takeIf { it.isNotBlank() } ?: "CSV 调查包"
      val table = requireNotNull(resolver.openInputStream(uri)) { "无法读取 CSV" }.use(SurveyCsv::read)
      name to table
    }
    mutable.update { it.copy(title = loaded.first, table = loaded.second, mapping = SurveyCsv.mapping(loaded.second), rows = null) }
  }
  fun preview() = work {
    val current = mutable.value
    val rows = withContext(Dispatchers.IO) { SurveyCsvMapper(words.builtinCatalog()).preview(requireNotNull(current.table), current.mapping,
      defaults(current.planType), current.planType) }
    mutable.update { it.copy(rows = rows, selected = rows.filter { row -> row.item != null }.mapTo(hashSetOf()) { row -> row.id }) }
  }
  fun draft(onReady: (SurveyPackage) -> Unit) = work {
    val current = mutable.value
    val task = withContext(Dispatchers.IO) {
      val items = current.rows.orEmpty().filter { it.id in current.selected }.map { requireNotNull(it.item) { "所选行存在错误" } }
      val task = SurveyPackage(packageId = UUID.randomUUID().toString(), title = current.title.trim(), items = items,
        defaults = defaults(current.planType), planType = current.planType)
      SurveyPackageCodec.encode(task)
      RecordingTaskSnapshot.chunks(task)
      task
    }
    // Finish the read/validation operation before handing the editable draft to Studio.
    mutable.value = SurveyCsvState()
    onReady(task)
  }
  fun saveTemplate(uri: Uri?) {
    if (uri == null) return
    work {
      withContext(Dispatchers.IO) {
        requireNotNull(resolver.openOutputStream(uri, "wt")) { "无法写入 CSV 模板" }.use { it.write(SurveyCsv.template.toByteArray(Charsets.UTF_8)) }
      }
      mutable.update { it.copy(open = true, message = "CSV 模板已保存") }
    }
  }
  private fun work(block: suspend () -> Unit) {
    if (mutable.value.busy) return
    mutable.update { it.copy(open = true, busy = true, message = null) }
    viewModelScope.launch {
      try { block() }
      catch (error: Exception) {
        if (error is CancellationException) throw error
        val message = if (error is IllegalArgumentException || error is IllegalStateException) error.message?.take(200)
          else "CSV 读写失败，请确认 UTF-8 格式、存储空间和文件权限"
        mutable.update { it.copy(message = message ?: "CSV 操作未完成，请重试") }
      } finally { mutable.update { it.copy(busy = false) } }
    }
  }
}

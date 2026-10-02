package com.wanluk.ui.studio

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wanluk.foundation.survey.*
import com.wanluk.libroom.repository.PackageImportStatus
import com.wanluk.libroom.repository.SurveyRepository
import com.wanluk.libroom.repository.WordCaseRepository
import com.wanluk.libroom.repository.SurveyTransferMapper.Companion.annotation
import com.wanluk.libroom.repository.SurveyTransferMapper.Companion.prompt
import com.wanluk.libroom.repository.SurveyTransferMapper.Companion.sourceNumber
import com.wanluk.libroom.repository.SurveyTransferMapper.Companion.text
import com.wanluk.libsurveytransfer.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PreparedSurveyImport(val name: String, val document: SurveyTransferDocument? = null,
  val summary: SurveyPackageSummary? = null, val status: PackageImportStatus? = null,
  val error: String? = null, val duplicateInBatch: Boolean = false, val imported: Boolean = false, val id: String = java.util.UUID.randomUUID().toString()) {
  val valid: Boolean get() = document != null && error == null && !duplicateInBatch
}
data class SurveyQrExport(val documents: List<SurveyTransferDocument>, val qrText: String?) {
  val title: String get() = if (documents.size == 1) documents.first().title else "${documents.size} 个方案"
}
enum class TransferExportFormat { PNG, PROTOBUF, ZIP }
data class SurveyTransferState(val importing: Boolean = false,
  val imports: List<PreparedSurveyImport> = emptyList(), val completed: Boolean = false,
  val exporting: SurveyQrExport? = null, val busy: Boolean = false, val message: String? = null,
  val exported: ExportedDocument? = null)

class SurveyTransferViewModel(context: Context, private val repository: SurveyRepository,
  private val words: WordCaseRepository) : ViewModel() {
  private val resolver = context.applicationContext.contentResolver
  private val mutableState = MutableStateFlow(SurveyTransferState())
  val state = mutableState.asStateFlow()
  private var pendingExport: Pair<TransferExportFormat, SurveyQrExport>? = null

  fun openImport() { if (!state.value.busy) mutableState.value = SurveyTransferState(importing = true) }
  fun close() {
    if (state.value.busy) return
    pendingExport = null
    mutableState.value = SurveyTransferState()
  }
  fun message(value: String?) { mutableState.update { it.copy(message = value) } }
  fun clearExport() { mutableState.update { it.copy(exported = null) } }

  fun generate(summary: SurveyPackageSummary) = generatePlans(listOf(summary), singleQr = true)
  fun generateZip(summaries: List<SurveyPackageSummary>) = generatePlans(summaries, singleQr = false)

  fun generateTask(task: SurveyPackage) = work {
    val export = withContext(Dispatchers.IO) {
      val document = words.transferMapper().document(task)
      SurveyTransferCodec.encode(document)
      SurveyQrExport(listOf(document), null)
    }
    mutableState.update { it.copy(importing = false, imports = emptyList(), exporting = export) }
  }

  fun generateWords(ids: List<Long>, type: SurveyItemType) = work {
    val document = withContext(Dispatchers.IO) {
      val selected = words.selected(ids)
      val task = SurveyPackage(title = "所选字目", defaults = SurveyDefaults(displayType = type.value),
        planType = if (type == SurveyItemType.WORD) RecordingPlanType.WORD else RecordingPlanType.CHARACTER,
        items = selected.map { word ->
            SurveyItem(itemId = "i_${java.util.UUID.randomUUID()}", type = type.value, text = word.text(type.value),
              instruction = word.prompt(type.value), phonology = word.annotation(), wordId = word.sourceNumber()?.toString())
        })
      words.transferMapper().document(task).also { SurveyTransferCodec.encode(it) }
    }
    mutableState.update { it.copy(importing = false, imports = emptyList(), exporting = SurveyQrExport(listOf(document), null)) }
  }

  private fun generatePlans(summaries: List<SurveyPackageSummary>, singleQr: Boolean) = work {
    require(summaries.size in 1..SurveyTransferFiles.MAX_PLANS) { "请选择 1–32 个方案" }
    val export = withContext(Dispatchers.IO) {
      val mapper = words.transferMapper()
      var expanded = 0L
      val documents = summaries.map {
        val document = mapper.document(repository.readPackage(it.packageId, it.revision))
        SurveyTransferCodec.encode(document)
        expanded += SurveyPackageCodec.encode(mapper.restore(document)).toByteArray(Charsets.UTF_8).size
        require(expanded <= SurveyTransferFiles.MAX_EXPANDED_BYTES) { "本批方案还原后超过 64 MB，请减少所选方案" }
        document
      }
      SurveyQrExport(documents, if (singleQr) SurveyQrCodec.encode(documents.single()) else null)
    }
    mutableState.update { it.copy(importing = false, imports = emptyList(), exporting = export) }
  }

  fun scan(text: String) = work {
    mutableState.update { it.copy(importing = true, completed = false, imports = emptyList(), exporting = null) }
    val document = withContext(Dispatchers.Default) { SurveyQrCodec.decode(text) }
    prepareImports(listOf(TransferFileResult("二维码", document)))
  }

  fun readFile(uri: Uri) = work {
    mutableState.update { it.copy(importing = true, completed = false, imports = emptyList(), exporting = null) }
    val files = withContext(Dispatchers.IO) { SurveyTransferFiles.read(resolver, uri) }
    prepareImports(files)
  }

  private suspend fun prepareImports(files: List<TransferFileResult>) {
    val prepared = withContext(Dispatchers.IO) {
      val mapper = words.transferMapper()
      val rows = mutableListOf<PreparedSurveyImport>()
      val seen = mutableMapOf<Pair<String, Int>, Int>()
      val conflicts = mutableSetOf<Pair<String, Int>>()
      var expanded = 0L
      files.forEach { file ->
        val document = file.document
        if (document == null) rows += PreparedSurveyImport(file.name, error = file.error ?: "无法识别方案")
        else {
          val key = document.packageId to document.revision
          try {
            val task = mapper.restore(document)
            val priorIndex = seen[key]
            if (key in conflicts) rows += PreparedSurveyImport(file.name, error = "ZIP 内同编号、同版本方案内容冲突")
            else if (priorIndex != null) {
              val previous = requireNotNull(rows[priorIndex].document)
              if (mapper.equivalent(mapper.restore(previous), task)) {
                rows += PreparedSurveyImport(file.name, duplicateInBatch = true, summary = SurveyPackageCodec.summary(SurveyPackageCodec.encode(task)))
              } else {
                conflicts += key
                rows[priorIndex] = rows[priorIndex].copy(error = "ZIP 内同编号、同版本方案内容冲突")
                rows += PreparedSurveyImport(file.name, error = "ZIP 内同编号、同版本方案内容冲突")
              }
            } else {
              val json = SurveyPackageCodec.encode(task)
              expanded += json.toByteArray(Charsets.UTF_8).size
              require(expanded <= SurveyTransferFiles.MAX_EXPANDED_BYTES) { "方案还原总量超过 64 MB，请分批导入" }
              val status = repository.inspectImport(task)
              seen[key] = rows.size
              rows += PreparedSurveyImport(file.name, document, SurveyPackageCodec.summary(json), status)
            }
          } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (expanded > SurveyTransferFiles.MAX_EXPANDED_BYTES) throw error
            rows += PreparedSurveyImport(file.name, error = errorMessage(error))
          }
        }
      }
      rows
    }
    mutableState.update { it.copy(importing = true, completed = false, imports = prepared) }
  }

  fun removeImports(ids: List<String>) {
    val selected = ids.toSet()
    if (!state.value.busy && !state.value.completed) mutableState.update { current -> current.copy(imports = current.imports.filter { it.id !in selected }) }
  }

  fun confirmImport(ids: List<String>? = null) = work {
    if (state.value.completed) return@work
    val rows = state.value.imports.toMutableList()
    val selectedIds = ids?.toSet() ?: rows.mapTo(hashSetOf()) { it.id }
    require(rows.mapTo(hashSetOf()) { it.id }.containsAll(selectedIds)) { "所选方案已变化" }
    val selected = rows.indices.filterTo(hashSetOf()) { rows[it].id in selectedIds }
    require(selected.isNotEmpty() && selected.all { it in rows.indices }) { "请选择要导入的方案" }
    var added = 0
    var duplicates = selected.count { rows[it].duplicateInBatch }
    withContext(Dispatchers.IO) {
      val mapper = words.transferMapper()
      rows.indices.forEach { index ->
        val row = rows[index]
        if (row.valid && index in selected) try {
          val status = repository.importTransferredPackage(mapper.restore(requireNotNull(row.document)))
          rows[index] = row.copy(status = status, imported = status == PackageImportStatus.ADDED)
          when (status) {
            PackageImportStatus.ADDED -> added++
            PackageImportStatus.DUPLICATE -> duplicates++
          }
        } catch (error: Exception) {
          if (error is CancellationException) throw error
          rows[index] = row.copy(error = errorMessage(error))
        }
      }
    }
    mutableState.update { it.copy(imports = rows, completed = true,
      message = "已导入 $added 个方案 · 重复 $duplicates 个 · 失败 ${selected.count { rows[it].error != null }} 个" +
        if (selected.size < rows.size) " · 未选择 ${rows.size - selected.size} 个" else "") }
  }

  fun prepareExport(format: TransferExportFormat): String {
    val export = requireNotNull(state.value.exporting)
    require(format == TransferExportFormat.ZIP || export.documents.size == 1)
    require(format != TransferExportFormat.PNG || export.qrText != null)
    pendingExport = format to export
    val base = if (export.documents.size == 1) "wanluk-${export.documents.first().packageId}-v${export.documents.first().revision}" else "wanluk-surveys"
    return "$base.${when (format) { TransferExportFormat.PNG -> "png"; TransferExportFormat.PROTOBUF -> "pb"; TransferExportFormat.ZIP -> "zip" }}"
  }

  fun saveDocument(uri: Uri?) {
    val pending = pendingExport
    pendingExport = null
    if (uri == null || pending == null) return
    work {
      val (format, export) = pending
      withContext(Dispatchers.IO) {
        requireNotNull(resolver.openOutputStream(uri, "wt")) { "无法写入所选位置" }.use { output ->
          when (format) {
            TransferExportFormat.PNG -> {
              val bitmap = SurveyQrImages.render(requireNotNull(export.qrText))
              try { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "二维码保存失败" } }
              finally { bitmap.recycle() }
            }
            TransferExportFormat.PROTOBUF -> output.write(SurveyTransferCodec.encode(export.documents.single()))
            TransferExportFormat.ZIP -> SurveyTransferFiles.writeZip(output, export.documents)
          }
        }
      }
      mutableState.update { it.copy(exported = ExportedDocument(uri, when (format) {
        TransferExportFormat.PNG -> "image/png"; TransferExportFormat.PROTOBUF -> "application/octet-stream"; TransferExportFormat.ZIP -> "application/zip"
      }), message = "已保存") }
    }
  }

  private fun errorMessage(error: Exception): String = when (error) {
    is IllegalArgumentException, is IllegalStateException -> error.message?.take(240) ?: "方案校验失败"
    is SecurityException -> "无法访问所选文件，请重新选择"
    else -> "方案交换未完成，请重试；本地方案保持不变"
  }

  private fun work(block: suspend () -> Unit) {
    if (state.value.busy) return
    mutableState.update { it.copy(busy = true, message = null) }
    viewModelScope.launch {
      try { block() }
      catch (error: Exception) {
        if (error is CancellationException) throw error
        mutableState.update { it.copy(message = errorMessage(error)) }
      } finally { mutableState.update { it.copy(busy = false) } }
    }
  }
}

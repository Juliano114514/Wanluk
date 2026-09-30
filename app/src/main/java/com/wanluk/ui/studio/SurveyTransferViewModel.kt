package com.wanluk.ui.studio

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wanluk.foundation.survey.SurveyPackage
import com.wanluk.libroom.repository.SurveyRepository
import com.wanluk.libsurveytransfer.SurveyQrCodec
import com.wanluk.libsurveytransfer.SurveyQrCollector
import com.wanluk.libsurveytransfer.SurveyQrImages
import com.wanluk.libsurveytransfer.SurveyQrProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SurveyQrExport(val task: SurveyPackage, val frames: List<String>)
data class SurveyTransferState(
  val importing: Boolean = false,
  val progress: SurveyQrProgress = SurveyQrProgress(),
  val exporting: SurveyQrExport? = null,
  val busy: Boolean = false,
  val message: String? = null,
  val exported: ExportedDocument? = null,
)

class SurveyTransferViewModel(context: Context, private val repository: SurveyRepository) : ViewModel() {
  private val resolver = context.applicationContext.contentResolver
  private val collector = SurveyQrCollector()
  private val mutableState = MutableStateFlow(SurveyTransferState())
  val state = mutableState.asStateFlow()
  private var pendingImage: String? = null

  fun openImport() {
    if (state.value.busy) return
    collector.reset()
    mutableState.value = SurveyTransferState(importing = true)
  }

  fun close() {
    if (state.value.busy) return
    collector.reset()
    mutableState.value = SurveyTransferState()
  }

  fun message(value: String?) { mutableState.update { it.copy(message = value) } }
  fun clearExport() { mutableState.update { it.copy(exported = null) } }

  fun generate(task: SurveyPackage) = work {
    val frames = withContext(Dispatchers.Default) { SurveyQrCodec.encode(task) }
    mutableState.update { it.copy(exporting = SurveyQrExport(task, frames)) }
  }

  fun scan(text: String) = work {
    if (!state.value.importing || state.value.progress.task != null) return@work
    val progress = withContext(Dispatchers.Default) { collector.accept(text) }
    mutableState.update { it.copy(progress = progress) }
  }

  fun readImage(uri: Uri) = work {
    if (!state.value.importing || state.value.progress.task != null) return@work
    val progress = withContext(Dispatchers.IO) { collector.accept(SurveyQrImages.read(resolver, uri)) }
    mutableState.update { it.copy(progress = progress) }
  }

  fun confirmImport() = work {
    val task = requireNotNull(state.value.progress.task)
    withContext(Dispatchers.IO) { repository.importPackage(task) }
    collector.reset()
    mutableState.update { it.copy(importing = false, progress = SurveyQrProgress(), message = "方案已导入，可从调查包列表开始录制") }
  }

  fun prepareImage(index: Int): String {
    val export = requireNotNull(state.value.exporting)
    pendingImage = export.frames[index]
    return "wanluk-qr-${export.task.packageId}-${index + 1}-of-${export.frames.size}.png"
  }

  fun saveImage(uri: Uri?) {
    val text = pendingImage
    pendingImage = null
    if (uri == null || text == null) return
    work {
      withContext(Dispatchers.IO) {
        val bitmap = SurveyQrImages.render(text)
        try {
          requireNotNull(resolver.openOutputStream(uri, "wt")) { "无法写入所选位置" }.use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "二维码图片保存失败" }
          }
        } finally { bitmap.recycle() }
      }
      mutableState.update { it.copy(exported = ExportedDocument(uri, "image/png")) }
    }
  }

  private fun work(block: suspend () -> Unit) {
    if (state.value.busy) return
    mutableState.update { it.copy(busy = true, message = null) }
    viewModelScope.launch {
      try { block() }
      catch (error: Exception) {
        if (error is CancellationException) throw error
        mutableState.update { it.copy(
          progress = if (it.progress.task == null) collector.progress else it.progress,
          message = when (error) {
            is IllegalArgumentException, is IllegalStateException -> error.message?.take(240) ?: "方案识别失败，请重试"
            is SecurityException -> "无法访问所选图片，请重新选择"
            else -> "方案交换未完成，请重试或使用 JSON 文件；本地方案保持不变"
          }) }
      } finally { mutableState.update { it.copy(busy = false) } }
    }
  }
}

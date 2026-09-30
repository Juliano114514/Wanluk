package com.wanluk.ui.studio

import com.wanluk.libcomposeui.AppButton as Button
import com.wanluk.libcomposeui.AppOutlinedButton as OutlinedButton
import com.wanluk.libcomposeui.AppTextButton as TextButton

import com.wanluk.libcomposeui.ActionSymbol

import com.wanluk.libcomposeui.AppDialog
import com.wanluk.libcomposeui.ActionIcon

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.wanluk.foundation.survey.SurveyPackage
import com.wanluk.libsurveytransfer.SurveyQrImages
import com.wanluk.libsurveytransfer.SurveyQrScanContract
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun SurveyTransferDialogs(viewModel: SurveyTransferViewModel, state: SurveyTransferState,
  onExportJson: (SurveyPackage) -> Unit) {
  val context = LocalContext.current
  val scanner = rememberLauncherForActivityResult(SurveyQrScanContract()) { text -> text?.let(viewModel::scan) }
  fun scan() {
    try { scanner.launch(Unit) }
    catch (_: Exception) { viewModel.message("无法打开相机，可以选择二维码图片识别") }
  }
  val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
    if (granted) scan() else viewModel.message("相机权限未开启，也可以从图片识别二维码；需要扫码时可到系统设置开启权限")
  }
  val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    uri?.let(viewModel::readImage)
  }
  val imageExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png"), viewModel::saveImage)

  if (state.importing) AppDialog(onDismissRequest = viewModel::close, symbol = ActionSymbol.QR, title = { Text("导入方案二维码") }, text = {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      val progress = state.progress
      val task = progress.task
      if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
      if (task == null) {
        Text(if (progress.total == 0) "扫描或选择方案二维码图片" else
          "已收到 ${progress.received} / ${progress.total} 张，继续扫描剩余二维码。")
        Button(onClick = {
          if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) scan()
          else permission.launch(Manifest.permission.CAMERA)
        }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
          ActionIcon(ActionSymbol.QR); Spacer(Modifier.width(8.dp))
          Text(if (progress.received == 0) "打开相机扫描" else "继续扫描")
        }
        OutlinedButton(onClick = { imagePicker.launch(arrayOf("image/*")) }, enabled = !state.busy,
          modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
          ActionIcon(ActionSymbol.IMAGE); Spacer(Modifier.width(8.dp)); Text("从图片识别")
        }
        if (progress.received > 0) TextButton(onClick = viewModel::openImport, enabled = !state.busy) { Text("重新开始") }
      } else {
        Text(task.title, style = MaterialTheme.typography.titleMedium)
        Text("${task.items.size} 个题目 · ${task.totalSteps} 次录制 · 版本 ${task.revision}")
        if (task.description.isNotBlank()) Text(task.description.take(300))
        Text("确认后保存到本机", color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
  }, confirmButton = {
    if (state.progress.task != null) TextButton(onClick = viewModel::confirmImport, enabled = !state.busy) { Text("确认导入") }
  }, dismissButton = { TextButton(onClick = viewModel::close, enabled = !state.busy) { Text("关闭") } })

  state.exporting?.let { export ->
    var index by rememberSaveable(export.task.packageId, export.task.revision) { mutableIntStateOf(0) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var imageError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(export, index) {
      bitmap = null
      imageError = null
      try { bitmap = withContext(Dispatchers.Default) { SurveyQrImages.render(export.frames[index]) } }
      catch (error: Exception) {
        if (error is CancellationException) throw error
        imageError = "二维码生成失败，可以使用 JSON 文件分享"
      }
    }
    AppDialog(onDismissRequest = viewModel::close, symbol = ActionSymbol.QR, title = { Text("分享方案二维码") }, text = {
      Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(export.task.title, style = MaterialTheme.typography.titleMedium)
        Text(if (export.frames.size == 1) "用韵录扫描，或保存图片" else
          "共 ${export.frames.size} 张，需收齐全部二维码")
        Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
          bitmap?.let { Image(it.asImageBitmap(), "方案二维码，第 ${index + 1} 张，共 ${export.frames.size} 张",
            Modifier.fillMaxSize(), filterQuality = FilterQuality.None) }
            ?: if (imageError == null) CircularProgressIndicator() else Text(imageError.orEmpty())
        }
        if (export.frames.size > 1) Row(verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
          TextButton(onClick = { index-- }, enabled = index > 0 && !state.busy) { Text("上一张") }
          Text("${index + 1} / ${export.frames.size}")
          TextButton(onClick = { index++ }, enabled = index < export.frames.lastIndex && !state.busy) { Text("下一张") }
        }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Button(onClick = { imageExport.launch(viewModel.prepareImage(index)) }, enabled = !state.busy && bitmap != null,
          modifier = Modifier.fillMaxWidth()) { Text(if (export.frames.size == 1) "保存二维码图片" else "保存第 ${index + 1} 张") }
        TextButton(onClick = { onExportJson(export.task) }, enabled = !state.busy) { Text("导出 JSON 文件") }
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
      }
    }, confirmButton = { Button(onClick = viewModel::close, enabled = !state.busy) { Text("完成") } })
  }

  if (!state.importing && state.exporting == null && (state.message != null || state.busy)) {
    AppDialog(onDismissRequest = { if (!state.busy) viewModel.message(null) }, symbol = ActionSymbol.QR, title = { Text("方案交换") }, text = {
      if (state.busy) Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        CircularProgressIndicator(Modifier.size(24.dp)); Text("正在准备二维码…")
      } else Text(state.message.orEmpty())
    }, confirmButton = { Button(onClick = { viewModel.message(null) }, enabled = !state.busy) { Text("关闭") } })
  }
}

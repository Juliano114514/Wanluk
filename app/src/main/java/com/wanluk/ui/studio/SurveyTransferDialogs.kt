package com.wanluk.ui.studio

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.wanluk.libcomposeui.AppButton as Button
import com.wanluk.libcomposeui.AppTextButton as TextButton
import com.wanluk.libcomposeui.AppOutlinedButton as OutlinedButton
import com.wanluk.libcomposeui.AppDialog
import com.wanluk.libcomposeui.ActionSymbol
import com.wanluk.libcomposeui.ActionIcon
import com.wanluk.libroom.repository.PackageImportStatus
import com.wanluk.libsurveytransfer.SurveyQrImages
import com.wanluk.libsurveytransfer.SurveyQrScanContract
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun SurveyTransferDialogs(viewModel: SurveyTransferViewModel, state: SurveyTransferState) {
  val context = LocalContext.current
  val selection = rememberMultiSelection(state.imports.map { it.id }, !state.busy, state.importing && !state.completed)
  var selectionMenu by remember { mutableStateOf(false) }
  val scanner = rememberLauncherForActivityResult(SurveyQrScanContract()) { it?.let(viewModel::scan) }
  fun scan() {
    try { scanner.launch(Unit) }
    catch (_: Exception) { viewModel.message("无法打开相机，可以从文件导入二维码图片") }
  }
  val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
    if (it) scan() else viewModel.message("相机权限未开启，也可以从文件导入")
  }
  val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(viewModel::readFile) }
  val imageExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png"), viewModel::saveDocument)
  val protoExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream"), viewModel::saveDocument)
  val zipExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip"), viewModel::saveDocument)

  if (state.importing) AppDialog(onDismissRequest = { if (!state.busy) { if (selection.active) selection.exit() else viewModel.close() } }, symbol = ActionSymbol.IMPORT,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (state.completed) "导入结果" else "导入方案", Modifier.weight(1f))
        if (!state.completed && state.imports.isNotEmpty()) Box {
          MoreButton(!state.busy) { selectionMenu = true }
          DropdownMenu(selectionMenu, { selectionMenu = false }) {
            DropdownMenuItem(text = { Text("多选") }, leadingIcon = { ActionIcon(ActionSymbol.MULTISELECT) },
              enabled = !selection.active, onClick = { selectionMenu = false; selection.enter() })
          }
        }
      }
    }, text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.imports.isEmpty()) {
          Text("扫描一个方案二维码，或选择方案文件、二维码图片、ZIP")
          Button(onClick = {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) scan()
            else permission.launch(Manifest.permission.CAMERA)
          }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("扫一扫") }
          OutlinedButton(onClick = { filePicker.launch(arrayOf("*/*")) }, enabled = !state.busy,
            modifier = Modifier.fillMaxWidth()) { Text("从文件导入") }
        } else {
          if (selection.active) SelectionToolbar(selection, !state.busy) { selection.all(state.imports.map { it.id }) }
          LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.imports, key = { it.id }) { row ->
              var rowMenu by remember { mutableStateOf(false) }
              Row(Modifier.fillMaxWidth().then(if (selection.active) Modifier.selectable(
                selected = row.id in selection.selected, enabled = !state.busy, role = Role.Checkbox,
                onClick = { selection.toggle(row.id) }) else Modifier), verticalAlignment = Alignment.CenterVertically) {
              if (selection.active) Checkbox(row.id in selection.selected, onCheckedChange = null, enabled = !state.busy)
              Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(row.summary?.title ?: row.name, style = MaterialTheme.typography.titleSmall)
                row.summary?.let { Text("${it.itemCount} 字目 · ${it.totalSteps} 次录制 · v${it.revision}",
                  style = MaterialTheme.typography.bodySmall) }
                Text(row.error ?: when {
                  row.duplicateInBatch || row.status == PackageImportStatus.DUPLICATE -> "重复方案，不再添加"
                  row.imported -> "已导入"
                  state.completed -> "未选择，未导入"
                  else -> "可导入"
                }, color = if (row.error == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                  style = MaterialTheme.typography.bodySmall)
              }
              if (!selection.active && !state.completed) Box {
                MoreButton(!state.busy) { rowMenu = true }
                DropdownMenu(rowMenu, { rowMenu = false }) {
                  DropdownMenuItem(text = { Text("移出预览") }, leadingIcon = { ActionIcon(ActionSymbol.TRASH) },
                    onClick = { rowMenu = false; viewModel.removeImports(listOf(row.id)) })
                }
              }
              }
            }
          }
          if (!state.completed) Text(if (selection.active) "导入所选的有效方案，已有冲突方案保留。" else "确认后导入有效方案，已有冲突方案不会覆盖。", style = MaterialTheme.typography.bodySmall)
          if (selection.active) SelectionActions {
            TextButton(onClick = { viewModel.removeImports(selection.ids); selection.clear() },
              enabled = !state.busy && selection.ids.isNotEmpty()) { Text("移出预览") }
          }
          TextButton(onClick = { selection.exit(); viewModel.openImport() }, enabled = !state.busy) { Text("选择其他文件") }
        }
        state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
      }
    }, confirmButton = {
      if (!state.completed && state.imports.any { it.valid }) Button(onClick = {
        viewModel.confirmImport(if (selection.active) selection.ids else null)
      }, enabled = !state.busy && (!selection.active || state.imports.any { it.id in selection.selected && it.valid })) {
        Text(if (selection.active) "导入所选" else "确认导入")
      }
    }, dismissButton = { TextButton(onClick = viewModel::close, enabled = !state.busy) { Text("关闭") } })

  state.exporting?.let { export ->
    var bitmap by remember(export) { mutableStateOf<Bitmap?>(null) }
    var imageError by remember(export) { mutableStateOf<String?>(null) }
    LaunchedEffect(export) {
      export.qrText?.let {
        try { bitmap = withContext(Dispatchers.Default) { SurveyQrImages.render(it) } }
        catch (error: Exception) {
          if (error is CancellationException) throw error
          imageError = "二维码生成失败，可导出 ZIP"
        }
      }
    }
    val displayedBitmap = bitmap
    DisposableEffect(displayedBitmap) { onDispose { displayedBitmap?.recycle() } }
    AppDialog(onDismissRequest = viewModel::close, symbol = ActionSymbol.EXPORT, title = { Text("分享方案") }, text = {
      Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(export.title, style = MaterialTheme.typography.titleMedium)
        if (export.qrText != null) {
          Text("扫描此二维码即可导入")
          Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
            bitmap?.let { Image(it.asImageBitmap(), "方案二维码", Modifier.fillMaxSize(), filterQuality = FilterQuality.None) }
              ?: if (imageError == null) CircularProgressIndicator() else Text(imageError.orEmpty())
          }
          Button(onClick = { imageExport.launch(viewModel.prepareExport(TransferExportFormat.PNG)) },
            enabled = !state.busy && bitmap != null, modifier = Modifier.fillMaxWidth()) { Text("保存二维码图片") }
        } else Text(if (export.documents.size == 1) "保存方案 ZIP 后即可分享。" else "保存一个 ZIP，接收方一次导入全部方案。")
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Button(onClick = { zipExport.launch(viewModel.prepareExport(TransferExportFormat.ZIP)) },
          enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("导出 ZIP") }
        if (export.documents.size == 1) TextButton(onClick = { protoExport.launch(viewModel.prepareExport(TransferExportFormat.PROTOBUF)) },
          enabled = !state.busy) { Text("导出方案文件") }
        state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
      }
    }, confirmButton = { TextButton(onClick = viewModel::close, enabled = !state.busy) { Text("完成") } })
  }
  if (!state.importing && state.exporting == null && (state.message != null || state.busy)) {
    AppDialog(onDismissRequest = { if (!state.busy) viewModel.message(null) }, symbol = ActionSymbol.EXPORT,
      title = { Text("方案交换") }, text = {
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth()) else Text(state.message.orEmpty())
      }, confirmButton = { TextButton(onClick = { viewModel.message(null) }, enabled = !state.busy) { Text("关闭") } })
  }
}

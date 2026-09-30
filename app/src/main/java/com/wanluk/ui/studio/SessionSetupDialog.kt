package com.wanluk.ui.studio

import com.wanluk.libcomposeui.ActionSymbol

import com.wanluk.libcomposeui.AppDialog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wanluk.foundation.survey.SurveyPackage
import com.wanluk.libsettings.RecorderSettings
import com.wanluk.libsettingsui.RecorderProfileFields
import com.wanluk.libsettingsui.RememberRecorderOption

@Composable
internal fun SessionSetupDialog(task: SurveyPackage, settings: RecorderSettings, enabled: Boolean,
  onDismiss: () -> Unit, onStart: (RecorderSettings) -> Unit) {
  var alias by rememberSaveable(task.packageId, task.revision) { mutableStateOf(settings.alias) }
  var dialect by rememberSaveable(task.packageId, task.revision) { mutableStateOf(settings.dialect) }
  var doNotAsk by rememberSaveable(task.packageId, task.revision) { mutableStateOf(settings.doNotAskAgain) }
  var editProfile by rememberSaveable(task.packageId, task.revision) { mutableStateOf(settings.shouldAsk) }
  AppDialog(onDismissRequest = { if (enabled) onDismiss() }, symbol = ActionSymbol.MIC, title = { Text("一起留住家乡话吧") }, text = {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text("这次录制《${task.title}》，按您平时说话的样子来就好。")
      if (editProfile) {
        Text("先认识一下您，这些都可以不填。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        RecorderProfileFields(alias, { alias = it }, dialect, { dialect = it }, enabled)
        RememberRecorderOption(doNotAsk, { doNotAsk = it }, enabled)
      } else {
        Text(listOf(alias.ifBlank { "未填写称呼" }, dialect.ifBlank { "未填写方言" }).joinToString(" · "))
        TextButton(onClick = { editProfile = true }, enabled = enabled) { Text("换个人录 / 修改信息") }
      }
      Text("录音会保存在本机，您可以自行导出分享。请先了解录制用途；如果帮他人录制，请征得对方同意。",
        style = MaterialTheme.typography.bodySmall)
      Text("点击“开始吧”，表示已了解用途并同意本次录制。", style = MaterialTheme.typography.bodySmall)
    }
  }, confirmButton = {
    Button(onClick = { onStart(settings.copy(alias = alias, dialect = dialect, doNotAskAgain = doNotAsk)) }, enabled = enabled) { Text("开始吧") }
  }, dismissButton = { TextButton(onClick = onDismiss, enabled = enabled) { Text("再等等") } })
}

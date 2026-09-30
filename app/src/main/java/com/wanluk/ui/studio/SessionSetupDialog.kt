package com.wanluk.ui.studio

import com.wanluk.libcomposeui.AppButton as Button
import com.wanluk.libcomposeui.AppTextButton as TextButton

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
  AppDialog(onDismissRequest = { if (enabled) onDismiss() }, symbol = ActionSymbol.MIC, title = { Text("准备录制") }, text = {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text(task.title, style = MaterialTheme.typography.titleMedium)
      if (editProfile) {
        RecorderProfileFields(alias, { alias = it }, dialect, { dialect = it }, enabled)
        RememberRecorderOption(doNotAsk, { doNotAsk = it }, enabled)
      } else {
        Text(listOf(alias, dialect).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "未填写录制者信息" })
        TextButton(onClick = { editProfile = true }, enabled = enabled) { Text("修改录制者信息") }
      }
      Text("开始即表示已了解用途并同意录制；代他人录制须征得同意。", style = MaterialTheme.typography.bodySmall)
    }
  }, confirmButton = {
    Button(onClick = { onStart(settings.copy(alias = alias, dialect = dialect, doNotAskAgain = doNotAsk)) }, enabled = enabled) { Text("开始录制") }
  }, dismissButton = { TextButton(onClick = onDismiss, enabled = enabled) { Text("取消") } })
}

package com.wanluk.libsettingsui

import com.wanluk.libcomposeui.AppButton as Button
import com.wanluk.libcomposeui.AppTextButton as TextButton

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wanluk.libcomposeui.ActionIcon
import com.wanluk.libcomposeui.ActionSymbol
import com.wanluk.libcomposeui.AppDialog
import com.wanluk.libsettings.RecorderSettings
import com.wanluk.libsettings.RecordingMode
import com.wanluk.libsettings.ThemeMode

@Composable
fun RecorderSettingsScreen(
  settings: RecorderSettings, enabled: Boolean,
  onSave: (RecorderSettings, () -> Unit) -> Unit,
  onRecordingMode: (RecordingMode) -> Unit,
  onThemeMode: (ThemeMode) -> Unit,
) {
  var showProfile by rememberSaveable { mutableStateOf(false) }
  var showMode by rememberSaveable { mutableStateOf(false) }
  var showTheme by rememberSaveable { mutableStateOf(false) }
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
    SettingsSectionLabel("通用")
    ListItem(headlineContent = { Text("外观") },
      trailingContent = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(settings.themeMode.label(), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
          ActionIcon(ActionSymbol.NEXT, Modifier.size(18.dp))
        }
      }, modifier = Modifier.clickable(enabled = enabled) { showTheme = true }.heightIn(min = 56.dp))
    SettingsSectionLabel("录制")
    Surface(color = MaterialTheme.colorScheme.surface) {
      Column {
        ListItem(headlineContent = { Text("录音方式") },
          trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Text(settings.recordingMode.label(), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
              ActionIcon(ActionSymbol.NEXT, Modifier.size(18.dp))
            }
          }, modifier = Modifier.clickable(enabled = enabled, onClick = { showMode = true }).heightIn(min = 56.dp))
        HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
        ListItem(headlineContent = { Text("录制者信息") }, supportingContent = {
          Text(listOf(settings.alias, settings.dialect).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "未填写" },
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        }, trailingContent = { ActionIcon(ActionSymbol.NEXT, Modifier.size(18.dp)) },
          modifier = Modifier.clickable(enabled = enabled, onClick = { showProfile = true }))
        HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
        ListItem(headlineContent = { Text("使用已保存的信息") },
          supportingContent = { Text("新录制不再询问") },
          trailingContent = { Switch(settings.doNotAskAgain, onCheckedChange = null, enabled = enabled) },
          modifier = Modifier.toggleable(settings.doNotAskAgain, enabled = enabled, role = Role.Switch) {
            onSave(settings.copy(doNotAskAgain = it), {})
          })
      }
    }
    Text("信息保存在本机，修改不影响已有录制。", Modifier.padding(16.dp),
      style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
  if (showTheme) AppDialog(onDismissRequest = { if (enabled) showTheme = false },
    title = { Text("外观") }, text = {
      Column(Modifier.selectableGroup()) {
        listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM).forEach { mode ->
          Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).selectable(settings.themeMode == mode,
            enabled = enabled, role = Role.RadioButton, onClick = {
              if (settings.themeMode != mode) onThemeMode(mode)
              showTheme = false
            }), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(settings.themeMode == mode, onClick = null, enabled = enabled)
            Text(mode.label(), Modifier.padding(start = 12.dp), color = MaterialTheme.colorScheme.onSurface)
          }
        }
      }
    }, confirmButton = { TextButton(onClick = { showTheme = false }, enabled = enabled) { Text("取消") } })
  if (showMode) AppDialog(onDismissRequest = { if (enabled) showMode = false },
    symbol = ActionSymbol.MIC, title = { Text("录音方式") }, text = {
      Column(Modifier.selectableGroup()) {
        RecordingMode.entries.forEach { mode ->
          Row(Modifier.fillMaxWidth().selectable(settings.recordingMode == mode, enabled = enabled, role = Role.RadioButton,
            onClick = { onRecordingMode(mode); showMode = false }).padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            RadioButton(settings.recordingMode == mode, onClick = null, enabled = enabled)
            Column(Modifier.padding(start = 12.dp)) {
              Text(mode.label(), color = MaterialTheme.colorScheme.onSurface)
              Text(if (mode == RecordingMode.HOLD) "长按开始，松手停止" else "点击开始，再次点击停止",
                style = MaterialTheme.typography.bodySmall)
            }
          }
        }
      }
    }, confirmButton = { TextButton(onClick = { showMode = false }, enabled = enabled) { Text("取消") } })
  if (showProfile) {
    var alias by rememberSaveable { mutableStateOf(settings.alias) }
    var dialect by rememberSaveable { mutableStateOf(settings.dialect) }
    AppDialog(onDismissRequest = { if (enabled) showProfile = false }, symbol = ActionSymbol.PROFILE,
      title = { Text("录制者信息") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          RecorderProfileFields(alias, { alias = it }, dialect, { dialect = it }, enabled)
        }
      }, confirmButton = {
        Button(onClick = { onSave(settings.copy(alias = alias, dialect = dialect)) { showProfile = false } },
          enabled = enabled) { Text("保存") }
      }, dismissButton = { TextButton(onClick = { showProfile = false }, enabled = enabled) { Text("取消") } })
  }
}

@Composable
private fun SettingsSectionLabel(text: String) {
  Text(text, Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun RecordingMode.label(): String = if (this == RecordingMode.HOLD) "长按录音" else "点击录音"
private fun ThemeMode.label(): String = when (this) {
  ThemeMode.LIGHT -> "浅色"
  ThemeMode.DARK -> "深色"
  ThemeMode.SYSTEM -> "跟随系统"
}

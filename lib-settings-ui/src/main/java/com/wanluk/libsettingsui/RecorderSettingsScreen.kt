package com.wanluk.libsettingsui

import androidx.compose.foundation.BorderStroke
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
import com.wanluk.libcomposeui.IconBadge
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
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
    verticalArrangement = Arrangement.spacedBy(20.dp)) {
    Text("按你的习惯", style = MaterialTheme.typography.headlineMedium)
    Text("让每次记录都自在一点。", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
      Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("外观主题", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM).forEach { mode ->
            val selected = settings.themeMode == mode
            Surface(Modifier.weight(1f), shape = MaterialTheme.shapes.medium,
              color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
              contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
              border = BorderStroke(if (selected) 2.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
              Column(Modifier.selectable(selected, enabled = enabled, role = Role.RadioButton,
                onClick = { if (!selected) onThemeMode(mode) }).padding(horizontal = 4.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionIcon(when (mode) {
                  ThemeMode.LIGHT -> ActionSymbol.SUN
                  ThemeMode.DARK -> ActionSymbol.MOON
                  ThemeMode.SYSTEM -> ActionSymbol.SYSTEM
                }, Modifier.size(28.dp))
                Text(mode.label(), style = MaterialTheme.typography.labelLarge)
                RadioButton(selected, onClick = null, enabled = enabled, modifier = Modifier.size(24.dp))
              }
            }
          }
        }
        Text(when (settings.themeMode) {
          ThemeMode.LIGHT -> "暖白纸面，清晰明亮。"
          ThemeMode.DARK -> "深绿底色，柔和沉静。"
          ThemeMode.SYSTEM -> "随设备的深浅色设置自动切换。"
        }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
    Text("录制偏好", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
      Column {
        ListItem(headlineContent = { Text("录音方式") }, supportingContent = { Text(settings.recordingMode.label()) },
          leadingContent = { IconBadge(ActionSymbol.MIC) },
          trailingContent = { ActionIcon(ActionSymbol.NEXT) },
          modifier = Modifier.clickable(enabled = enabled, onClick = { showMode = true }).padding(vertical = 8.dp))
        HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
        ListItem(headlineContent = { Text("录制者信息") }, supportingContent = {
          Text("${settings.alias.ifBlank { "未填写称呼" }} · ${settings.dialect.ifBlank { "未填写方言" }}",
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        }, leadingContent = { IconBadge(ActionSymbol.PROFILE) },
          trailingContent = { ActionIcon(ActionSymbol.NEXT) },
          modifier = Modifier.clickable(enabled = enabled, onClick = { showProfile = true }).padding(vertical = 8.dp))
        HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
        ListItem(headlineContent = { Text("不重复提问录制者信息") },
          supportingContent = { Text("新录制使用本机已保存的信息") },
          trailingContent = { Switch(settings.doNotAskAgain, onCheckedChange = null, enabled = enabled) },
          modifier = Modifier.toggleable(settings.doNotAskAgain, enabled = enabled, role = Role.Switch) {
            onSave(settings.copy(doNotAskAgain = it), {})
          }.padding(vertical = 8.dp))
      }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      ActionIcon(ActionSymbol.SHIELD, Modifier.size(18.dp))
      Text("设置只保存在这台设备上。修改录制者信息不会改变已有录制。",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(8.dp))
  }
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
          Text("下面都可以留空，也可以随时回来修改。")
          RecorderProfileFields(alias, { alias = it }, dialect, { dialect = it }, enabled)
        }
      }, confirmButton = {
        Button(onClick = { onSave(settings.copy(alias = alias, dialect = dialect)) { showProfile = false } },
          enabled = enabled) { Text("保存") }
      }, dismissButton = { TextButton(onClick = { showProfile = false }, enabled = enabled) { Text("取消") } })
  }
}

private fun RecordingMode.label(): String = if (this == RecordingMode.HOLD) "长按录音" else "点击录音"
private fun ThemeMode.label(): String = when (this) {
  ThemeMode.LIGHT -> "浅色"
  ThemeMode.DARK -> "深色"
  ThemeMode.SYSTEM -> "跟随系统"
}

package com.wanluk.libsettingsui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
fun RecorderProfileFields(alias: String, onAlias: (String) -> Unit, dialect: String,
  onDialect: (String) -> Unit, enabled: Boolean) {
  OutlinedTextField(alias, { onAlias(it.take(80)) }, modifier = Modifier.fillMaxWidth(), enabled = enabled,
    label = { Text("录制者（选填）") }, placeholder = { Text("姓名或代号") }, singleLine = true)
  OutlinedTextField(dialect, { onDialect(it.take(160)) }, modifier = Modifier.fillMaxWidth(), enabled = enabled,
    label = { Text("方言（选填）") }, placeholder = { Text("如：苏州话") })
}

@Composable
fun RememberRecorderOption(checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean) {
  Row(Modifier.fillMaxWidth().toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange)
    .heightIn(min = 48.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
    Checkbox(checked, onCheckedChange = null, enabled = enabled)
    Text("保存信息，下次不再询问", modifier = Modifier.padding(start = 8.dp))
  }
}

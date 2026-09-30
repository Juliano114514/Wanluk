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
    label = { Text("怎么称呼您？") }, placeholder = { Text("名字、小名或代号都可以") }, singleLine = true,
    supportingText = { Text("选填") })
  OutlinedTextField(dialect, { onDialect(it.take(160)) }, modifier = Modifier.fillMaxWidth(), enabled = enabled,
    label = { Text("怎么称呼您的方言？") }, placeholder = { Text("比如：苏州话、家乡的土话") },
    supportingText = { Text("选填") })
}

@Composable
fun RememberRecorderOption(checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean) {
  Row(Modifier.fillMaxWidth().toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange)
    .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
    Checkbox(checked, onCheckedChange = null, enabled = enabled)
    Text("不重复提问录制者信息", modifier = Modifier.padding(start = 8.dp))
  }
}

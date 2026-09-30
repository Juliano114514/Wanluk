package com.wanluk.ui.studio

import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import com.wanluk.libcomposeui.ActionIcon
import com.wanluk.libcomposeui.ActionSymbol

@Composable
internal fun MoreButton(enabled: Boolean, onClick: () -> Unit) {
  IconButton(onClick = onClick, enabled = enabled) {
    ActionIcon(ActionSymbol.MORE, contentDescription = "更多")
  }
}

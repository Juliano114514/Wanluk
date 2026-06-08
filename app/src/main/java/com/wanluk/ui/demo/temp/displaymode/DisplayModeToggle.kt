package com.wanluk.ui.demo.temp.displaymode

import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 字目 / 组词 切换按钮。
 *
 * 以 [FilterChip] 呈现，点击即切换；独立组件，可随时替换或删除。
 */
@Composable
fun DisplayModeToggle(
  current: DisplayMode,
  onToggle: () -> Unit,
  modifier: Modifier = Modifier,
) {
  FilterChip(
    selected = current == DisplayMode.PHRASES,
    onClick = onToggle,
    label = { Text(current.label) },
    modifier = modifier,
  )
}

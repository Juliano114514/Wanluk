package com.wanluk.ui.demo.temp.rarityfilter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 演示用罕度筛选条（临时）。与 [DemoRarityFilter] 同包，发布前一并删除。
 */
@Composable
fun DemoRarityFilterBar(
  selected: DemoRarityFilter,
  onSelected: (DemoRarityFilter) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      text = "【演示】按罕度筛选（正式版将移除）",
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.secondary,
      modifier = Modifier.padding(horizontal = 16.dp),
    )
    LazyRow(
      contentPadding = PaddingValues(horizontal = 16.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      items(DemoRarityFilter.entries.toList()) { option ->
        FilterChip(
          selected = option == selected,
          onClick = { onSelected(option) },
          label = { Text(option.label) },
        )
      }
    }
  }
}

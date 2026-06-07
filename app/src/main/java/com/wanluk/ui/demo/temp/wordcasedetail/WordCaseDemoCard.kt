package com.wanluk.ui.demo.temp.wordcasedetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wanluk.lib_room.entities.WordCaseEntity

/**
 * 演示用可点击字例卡片（临时）。发布前与详情浮层同包一并删除。
 */
@Composable
fun WordCaseDemoCard(
  item: WordCaseEntity,
  onClick: (WordCaseEntity, Rect) -> Unit,
  modifier: Modifier = Modifier,
) {
  var cardBounds by remember(item.id) { mutableStateOf(Rect.Zero) }

  Card(
    modifier = modifier
      .fillMaxWidth()
      .onGloballyPositioned { coordinates ->
        cardBounds = coordinates.boundsInWindow()
      }
      .clickable { onClick(item, cardBounds) },
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(
        text = item.coreChar,
        fontSize = 32.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
      )
      Text(
        text = "${item.she}·${item.yun}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = item.phrases.orEmpty().ifEmpty { "罕${item.rarity}" },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

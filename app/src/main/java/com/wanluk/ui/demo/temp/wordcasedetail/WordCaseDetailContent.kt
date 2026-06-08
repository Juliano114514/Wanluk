package com.wanluk.ui.demo.temp.wordcasedetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wanluk.libroom.entity.WordCaseEntity

/**
 * 演示用字例详情内容（临时）。与 [WordCaseDetailOverlay] 同包，发布前一并删除。
 */
@Composable
fun WordCaseDetailContent(
  item: WordCaseEntity,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .verticalScroll(rememberScrollState())
      .padding(20.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(
      text = item.coreChar,
      fontSize = 48.sp,
      textAlign = TextAlign.Center,
      modifier = Modifier.fillMaxWidth(),
    )
    DetailRow(label = "摄·韵", value = "${item.she} · ${item.yun}")
    DetailRow(label = "声·呼·等", value = "${item.sheng} · ${item.hu} · ${dengLabel(item.deng)}")
    DetailRow(label = "调·组", value = "${item.diao} · ${item.zu.orEmpty().ifEmpty { "—" }}")
    DetailRow(label = "罕度", value = item.rarity.toString())
    DetailRow(label = "组词", value = item.phrases.orEmpty().ifEmpty { "—" })
    DetailRow(label = "原注", value = item.remark.orEmpty().ifEmpty { "—" })
    DetailRow(label = "库内 ID", value = item.id.toString())
  }
}

@Composable
private fun DetailRow(label: String, value: String) {
  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.primary,
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurface,
    )
  }
}

private fun dengLabel(deng: Int): String = when (deng) {
  1 -> "一"
  2 -> "二"
  3 -> "三"
  4 -> "四"
  else -> deng.toString()
}

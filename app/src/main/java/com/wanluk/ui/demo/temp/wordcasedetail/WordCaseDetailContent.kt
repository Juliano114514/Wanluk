package com.wanluk.ui.demo.temp.wordcasedetail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wanluk.libroom.entity.WordCaseEntity

/** Read-only details shared by the library and the legacy preview. */
@Composable
fun WordCaseDetailContent(item: WordCaseEntity, modifier: Modifier = Modifier) {
  Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Surface(color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface) {
      Text(item.coreChar, fontSize = 60.sp, lineHeight = 78.sp, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp))
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    DetailRow("摄 · 韵", "${item.she} · ${item.yun}")
    DetailRow("声 · 呼 · 等", "${item.sheng} · ${item.hu} · ${dengLabel(item.deng)}")
    DetailRow("调 · 组", "${item.diao} · ${item.zu.orEmpty().ifEmpty { "—" }}")
    DetailRow("罕度", item.rarity.toString())
    DetailRow("组词", item.phrases.orEmpty().ifEmpty { "—" })
    DetailRow("原注", item.remark.orEmpty().ifEmpty { "—" })
    DetailRow("库内 ID", item.id.toString())
  }
}

@Composable
private fun DetailRow(label: String, value: String) {
  Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(label, Modifier.width(80.dp), style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurface)
  }
}

private fun dengLabel(deng: Int): String = when (deng) {
  1 -> "一"
  2 -> "二"
  3 -> "三"
  4 -> "四"
  else -> deng.toString()
}

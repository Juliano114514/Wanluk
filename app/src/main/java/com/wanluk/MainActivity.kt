package com.wanluk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wanluk.lib_room.entities.WordCaseEntity
import com.wanluk.ui.demo.WordCaseDemoViewModel
import com.wanluk.ui.theme.WanlukTheme
import org.koin.androidx.compose.koinViewModel

class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      WanlukTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
          WordCaseDemoScreen(viewModel = koinViewModel())
        }
      }
    }
  }
}

@Composable
private fun WordCaseDemoScreen(viewModel: WordCaseDemoViewModel) {
  val wordCases by viewModel.wordCases.collectAsStateWithLifecycle()

  Column(modifier = Modifier.fillMaxSize()) {
    Text(
      text = "字例预览（${wordCases.size} 条）",
      style = MaterialTheme.typography.titleMedium,
      modifier = Modifier.padding(16.dp),
    )
    LazyVerticalGrid(
      columns = GridCells.Adaptive(minSize = 96.dp),
      contentPadding = PaddingValues(16.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier.weight(1f),
    ) {
      items(wordCases, key = { it.id }) { item ->
        WordCaseCard(item)
      }
    }
  }
}

@Composable
private fun WordCaseCard(item: WordCaseEntity) {
  Card(
    modifier = Modifier.fillMaxWidth(),
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
    }
  }
}

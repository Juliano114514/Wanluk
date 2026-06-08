package com.wanluk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wanluk.lib_room.entities.WordCaseEntity
import com.wanluk.ui.demo.WordCaseDemoViewModel
import com.wanluk.ui.demo.temp.displaymode.DisplayMode
import com.wanluk.ui.demo.temp.displaymode.DisplayModeToggle
import com.wanluk.ui.demo.temp.rarityfilter.DemoRarityFilterBar
import com.wanluk.ui.demo.temp.wordcasedetail.WordCaseDemoCard
import com.wanluk.ui.demo.temp.wordcasedetail.WordCaseDetailOverlay
import com.wanluk.ui.demo.temp.wordcasedetail.WordCaseDetailSelection
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
  val rarityFilter by viewModel.demoRarityFilterState.collectAsStateWithLifecycle()
  // TODO(demo): 发布前删除详情浮层选中态及 temp/wordcasedetail 包
  var detailSelection by remember { mutableStateOf<WordCaseDetailSelection?>(null) }
  var displayMode by remember { mutableStateOf(DisplayMode.CHAR) }

  Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
      Text(
        text = "字例预览（${wordCases.size} 条）",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
      )
      // TODO(demo): 发布前删除 DemoRarityFilterBar 及 ViewModel 罕度筛选状态
      DemoRarityFilterBar(
        selected = rarityFilter,
        onSelected = viewModel::onDemoRarityFilterSelected,
        modifier = Modifier.padding(vertical = 8.dp),
      )
      DisplayModeToggle(
        current = displayMode,
        onToggle = {
          displayMode = when (displayMode) {
            DisplayMode.CHAR -> DisplayMode.PHRASES
            DisplayMode.PHRASES -> DisplayMode.CHAR
          }
        },
        modifier = Modifier.padding(horizontal = 16.dp),
      )
      LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 96.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.weight(1f),
      ) {
        items(wordCases, key = { it.id }) { item ->
          WordCaseDemoCard(
            item = item,
            onClick = { clicked, bounds ->
              detailSelection = WordCaseDetailSelection(item = clicked, anchorBounds = bounds)
            },
            displayMode = displayMode,
          )
        }
      }
    }

    detailSelection?.let { selection ->
      WordCaseDetailOverlay(
        item = selection.item,
        anchorBounds = selection.anchorBounds,
        onDismiss = { detailSelection = null },
      )
    }
  }
}

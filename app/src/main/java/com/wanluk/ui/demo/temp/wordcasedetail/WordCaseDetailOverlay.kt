package com.wanluk.ui.demo.temp.wordcasedetail

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import com.wanluk.lib_room.entities.WordCaseEntity
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 演示用字例详情浮层（临时）：从卡片原位放大至居中，点击遮罩或返回键关闭。
 * 发布前与 [WordCaseDetailContent]、[WordCaseDemoCard] 一并删除。
 */
@Composable
fun WordCaseDetailOverlay(
  item: WordCaseEntity,
  anchorBounds: Rect,
  onDismiss: () -> Unit,
) {
  val density = LocalDensity.current
  val configuration = LocalConfiguration.current
  val scope = rememberCoroutineScope()
  val progress = remember { Animatable(0f) }

  val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
  val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
  val targetWidthPx = screenWidthPx * 0.88f
  val targetHeightPx = screenHeightPx * 0.62f

  fun dismissAnimated() {
    scope.launch {
      progress.animateTo(0f, tween(durationMillis = 220))
      onDismiss()
    }
  }

  LaunchedEffect(Unit) {
    progress.animateTo(
      targetValue = 1f,
      animationSpec = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
      ),
    )
  }

  BackHandler(onBack = ::dismissAnimated)

  val startCenterX = anchorBounds.left + anchorBounds.width / 2f
  val startCenterY = anchorBounds.top + anchorBounds.height / 2f
  val endCenterX = screenWidthPx / 2f
  val endCenterY = screenHeightPx / 2f

  val currentCenterX = lerp(startCenterX, endCenterX, progress.value)
  val currentCenterY = lerp(startCenterY, endCenterY, progress.value)
  val currentWidth = lerp(anchorBounds.width, targetWidthPx, progress.value)
  val currentHeight = lerp(anchorBounds.height, targetHeightPx, progress.value)
  val scrimAlpha = 0.45f * progress.value

  Box(modifier = Modifier.fillMaxSize().zIndex(1f)) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(Color.Black.copy(alpha = scrimAlpha))
        .clickable(
          indication = null,
          interactionSource = remember { MutableInteractionSource() },
          onClick = ::dismissAnimated,
        ),
    )

    Card(
      modifier = Modifier
        .offset {
          IntOffset(
            x = (currentCenterX - currentWidth / 2f).roundToInt(),
            y = (currentCenterY - currentHeight / 2f).roundToInt(),
          )
        }
        .width(with(density) { currentWidth.toDp() })
        .heightIn(max = with(density) { currentHeight.toDp() })
        .graphicsLayer {
          alpha = progress.value.coerceIn(0.4f, 1f)
        },
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
      WordCaseDetailContent(item = item)
    }
  }
}

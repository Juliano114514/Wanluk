package com.wanluk.librecordui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** UI-only recording input. The caller owns permission, capture, storage and lifecycle. */
@Composable
fun RecordingControl(
  label: String, canStart: Boolean, recording: Boolean, stopping: Boolean, holdToRecord: Boolean,
  onStart: (held: Boolean) -> Unit, onStop: () -> Unit,
  modifier: Modifier = Modifier, primary: Boolean = false, icon: @Composable () -> Unit,
) {
  val latestCanStart by rememberUpdatedState(canStart)
  val latestRecording by rememberUpdatedState(recording)
  val latestStopping by rememberUpdatedState(stopping)
  val latestStart by rememberUpdatedState(onStart)
  val latestStop by rememberUpdatedState(onStop)
  val enabled = canStart || (recording && !stopping)
  fun click() { if (recording) onStop() else onStart(false) }
  // Do not key the gesture by capture/enabled: starting capture changes both while the finger is down.
  val input = if (holdToRecord) Modifier.pointerInput(Unit) {
    awaitEachGesture {
      val down = awaitFirstDown()
      if (latestRecording && !latestStopping) {
        waitForUpOrCancellation()?.let { it.consume(); latestStop() }
      } else if (latestCanStart) {
        val press = awaitLongPressOrCancellation(down.id)
        if (press != null && latestCanStart) {
          press.consume()
          try {
            latestStart(true)
            waitForUpOrCancellation()?.consume()
          } finally {
            // Release, drag cancellation and disposal must all stop the gesture-owned capture.
            latestStop()
          }
        }
      }
    }
  }.semantics {
    role = Role.Button
    if (enabled) onClick(label = if (recording) "停止录音" else "开始录音") { click(); true }
    else disabled()
  } else Modifier.clickable(enabled = enabled, role = Role.Button, onClick = ::click)
  val container = when {
    !enabled -> MaterialTheme.colorScheme.surfaceVariant
    recording -> MaterialTheme.colorScheme.error
    primary -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.surface
  }
  val foreground = when {
    !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    recording -> MaterialTheme.colorScheme.onError
    primary -> MaterialTheme.colorScheme.onPrimary
    else -> MaterialTheme.colorScheme.onSurface
  }
  val animatedContainer by animateColorAsState(container, label = "recordColor")
  val animatedForeground by animateColorAsState(foreground, label = "recordContent")
  val scale by animateFloatAsState(if (recording && !stopping) 0.96f else 1f, label = "recordPress")
  val shape = if (primary) MaterialTheme.shapes.large else MaterialTheme.shapes.medium
  Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
    Surface(shape = shape, color = animatedContainer, contentColor = animatedForeground,
      border = if (primary) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      modifier = Modifier.size(if (primary) 72.dp else 48.dp)
        .graphicsLayer { scaleX = scale; scaleY = scale }.clip(shape)
        .then(input).semantics { contentDescription = label }) {
      Box(contentAlignment = Alignment.Center) { icon() }
    }
    Text(label, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
      color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
  }
}

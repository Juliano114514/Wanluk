package com.wanluk.libcomposeui

import com.wanluk.libcomposeui.AppTextButton as TextButton

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.AccessibilityManager
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withTimeoutOrNull

/** An in-app toast with an optional action. No Activity or business state is retained. */
class ActionToast private constructor(
  override val message: String,
  override val actionLabel: String?,
  val durationMillis: Long,
  /** Distance from the host's bottom edge to the toast's bottom edge; null uses the default. */
  val positionY: Dp?,
) : SnackbarVisuals {
  override val withDismissAction: Boolean = false
  override val duration: SnackbarDuration = SnackbarDuration.Indefinite

  class Builder(private val message: String) {
    private var actionLabel: String? = null
    private var durationMillis = 4_000L
    private var positionY: Dp? = null

    fun action(label: String) = apply { actionLabel = label }
    fun duration(valueMillis: Long) = apply { durationMillis = valueMillis }
    /** Set a bottom inset within ActionToastHost. Numeric overloads also use dp. */
    fun setY(bottomInset: Dp) = apply {
      require(bottomInset.value.isFinite() && bottomInset >= 0.dp)
      positionY = bottomInset
    }
    fun setY(bottomInsetDp: Int) = setY(bottomInsetDp.dp)
    fun setY(bottomInsetDp: Float) = setY(bottomInsetDp.dp)
    fun build(): ActionToast {
      require(message.isNotBlank())
      require(actionLabel == null || !actionLabel.isNullOrBlank())
      require(durationMillis > 0)
      return ActionToast(message, actionLabel, durationMillis, positionY)
    }
  }
}

class ActionToastHostState(private val accessibilityManager: AccessibilityManager? = null) {
  internal val snackbar = SnackbarHostState()

  /** The caller owns cancellation and acts only when the visible action was pressed. */
  suspend fun show(toast: ActionToast): Boolean {
    snackbar.currentSnackbarData?.dismiss()
    val timeout = accessibilityManager?.calculateRecommendedTimeoutMillis(
      toast.durationMillis, containsText = true, containsControls = toast.actionLabel != null,
    ) ?: toast.durationMillis
    return withTimeoutOrNull(timeout) {
      snackbar.showSnackbar(toast)
    } == SnackbarResult.ActionPerformed
  }

  fun dismiss() { snackbar.currentSnackbarData?.dismiss() }
}

@Composable
fun rememberActionToastHostState(): ActionToastHostState {
  val accessibility = LocalAccessibilityManager.current
  return remember(accessibility) { ActionToastHostState(accessibility) }
}

@Composable
fun ActionToastHost(state: ActionToastHostState, modifier: Modifier = Modifier) {
  BoxWithConstraints(modifier.fillMaxSize()) {
    SnackbarHost(state.snackbar, Modifier.align(Alignment.BottomCenter)
      .padding(horizontal = 20.dp)) { data ->
      val bottomInset = (data.visuals as? ActionToast)?.positionY ?: (maxHeight * 0.33f)
      Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 3.dp, modifier = Modifier.padding(bottom = bottomInset.coerceAtMost(maxHeight))
          .widthIn(max = 480.dp).wrapContentWidth()) {
        Row(Modifier.heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(data.visuals.message, Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
          data.visuals.actionLabel?.let { label ->
            TextButton(onClick = data::performAction,
              colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.inversePrimary)) {
              Text(label)
            }
          }
        }
      }
    }
  }
}

package com.wanluk.libcomposeui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** One visual contract for confirmations, editors and informational dialogs. */
@Composable
fun AppDialog(
  onDismissRequest: () -> Unit,
  confirmButton: @Composable () -> Unit,
  title: @Composable () -> Unit,
  text: @Composable () -> Unit,
  dismissButton: (@Composable () -> Unit)? = null,
  symbol: ActionSymbol? = null,
) {
  AlertDialog(onDismissRequest = onDismissRequest, confirmButton = confirmButton,
    dismissButton = dismissButton, title = {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        symbol?.let { ActionIcon(it, Modifier.size(22.dp)) }
        title()
      }
    }, text = text, shape = MaterialTheme.shapes.extraLarge,
    containerColor = MaterialTheme.colorScheme.surface,
    titleContentColor = MaterialTheme.colorScheme.onSurface,
    textContentColor = MaterialTheme.colorScheme.onSurfaceVariant, tonalElevation = 0.dp)
}

@Composable
fun IconBadge(symbol: ActionSymbol, modifier: Modifier = Modifier) {
  Box(modifier.size(32.dp), contentAlignment = Alignment.Center) {
    ActionIcon(symbol, Modifier.size(24.dp))
  }
}

@Composable
fun EmptyState(symbol: ActionSymbol, title: String, description: String, modifier: Modifier = Modifier) {
  Column(modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
    IconBadge(symbol)
    Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    if (description.isNotBlank()) Text(description, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

// Material buttons default to a full capsule regardless of the theme shape scale.
// Keep their native semantics, touch targets and disabled states with a restrained shape.
@Composable
fun AppButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
  colors: ButtonColors = ButtonDefaults.buttonColors(), content: @Composable RowScope.() -> Unit) {
  Button(onClick, modifier.heightIn(min = 48.dp), enabled, shape = MaterialTheme.shapes.small,
    colors = colors, content = content)
}

@Composable
fun AppOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
  content: @Composable RowScope.() -> Unit) {
  OutlinedButton(onClick, modifier.heightIn(min = 48.dp), enabled, shape = MaterialTheme.shapes.small,
    content = content)
}

@Composable
fun AppTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
  colors: ButtonColors = ButtonDefaults.textButtonColors(), content: @Composable RowScope.() -> Unit) {
  TextButton(onClick, modifier.heightIn(min = 48.dp), enabled, shape = MaterialTheme.shapes.small,
    colors = colors, content = content)
}

@Composable
fun AppSnackbar(data: SnackbarData) {
  Snackbar(Modifier.padding(16.dp), shape = MaterialTheme.shapes.medium,
    containerColor = MaterialTheme.colorScheme.inverseSurface,
    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
    action = data.visuals.actionLabel?.let { label -> {
      AppTextButton(onClick = data::performAction,
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.inversePrimary)) { Text(label) }
    } }, dismissAction = if (data.visuals.withDismissAction) ({
      IconButton(onClick = data::dismiss) { ActionIcon(ActionSymbol.CLOSE, Modifier.size(20.dp), "关闭提示") }
    }) else null) {
    Text(data.visuals.message, style = MaterialTheme.typography.bodyMedium)
  }
}

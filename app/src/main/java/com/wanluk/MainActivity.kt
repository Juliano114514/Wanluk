package com.wanluk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wanluk.libsettings.ThemeMode
import com.wanluk.ui.studio.StudioApp
import com.wanluk.ui.studio.StudioViewModel
import com.wanluk.ui.theme.WanlukTheme
import org.koin.androidx.compose.koinViewModel

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val viewModel: StudioViewModel = koinViewModel()
      val settings by viewModel.recorderSettings.collectAsStateWithLifecycle()
      val darkTheme = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
      }
      WanlukTheme(darkTheme) {
        val background = MaterialTheme.colorScheme.background.toArgb()
        SideEffect {
          val bars = if (darkTheme) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
          enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
          window.decorView.setBackgroundColor(background)
        }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          StudioApp(viewModel = viewModel, lifecycle = lifecycle)
        }
      }
    }
  }
}

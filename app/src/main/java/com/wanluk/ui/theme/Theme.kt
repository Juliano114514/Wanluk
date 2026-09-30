package com.wanluk.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Complete semantic palettes: menus, dialogs, fields and feedback share the same roles.
private val LightColors = lightColorScheme(
  primary = Color(0xFF386A35), onPrimary = Color.White,
  primaryContainer = Color(0xFFD9EEC8), onPrimaryContainer = Color(0xFF244D24),
  inversePrimary = Color(0xFFACD794),
  secondary = Color(0xFF56634D), onSecondary = Color.White,
  secondaryContainer = Color(0xFFE3EAD9), onSecondaryContainer = Color(0xFF394632),
  tertiary = Color(0xFF805B28), onTertiary = Color.White,
  tertiaryContainer = Color(0xFFFFE8C4), onTertiaryContainer = Color(0xFF614315),
  background = Color(0xFFF7F8F2), onBackground = Color(0xFF222A21),
  surface = Color(0xFFFEFFF8), onSurface = Color(0xFF222A21),
  surfaceVariant = Color(0xFFE9EDDF), onSurfaceVariant = Color(0xFF5D6757),
  surfaceDim = Color(0xFFDADFD2), surfaceBright = Color(0xFFFEFFF8),
  surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF1F4E9),
  surfaceContainer = Color(0xFFEDF1E5), surfaceContainerHigh = Color(0xFFE8EEDF),
  surfaceContainerHighest = Color(0xFFE3E9DA),
  inverseSurface = Color(0xFF2D372A), inverseOnSurface = Color(0xFFF2F6EA),
  error = Color(0xFFB43F3C), onError = Color.White,
  errorContainer = Color(0xFFFFDAD5), onErrorContainer = Color(0xFF842523),
  outline = Color(0xFF798371), outlineVariant = Color(0xFFD6DECC), scrim = Color(0xFF10170F),
)

private val DarkColors = darkColorScheme(
  primary = Color(0xFFACD794), onPrimary = Color(0xFF173A17),
  primaryContainer = Color(0xFF2F5029), onPrimaryContainer = Color(0xFFD3EDC0),
  inversePrimary = Color(0xFF386A35),
  secondary = Color(0xFFC0CBB3), onSecondary = Color(0xFF2B3625),
  secondaryContainer = Color(0xFF3D4B34), onSecondaryContainer = Color(0xFFE3EAD9),
  tertiary = Color(0xFFF0C382), onTertiary = Color(0xFF452B05),
  tertiaryContainer = Color(0xFF5F431C), onTertiaryContainer = Color(0xFFFFE8C4),
  background = Color(0xFF131A13), onBackground = Color(0xFFE5EBDD),
  surface = Color(0xFF1C241B), onSurface = Color(0xFFE5EBDD),
  surfaceVariant = Color(0xFF35402E), onSurfaceVariant = Color(0xFFB7C3AD),
  surfaceDim = Color(0xFF131A13), surfaceBright = Color(0xFF384232),
  surfaceContainerLowest = Color(0xFF0F150F), surfaceContainerLow = Color(0xFF1C241B),
  surfaceContainer = Color(0xFF222D20), surfaceContainerHigh = Color(0xFF293427),
  surfaceContainerHighest = Color(0xFF323E2E),
  inverseSurface = Color(0xFFE5EBDD), inverseOnSurface = Color(0xFF263022),
  error = Color(0xFFFFB4AA), onError = Color(0xFF650D11),
  errorContainer = Color(0xFF862824), onErrorContainer = Color(0xFFFFDAD5),
  outline = Color(0xFF85927A), outlineVariant = Color(0xFF414F39), scrim = Color.Black,
)

private val YunluShapes = Shapes(
  extraSmall = RoundedCornerShape(14.dp), small = RoundedCornerShape(14.dp),
  medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(24.dp),
  extraLarge = RoundedCornerShape(32.dp),
)

private val YunluTypography = Typography(
  displayLarge = TextStyle(fontSize = 56.sp, lineHeight = 64.sp, fontWeight = FontWeight.Bold),
  displayMedium = TextStyle(fontSize = 44.sp, lineHeight = 54.sp, fontWeight = FontWeight.Bold),
  displaySmall = TextStyle(fontSize = 36.sp, lineHeight = 48.sp, fontWeight = FontWeight.Bold),
  headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
  headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
  headlineSmall = TextStyle(fontSize = 23.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
  titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
  titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
  titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
  bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 26.sp),
  bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 23.sp),
  bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 19.sp),
  labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
  labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
  labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun WanlukTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors,
    typography = YunluTypography, shapes = YunluShapes, content = content)
}

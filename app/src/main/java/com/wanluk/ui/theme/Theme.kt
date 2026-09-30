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
  primary = Color(0xFF286747), onPrimary = Color.White,
  primaryContainer = Color(0xFFE5F0E9), onPrimaryContainer = Color(0xFF205239),
  inversePrimary = Color(0xFF94CFAE),
  secondary = Color(0xFF5F6368), onSecondary = Color.White,
  secondaryContainer = Color(0xFFECEEED), onSecondaryContainer = Color(0xFF414644),
  tertiary = Color(0xFF71633A), onTertiary = Color.White,
  tertiaryContainer = Color(0xFFF3ECD7), onTertiaryContainer = Color(0xFF524722),
  background = Color(0xFFFAFAFA), onBackground = Color(0xFF202124),
  surface = Color(0xFFFFFFFF), onSurface = Color(0xFF202124),
  surfaceVariant = Color(0xFFF0F1F0), onSurfaceVariant = Color(0xFF656B68),
  surfaceDim = Color(0xFFDADDDC), surfaceBright = Color(0xFFFFFFFF),
  surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF7F8F7),
  surfaceContainer = Color(0xFFF2F3F2), surfaceContainerHigh = Color(0xFFECEEED),
  surfaceContainerHighest = Color(0xFFE5E8E6),
  inverseSurface = Color(0xFF2F3331), inverseOnSurface = Color(0xFFF5F6F5),
  error = Color(0xFFBA3434), onError = Color.White,
  errorContainer = Color(0xFFFFE5E3), onErrorContainer = Color(0xFF8D2222),
  outline = Color(0xFF858B87), outlineVariant = Color(0xFFDFE3E0), scrim = Color(0xFF111312),
)

private val DarkColors = darkColorScheme(
  primary = Color(0xFF94CFAE), onPrimary = Color(0xFF103B27),
  primaryContainer = Color(0xFF254C37), onPrimaryContainer = Color(0xFFD5EDDE),
  inversePrimary = Color(0xFF286747),
  secondary = Color(0xFFC3C9C5), onSecondary = Color(0xFF2D3430),
  secondaryContainer = Color(0xFF3D4540), onSecondaryContainer = Color(0xFFECEEED),
  tertiary = Color(0xFFD9C990), onTertiary = Color(0xFF3C341B),
  tertiaryContainer = Color(0xFF51482A), onTertiaryContainer = Color(0xFFF3ECD7),
  background = Color(0xFF121413), onBackground = Color(0xFFE8EBE9),
  surface = Color(0xFF1C1F1D), onSurface = Color(0xFFE8EBE9),
  surfaceVariant = Color(0xFF343A36), onSurfaceVariant = Color(0xFFB7BFB9),
  surfaceDim = Color(0xFF121413), surfaceBright = Color(0xFF353B37),
  surfaceContainerLowest = Color(0xFF0D100E), surfaceContainerLow = Color(0xFF1C1F1D),
  surfaceContainer = Color(0xFF232824), surfaceContainerHigh = Color(0xFF2B312C),
  surfaceContainerHighest = Color(0xFF343B35),
  inverseSurface = Color(0xFFE8EBE9), inverseOnSurface = Color(0xFF282E29),
  error = Color(0xFFFFB4AB), onError = Color(0xFF690C0C),
  errorContainer = Color(0xFF8B2424), onErrorContainer = Color(0xFFFFE5E3),
  outline = Color(0xFF89958D), outlineVariant = Color(0xFF424B45), scrim = Color.Black,
)

private val YunluShapes = Shapes(
  extraSmall = RoundedCornerShape(2.dp), small = RoundedCornerShape(4.dp),
  medium = RoundedCornerShape(6.dp), large = RoundedCornerShape(8.dp),
  extraLarge = RoundedCornerShape(12.dp),
)

private val YunluTypography = Typography(
  displayLarge = TextStyle(fontSize = 56.sp, lineHeight = 64.sp, fontWeight = FontWeight.Bold),
  displayMedium = TextStyle(fontSize = 44.sp, lineHeight = 54.sp, fontWeight = FontWeight.Bold),
  displaySmall = TextStyle(fontSize = 36.sp, lineHeight = 48.sp, fontWeight = FontWeight.Bold),
  headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
  headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
  headlineSmall = TextStyle(fontSize = 23.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
  titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
  titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
  titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
  bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
  bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
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

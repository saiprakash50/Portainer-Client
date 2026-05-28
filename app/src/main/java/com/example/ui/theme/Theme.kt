package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = ElegantPrimary,
    onPrimary = ElegantOnPrimary,
    background = DarkBg,
    surface = DarkBg,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    outline = DarkBorder,
    outlineVariant = DarkBorder,
    primaryContainer = DarkSurface,
    onPrimaryContainer = ElegantPrimary,
    secondary = IndicatorPill,
    surfaceVariant = DarkSurface,
    error = Color(0xFFF2B8B5)
  )

private val LightColorScheme = lightColorScheme(
    primary = ElegantPrimary,
    onPrimary = Color.White,
    background = Color(0xFFFDFDFD),
    surface = Color(0xFFFDFDFD),
    onBackground = Color(0xFF1E1E1E),
    onSurface = Color(0xFF1E1E1E),
    onSurfaceVariant = Color(0xFF6E6E6E),
    outline = Color(0xFFE0E0E0),
    outlineVariant = Color(0xFFEEEEEE),
    primaryContainer = Color(0xFFE3F2FD),
    onPrimaryContainer = ElegantPrimary,
    secondary = IndicatorPill,
    surfaceVariant = Color(0xFFF5F5F5),
    error = Color(0xFFB3261E)
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

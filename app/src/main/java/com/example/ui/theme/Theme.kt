package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
  primary = AiBlueDark,
  tertiary = TechGreenDark,
  background = BackgroundDark,
  surface = SurfaceDark,
  onSurface = OnSurfaceDark,
  onPrimary = Color.Black,
  onTertiary = Color.Black
)

private val LightColorScheme = lightColorScheme(
  primary = AiBlueLight,
  tertiary = TechGreenLight,
  background = BackgroundLight,
  surface = SurfaceLight,
  onSurface = OnSurfaceLight,
  onPrimary = Color.White,
  onTertiary = Color.White
)

@Composable
fun TechMateTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}


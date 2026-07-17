package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = NeonEmerald,
    secondary = NeonCyan,
    tertiary = NeonCoral,
    background = RadarSlate900,
    surface = RadarSlate800,
    onPrimary = RadarSlate900,
    onSecondary = RadarSlate900,
    onTertiary = Color.White,
    onBackground = Color(0xFFF8FAFC),
    onSurface = Color(0xFFF8FAFC),
    primaryContainer = RadarSlate800,
    secondaryContainer = RadarSlate700
  )

private val LightColorScheme = DarkColorScheme // Keep consistent dark tactical radar for the theme


@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  // Always use our cohesive dark tactical radar color scheme for the "Presente" app
  val colorScheme = DarkColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

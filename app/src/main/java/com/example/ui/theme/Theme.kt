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

private val DarkColorScheme =
  lightColorScheme(
    primary = BentoPrimary,
    secondary = BentoPrimaryContainer,
    tertiary = BentoOnPrimaryContainer,
    background = BentoBackground,
    surface = BentoSurface,
    onBackground = BentoTextPrimary,
    onSurface = BentoTextPrimary
  )

private val LightColorScheme =
  lightColorScheme(
    primary = BentoPrimary,
    secondary = BentoPrimaryContainer,
    tertiary = BentoOnPrimaryContainer,
    background = BentoBackground,
    surface = BentoSurface,
    onBackground = BentoTextPrimary,
    onSurface = BentoTextPrimary
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = false, // Use the Bento light mode by default
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = LightColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

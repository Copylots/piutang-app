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
  darkColorScheme(
    primary = SteelBlueDark,
    secondary = SlateGreyDark,
    tertiary = BronzeDark,
    background = DarkBackground,
    surface = SurfaceDark,
    onPrimary = DarkBackground,
    onSecondary = DarkBackground,
    onBackground = OffWhite,
    onSurface = OffWhite
  )

private val LightColorScheme =
  lightColorScheme(
    primary = SteelBlueLight,
    secondary = SlateGreyLight,
    tertiary = BronzeLight,
    background = OffWhite,
    surface = androidx.compose.ui.graphics.Color.White,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    onBackground = DarkBackground,
    onSurface = DarkBackground
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Disable dynamic color to maintain our branded Sinar Mas executive look
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

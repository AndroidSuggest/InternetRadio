package com.armanmaurya.internetradio.ui.shared.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.platform.LocalContext
import com.armanmaurya.internetradio.domain.model.AppPreferences

private fun ColorScheme.applyPureBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainer = Color.Black,
    surfaceContainerLow = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerHigh = Color.Black,
    surfaceContainerHighest = Color.Black
)

@Composable
fun InternetRadioTheme(
    appPreferences: AppPreferences = AppPreferences(),
    content: @Composable () -> Unit
) {
    val darkTheme = when (appPreferences.themeMode) {
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
        AppTheme.SYSTEM -> isSystemInDarkTheme()
    }

    val dynamicColor = appPreferences.useDynamicColor

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            val baseScheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            if (darkTheme && appPreferences.pureBlack) baseScheme.applyPureBlack() else baseScheme
        }
        else -> {
            val baseScheme = appPreferences.appColor.getColorScheme(
                darkTheme = darkTheme,
                customColorArgb = appPreferences.customColorArgb
            )
            if (darkTheme && appPreferences.pureBlack) baseScheme.applyPureBlack() else baseScheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
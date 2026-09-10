package com.armanmaurya.internetradio.ui.shared.theme

import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.armanmaurya.internetradio.R
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme

enum class AppColor(
    @StringRes val titleRes: Int,
    val previewColor: Color
) {
    PURPLE(
        titleRes = R.string.settings_color_purple,
        previewColor = Color(0xFF7B1FA2)
    ),
    BLUE(
        titleRes = R.string.settings_color_blue,
        previewColor = Color(0xFF0066CC)
    ),
    TEAL(
        titleRes = R.string.settings_color_teal,
        previewColor = Color(0xFF00796B)
    ),
    GREEN(
        titleRes = R.string.settings_color_green,
        previewColor = Color(0xFF2E7D32)
    ),
    YELLOW(
        titleRes = R.string.settings_color_yellow,
        previewColor = Color(0xFFB26A00)
    ),
    PINK(
        titleRes = R.string.settings_color_pink,
        previewColor = Color(0xFFE91E63)
    ),
    RED(
        titleRes = R.string.settings_color_red,
        previewColor = Color(0xFFC62828)
    ),
    CUSTOM(
        titleRes = R.string.settings_color_custom,
        previewColor = Color(0xFF00BCD4)
    );

    fun getLightColorScheme(): ColorScheme = getColorScheme(darkTheme = false)

    fun getDarkColorScheme(): ColorScheme = getColorScheme(darkTheme = true)

    fun getColorScheme(darkTheme: Boolean, customColorArgb: Int? = null): ColorScheme {
        val seed = if (this == CUSTOM && customColorArgb != null) {
            Color(customColorArgb)
        } else {
            previewColor
        }
        return dynamicColorScheme(
            seedColor = seed,
            isDark = darkTheme,
            style = PaletteStyle.Vibrant
        )
    }

    fun getPreviewColors(darkTheme: Boolean, customColorArgb: Int? = null): List<Color> {
        val scheme = getColorScheme(darkTheme, customColorArgb)
        return listOf(scheme.primary, scheme.secondary, scheme.tertiary, scheme.primaryContainer)
    }
}

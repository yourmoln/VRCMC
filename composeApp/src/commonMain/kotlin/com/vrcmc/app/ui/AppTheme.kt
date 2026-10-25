package com.vrcmc.app

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal enum class ThemeMode(val storageKey: String) {
    SYSTEM("system"),
    DARK("dark"),
    LIGHT("light");

    companion object {
        fun fromStorage(value: String): ThemeMode =
            entries.firstOrNull { it.storageKey == value } ?: SYSTEM
    }
}

internal enum class ThemeColor(
    val storageKey: String,
    val swatch: Color,
    internal val palette: AccentPalette,
) {
    BLUE(
        storageKey = "blue",
        swatch = Color(0xFF8DCBFF),
        palette =
            AccentPalette(
                lightPrimary = Color(0xFF00639B),
                lightPrimaryContainer = Color(0xFFCDE5FF),
                lightOnPrimaryContainer = Color(0xFF001D32),
                darkPrimary = Color(0xFF8DCBFF),
                darkOnPrimary = Color(0xFF003353),
                darkPrimaryContainer = Color(0xFF004A77),
                darkOnPrimaryContainer = Color(0xFFCDE5FF),
            ),
    ),
    PURPLE(
        storageKey = "purple",
        swatch = Color(0xFFC7A5FF),
        palette =
            AccentPalette(
                lightPrimary = Color(0xFF70499B),
                lightPrimaryContainer = Color(0xFFEDDCFF),
                lightOnPrimaryContainer = Color(0xFF290052),
                darkPrimary = Color(0xFFD8B8FF),
                darkOnPrimary = Color(0xFF3F1D66),
                darkPrimaryContainer = Color(0xFF57387E),
                darkOnPrimaryContainer = Color(0xFFEDDCFF),
            ),
    ),
    PINK(
        storageKey = "pink",
        swatch = Color(0xFFFFA7C4),
        palette =
            AccentPalette(
                lightPrimary = Color(0xFF9D3D67),
                lightPrimaryContainer = Color(0xFFFFD9E4),
                lightOnPrimaryContainer = Color(0xFF3F001C),
                darkPrimary = Color(0xFFFFB0CC),
                darkOnPrimary = Color(0xFF5E1134),
                darkPrimaryContainer = Color(0xFF7A294E),
                darkOnPrimaryContainer = Color(0xFFFFD9E4),
            ),
    ),
    ORANGE(
        storageKey = "orange",
        swatch = Color(0xFFFFAE68),
        palette =
            AccentPalette(
                lightPrimary = Color(0xFF8A4A00),
                lightPrimaryContainer = Color(0xFFFFDCC2),
                lightOnPrimaryContainer = Color(0xFF2D1600),
                darkPrimary = Color(0xFFFFB875),
                darkOnPrimary = Color(0xFF4D2600),
                darkPrimaryContainer = Color(0xFF6F3300),
                darkOnPrimaryContainer = Color(0xFFFFDCC2),
            ),
    ),
    GREEN(
        storageKey = "green",
        swatch = Color(0xFF7FDA9E),
        palette =
            AccentPalette(
                lightPrimary = Color(0xFF1F6B42),
                lightPrimaryContainer = Color(0xFFA8F5C2),
                lightOnPrimaryContainer = Color(0xFF00210F),
                darkPrimary = Color(0xFF84DDA4),
                darkOnPrimary = Color(0xFF00391E),
                darkPrimaryContainer = Color(0xFF005230),
                darkOnPrimaryContainer = Color(0xFFA8F5C2),
            ),
    ),
    CYAN(
        storageKey = "cyan",
        swatch = Color(0xFF65D6DE),
        palette =
            AccentPalette(
                lightPrimary = Color(0xFF00636B),
                lightPrimaryContainer = Color(0xFFA1EFF7),
                lightOnPrimaryContainer = Color(0xFF002022),
                darkPrimary = Color(0xFF66D7DF),
                darkOnPrimary = Color(0xFF00363C),
                darkPrimaryContainer = Color(0xFF004F56),
                darkOnPrimaryContainer = Color(0xFFA1EFF7),
            ),
    );

    companion object {
        fun fromStorage(value: String): ThemeColor =
            entries.firstOrNull { it.storageKey == value } ?: BLUE
    }
}

internal data class AccentPalette(
    val lightPrimary: Color,
    val lightPrimaryContainer: Color,
    val lightOnPrimaryContainer: Color,
    val darkPrimary: Color,
    val darkOnPrimary: Color,
    val darkPrimaryContainer: Color,
    val darkOnPrimaryContainer: Color,
)

internal fun vrcmcColorScheme(color: ThemeColor, dark: Boolean): ColorScheme {
    val palette = color.palette
    return if (dark) {
        darkColorScheme(
            primary = palette.darkPrimary,
            onPrimary = palette.darkOnPrimary,
            primaryContainer = palette.darkPrimaryContainer,
            onPrimaryContainer = palette.darkOnPrimaryContainer,
            inversePrimary = palette.lightPrimary,
            secondary = palette.darkPrimary,
            onSecondary = palette.darkOnPrimary,
            secondaryContainer = palette.darkPrimaryContainer,
            onSecondaryContainer = palette.darkOnPrimaryContainer,
            tertiary = palette.darkPrimary,
            onTertiary = palette.darkOnPrimary,
            tertiaryContainer = palette.darkPrimaryContainer,
            onTertiaryContainer = palette.darkOnPrimaryContainer,
            background = Color(0xFF101010),
            onBackground = Color(0xFFF1F1F1),
            surface = Color(0xFF171717),
            onSurface = Color(0xFFF1F1F1),
            surfaceVariant = Color(0xFF414141),
            onSurfaceVariant = Color(0xFFC5C5C5),
            surfaceTint = palette.darkPrimary,
            inverseSurface = Color(0xFFE9E9E9),
            inverseOnSurface = Color(0xFF1A1A1A),
            outline = Color(0xFF929292),
            outlineVariant = Color(0xFF414141),
            scrim = Color.Black,
            surfaceBright = Color(0xFF303030),
            surfaceDim = Color(0xFF101010),
            surfaceContainerLowest = Color(0xFF0B0B0B),
            surfaceContainerLow = Color(0xFF151515),
            surfaceContainer = Color(0xFF1C1C1C),
            surfaceContainerHigh = Color(0xFF252525),
            surfaceContainerHighest = Color(0xFF303030),
        )
    } else {
        lightColorScheme(
            primary = palette.lightPrimary,
            onPrimary = Color.White,
            primaryContainer = palette.lightPrimaryContainer,
            onPrimaryContainer = palette.lightOnPrimaryContainer,
            inversePrimary = palette.darkPrimary,
            secondary = palette.lightPrimary,
            onSecondary = Color.White,
            secondaryContainer = palette.lightPrimaryContainer,
            onSecondaryContainer = palette.lightOnPrimaryContainer,
            tertiary = palette.lightPrimary,
            onTertiary = Color.White,
            tertiaryContainer = palette.lightPrimaryContainer,
            onTertiaryContainer = palette.lightOnPrimaryContainer,
            background = Color(0xFFF8F8F8),
            onBackground = Color(0xFF1B1B1B),
            surface = Color(0xFFFCFCFC),
            onSurface = Color(0xFF1B1B1B),
            surfaceVariant = Color(0xFFDDDDDF),
            onSurfaceVariant = Color(0xFF4A4A4D),
            surfaceTint = palette.lightPrimary,
            inverseSurface = Color(0xFF303030),
            inverseOnSurface = Color(0xFFF2F2F2),
            outline = Color(0xFF77777A),
            outlineVariant = Color(0xFFC7C7CA),
            scrim = Color.Black,
            surfaceBright = Color(0xFFFFFFFF),
            surfaceDim = Color(0xFFE2E2E2),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFF5F5F5),
            surfaceContainer = Color(0xFFEEEEEF),
            surfaceContainerHigh = Color(0xFFE8E8E9),
            surfaceContainerHighest = Color(0xFFE1E1E3),
        )
    }
}

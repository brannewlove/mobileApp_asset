package com.antigravity.assetmanager.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = AppColors.Primary,
    onPrimary = AppColors.TextMain,
    secondary = AppColors.Secondary,
    onSecondary = AppColors.TextMain,
    error = AppColors.Danger,
    onError = AppColors.TextMain,
    background = AppColors.BgDark,
    onBackground = AppColors.TextMain,
    surface = AppColors.BgCardSolid,
    onSurface = AppColors.TextMain,
    surfaceVariant = AppColors.BgCardSolid,
    onSurfaceVariant = AppColors.TextMuted,
    outline = AppColors.BorderGlass
)

@Composable
fun AssetManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}

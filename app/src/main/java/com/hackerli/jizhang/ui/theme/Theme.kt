package com.hackerli.jizhang.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F6F5F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5EDE6),
    onPrimaryContainer = Color(0xFF123E39),
    secondary = Color(0xFFE76F51),
    background = Color(0xFFF8F7F3),
    surface = Color(0xFFFFFBF7),
    surfaceVariant = Color(0xFFF0EDE7),
    outline = Color(0xFF81746A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF83D5C2),
    onPrimary = Color(0xFF003732),
    primaryContainer = Color(0xFF145D55),
    secondary = Color(0xFFFFB4A3),
    background = Color(0xFF141412),
    surface = Color(0xFF1C1B19),
    surfaceVariant = Color(0xFF302E2A),
)

@Composable
fun JiDeJiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                window.statusBarColor = Color.Transparent.toArgb()
                window.navigationBarColor = Color.Transparent.toArgb()
            }
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

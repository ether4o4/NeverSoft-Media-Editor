package com.neversoft.editor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// A single, confident dark identity — video editors live in the dark so footage
// colors read true. Brand gradient runs violet -> magenta.
val Violet = Color(0xFF9B6BFF)
val Magenta = Color(0xFFFF3D9A)
val Bg = Color(0xFF0B0B10)
val Surface1 = Color(0xFF15151E)
val Surface2 = Color(0xFF1E1E2A)
val OnDim = Color(0xFF9A9AB2)
val Stroke = Color(0xFF2A2A38)
val Success = Color(0xFF3DDC84)

private val Scheme = darkColorScheme(
    primary = Violet,
    onPrimary = Color.White,
    secondary = Magenta,
    onSecondary = Color.White,
    background = Bg,
    onBackground = Color.White,
    surface = Surface1,
    onSurface = Color.White,
    surfaceVariant = Surface2,
    onSurfaceVariant = OnDim,
    outline = Stroke,
    error = Color(0xFFFF5A5A),
)

@Composable
fun NeverSoftTheme(content: @Composable () -> Unit) {
    // Always dark on purpose — ignore the system setting for a consistent look.
    @Suppress("UNUSED_EXPRESSION")
    isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = Scheme,
        typography = Typography(),
        content = content,
    )
}

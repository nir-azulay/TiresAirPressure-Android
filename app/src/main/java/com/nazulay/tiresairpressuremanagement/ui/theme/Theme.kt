package com.nazulay.tiresairpressuremanagement.ui.theme

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Modern blue-based color scheme
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF1976D2),      // Deeper Blue
    secondary = Color(0xFF26A69A),     // Teal
    tertiary = Color(0xFF7E57C2),      // Purple
    background = Color(0xFF121212),    // Dark Gray
    surface = Color(0xFF1E1E1E),       // Slightly lighter dark gray
    error = Color(0xFFE57373),         // Soft Red
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
    onError = Color.Black
)

// Light color scheme is no longer needed but kept for reference
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1976D2),      // Deeper Blue
    secondary = Color(0xFF26A69A),     // Teal
    tertiary = Color(0xFF7E57C2),      // Purple
    background = Color.White,
    surface = Color(0xFFF5F5F5),       // Light Gray
    error = Color(0xFFE53935),         // Red
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF121212),
    onSurface = Color(0xFF121212),
    onError = Color.White
)

@Composable
fun TiresAirPressureManagementTheme(
    // Dark theme parameter is no longer used but kept for compatibility
    darkTheme: Boolean = true,
    // Dynamic color parameter remains but will only apply to dark theme
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    // Always use dark theme regardless of system settings or preferences
    val shouldUseDarkTheme = true
    
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            // Always use dynamic dark color scheme if available
            dynamicDarkColorScheme(context)
        }
        else -> DarkColorScheme // Always use dark color scheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            // Always use dark status bar appearance
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
} 

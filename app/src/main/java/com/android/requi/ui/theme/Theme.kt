package com.android.requi.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private fun getDarkColorScheme(accent: String, amoled: Boolean): ColorScheme {
    val primaryColor = when (accent.lowercase()) {
        "blue" -> Color(0xFF93C5FD)
        "teal" -> Color(0xFF99F6E4)
        "green" -> Color(0xFFA7F3D0)
        "orange" -> Color(0xFFFED7AA)
        "red" -> Color(0xFFFECACA)
        "pink" -> Color(0xFFFBCFE8)
        else -> Color(0xFFD0BCFF) // purple
    }
    val secondaryColor = when (accent.lowercase()) {
        "blue" -> Color(0xFFBFDBFE)
        "teal" -> Color(0xFFCCFBF1)
        "green" -> Color(0xFFD1FAE5)
        "orange" -> Color(0xFFFFEAD2)
        "red" -> Color(0xFFFEE2E2)
        "pink" -> Color(0xFFFCE7F3)
        else -> Color(0xFFCCC2DC) // purple
    }
    val tertiaryColor = when (accent.lowercase()) {
        "blue" -> Color(0xFFDBEAFE)
        "teal" -> Color(0xFFE6FFFA)
        "green" -> Color(0xFFECFDF5)
        "orange" -> Color(0xFFFFF7ED)
        "red" -> Color(0xFFFEF2F2)
        "pink" -> Color(0xFFFDF2F8)
        else -> Color(0xFFEFB8C8) // purple
    }

    val baseScheme = darkColorScheme(
        primary = primaryColor,
        secondary = secondaryColor,
        tertiary = tertiaryColor
    )

    return if (amoled) {
        baseScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceVariant = Color(0xFF121212),
            onBackground = Color.White,
            onSurface = Color.White,
            onSurfaceVariant = Color(0xFFE0E0E0),
            secondaryContainer = Color(0xFF1E1E1E),
            onSecondaryContainer = Color.White
        )
    } else {
        baseScheme
    }
}

private fun getLightColorScheme(accent: String): ColorScheme {
    val primaryColor = when (accent.lowercase()) {
        "blue" -> Color(0xFF1D4ED8)
        "teal" -> Color(0xFF0F766E)
        "green" -> Color(0xFF047857)
        "orange" -> Color(0xFFC2410C)
        "red" -> Color(0xFFB91C1C)
        "pink" -> Color(0xFFBE185D)
        else -> Color(0xFF6650a4) // purple
    }
    val secondaryColor = when (accent.lowercase()) {
        "blue" -> Color(0xFF2563EB)
        "teal" -> Color(0xFF0D9488)
        "green" -> Color(0xFF059669)
        "orange" -> Color(0xFFEA580C)
        "red" -> Color(0xFFDC2626)
        "pink" -> Color(0xFFDB2777)
        else -> Color(0xFF625b71) // purple
    }
    val tertiaryColor = when (accent.lowercase()) {
        "blue" -> Color(0xFF3B82F6)
        "teal" -> Color(0xFF14B8A6)
        "green" -> Color(0xFF10B981)
        "orange" -> Color(0xFFF97316)
        "red" -> Color(0xFFEF4444)
        "pink" -> Color(0xFFEC4899)
        else -> Color(0xFF7D5260) // purple
    }

    return lightColorScheme(
        primary = primaryColor,
        secondary = secondaryColor,
        tertiary = tertiaryColor
    )
}

@Composable
fun ReqUITheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentColor: String = "purple",
    amoledMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val useDynamicColors = accentColor.lowercase() == "system" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = when {
        useDynamicColors && darkTheme -> {
            val dynamicScheme = dynamicDarkColorScheme(context)
            if (amoledMode) {
                dynamicScheme.copy(
                    background = Color.Black,
                    surface = Color.Black,
                    surfaceVariant = Color(0xFF121212),
                    onBackground = Color.White,
                    onSurface = Color.White,
                    onSurfaceVariant = Color(0xFFE0E0E0),
                    secondaryContainer = Color(0xFF1E1E1E),
                    onSecondaryContainer = Color.White
                )
            } else {
                dynamicScheme
            }
        }
        useDynamicColors && !darkTheme -> dynamicLightColorScheme(context)
        darkTheme -> getDarkColorScheme(accentColor, amoledMode)
        else -> getLightColorScheme(accentColor)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
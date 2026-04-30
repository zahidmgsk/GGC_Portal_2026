package com.example.myapplication.ui.theme

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class AppTheme(val displayName: String) {
    DEFAULT("Default"),
    RED("Red"),
    GREEN("Green"),
    BLUE("Blue"),
    ORANGE("Orange")
}

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

private val RedColorScheme = lightColorScheme(
    primary = RedPrimary,
    secondary = RedSecondary,
    tertiary = RedTertiary
)

private val GreenColorScheme = lightColorScheme(
    primary = GreenPrimary,
    secondary = GreenSecondary,
    tertiary = GreenTertiary
)

private val BlueColorScheme = lightColorScheme(
    primary = BluePrimary,
    secondary = BlueSecondary,
    tertiary = BlueTertiary
)

private val OrangeColorScheme = lightColorScheme(
    primary = OrangePrimary,
    secondary = OrangeSecondary,
    tertiary = OrangeTertiary
)

class ThemeViewModel(private val sharedPreferences: SharedPreferences) : ViewModel() {

    private val _theme = MutableStateFlow(getCurrentTheme())
    val theme: StateFlow<AppTheme> = _theme

    private fun getCurrentTheme(): AppTheme {
        val themeName = sharedPreferences.getString("theme", AppTheme.DEFAULT.name) ?: AppTheme.DEFAULT.name
        return AppTheme.valueOf(themeName)
    }

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch {
            _theme.emit(theme)
            sharedPreferences.edit().putString("theme", theme.name).apply()
        }
    }
}

@Composable
fun ComplaintPortalTheme(
    themeViewModel: ThemeViewModel,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val currentTheme by themeViewModel.theme.collectAsState()
    val colorScheme = when (currentTheme) {
        AppTheme.DEFAULT -> if (darkTheme) DarkColorScheme else LightColorScheme
        AppTheme.RED -> RedColorScheme
        AppTheme.GREEN -> GreenColorScheme
        AppTheme.BLUE -> BlueColorScheme
        AppTheme.ORANGE -> OrangeColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

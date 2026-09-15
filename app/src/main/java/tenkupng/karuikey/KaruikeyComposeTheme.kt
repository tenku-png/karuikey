package tenkupng.karuikey

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun KaruikeyComposeTheme(
    themeMode: String,
    dynamicColors: Boolean,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val dark = when (themeMode) {
        KaruikeyPreferences.THEME_LIGHT -> false
        KaruikeyPreferences.THEME_DARK -> true
        else -> isSystemInDarkTheme()
    }
    val colors = if (dynamicColors && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) {
        darkColorScheme(
            primary = Color(0xFFB8C7FF),
            onPrimary = Color(0xFF172D61),
            primaryContainer = Color(0xFF304578),
            onPrimaryContainer = Color(0xFFDCE2FF),
            surface = Color(0xFF111318),
            surfaceContainer = Color(0xFF1D1F25)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF465D91),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFD9E2FF),
            onPrimaryContainer = Color(0xFF001A41),
            surface = Color(0xFFFAF8FF),
            surfaceContainer = Color(0xFFEDEEF5)
        )
    }
    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp),
            extraLarge = RoundedCornerShape(32.dp)
        ),
        content = content
    )
}

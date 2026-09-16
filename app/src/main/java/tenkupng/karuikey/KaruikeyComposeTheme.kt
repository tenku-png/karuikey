package tenkupng.karuikey

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun robotoFlex(context: Context) = FontFamily(
    Font(
        "roboto_flex.ttf",
        context.assets,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(400), FontVariation.width(KaruikeyTypeface.WIDTH),
            FontVariation.opticalSizing(16.sp)
        )
    ),
    Font(
        "roboto_flex.ttf",
        context.assets,
        weight = FontWeight(500),
        variationSettings = FontVariation.Settings(
            FontVariation.weight(500), FontVariation.width(KaruikeyTypeface.WIDTH),
            FontVariation.opticalSizing(16.sp)
        )
    ),
    Font(
        "roboto_flex.ttf",
        context.assets,
        weight = FontWeight(600),
        variationSettings = FontVariation.Settings(
            FontVariation.weight(600), FontVariation.width(KaruikeyTypeface.WIDTH),
            FontVariation.opticalSizing(16.sp)
        )
    )
)

private fun TextStyle.withRobotoFlex(fontFamily: FontFamily, weight: FontWeight? = null) = copy(
    fontFamily = fontFamily,
    fontWeight = weight ?: fontWeight
)

private fun karuikeyTypography(context: Context) = Typography().let { base ->
    val fontFamily = robotoFlex(context)
    base.copy(
        displayLarge = base.displayLarge.withRobotoFlex(fontFamily, FontWeight(600)),
        displayMedium = base.displayMedium.withRobotoFlex(fontFamily, FontWeight(600)),
        displaySmall = base.displaySmall.withRobotoFlex(fontFamily, FontWeight(600)),
        headlineLarge = base.headlineLarge.withRobotoFlex(fontFamily, FontWeight(600)),
        headlineMedium = base.headlineMedium.withRobotoFlex(fontFamily, FontWeight(600)),
        headlineSmall = base.headlineSmall.withRobotoFlex(fontFamily, FontWeight(600)),
        titleLarge = base.titleLarge.withRobotoFlex(fontFamily, FontWeight(600)),
        titleMedium = base.titleMedium.withRobotoFlex(fontFamily, FontWeight(500)),
        titleSmall = base.titleSmall.withRobotoFlex(fontFamily, FontWeight(500)),
        bodyLarge = base.bodyLarge.withRobotoFlex(fontFamily, FontWeight.Normal),
        bodyMedium = base.bodyMedium.withRobotoFlex(fontFamily, FontWeight.Normal),
        bodySmall = base.bodySmall.withRobotoFlex(fontFamily, FontWeight.Normal),
        labelLarge = base.labelLarge.withRobotoFlex(fontFamily, FontWeight(500)),
        labelMedium = base.labelMedium.withRobotoFlex(fontFamily, FontWeight(500)),
        labelSmall = base.labelSmall.withRobotoFlex(fontFamily, FontWeight(500))
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KaruikeyComposeTheme(
    themeMode: String,
    dynamicColors: Boolean,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val typography = remember(context) { karuikeyTypography(context) }
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
        expressiveLightColorScheme().copy(
            primary = Color(0xFF465D91),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFD9E2FF),
            onPrimaryContainer = Color(0xFF001A41),
            surface = Color(0xFFFAF8FF),
            surfaceContainer = Color(0xFFEDEEF5)
        )
    }
    MaterialExpressiveTheme(
        colorScheme = colors,
        motionScheme = MotionScheme.expressive(),
        typography = typography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(4.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(24.dp),
            extraLarge = RoundedCornerShape(32.dp),
            largeIncreased = RoundedCornerShape(36.dp),
            extraLargeIncreased = RoundedCornerShape(44.dp),
            extraExtraLarge = RoundedCornerShape(52.dp)
        ),
        content = content
    )
}

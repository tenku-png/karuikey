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
import androidx.compose.material3.lightColorScheme
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

// Full Material 3 schemes generated from seed #465D91 (TonalSpot, standard contrast).
private val karuikeyLightColors = lightColorScheme(
        primary = Color(0xFF465D91),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD9E2FF),
        onPrimaryContainer = Color(0xFF2E4578),
        inversePrimary = Color(0xFFAFC6FF),
        secondary = Color(0xFF575E71),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFDCE2F9),
        onSecondaryContainer = Color(0xFF404659),
        tertiary = Color(0xFF725572),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFDD7FA),
        onTertiaryContainer = Color(0xFF593D5A),
        background = Color(0xFFFAF8FF),
        onBackground = Color(0xFF1A1B20),
        surface = Color(0xFFFAF8FF),
        onSurface = Color(0xFF1A1B20),
        surfaceVariant = Color(0xFFE1E2EC),
        onSurfaceVariant = Color(0xFF44464F),
        surfaceTint = Color(0xFF465D91),
        inverseSurface = Color(0xFF2F3036),
        inverseOnSurface = Color(0xFFF1F0F7),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF93000A),
        outline = Color(0xFF757780),
        outlineVariant = Color(0xFFC5C6D0),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFFFAF8FF),
        surfaceContainer = Color(0xFFEEEDF4),
        surfaceContainerHigh = Color(0xFFE8E7EF),
        surfaceContainerHighest = Color(0xFFE2E2E9),
        surfaceContainerLow = Color(0xFFF4F3FA),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceDim = Color(0xFFDAD9E0)
)

private val karuikeyDarkColors = darkColorScheme(
        primary = Color(0xFFAFC6FF),
        onPrimary = Color(0xFF142F60),
        primaryContainer = Color(0xFF2E4578),
        onPrimaryContainer = Color(0xFFD9E2FF),
        inversePrimary = Color(0xFF465D91),
        secondary = Color(0xFFBFC6DC),
        onSecondary = Color(0xFF293042),
        secondaryContainer = Color(0xFF404659),
        onSecondaryContainer = Color(0xFFDCE2F9),
        tertiary = Color(0xFFDFBBDE),
        onTertiary = Color(0xFF412742),
        tertiaryContainer = Color(0xFF593D5A),
        onTertiaryContainer = Color(0xFFFDD7FA),
        background = Color(0xFF121318),
        onBackground = Color(0xFFE2E2E9),
        surface = Color(0xFF121318),
        onSurface = Color(0xFFE2E2E9),
        surfaceVariant = Color(0xFF44464F),
        onSurfaceVariant = Color(0xFFC5C6D0),
        surfaceTint = Color(0xFFAFC6FF),
        inverseSurface = Color(0xFFE2E2E9),
        inverseOnSurface = Color(0xFF2F3036),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        outline = Color(0xFF8F9099),
        outlineVariant = Color(0xFF44464F),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFF38393F),
        surfaceContainer = Color(0xFF1E1F25),
        surfaceContainerHigh = Color(0xFF282A2F),
        surfaceContainerHighest = Color(0xFF33353A),
        surfaceContainerLow = Color(0xFF1A1B20),
        surfaceContainerLowest = Color(0xFF0C0E13),
        surfaceDim = Color(0xFF121318)
)

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
        karuikeyDarkColors
    } else {
        karuikeyLightColors
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

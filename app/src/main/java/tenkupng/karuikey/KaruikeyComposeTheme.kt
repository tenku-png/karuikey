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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalFontFamilyResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Body copy keeps a near-normal width so long summaries stay readable.
private const val BODY_WIDTH = 100f
private const val BODY_YTLC = 520f

// Display, headline and title text use Roboto Flex's wide, heavy masters for the
// bold Expressive look; the font already ships with Cyrillic, unlike Google Sans Flex.
private const val DISPLAY_WIDTH = 140f
private const val TITLE_WIDTH = 115f

private fun robotoFlex(
    context: Context,
    opticalSize: TextUnit,
    width: Float,
    weights: List<Int>
) = FontFamily(
    weights.map { weight ->
        Font(
            "roboto_flex.ttf",
            context.assets,
            weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(
                FontVariation.weight(weight), FontVariation.width(width),
                FontVariation.Setting("YTLC", BODY_YTLC),
                FontVariation.opticalSizing(opticalSize)
            )
        )
    }
)

private fun TextStyle.flex(fontFamily: FontFamily, weight: Int, tracking: TextUnit? = null) = copy(
    fontFamily = fontFamily,
    fontWeight = FontWeight(weight),
    letterSpacing = tracking ?: letterSpacing
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun karuikeyTypography(context: Context) = Typography().let { base ->
    // Optical size per Material 3 role group so large and small text use the matching Flex master.
    val display = robotoFlex(context, 48.sp, DISPLAY_WIDTH, listOf(800, 900))
    val headline = robotoFlex(context, 28.sp, DISPLAY_WIDTH, listOf(700, 800))
    val title = robotoFlex(context, 20.sp, TITLE_WIDTH, listOf(600, 700, 800))
    val body = robotoFlex(context, 16.sp, BODY_WIDTH, listOf(400, 500, 600))
    val label = robotoFlex(context, 12.sp, TITLE_WIDTH, listOf(600, 700, 800))
    val tight = (-0.5).sp
    base.copy(
        displayLarge = base.displayLarge.flex(display, 800, tight),
        displayMedium = base.displayMedium.flex(display, 800, tight),
        displaySmall = base.displaySmall.flex(display, 800, tight),
        headlineLarge = base.headlineLarge.flex(headline, 700, tight),
        headlineMedium = base.headlineMedium.flex(headline, 700, tight),
        headlineSmall = base.headlineSmall.flex(headline, 700),
        titleLarge = base.titleLarge.flex(title, 700),
        titleMedium = base.titleMedium.flex(title, 600),
        titleSmall = base.titleSmall.flex(title, 600),
        bodyLarge = base.bodyLarge.flex(body, 400),
        bodyMedium = base.bodyMedium.flex(body, 400),
        bodySmall = base.bodySmall.flex(body, 400),
        labelLarge = base.labelLarge.flex(label, 700),
        labelMedium = base.labelMedium.flex(label, 600),
        labelSmall = base.labelSmall.flex(label, 600),
        displayLargeEmphasized = base.displayLargeEmphasized.flex(display, 900, tight),
        displayMediumEmphasized = base.displayMediumEmphasized.flex(display, 900, tight),
        displaySmallEmphasized = base.displaySmallEmphasized.flex(display, 900, tight),
        headlineLargeEmphasized = base.headlineLargeEmphasized.flex(headline, 800, tight),
        headlineMediumEmphasized = base.headlineMediumEmphasized.flex(headline, 800, tight),
        headlineSmallEmphasized = base.headlineSmallEmphasized.flex(headline, 800),
        titleLargeEmphasized = base.titleLargeEmphasized.flex(title, 800),
        titleMediumEmphasized = base.titleMediumEmphasized.flex(title, 700),
        titleSmallEmphasized = base.titleSmallEmphasized.flex(title, 700),
        bodyLargeEmphasized = base.bodyLargeEmphasized.flex(body, 600),
        bodyMediumEmphasized = base.bodyMediumEmphasized.flex(body, 600),
        bodySmallEmphasized = base.bodySmallEmphasized.flex(body, 600),
        labelLargeEmphasized = base.labelLargeEmphasized.flex(label, 800),
        labelMediumEmphasized = base.labelMediumEmphasized.flex(label, 700),
        labelSmallEmphasized = base.labelSmallEmphasized.flex(label, 700)
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun Typography.allFamilies() = listOfNotNull(
    displayLargeEmphasized.fontFamily, headlineLargeEmphasized.fontFamily,
    titleLargeEmphasized.fontFamily, bodyLargeEmphasized.fontFamily,
    labelLargeEmphasized.fontFamily
).distinct()

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
    val fontResolver = LocalFontFamilyResolver.current
    LaunchedEffect(typography) {
        // Each wdth/wght instance is built from the 1.6 MB variable font on first use; warm
        // them off the main thread so the first visit to a tab doesn't stall on font loading.
        withContext(Dispatchers.Default) {
            typography.allFamilies().forEach { fontResolver.preload(it) }
        }
    }
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

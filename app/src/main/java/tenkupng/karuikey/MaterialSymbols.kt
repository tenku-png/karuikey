package tenkupng.karuikey

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class KaruikeySymbol(val codePoint: Int) {
    LANGUAGE(0xEA07),
    PALETTE(0xE40A),
    KEYBOARD(0xE312),
    CONTENT_PASTE(0xE14F),
    KEYBOARD_ALT(0xF028),
    INFO(0xE88E),
    SETTINGS(0xE8B8),
    SEARCH(0xEF7A),
    ARROW_BACK(0xE5C4),
    CHEVRON_RIGHT(0xE5CC),
    EDIT(0xF097),
    DELETE_SWEEP(0xE16C),
    HOME(0xE9B2),
    TUNE(0xE429),
    CHECK_CIRCLE(0xF0BE),
    DARK_MODE(0xE51C),
    LIGHT_MODE(0xE518),
    BRIGHTNESS_AUTO(0xE1AB),
    HEIGHT(0xEA16),
    BLUR_ON(0xE3A5),
    OPEN_IN_NEW(0xE89E),
    ARROW_FORWARD(0xE5C8),
    SPELLCHECK(0xE8CE),
    SWIPE(0xE9EC),
    TEXT_FIELDS(0xE262),
    AUTO_AWESOME(0xE65F),
    EMOJI(0xEA22),
    WARNING(0xF083);

    val glyph: String = String(Character.toChars(codePoint))
}

private val materialSymbolsRounded = FontFamily(Font(R.font.material_symbols_rounded))
private val materialSymbolsRoundedFilled = FontFamily(
    Font(
        R.font.material_symbols_rounded,
        variationSettings = FontVariation.Settings(FontVariation.Setting("FILL", 1f))
    )
)

@Composable
fun MaterialSymbolIcon(
    symbol: KaruikeySymbol,
    contentDescription: String? = null,
    modifier: Modifier = Modifier.size(24.dp),
    size: TextUnit = 24.sp,
    tint: Color = MaterialTheme.colorScheme.primary,
    filled: Boolean = false
) {
    val semanticsModifier = if (contentDescription == null) {
        modifier.clearAndSetSemantics {}
    } else {
        modifier.semantics { this.contentDescription = contentDescription }
    }
    // Symbol glyphs fill an em square; trim the line box to it so the glyph sits on the
    // container's optical center instead of riding the text baseline.
    Box(semanticsModifier, contentAlignment = Alignment.Center) {
        Text(
            text = symbol.glyph,
            color = tint,
            modifier = Modifier.wrapContentSize(unbounded = true),
            style = TextStyle(
                fontFamily = if (filled) materialSymbolsRoundedFilled else materialSymbolsRounded,
                fontSize = size,
                lineHeight = size,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both
                )
            )
        )
    }
}

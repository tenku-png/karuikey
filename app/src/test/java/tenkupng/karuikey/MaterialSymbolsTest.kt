package tenkupng.karuikey

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MaterialSymbolsTest {
    @Test
    fun settingsSymbolsHaveDistinctGlyphs() {
        val symbols = listOf(
            KaruikeySymbol.LANGUAGE,
            KaruikeySymbol.PALETTE,
            KaruikeySymbol.KEYBOARD,
            KaruikeySymbol.CONTENT_PASTE,
            KaruikeySymbol.KEYBOARD_ALT,
            KaruikeySymbol.INFO,
            KaruikeySymbol.SETTINGS,
            KaruikeySymbol.SEARCH,
            KaruikeySymbol.DELETE_SWEEP
        )

        assertEquals(symbols.size, symbols.map { it.glyph }.toSet().size)
        assertTrue(symbols.all { it.glyph.isNotEmpty() })
    }

    @Test
    fun semanticSymbolsUseExpectedCodepoints() {
        assertEquals(0xEA07, KaruikeySymbol.LANGUAGE.codePoint)
        assertEquals(0xE40A, KaruikeySymbol.PALETTE.codePoint)
        assertEquals(0xF097, KaruikeySymbol.EDIT.codePoint)
        assertEquals(0xE16C, KaruikeySymbol.DELETE_SWEEP.codePoint)
    }
}

package tenkupng.karuikey

import android.text.InputType
import android.graphics.Paint
import android.content.Context
import android.os.Debug
import android.util.Log
import android.view.inputmethod.EditorInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.inputmethod.compat.InputMethodSubtypeCompatUtils
import com.android.inputmethod.keyboard.KeyDetector
import com.android.inputmethod.keyboard.Key
import com.android.inputmethod.keyboard.Keyboard
import com.android.inputmethod.keyboard.KeyboardId
import com.android.inputmethod.keyboard.KeyboardLayoutSet
import com.android.inputmethod.keyboard.KeyboardView
import com.android.inputmethod.keyboard.internal.PopupGeometry
import com.android.inputmethod.latin.RichInputMethodSubtype
import com.android.inputmethod.latin.common.Constants
import com.android.inputmethod.latin.common.InputPointers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class KeyboardResourceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun calculatedKeyBoundsStayInsideAvailableWidth() {
        for (width in intArrayOf(240, 320, 411, 1080)) {
            for ((layout, locale) in arrayOf(
                "qwerty" to "en_US",
                "east_slavic" to "ru_RU",
                "qwertz" to "de_DE",
                "azerty" to "fr_FR",
                "spanish" to "es_ES"
            )) {
                val layoutSet = keyboardLayoutSet(width, 260, layout, locale)
                for (element in intArrayOf(
                    KeyboardId.ELEMENT_ALPHABET,
                    KeyboardId.ELEMENT_SYMBOLS,
                    KeyboardId.ELEMENT_SYMBOLS_SHIFTED,
                    KeyboardId.ELEMENT_NUMBER,
                    KeyboardId.ELEMENT_PHONE,
                    KeyboardId.ELEMENT_PHONE_SYMBOLS
                )) {
                    assertKeyboardBounds(layoutSet.getKeyboard(element), width)
                }
            }
        }
    }

    @Test
    fun actionKeyUsesLastRowGeometryAtEveryTestedSize() {
        for (width in intArrayOf(240, 411, 1080)) {
            for (height in intArrayOf(180, 260, 340)) {
                val keyboard = keyboardLayoutSet(width, height, "qwerty", "en_US")
                    .getKeyboard(KeyboardId.ELEMENT_ALPHABET)
                val actionKeys = keyboard.getSortedKeys().filter { it.isActionKey() }
                assertEquals(1, actionKeys.size)
                val action = actionKeys.single()
                val rowKeys = keyboard.getSortedKeys().filter {
                    !it.isSpacer() && it.y == action.y
                }
                assertTrue(rowKeys.isNotEmpty())
                assertTrue(rowKeys.all { it.height == action.height })
                assertTrue(action.y >= 0)
                assertTrue(action.y + action.height <= keyboard.mOccupiedHeight)
                assertTrue(action.drawX >= 0)
                assertTrue(action.drawX + action.drawWidth <= width)
                assertTrue(action.hitBox.top >= 0)
                assertTrue(action.hitBox.bottom <= keyboard.mOccupiedHeight)
            }
        }
    }

    @Test
    fun keyboardRowsResizeWithContentHeight() {
        val short = keyboardLayoutSet(411, 180, "qwerty", "en_US")
            .getKeyboard(KeyboardId.ELEMENT_ALPHABET)
        val tall = keyboardLayoutSet(411, 340, "qwerty", "en_US")
            .getKeyboard(KeyboardId.ELEMENT_ALPHABET)
        val shortAction = short.getSortedKeys().single { it.isActionKey() }
        val tallAction = tall.getSortedKeys().single { it.isActionKey() }

        assertTrue(tallAction.height > shortAction.height)
        assertTrue(shortAction.hitBox.bottom <= short.mOccupiedHeight)
        assertTrue(tallAction.hitBox.bottom <= tall.mOccupiedHeight)
    }

    @Test
    fun allEditorActionVariantsUseTheSameBottomRowSlot() {
        for (imeAction in intArrayOf(
            EditorInfo.IME_ACTION_NONE,
            EditorInfo.IME_ACTION_DONE,
            EditorInfo.IME_ACTION_GO,
            EditorInfo.IME_ACTION_NEXT,
            EditorInfo.IME_ACTION_PREVIOUS,
            EditorInfo.IME_ACTION_SEND,
            EditorInfo.IME_ACTION_SEARCH
        )) {
            val keyboard = keyboardLayoutSet(
                411, 260, "qwerty", "en_US", false, imeAction
            ).getKeyboard(KeyboardId.ELEMENT_ALPHABET)
            val action = keyboard.getSortedKeys().single { it.isActionKey() }
            assertEquals(
                keyboard.getSortedKeys().filter { !it.isSpacer() && it.y == action.y }
                    .map { it.height }.toSet().single(),
                action.height
            )
            assertTrue(action.hitBox.bottom <= keyboard.mOccupiedHeight)
        }
    }

    @Test
    fun everyCatalogLanguageMapsToBundledAospLayout() {
        val languages = KaruikeyPreferences.languages(context)
        assertTrue(languages.size > 10)
        for (language in languages) {
            val keyboard = keyboardLayoutSet(320, 260, language.layoutSet, language.locale)
                .getKeyboard(KeyboardId.ELEMENT_ALPHABET)
            assertTrue("No keys for ${language.id}", keyboard.getSortedKeys().isNotEmpty())
        }
    }

    @Test
    fun everyRawCatalogEntryIsUniqueValidAndBuildable() {
        val entries = context.resources.getStringArray(R.array.supported_languages)
        val languages = entries.map { entry ->
            val language = KaruikeyPreferences.parseLanguageEntry(entry)
            assertNotNull("Invalid catalog entry: $entry", language)
            language!!
        }
        assertEquals(languages.size, languages.map { it.id }.toSet().size)
        for (language in languages) {
            assertTrue(language.id.isNotBlank())
            assertTrue(Locale.forLanguageTag(language.locale.replace('_', '-')).language.isNotEmpty())
            assertTrue(language.displayName.isNotBlank())
            assertTrue(language.nativeName.isNotBlank())
            assertTrue(language.layoutName.isNotBlank())
            assertTrue(language.spacebarLabel.isNotBlank())
            assertTrue(KaruikeyPreferences.hasKeyboardLayoutResource(context, language))
            val keyboard = keyboardLayoutSet(320, 260, language.layoutSet, language.locale)
                .getKeyboard(KeyboardId.ELEMENT_ALPHABET)
            assertTrue("No keys for ${language.id}", keyboard.getSortedKeys().isNotEmpty())
        }
    }

    @Test
    fun detectorIgnoresPointsOutsideKeyboardBounds() {
        val keyboard = keyboardLayoutSet(320, 260, "qwerty", "en_US")
            .getKeyboard(KeyboardId.ELEMENT_ALPHABET)
        val detector = KeyDetector(0f, 0f).also { it.setKeyboard(keyboard, 0f, 0f) }

        assertNull(detector.detectHitKey(-1, keyboard.mOccupiedHeight / 2))
        assertNull(detector.detectHitKey(keyboard.mOccupiedWidth, keyboard.mOccupiedHeight / 2))
        assertNull(detector.detectHitKey(keyboard.mOccupiedWidth / 2, -1))
        assertNull(detector.detectHitKey(keyboard.mOccupiedWidth / 2, keyboard.mOccupiedHeight))
    }

    @Test
    fun russianSubtypeUsesEastSlavicLayoutAndSymbolsHaveOneAbcKey() {
        val layoutSet = keyboardLayoutSet(320, 260, "east_slavic", "ru_RU")
        val alphabet = layoutSet.getKeyboard(KeyboardId.ELEMENT_ALPHABET)
        val symbols = layoutSet.getKeyboard(KeyboardId.ELEMENT_SYMBOLS)

        assertTrue(alphabet.getKey('ф'.code) != null)
        assertEquals(1, symbols.getSortedKeys().count { it.label == "АБВ" })
    }

    @Test
    fun languageKeyAndMoreKeysAreProvidedByTheAospLayout() {
        val layoutSet = keyboardLayoutSet(320, 260, "qwerty", "en_US", true)
        val alphabet = layoutSet.getKeyboard(KeyboardId.ELEMENT_ALPHABET)
        val symbols = layoutSet.getKeyboard(KeyboardId.ELEMENT_SYMBOLS)

        assertNotNull(alphabet.getKey(Constants.CODE_LANGUAGE_SWITCH))
        assertEquals('1'.code, alphabet.getKey('q'.code)?.getMoreKeys()?.first()?.mCode)
        assertEquals('0'.code, alphabet.getKey('p'.code)?.getMoreKeys()?.first()?.mCode)
        assertTrue((symbols.getKey('$'.code)?.getMoreKeys()?.size ?: 0) > 1)
    }

    @Test
    fun qwertyNumberHintsAreSingleAlternates() {
        val alphabet = keyboardLayoutSet(320, 260, "qwerty", "en_US")
            .getKeyboard(KeyboardId.ELEMENT_ALPHABET)

        for ((letter, number) in "qwertyuiop".zip("1234567890")) {
            val moreKeys = alphabet.getKey(letter.code)?.getMoreKeys()
            assertNotNull("Missing numeric alternate for $letter", moreKeys)
            assertEquals(1, moreKeys!!.size)
            assertEquals(number.code, moreKeys[0].mCode)
        }
    }

    @Test
    fun robotoFlexLoadsAndCoversKeyboardText() {
        val typeface = KaruikeyTypeface.create(context, 425)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
        }
        for (text in listOf("Aa", "Русский", "123?!", "€$£¥₽")) {
            assertTrue("No measurable glyphs for $text", paint.measureText(text) > 0f)
        }
    }

    @Test
    fun keyboardLabelAndPreviewResolveTheCachedRobotoFlexTypeface() {
        val expected = KaruikeyTypeface.create(context, 425)
        val expectedPreview = KaruikeyTypeface.create(context, 500)
        val view = ExposedKeyboardView(KaruikeyPreferences.keyboardContext(context))
        val key = keyboardLayoutSet(320, 260, "qwerty", "en_US")
            .getKeyboard(KeyboardId.ELEMENT_ALPHABET).getKey('r'.code)

        assertNotNull(key)
        assertEquals(expected, view.defaultTypeface())
        assertEquals(expected, view.labelTypeface(key!!))
        assertEquals(expectedPreview, view.previewTypeface(key))
    }

    @Test
    fun keyPreviewHeightIsBounded() {
        val attrs = context.obtainStyledAttributes(
            R.style.MainKeyboardView, R.styleable.MainKeyboardView
        )
        try {
            val height = attrs.getDimensionPixelSize(
                R.styleable.MainKeyboardView_keyPreviewHeight, 0
            )
            val density = context.resources.displayMetrics.density
            assertTrue(height >= 70 * density)
            assertTrue(height <= 74 * density)
        } finally {
            attrs.recycle()
        }
    }

    @Test
    fun previewGlyphsFitTheFixedPreviewBoundsAtAllKeyboardHeights() {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = KaruikeyTypeface.create(context, 500)
        }
        for (keyHeight in intArrayOf(85, 100, 115)) {
            val keyWidth = (keyHeight * 0.9f).toInt()
            val previewHeight = PopupGeometry.getPreviewHeight(keyHeight, 200)
            val previewWidth = PopupGeometry.clampPreviewWidth(keyWidth, keyWidth)
            val contentWidth = previewWidth - 24
            val contentHeight = previewHeight - 12
            for (label in listOf("1", "4", "6", "8", "0", "i", "I", "Я", "₽")) {
                val desired = PopupGeometry.getPreviewTextSize((keyHeight * 0.55f).toInt())
                val fitted = PopupGeometry.getFittedPreviewTextSize(
                    paint, label, desired.toFloat(), contentWidth, contentHeight
                )
                paint.textSize = fitted
                assertTrue("Width clipped for $label", paint.measureText(label) <= contentWidth + 0.1f)
                assertTrue(
                    "Height clipped for $label",
                    paint.descent() - paint.ascent() <= contentHeight + 0.1f
                )
                assertTrue("Size grew for $label", fitted <= desired + 0.1f)
            }
        }
    }

    @Test
    fun alternatePreviewKeepsTheKeyRelativeContainerWidth() {
        for (keyWidth in intArrayOf(42, 60, 96, 180)) {
            val before = PopupGeometry.clampPreviewWidth(keyWidth / 2, keyWidth)
            val after = PopupGeometry.clampPreviewWidth(keyWidth * 2, keyWidth)
            assertTrue(before >= PopupGeometry.getMinimumPreviewWidth(keyWidth))
            assertTrue(after >= PopupGeometry.getMinimumPreviewWidth(keyWidth))
            assertTrue(before <= PopupGeometry.getMaximumPreviewWidth(keyWidth))
            assertTrue(after <= PopupGeometry.getMaximumPreviewWidth(keyWidth))
        }
    }

    @Test
    fun languageKeyIsOnlyPresentWhenTheLayoutRequestsIt() {
        assertNull(keyboardLayoutSet(320, 260, "qwerty", "en_US")
            .getKeyboard(KeyboardId.ELEMENT_ALPHABET).getKey(Constants.CODE_LANGUAGE_SWITCH))
        assertNull(keyboardLayoutSet(320, 260, "east_slavic", "ru_RU")
            .getKeyboard(KeyboardId.ELEMENT_ALPHABET).getKey(Constants.CODE_LANGUAGE_SWITCH))
        assertNotNull(keyboardLayoutSet(320, 260, "east_slavic", "ru_RU", true)
            .getKeyboard(KeyboardId.ELEMENT_ALPHABET).getKey(Constants.CODE_LANGUAGE_SWITCH))
    }

    @Test
    fun backspaceIsConfiguredForAospRepeatHandling() {
        val keyboard = keyboardLayoutSet(320, 260, "qwerty", "en_US")
            .getKeyboard(KeyboardId.ELEMENT_ALPHABET)
        assertTrue(keyboard.getKey(Constants.CODE_DELETE)?.isRepeatable() == true)
    }

    @Test
    fun gestureDecoderUsesTheLoadedKeyboardGeometry() {
        val keyboard = keyboardLayoutSet(411, 260, "qwerty", "en_US")
            .getKeyboard(KeyboardId.ELEMENT_ALPHABET)
        val points = InputPointers(8)
        for ((time, code) in listOf('h'.code, 'e'.code, 'l'.code, 'o'.code).withIndex()) {
            val key = keyboard.getKey(code)
            assertNotNull(key)
            points.addPointer(
                key!!.x + key.width / 2,
                key.y + key.height / 2,
                0,
                time * 100
            )
        }

        assertEquals("helo", GestureDecoder.decode(keyboard, points))
    }

    @Test
    fun gestureDecoderMatchesCenterPathDespiteIntermediateKeys() {
        val keyboard = keyboardLayoutSet(411, 260, "qwerty", "en_US")
            .getKeyboard(KeyboardId.ELEMENT_ALPHABET)
        val points = InputPointers(16)
        val path = listOf(
            'h', 'g', 'f', 'r', 'e', 'r', 'g', 'h', 'j', 'l', 'l', 'k', 'o'
        )
        for ((time, character) in path.withIndex()) {
            val key = keyboard.getKey(character.code)
            assertNotNull(key)
            points.addPointer(
                key!!.x + key.width / 2,
                key.y + key.height / 2,
                0,
                time * 100
            )
        }

        assertEquals(
            "hello",
            GestureDecoder.findBestCandidate(keyboard, points, arrayOf("help", "hello"))
        )
    }

    @Test
    fun gestureCoveragePrefersAFullRussianWordOverItsShortPrefix() {
        val keyboard = keyboardLayoutSet(411, 260, "east_slavic", "ru_RU")
            .getKeyboard(KeyboardId.ELEMENT_ALPHABET)
        val points = InputPointers(16)
        for ((time, character) in "привет".withIndex()) {
            val key = keyboard.getKey(character.code)
            assertNotNull(key)
            points.addPointer(
                key!!.x + key.width / 2,
                key.y + key.height / 2,
                0,
                time * 100
            )
        }

        SuggestionEngine.loadForTests(context, "ru_RU")
        try {
            assertEquals(
                "привет",
                SuggestionEngine.findGestureCandidate(
                    "ru_RU", keyboard, points, null
                )
            )
        } finally {
            SuggestionEngine.endSession()
        }
    }

    @Test
    fun realDictionariesProvidePrefixContextFallbackAndContinuousCandidates() {
        val suggestions = ArrayList<String>(3)
        SuggestionEngine.endSession()
        val start = System.nanoTime()
        SuggestionEngine.loadForTests(context, "en_US")
        val loadMs = (System.nanoTime() - start) / 1_000_000.0
        try {
            assertTrue(SuggestionEngine.isReady("en_US"))

            SuggestionEngine.fill("en_US", "th", suggestions)
            assertTrue(suggestions.any { it == "the" })
            assertTrue(suggestions.size <= 3)

            SuggestionEngine.fill("en_US", "the", null, null, "", suggestions)
            assertEquals("first", suggestions.first())

            var previous = "the"
            repeat(6) {
                SuggestionEngine.fill("en_US", previous, null, null, "", suggestions)
                assertTrue("No candidate after $previous", suggestions.isNotEmpty())
                previous = suggestions.first()
            }

            SuggestionEngine.fill("en_US", "not-in-the-index", null, null, "", suggestions)
            assertEquals(3, suggestions.size)
        } finally {
            SuggestionEngine.endSession()
        }

        val memoryBefore = Debug.MemoryInfo().also(Debug::getMemoryInfo).totalPss
        val russianStart = System.nanoTime()
        SuggestionEngine.loadForTests(context, "ru_RU")
        val russianLoadMs = (System.nanoTime() - russianStart) / 1_000_000.0
        try {
            assertTrue(SuggestionEngine.isReady("ru_RU"))
            SuggestionEngine.fill("ru_RU", "при", suggestions)
            assertTrue(suggestions.any { it.startsWith("при") })
            SuggestionEngine.fill("ru_RU", "привет", null, null, "", suggestions)
            assertTrue(suggestions.isNotEmpty())
            val memoryAfter = Debug.MemoryInfo().also(Debug::getMemoryInfo).totalPss
            val prefixNanos = measureAverageNanos {
                SuggestionEngine.fill("ru_RU", "при", suggestions)
            }
            val nextNanos = measureAverageNanos {
                SuggestionEngine.fill("ru_RU", "привет", null, null, "", suggestions)
            }
            val assetBytes = context.assets.open("dictionaries/ru.krd").use { it.available() }
            Log.i(
                "KaruikeyMetrics",
                "ru bytes=$assetBytes loadMs=$russianLoadMs pssDeltaKb=${memoryAfter - memoryBefore} " +
                    "prefixUs=${prefixNanos / 1_000.0} nextUs=${nextNanos / 1_000.0}"
            )
        } finally {
            SuggestionEngine.endSession()
        }

        val englishBytes = context.assets.open("dictionaries/en_us.krd").use { it.available() }
        Log.i("KaruikeyMetrics", "en bytes=$englishBytes loadMs=$loadMs")
    }

    private fun measureAverageNanos(block: () -> Unit): Double {
        repeat(20) { block() }
        val start = System.nanoTime()
        repeat(200) { block() }
        return (System.nanoTime() - start) / 200.0
    }

    private fun assertKeyboardBounds(keyboard: Keyboard, width: Int) {
        assertEquals(width, keyboard.mOccupiedWidth)
        assertTrue(keyboard.getSortedKeys().isNotEmpty())
        for (key in keyboard.getSortedKeys()) {
            assertTrue("left=${key.x} width=$width", key.x >= 0)
            assertTrue("right=${key.x + key.width} width=$width", key.x + key.width <= width)
            assertTrue("hitBox=${key.hitBox} width=$width", key.hitBox.left >= 0)
            assertTrue("hitBox=${key.hitBox} width=$width", key.hitBox.right <= width)
        }
    }

    private fun keyboardLayoutSet(
        width: Int,
        height: Int,
        layout: String,
        locale: String,
        languageSwitchKeyEnabled: Boolean = false,
        imeOptions: Int = EditorInfo.IME_ACTION_NONE
    ): KeyboardLayoutSet {
        val subtype = InputMethodSubtypeCompatUtils.newInputMethodSubtype(
            0,
            0,
            locale,
            "keyboard",
            "KeyboardLayoutSet=$layout,AsciiCapable",
            false,
            false,
            width
        )
        val editorInfo = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT
            this.imeOptions = imeOptions
        }
        return KeyboardLayoutSet.Builder(context, editorInfo)
            .setSubtype(RichInputMethodSubtype(subtype))
            .setKeyboardGeometry(width, height)
            .setVoiceInputKeyEnabled(false)
            .setLanguageSwitchKeyEnabled(languageSwitchKeyEnabled)
            .build()
    }

    private class ExposedKeyboardView(context: Context) : KeyboardView(context, null) {
        fun defaultTypeface() = newLabelPaint(null).typeface

        fun labelTypeface(key: Key) = key.selectTypeface(getKeyDrawParams())

        fun previewTypeface(key: Key) = key.selectPreviewTypeface(getKeyDrawParams())
    }
}

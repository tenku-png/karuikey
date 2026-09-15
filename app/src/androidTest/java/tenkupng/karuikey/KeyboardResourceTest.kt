package tenkupng.karuikey

import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.inputmethod.compat.InputMethodSubtypeCompatUtils
import com.android.inputmethod.keyboard.KeyDetector
import com.android.inputmethod.keyboard.Keyboard
import com.android.inputmethod.keyboard.KeyboardId
import com.android.inputmethod.keyboard.KeyboardLayoutSet
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
        assertTrue(symbols.getKey('$'.code)?.getMoreKeys()?.isNotEmpty() == true)
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

        assertEquals(
            "привет",
            SuggestionEngine.findGestureCandidate(
                "ru_RU", keyboard, points, null
            )
        )
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
}

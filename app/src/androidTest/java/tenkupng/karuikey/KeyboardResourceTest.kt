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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeyboardResourceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun calculatedKeyBoundsStayInsideAvailableWidth() {
        for (width in intArrayOf(240, 320, 411, 1080)) {
            val layoutSet = keyboardLayoutSet(width, 260, "qwerty", "en_US")
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
        locale: String
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
        val editorInfo = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }
        return KeyboardLayoutSet.Builder(context, editorInfo)
            .setSubtype(RichInputMethodSubtype(subtype))
            .setKeyboardGeometry(width, height)
            .setVoiceInputKeyEnabled(false)
            .setLanguageSwitchKeyEnabled(false)
            .build()
    }
}

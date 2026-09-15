package tenkupng.karuikey

import android.text.TextUtils
import com.android.inputmethod.event.Event
import com.android.inputmethod.keyboard.internal.KeyboardState
import com.android.inputmethod.latin.common.Constants
import com.android.inputmethod.latin.utils.RecapitalizeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class KeyboardStateTest {
    @Test
    fun startsLowercaseAndOneShotShiftResetsAfterLetter() {
        val actions = FakeSwitchActions()
        val state = KeyboardState(actions)
        actions.state = state
        state.onLoadKeyboard(0, RecapitalizeStatus.NOT_A_RECAPITALIZE_MODE)
        assertEquals("alpha", actions.keyboard)

        state.onPressKey(Constants.CODE_SHIFT, true, 0, RecapitalizeStatus.NOT_A_RECAPITALIZE_MODE)
        assertEquals("manual", actions.keyboard)
        state.onReleaseKey(Constants.CODE_SHIFT, false, 0, RecapitalizeStatus.NOT_A_RECAPITALIZE_MODE)
        state.onEvent(letterEvent('a'.code), 0, RecapitalizeStatus.NOT_A_RECAPITALIZE_MODE)
        assertEquals("alpha", actions.keyboard)
    }

    @Test
    fun editorCapitalizationStartsAutomaticAndReturnsToLowercase() {
        val actions = FakeSwitchActions()
        val state = KeyboardState(actions)
        actions.state = state
        state.onLoadKeyboard(TextUtils.CAP_MODE_SENTENCES, RecapitalizeStatus.NOT_A_RECAPITALIZE_MODE)
        assertEquals("automatic", actions.keyboard)
        state.onEvent(letterEvent('a'.code), 0,
            RecapitalizeStatus.NOT_A_RECAPITALIZE_MODE)
        assertEquals("alpha", actions.keyboard)
    }

    @Test
    fun allEditorCapitalizationModesEnableAutomaticShift() {
        for (flags in intArrayOf(
            TextUtils.CAP_MODE_SENTENCES,
            TextUtils.CAP_MODE_WORDS,
            TextUtils.CAP_MODE_CHARACTERS
        )) {
            val actions = FakeSwitchActions()
            val state = KeyboardState(actions)
            actions.state = state
            state.onLoadKeyboard(flags, RecapitalizeStatus.NOT_A_RECAPITALIZE_MODE)
            assertEquals("automatic", actions.keyboard)
        }
    }

    @Test
    fun doubleTapShiftLocksAndTheNextShiftDisablesIt() {
        val actions = FakeSwitchActions()
        val state = KeyboardState(actions)
        actions.state = state
        state.onLoadKeyboard(0, RecapitalizeStatus.NOT_A_RECAPITALIZE_MODE)
        state.onPressKey(Constants.CODE_SHIFT, true, 0, RecapitalizeStatus.NOT_A_RECAPITALIZE_MODE)
        state.onReleaseKey(Constants.CODE_SHIFT, false, 0, RecapitalizeStatus.NOT_A_RECAPITALIZE_MODE)

        actions.doubleTap = true
        state.onPressKey(Constants.CODE_SHIFT, true, 0, RecapitalizeStatus.NOT_A_RECAPITALIZE_MODE)
        state.onReleaseKey(Constants.CODE_SHIFT, false, 0, RecapitalizeStatus.NOT_A_RECAPITALIZE_MODE)
        assertTrue(actions.keyboard == "locked")

        actions.doubleTap = false
        state.onPressKey(Constants.CODE_SHIFT, true, 0, RecapitalizeStatus.NOT_A_RECAPITALIZE_MODE)
        state.onReleaseKey(Constants.CODE_SHIFT, false, 0, RecapitalizeStatus.NOT_A_RECAPITALIZE_MODE)
        assertEquals("alpha", actions.keyboard)
    }

    private fun letterEvent(code: Int) = Event.createSoftwareKeypressEvent(
        code,
        Event.NOT_A_KEY_CODE,
        Constants.NOT_A_COORDINATE,
        Constants.NOT_A_COORDINATE,
        false
    )

    private class FakeSwitchActions : KeyboardState.SwitchActions {
        var keyboard = ""
        var doubleTap = false
        lateinit var state: KeyboardState

        override fun setAlphabetKeyboard() { keyboard = "alpha" }
        override fun setAlphabetManualShiftedKeyboard() { keyboard = "manual" }
        override fun setAlphabetAutomaticShiftedKeyboard() { keyboard = "automatic" }
        override fun setAlphabetShiftLockedKeyboard() { keyboard = "locked" }
        override fun setAlphabetShiftLockShiftedKeyboard() { keyboard = "locked-shifted" }
        override fun setEmojiKeyboard() = Unit
        override fun setSymbolsKeyboard() { keyboard = "symbols" }
        override fun setSymbolsShiftedKeyboard() { keyboard = "symbols-shifted" }
        override fun requestUpdatingShiftState(autoCapsFlags: Int, recapitalizeMode: Int) {
            state.onUpdateShiftState(autoCapsFlags, recapitalizeMode)
        }
        override fun startDoubleTapShiftKeyTimer() = Unit
        override fun isInDoubleTapShiftKeyTimeout() = doubleTap
        override fun cancelDoubleTapShiftKeyTimer() = Unit
    }
}

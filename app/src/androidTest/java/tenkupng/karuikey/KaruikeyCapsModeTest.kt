package tenkupng.karuikey

import android.text.InputType
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.view.inputmethod.EditorInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KaruikeyCapsModeTest {
    @Test
    fun characterOnlyCallbackDoesNotRestoreAutoShiftForPlainText() {
        assertEquals(
            0,
            KaruikeyCapsMode.normalize(
                TextUtils.CAP_MODE_CHARACTERS,
                InputType.TYPE_CLASS_TEXT
            )
        )
    }

    @Test
    fun explicitCharacterCapRemainsActive() {
        assertEquals(
            TextUtils.CAP_MODE_CHARACTERS,
            KaruikeyCapsMode.normalize(
                TextUtils.CAP_MODE_CHARACTERS,
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            )
        )
    }

    @Test
    fun wordAndSentenceBitsArePreserved() {
        val caps = TextUtils.CAP_MODE_WORDS or TextUtils.CAP_MODE_SENTENCES
        assertEquals(caps, KaruikeyCapsMode.normalize(caps, InputType.TYPE_CLASS_TEXT))
    }

    @Test
    fun editableInputConnectionChangesFromWordStartToCharacterOnly() {
        val scenario = ActivityScenario.launch(TryKaruikeyActivity::class.java)
        scenario.onActivity { activity ->
            val editor = findNormalEditor(activity.window.decorView)
            checkNotNull(editor)
            editor.requestFocus()

            val editorInfo = EditorInfo()
            val connection = checkNotNull(editor.onCreateInputConnection(editorInfo))
            val capsModes = TextUtils.CAP_MODE_CHARACTERS or
                TextUtils.CAP_MODE_WORDS or TextUtils.CAP_MODE_SENTENCES
            val before = connection.getCursorCapsMode(capsModes)
            connection.commitText("a", 1)
            val after = connection.getCursorCapsMode(capsModes)

            assertTrue(KaruikeyCapsMode.normalize(before, editorInfo.inputType) != 0)
            assertEquals(0, KaruikeyCapsMode.normalize(after, editorInfo.inputType))
        }
        scenario.close()
    }

    @Test
    fun editableSentenceInputConnectionRestoresSentenceCapitalization() {
        val scenario = ActivityScenario.launch(TryKaruikeyActivity::class.java)
        scenario.onActivity { activity ->
            val editor = findEditor(activity.window.decorView, "Sentence capitalization")
            checkNotNull(editor)
            editor.requestFocus()
            val editorInfo = EditorInfo()
            val connection = checkNotNull(editor.onCreateInputConnection(editorInfo))
            val capsModes = TextUtils.CAP_MODE_CHARACTERS or
                TextUtils.CAP_MODE_WORDS or TextUtils.CAP_MODE_SENTENCES

            assertTrue(
                editorInfo.inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES != 0
            )
            connection.commitText("a", 1)
            connection.commitText(". ", 1)

            assertTrue(KaruikeyCapsMode.normalize(
                connection.getCursorCapsMode(capsModes), editorInfo.inputType
            ) and TextUtils.CAP_MODE_SENTENCES != 0)
        }
        scenario.close()
    }

    private fun findNormalEditor(view: View): EditText? {
        return findEditor(view, "Normal text")
    }

    private fun findEditor(view: View, hint: String): EditText? {
        if (view is EditText && view.hint == hint) return view
        if (view !is ViewGroup) return null
        for (index in 0 until view.childCount) {
            findEditor(view.getChildAt(index), hint)?.let { return it }
        }
        return null
    }
}

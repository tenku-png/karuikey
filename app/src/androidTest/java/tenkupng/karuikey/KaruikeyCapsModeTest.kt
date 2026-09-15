package tenkupng.karuikey

import android.text.InputType
import android.text.TextUtils
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KaruikeyCapsModeTest {
    @Test
    fun plainTextDoesNotRestoreAutoShiftWithoutEditorInfoCapFlags() {
        assertEquals(
            0,
            KaruikeyCapsMode.normalize(
                TextUtils.CAP_MODE_CHARACTERS or TextUtils.CAP_MODE_SENTENCES,
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
    fun requestedWordAndSentenceModesArePreserved() {
        val caps = TextUtils.CAP_MODE_WORDS or TextUtils.CAP_MODE_SENTENCES
        assertEquals(
            caps,
            KaruikeyCapsMode.normalize(
                caps,
                InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_CAP_WORDS or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            )
        )
    }

    @Test
    fun autoCapitalizationCanBeDisabledWithoutAffectingExplicitCharacterCaps() {
        assertEquals(
            0,
            KaruikeyCapsMode.normalize(
                TextUtils.CAP_MODE_WORDS or TextUtils.CAP_MODE_SENTENCES,
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,
                false
            )
        )
        assertEquals(
            TextUtils.CAP_MODE_CHARACTERS,
            KaruikeyCapsMode.normalize(
                TextUtils.CAP_MODE_CHARACTERS,
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS,
                false
            )
        )
    }
}

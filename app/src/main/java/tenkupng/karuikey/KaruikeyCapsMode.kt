package tenkupng.karuikey

import android.text.InputType
import android.text.TextUtils

/** Adapts the platform caps callback to the cap modes expected by AOSP KeyboardState. */
object KaruikeyCapsMode {
    fun normalize(reportedCapsMode: Int, inputType: Int): Int {
        val wordAndSentenceModes = reportedCapsMode and
            (TextUtils.CAP_MODE_WORDS or TextUtils.CAP_MODE_SENTENCES)
        return if (inputType and InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS != 0) {
            wordAndSentenceModes or TextUtils.CAP_MODE_CHARACTERS
        } else {
            wordAndSentenceModes
        }
    }
}

package tenkupng.karuikey

import android.text.InputType
import android.text.TextUtils

/** Adapts the platform caps callback to the cap modes expected by AOSP KeyboardState. */
object KaruikeyCapsMode {
    fun normalize(
        reportedCapsMode: Int,
        inputType: Int,
        autoCapitalizationEnabled: Boolean = true
    ): Int {
        val requestedFlags = inputType and (
            InputType.TYPE_TEXT_FLAG_CAP_WORDS or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            )
        val reportedModes = reportedCapsMode and (
            TextUtils.CAP_MODE_WORDS or TextUtils.CAP_MODE_SENTENCES or TextUtils.CAP_MODE_CHARACTERS
            )
        if (!autoCapitalizationEnabled &&
            requestedFlags and InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS == 0
        ) return 0
        val allowedModes = if (autoCapitalizationEnabled) {
            requestedFlags
        } else {
            InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        }
        return (reportedModes and allowedModes) or
            if (requestedFlags and InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS != 0) {
                TextUtils.CAP_MODE_CHARACTERS
            } else 0
    }
}

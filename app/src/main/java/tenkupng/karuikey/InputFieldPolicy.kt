package tenkupng.karuikey

import android.text.InputType
import android.view.inputmethod.EditorInfo

/** What the keyboard may do in an editor, decided from its EditorInfo alone. */
internal object InputFieldPolicy {
    fun isPassword(inputType: Int): Boolean {
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return when (inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_TEXT -> variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }

    /** Passwords and fields that refuse suggestions: no clipboard capture, no emoji. */
    fun isSensitive(info: EditorInfo): Boolean =
        (info.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0 || isPassword(info.inputType)

    /**
     * Incognito editors (private browser tabs and the like) ask the keyboard not to learn.
     * Nothing typed there may reach learned words or clipboard history.
     */
    fun isIncognito(info: EditorInfo): Boolean =
        (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0 || isSensitive(info)

    /** Plain text fields that welcome suggestions and gesture typing. */
    fun allowsWordInput(info: EditorInfo): Boolean {
        val inputType = info.inputType
        if ((inputType and InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) return false
        if ((inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0) return false
        return !isPassword(inputType)
    }
}

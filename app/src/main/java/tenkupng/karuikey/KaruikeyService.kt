package tenkupng.karuikey

import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.content.Intent
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.android.inputmethod.accessibility.AccessibilityUtils
import com.android.inputmethod.compat.InputMethodSubtypeCompatUtils
import com.android.inputmethod.event.Event
import com.android.inputmethod.keyboard.KeyboardActionListener
import com.android.inputmethod.keyboard.KeyboardLayoutSet
import com.android.inputmethod.keyboard.KeyboardSwitcher
import com.android.inputmethod.keyboard.MainKeyboardView
import com.android.inputmethod.latin.common.Constants
import com.android.inputmethod.latin.common.InputPointers
import com.android.inputmethod.latin.common.StringUtils
import com.android.inputmethod.latin.utils.RecapitalizeStatus
import com.android.inputmethod.latin.utils.SubtypeLocaleUtils

class KaruikeyService : InputMethodService() {
    private var inputView: KaruikeyInputView? = null
    private var keyboardSwitcher: KeyboardSwitcher? = null
    private var editorInfo: EditorInfo? = null
    private var currentSubtype: InputMethodSubtype? = null
    private var currentLanguage: KaruikeyLanguage? = null
    private var loadedWidth = 0
    private var loadedHeight = 0
    private var preferencesListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private val composingWord = StringBuilder(20)
    private val suggestionResults = ArrayList<String>(3)
    private var suggestionsAllowed = false
    private var gestureAllowed = false
    private var expectedCursorPosition = -1
    // Session-only context. It is never persisted or logged and is cleared with the editor.
    private var previousWord: String? = null

    override fun onCreate() {
        AccessibilityUtils.init(this)
        SubtypeLocaleUtils.init(this)
        super.onCreate()
        val preferences = getSharedPreferences("karuikey_settings", MODE_PRIVATE)
        preferencesListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            inputView?.post { refreshInputViewForPreferences() }
        }
        preferences.registerOnSharedPreferenceChangeListener(preferencesListener)
    }

    private val keyboardActionListener = object : KeyboardActionListener.Adapter() {
        override fun onPressKey(primaryCode: Int, repeatCount: Int, isSinglePointer: Boolean) {
            keyboardSwitcher?.onPressKey(primaryCode, isSinglePointer, autoCapsMode())
        }

        override fun onReleaseKey(primaryCode: Int, withSliding: Boolean) {
            keyboardSwitcher?.onReleaseKey(primaryCode, withSliding, autoCapsMode())
            if (!Constants.isLetterCode(primaryCode) && primaryCode != Constants.CODE_SHIFT &&
                primaryCode != Constants.CODE_SWITCH_ALPHA_SYMBOL
            ) {
                // AOSP updates shift while processing the key event. The service must perform the
                // matching post-release update so one-shot shift does not become sticky.
                keyboardSwitcher?.requestUpdatingShiftState(
                    autoCapsMode(),
                    RecapitalizeStatus.NOT_A_RECAPITALIZE_MODE
                )
            }
        }

        override fun onCodeInput(
            primaryCode: Int,
            x: Int,
            y: Int,
            isKeyRepeat: Boolean
        ) {
            if (primaryCode == Constants.CODE_LANGUAGE_SWITCH) {
                cycleLanguage()
                return
            }
            val connection = currentInputConnection
            when (primaryCode) {
                Constants.CODE_DELETE -> {
                    connection?.deleteSurroundingText(1, 0)
                    deleteLastCodePoint()
                }
                Constants.CODE_SPACE -> {
                    connection?.commitText(" ", 1)
                    if (expectedCursorPosition >= 0) expectedCursorPosition++
                    rememberCompletedWord()
                    clearCurrentWord()
                }
                Constants.CODE_ENTER,
                Constants.CODE_SHIFT_ENTER -> if (connection != null) {
                    sendEnter(connection)
                    clearComposingWord()
                }
                Constants.CODE_SHIFT,
                Constants.CODE_CAPSLOCK -> Unit
                Constants.CODE_SWITCH_ALPHA_SYMBOL -> clearCurrentWord()
                else -> if (primaryCode > 0) {
                    val text = StringUtils.newSingleCodePointString(primaryCode)
                    connection?.commitText(text, 1)
                    if (Constants.isLetterCode(primaryCode)) {
                        if (suggestionsAllowed) composingWord.append(text)
                    } else {
                        rememberCompletedWord()
                        clearCurrentWord()
                    }
                    if (expectedCursorPosition >= 0) expectedCursorPosition += text.length
                }
            }
            keyboardSwitcher?.onEvent(
                createKeyEvent(primaryCode, x, y, isKeyRepeat),
                autoCapsMode()
            )
            refreshSuggestions()
        }

        override fun onTextInput(text: String) {
            currentInputConnection?.commitText(text, 1)
            clearComposingWord()
            keyboardSwitcher?.onEvent(
                Event.createSoftwareTextEvent(text, Constants.CODE_OUTPUT_TEXT),
                autoCapsMode()
            )
        }

        override fun onEndBatchInput(batchPointers: InputPointers) {
            if (!gestureAllowed) return
            val locale = currentLanguage?.locale ?: return
            val candidate = SuggestionEngine.findGestureCandidate(
                locale, keyboardSwitcher?.getKeyboard(), batchPointers, previousWord
            ) ?: return
            currentInputConnection?.commitText(candidate, 1)
            if (expectedCursorPosition >= 0) expectedCursorPosition += candidate.length
            rememberCompletedWord(candidate)
            clearCurrentWord()
            keyboardSwitcher?.requestUpdatingShiftState(
                autoCapsMode(), RecapitalizeStatus.NOT_A_RECAPITALIZE_MODE
            )
        }

        override fun onFinishSlidingInput() {
            keyboardSwitcher?.onFinishSlidingInput(autoCapsMode())
        }

        override fun onCustomRequest(requestCode: Int): Boolean {
            if (requestCode != Constants.CUSTOM_CODE_SHOW_INPUT_METHOD_PICKER) return false
            getSystemService(InputMethodManager::class.java).showInputMethodPicker()
            return true
        }
    }

    override fun onCreateInputView(): View {
        return createInputView()
    }

    private fun createInputView(): KaruikeyInputView {
        // KeyboardLayoutSet caches icon-bearing Keyboard instances statically; a new themed
        // context must not reuse drawables created for the previous keyboard appearance.
        KeyboardLayoutSet.onKeyboardThemeChanged()
        val view = KaruikeyInputView()
        inputView = view
        keyboardSwitcher = view.keyboardSwitcher
        return view
    }

    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        inputView?.hideClipboardPanel()
        editorInfo = attribute
        currentLanguage = KaruikeyPreferences.activeLanguage(this)
        currentSubtype = subtypeFor(currentLanguage!!)
        loadedWidth = 0
        loadedHeight = 0
        suggestionsAllowed = suggestionsEnabledFor(attribute)
        gestureAllowed = gestureEnabledFor(attribute, currentLanguage!!.locale)
        expectedCursorPosition = attribute.initialSelStart
        clearComposingWord()
        keyboardSwitcher?.resetForNewInput()
    }

    override fun onStartInputView(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInputView(attribute, restarting)
        editorInfo = attribute
        ensureInputViewForPreferences()
        if (currentLanguage == null) currentLanguage = KaruikeyPreferences.activeLanguage(this)
        if (currentSubtype == null) currentSubtype = subtypeFor(currentLanguage!!)
        inputView?.applyPreferences()
        configureImeWindow()
        loadKeyboardIfMeasured()
        refreshSuggestions()
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(
            oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd
        )
        if (editorInfo != null) {
            keyboardSwitcher?.requestUpdatingShiftState(
                autoCapsMode(), RecapitalizeStatus.NOT_A_RECAPITALIZE_MODE
            )
        }
        if (suggestionsAllowed) {
            if (newSelStart != newSelEnd ||
                (expectedCursorPosition >= 0 && newSelStart != expectedCursorPosition)
            ) {
                clearComposingWord()
            }
            expectedCursorPosition = newSelStart
            refreshSuggestions()
        }
    }

    override fun onCurrentInputMethodSubtypeChanged(newSubtype: InputMethodSubtype) {
        super.onCurrentInputMethodSubtypeChanged(newSubtype)
        val language = languageForSubtype(newSubtype)
        if (language != null && KaruikeyPreferences.enabledLanguages(this).any { it.id == language.id }) {
            currentLanguage = language
            KaruikeyPreferences.setActiveLanguage(this, language)
            currentSubtype = subtypeFor(language)
        }
        gestureAllowed = editorInfo?.let {
            gestureEnabledFor(it, currentLanguage?.locale ?: "")
        } ?: false
        if (editorInfo != null) {
            keyboardSwitcher?.resetForNewInput()
            loadedWidth = 0
            loadedHeight = 0
            clearComposingWord()
            loadKeyboardIfMeasured()
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        inputView?.hideClipboardPanel()
        super.onFinishInputView(finishingInput)
        keyboardSwitcher?.closing()
        clearComposingWord()
    }

    override fun onFinishInput() {
        inputView?.hideClipboardPanel()
        super.onFinishInput()
        keyboardSwitcher?.closing()
        keyboardSwitcher?.resetForNewInput()
        editorInfo = null
        currentSubtype = null
        currentLanguage = null
        loadedWidth = 0
        loadedHeight = 0
        suggestionsAllowed = false
        gestureAllowed = false
        expectedCursorPosition = -1
        clearComposingWord()
    }

    override fun onWindowHidden() {
        inputView?.hideClipboardPanel()
        super.onWindowHidden()
        keyboardSwitcher?.onHideWindow()
        keyboardSwitcher?.closing()
        clearComposingWord()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        inputView?.hideClipboardPanel()
        keyboardSwitcher?.closing()
        loadedWidth = 0
        loadedHeight = 0
        super.onConfigurationChanged(newConfig)
        loadKeyboardIfMeasured()
    }

    override fun onDestroy() {
        inputView?.hideClipboardPanel()
        keyboardSwitcher?.closing()
        keyboardSwitcher?.deallocateMemory()
        inputView = null
        keyboardSwitcher = null
        preferencesListener?.let {
            getSharedPreferences("karuikey_settings", MODE_PRIVATE)
                .unregisterOnSharedPreferenceChangeListener(it)
        }
        preferencesListener = null
        editorInfo = null
        super.onDestroy()
    }

    private fun ensureInputViewForPreferences() {
        val view = inputView ?: return
        if (view.appearance == KaruikeyPreferences.resolveKeyboardAppearance(this)) return
        val replacement = createInputView()
        setInputView(replacement)
    }

    private fun refreshInputViewForPreferences() {
        val view = inputView ?: return
        if (view.appearance != KaruikeyPreferences.resolveKeyboardAppearance(this)) {
            if (isInputViewShown) {
                val replacement = createInputView()
                setInputView(replacement)
                configureImeWindow()
            }
            return
        }
        view.applyPreferences()
        configureImeWindow()
        loadedWidth = 0
        loadedHeight = 0
        view.post {
            loadedWidth = 0
            loadedHeight = 0
            loadKeyboardIfMeasured()
        }
    }

    private fun loadKeyboardIfMeasured() {
        val view = inputView ?: return
        val keyboardView = view.keyboardView
        if (keyboardView.width <= 0 || keyboardView.height <= 0) {
            view.post { loadKeyboardIfMeasured() }
            return
        }
        if (keyboardView.width == loadedWidth && keyboardView.height == loadedHeight) return
        val editor = editorInfo ?: return
        val language = currentLanguage ?: KaruikeyPreferences.activeLanguage(this)
        val subtype = currentSubtype ?: subtypeFor(language)
        val multipleLanguages = KaruikeyPreferences.enabledLanguages(this).size > 1
        keyboardSwitcher?.loadKeyboard(
            editor,
            subtype,
            keyboardView.width,
            keyboardView.height,
            autoCapsMode(),
            multipleLanguages
        )
        keyboardView.setMainDictionaryAvailability(gestureAllowed)
        // Keep the optional AOSP trail off until its visual parameters are configured for this
        // standalone surface; decoding does not depend on drawing it.
        keyboardView.setGestureHandlingEnabledByUser(gestureAllowed, false, false)
        loadedWidth = keyboardView.width
        loadedHeight = keyboardView.height
    }

    private fun cycleLanguage() {
        val enabled = KaruikeyPreferences.enabledLanguages(this)
        if (enabled.size < 2) return
        val currentId = currentLanguage?.id ?: KaruikeyPreferences.activeLanguage(this).id
        val currentIndex = enabled.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
        val next = enabled[(currentIndex + 1) % enabled.size]
        currentLanguage = next
        currentSubtype = subtypeFor(next)
        gestureAllowed = editorInfo?.let { gestureEnabledFor(it, next.locale) } ?: false
        KaruikeyPreferences.setActiveLanguage(this, next)
        clearComposingWord()
        keyboardSwitcher?.resetForNewInput()
        loadedWidth = 0
        loadedHeight = 0
        loadKeyboardIfMeasured()
    }

    private fun languageForSubtype(subtype: InputMethodSubtype): KaruikeyLanguage? {
        val locale = subtype.locale.replace('-', '_')
        return KaruikeyPreferences.languages(this).firstOrNull {
            locale.equals(it.locale, ignoreCase = true) || locale.startsWith(
                it.locale.substringBefore('_'),
                ignoreCase = true
            )
        }
    }

    private fun subtypeFor(language: KaruikeyLanguage): InputMethodSubtype =
        InputMethodSubtypeCompatUtils.newInputMethodSubtype(
            0,
            0,
            language.locale,
            Constants.Subtype.KEYBOARD_MODE,
            "KeyboardLayoutSet=${language.layoutSet},AsciiCapable",
            false,
            false,
            language.id.hashCode()
        )

    private fun autoCapsMode(): Int {
        val capsModes = TextUtils.CAP_MODE_CHARACTERS or
            TextUtils.CAP_MODE_WORDS or TextUtils.CAP_MODE_SENTENCES
        val info = editorInfo ?: return 0
        val reported = currentInputConnection?.getCursorCapsMode(capsModes) ?: 0
        return KaruikeyCapsMode.normalize(reported, info.inputType)
    }

    private fun suggestionsEnabledFor(info: EditorInfo): Boolean {
        if (!KaruikeyPreferences.suggestionsEnabled(this)) return false
        val inputType = info.inputType
        if ((inputType and InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) return false
        if ((inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0) return false
        return when (inputType and InputType.TYPE_MASK_VARIATION) {
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD -> false
            else -> true
        }
    }

    private fun gestureEnabledFor(info: EditorInfo, locale: String): Boolean {
        if (!SuggestionEngine.hasDictionary(locale)) return false
        val inputType = info.inputType
        if ((inputType and InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) return false
        if ((inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0) return false
        return when (inputType and InputType.TYPE_MASK_VARIATION) {
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD -> false
            else -> true
        }
    }

    private fun deleteLastCodePoint() {
        if (composingWord.isEmpty()) return
        val last = Character.codePointBefore(composingWord, composingWord.length)
        val charCount = Character.charCount(last)
        composingWord.setLength(composingWord.length - charCount)
        if (expectedCursorPosition >= charCount) expectedCursorPosition -= charCount
    }

    private fun clearComposingWord() {
        previousWord = null
        clearCurrentWord()
    }

    private fun clearCurrentWord() {
        composingWord.setLength(0)
        suggestionResults.clear()
        inputView?.setSuggestions(suggestionResults)
    }

    private fun rememberCompletedWord(word: String = composingWord.toString()) {
        if (word.isBlank()) return
        previousWord = word
    }

    private fun refreshSuggestions() {
        if (!suggestionsAllowed) {
            clearCurrentWord()
            return
        }
        SuggestionEngine.fill(
            currentLanguage?.locale ?: "en",
            previousWord,
            composingWord,
            suggestionResults
        )
        inputView?.setSuggestions(suggestionResults)
    }

    private fun commitSuggestion(candidate: String) {
        val connection = currentInputConnection ?: return
        if (composingWord.isEmpty()) return
        val replacement = if (composingWord[0].isUpperCase() && candidate.isNotEmpty()) {
            candidate[0].uppercaseChar().toString() + candidate.substring(1)
        } else {
            candidate
        }
        connection.deleteSurroundingText(composingWord.length, 0)
        connection.commitText(replacement, 1)
        expectedCursorPosition += replacement.length - composingWord.length
        composingWord.setLength(0)
        composingWord.append(replacement)
        suggestionResults.clear()
        inputView?.setSuggestions(suggestionResults)
    }

    private fun sendEnter(connection: InputConnection) {
        val info = editorInfo
        val options = info?.imeOptions ?: EditorInfo.IME_ACTION_NONE
        val action = options and EditorInfo.IME_MASK_ACTION
        val noEnterAction = options and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
        val multiline = info != null &&
            (info.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
        if (!multiline && !noEnterAction && action != EditorInfo.IME_ACTION_NONE &&
            action != EditorInfo.IME_ACTION_UNSPECIFIED
        ) {
            connection.performEditorAction(action)
        } else {
            connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
    }

    private fun createKeyEvent(code: Int, x: Int, y: Int, isKeyRepeat: Boolean): Event {
        val keyCode = if (code <= 0) code else Event.NOT_A_KEY_CODE
        val codePoint = if (code <= 0) Event.NOT_A_CODE_POINT else code
        return Event.createSoftwareKeypressEvent(codePoint, keyCode, x, y, isKeyRepeat)
    }

    private fun configureImeWindow() {
        val imeWindow = window?.window ?: return
        val view = inputView ?: return
        val surface = view.appearance.keyboardBackground
        val transparent = KaruikeyPreferences.transparencyEnabled(this)
        imeWindow.setBackgroundDrawable(
            ColorDrawable(if (transparent) Color.TRANSPARENT else surface)
        )
        view.alpha = KaruikeyPreferences.keyboardSurfaceAlpha(this)
        // The IME window is full-width. A window-level blur would include application content
        // outside the keyboard, so blur remains disabled until it can be bounded safely.
        val insetsController = androidx.core.view.WindowCompat.getInsetsController(
            imeWindow,
            view
        )
        insetsController.isAppearanceLightNavigationBars = Color.luminance(surface) > 0.5f
        if (android.os.Build.VERSION.SDK_INT >= 35) {
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(imeWindow, false)
            imeWindow.navigationBarColor = Color.TRANSPARENT
        } else {
            imeWindow.navigationBarColor = surface
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(imeWindow, true)
        }
    }

    private fun openSettings() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun showClipboard() {
        val view = inputView ?: return
        val context = view.keyboardContext
        val clipboard = getSystemService(ClipboardManager::class.java)
        val clip = clipboard.primaryClip
        val item = clip?.takeIf { it.description.hasMimeType("text/*") }
            ?.getItemAt(0)
        val text = item?.coerceToText(context)?.toString()
        val message = when {
            clip == null -> R.string.clipboard_empty
            item != null && text.isNullOrEmpty() -> R.string.clipboard_empty
            else -> R.string.clipboard_non_text
        }
        view.showClipboard(
            text,
            message
        )
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && inputView?.hideClipboardPanel() == true) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private inner class KaruikeyInputView : FrameLayout(this@KaruikeyService) {
        val appearance: KeyboardAppearance =
            KaruikeyPreferences.resolveKeyboardAppearance(this@KaruikeyService)
        val keyboardContext: Context = KaruikeyPreferences.keyboardContext(this@KaruikeyService)
        val keyboardView = MainKeyboardView(keyboardContext, null).also {
            it.setKeyboardActionListener(keyboardActionListener)
        }
        val keyboardSwitcher = KeyboardSwitcher(keyboardContext, keyboardView)
        private val toolbar = FrameLayout(keyboardContext)
        private val keyboardContent = FrameLayout(keyboardContext)
        private val utilityToolbar = LinearLayout(keyboardContext)
        private val suggestionToolbar = LinearLayout(keyboardContext)
        private var clipboardText: String? = null
        private val clipboardPanel = LinearLayout(keyboardContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(12))
            setBackgroundColor(appearance.keyboardBackground)
            visibility = View.GONE
        }
        private val clipboardPreview = TextView(keyboardContext).apply {
            textSize = 16f
            maxLines = 3
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(appearance.primaryText)
        }
        private val clipboardPaste = TextView(keyboardContext).apply {
            text = getString(R.string.clipboard_paste)
            textSize = 14f
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            contentDescription = getString(R.string.clipboard_paste)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setTextColor(appearance.primaryText)
            setBackgroundResource(R.drawable.keyboard_toolbar_button_background)
            visibility = View.GONE
            setOnClickListener {
                val text = clipboardText
                if (!text.isNullOrEmpty()) {
                    currentInputConnection?.commitText(text, 1)
                    clearComposingWord()
                }
                hideClipboardPanel()
            }
        }
        private val candidateViews = Array(3) {
            TextView(keyboardContext).apply {
                gravity = Gravity.CENTER
                textSize = 14f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                isClickable = true
                isFocusable = true
                setBackgroundResource(R.drawable.keyboard_toolbar_button_background)
                setTextColor(appearance.primaryText)
                layoutParams = LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
                setOnClickListener { (tag as? String)?.let(::commitSuggestion) }
            }
        }
        private var navigationBottomInset = 0
        private val toolbarHeight = resources.getDimensionPixelSize(R.dimen.keyboard_toolbar_height)

        init {
            setBackgroundColor(appearance.keyboardBackground)
            toolbar.setBackgroundColor(appearance.keyboardBackground)
            utilityToolbar.orientation = LinearLayout.HORIZONTAL
            utilityToolbar.gravity = Gravity.CENTER_VERTICAL
            utilityToolbar.addView(
                toolbarButton(
                    R.drawable.ic_keyboard_clipboard,
                    R.string.toolbar_clipboard
                ) { showClipboard() },
                fixedToolbarButtonParams()
            )
            utilityToolbar.addView(
                toolbarButton(
                    R.drawable.ic_keyboard_settings,
                    R.string.toolbar_settings
                ) { openSettings() },
                fixedToolbarButtonParams()
            )
            suggestionToolbar.orientation = LinearLayout.HORIZONTAL
            suggestionToolbar.gravity = Gravity.CENTER_VERTICAL
            candidateViews.forEach { suggestionToolbar.addView(it) }
            suggestionToolbar.addView(
                toolbarButton(
                    R.drawable.ic_keyboard_clipboard,
                    R.string.toolbar_clipboard
                ) { showClipboard() },
                fixedToolbarButtonParams()
            )
            suggestionToolbar.addView(
                toolbarButton(
                    R.drawable.ic_keyboard_settings,
                    R.string.toolbar_settings
                ) { openSettings() },
                fixedToolbarButtonParams()
            )
            toolbar.addView(suggestionToolbar, FrameLayout.LayoutParams.MATCH_PARENT, toolbarHeight)
            toolbar.addView(utilityToolbar, FrameLayout.LayoutParams.MATCH_PARENT, toolbarHeight)
            utilityToolbar.visibility = View.VISIBLE
            suggestionToolbar.visibility = View.GONE
            val clipboardHeader = LinearLayout(keyboardContext).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            clipboardHeader.addView(TextView(keyboardContext).apply {
                text = "‹"
                textSize = 30f
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                contentDescription = getString(R.string.clipboard_back)
                setTextColor(appearance.primaryText)
                setBackgroundResource(R.drawable.keyboard_toolbar_button_background)
                setOnClickListener { hideClipboardPanel() }
            }, LinearLayout.LayoutParams(toolbarHeight, toolbarHeight))
            clipboardHeader.addView(TextView(keyboardContext).apply {
                text = getString(R.string.toolbar_clipboard)
                textSize = 16f
                gravity = Gravity.CENTER_VERTICAL
                setTextColor(appearance.primaryText)
                setPadding(dp(12), 0, 0, 0)
            }, LinearLayout.LayoutParams(0, toolbarHeight, 1f))
            clipboardPanel.addView(clipboardHeader)
            clipboardPanel.addView(clipboardPreview, LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, 0, 1f
            ))
            clipboardPanel.addView(clipboardPaste, LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT
            ))
            addView(toolbar, LayoutParams.MATCH_PARENT, toolbarHeight)
            keyboardContent.addView(keyboardView, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            keyboardContent.addView(clipboardPanel, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            addView(keyboardContent, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
                val bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                if (navigationBottomInset != bottom) {
                    navigationBottomInset = bottom
                    requestLayout()
                }
                insets
            }
        }

        fun applyPreferences() {
            toolbar.visibility = if (KaruikeyPreferences.toolbarEnabled(this@KaruikeyService)) View.VISIBLE else View.GONE
            alpha = KaruikeyPreferences.keyboardSurfaceAlpha(this@KaruikeyService)
            keyboardView.setKeyPreviewPopupEnabled(
                KaruikeyPreferences.keyPreviewEnabled(this@KaruikeyService),
                500
            )
            requestLayout()
        }

        fun showClipboard(text: String?, emptyMessage: Int) {
            clipboardText = text?.takeIf { it.isNotEmpty() }
            clipboardPreview.text = clipboardText ?: getString(emptyMessage)
            clipboardPaste.visibility = if (clipboardText == null) View.GONE else View.VISIBLE
            keyboardView.visibility = View.GONE
            clipboardPanel.visibility = View.VISIBLE
            requestLayout()
        }

        fun hideClipboardPanel(): Boolean {
            if (clipboardPanel.visibility != View.VISIBLE) return false
            clipboardText = null
            clipboardPreview.text = null
            clipboardPaste.visibility = View.GONE
            clipboardPanel.visibility = View.GONE
            keyboardView.visibility = View.VISIBLE
            requestLayout()
            return true
        }

        fun setSuggestions(suggestions: List<String>) {
            val hasSuggestions = suggestions.isNotEmpty()
            candidateViews.forEachIndexed { index, candidate ->
                if (index < suggestions.size) {
                    val text = suggestions[index]
                    candidate.text = text
                    candidate.tag = text
                    candidate.visibility = View.VISIBLE
                } else {
                    candidate.text = null
                    candidate.tag = null
                    // Keep empty slots reserved so one suggestion never becomes a giant button.
                    candidate.visibility = View.INVISIBLE
                }
            }
            suggestionToolbar.visibility = if (hasSuggestions) View.VISIBLE else View.GONE
            utilityToolbar.visibility = if (hasSuggestions) View.GONE else View.VISIBLE
        }

        private fun toolbarButton(icon: Int, description: Int, action: () -> Unit) =
            ImageButton(keyboardContext).apply {
                contentDescription = getString(description)
                setImageResource(icon)
                imageTintList = ColorStateList.valueOf(appearance.primaryText)
                isClickable = true
                isFocusable = true
                setBackgroundResource(R.drawable.keyboard_toolbar_button_background)
                setPadding(dp(12), 0, dp(12), 0)
                setOnClickListener { action() }
                layoutParams = LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
            }

        private fun fixedToolbarButtonParams() = LinearLayout.LayoutParams(toolbarHeight, toolbarHeight)

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val defaultKeyboardHeight = resources.getDimensionPixelSize(
                R.dimen.config_default_keyboard_height
            ) * KaruikeyPreferences.heightPercent(this@KaruikeyService) / 100
            val visibleToolbarHeight = if (toolbar.visibility == View.VISIBLE) toolbarHeight else 0
            val desiredHeight = defaultKeyboardHeight + visibleToolbarHeight + navigationBottomInset
            val height = when (MeasureSpec.getMode(heightMeasureSpec)) {
                MeasureSpec.EXACTLY -> MeasureSpec.getSize(heightMeasureSpec)
                MeasureSpec.AT_MOST -> desiredHeight.coerceAtMost(MeasureSpec.getSize(heightMeasureSpec))
                else -> desiredHeight
            }
            val keyboardHeight = (height - visibleToolbarHeight - navigationBottomInset).coerceAtLeast(1)
            setMeasuredDimension(width, height)
            toolbar.measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(visibleToolbarHeight, MeasureSpec.EXACTLY)
            )
            keyboardContent.measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(keyboardHeight, MeasureSpec.EXACTLY)
            )
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            val visibleToolbarHeight = if (toolbar.visibility == View.VISIBLE) toolbarHeight else 0
            toolbar.layout(0, 0, width, visibleToolbarHeight)
            keyboardContent.layout(0, visibleToolbarHeight, width, height - navigationBottomInset)
        }

        override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
            super.onSizeChanged(width, height, oldWidth, oldHeight)
            loadKeyboardIfMeasured()
        }
    }
}

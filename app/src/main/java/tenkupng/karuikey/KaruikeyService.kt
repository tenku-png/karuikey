package tenkupng.karuikey

import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.content.Intent
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.Typeface
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
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import java.util.HashSet
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.android.inputmethod.accessibility.AccessibilityUtils
import com.android.inputmethod.compat.InputMethodSubtypeCompatUtils
import com.android.inputmethod.event.Event
import com.android.inputmethod.keyboard.KeyboardActionListener
import com.android.inputmethod.keyboard.Key
import com.android.inputmethod.keyboard.KeyboardLayoutSet
import com.android.inputmethod.keyboard.KeyboardSwitcher
import com.android.inputmethod.keyboard.MainKeyboardView
import com.android.inputmethod.latin.common.Constants
import com.android.inputmethod.latin.common.InputPointers
import com.android.inputmethod.latin.common.StringUtils
import com.android.inputmethod.latin.utils.RecapitalizeStatus
import com.android.inputmethod.latin.utils.SubtypeLocaleUtils

class KaruikeyService : InputMethodService() {
    private val keyboardTypeface: Typeface by lazy { KaruikeyTypeface.create(this, 400) }
    private val suggestionTypeface: Typeface by lazy { KaruikeyTypeface.create(this, 475) }
    private var inputView: KaruikeyInputView? = null
    private var keyboardSwitcher: KeyboardSwitcher? = null
    private var editorInfo: EditorInfo? = null
    private var currentSubtype: InputMethodSubtype? = null
    private var currentLanguage: KaruikeyLanguage? = null
    private var loadedWidth = 0
    private var loadedHeight = 0
    private var preferencesListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private val suggestionSession = SuggestionSession()
    private val suggestionResults = ArrayList<String>(3)
    private var suggestionRequestId = 0L
    private var gestureRequestId = 0L
    private var suggestionsAllowed = false
    private var gestureAllowed = false
    private var emojiBottomRow = false
    private var expectedCursorPosition = -1
    private var expectedSelectionEnd = -1
    private var composingStart = -1
    private var editorUpdateDepth = 0
    private var pendingEditorSelection = -1
    private var clipboardListenerRegistered = false
    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        capturePrimaryClipboard()
    }

    override fun onCreate() {
        AccessibilityUtils.init(this)
        SubtypeLocaleUtils.init(this)
        super.onCreate()
        SuggestionEngine.initialize(this)
        val preferences = getSharedPreferences("karuikey_settings", MODE_PRIVATE)
        preferencesListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            inputView?.post { refreshInputViewForPreferences() }
        }
        preferences.registerOnSharedPreferenceChangeListener(preferencesListener)
        getSharedPreferences("karuikey_clipboard_history", MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(preferencesListener)
        ClipboardHistory.purge(this)
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
            if (inputView?.isEmojiSearchActive == true) {
                handleEmojiSearchCode(primaryCode)
                return
            }
            gestureRequestId++
            pendingEditorSelection = -1
            if (primaryCode == Constants.CODE_EMOJI) {
                openEmojiPanel()
                return
            }
            if (primaryCode == Constants.CODE_LANGUAGE_SWITCH) {
                if (emojiBottomRow) {
                    openEmojiPanel()
                    return
                }
                cycleLanguage()
                return
            }
            val connection = currentInputConnection
            when (primaryCode) {
                Constants.CODE_DELETE -> {
                    handleBackspace(connection)
                }
                Constants.CODE_SPACE -> {
                    commitSeparator(connection, " ")
                }
                Constants.CODE_ENTER,
                Constants.CODE_SHIFT_ENTER -> if (connection != null) {
                    finishEditorComposition(connection)
                    sendEnter(connection)
                    clearComposingWord()
                }
                Constants.CODE_SHIFT,
                Constants.CODE_CAPSLOCK -> Unit
                Constants.CODE_SWITCH_ALPHA_SYMBOL -> {
                    finishEditorComposition(connection)
                    clearCurrentWord()
                    suggestionSession.clearAutomaticSpace()
                }
                else -> if (primaryCode > 0) {
                    val text = StringUtils.newSingleCodePointString(primaryCode)
                    if (Character.isLetter(primaryCode) && suggestionsAllowed) {
                        appendToComposition(connection, text)
                    } else if (suggestionSession.hasAutomaticSpace &&
                        isAutoSpacePunctuation(primaryCode)
                    ) {
                        commitPunctuationAfterAutomaticSpace(connection, text)
                    } else {
                        suggestionSession.clearAutomaticSpace()
                        finishEditorComposition(connection)
                        if (expectedCursorPosition >= 0) {
                            expectedCursorPosition += text.length
                            expectedSelectionEnd = expectedCursorPosition
                        }
                        duringEditorUpdate { connection?.commitText(text, 1) }
                        if (Character.isLetter(primaryCode)) {
                            clearCurrentWord()
                        } else {
                            completeCurrentWord()
                        }
                    }
                }
            }
            keyboardSwitcher?.onEvent(
                createKeyEvent(primaryCode, x, y, isKeyRepeat),
                autoCapsMode()
            )
            refreshSuggestions()
        }

        override fun onTextInput(text: String) {
            if (inputView?.isEmojiSearchActive == true) {
                inputView?.appendEmojiSearchText(text)
                return
            }
            val connection = currentInputConnection
            suggestionSession.clearAutomaticSpace()
            finishEditorComposition(connection)
            duringEditorUpdate { connection?.commitText(text, 1) }
            clearComposingWord()
            keyboardSwitcher?.onEvent(
                Event.createSoftwareTextEvent(text, Constants.CODE_OUTPUT_TEXT),
                autoCapsMode()
            )
        }

        override fun onEndBatchInput(batchPointers: InputPointers) {
            if (!gestureAllowed) return
            val locale = currentLanguage?.locale ?: return
            val requestId = ++gestureRequestId
            val keyboard = keyboardSwitcher?.getKeyboard()
            val previousWord = suggestionSession.previousWord
            SuggestionEngine.requestGestureCandidate(
                locale, keyboard, batchPointers, previousWord
            ) { candidate ->
                inputView?.post {
                    if (candidate.isNullOrEmpty() || requestId != gestureRequestId ||
                        editorInfo == null || currentLanguage?.locale != locale
                    ) return@post
                    finishEditorComposition(currentInputConnection)
                    suggestionSession.clearAutomaticSpace()
                    duringEditorUpdate { currentInputConnection?.commitText(candidate, 1) }
                    if (expectedCursorPosition >= 0) {
                        expectedCursorPosition += candidate.length
                        expectedSelectionEnd = expectedCursorPosition
                    }
                    completeWord(candidate)
                    keyboardSwitcher?.requestUpdatingShiftState(
                        autoCapsMode(), RecapitalizeStatus.NOT_A_RECAPITALIZE_MODE
                    )
                }
            }
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
        gestureRequestId++
        inputView?.resetSpaceCursor()
        inputView?.hideClipboardPanel()
        inputView?.hideEmojiPanel()
        stopClipboardMonitoring()
        editorInfo = attribute
        currentLanguage = KaruikeyPreferences.activeLanguage(this)
        currentSubtype = subtypeFor(currentLanguage!!)
        beginSuggestionSession(currentLanguage!!.locale)
        loadedWidth = 0
        loadedHeight = 0
        suggestionsAllowed = suggestionsEnabledFor(attribute)
        gestureAllowed = gestureEnabledFor(attribute, currentLanguage!!.locale)
        expectedCursorPosition = attribute.initialSelStart
        expectedSelectionEnd = attribute.initialSelEnd
        clearComposingWord()
        composingStart = -1
        emojiBottomRow = false
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
        updateClipboardMonitoring()
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
        if (!suggestionsAllowed && editorUpdateDepth == 0) {
            if (newSelStart != expectedCursorPosition || newSelEnd != expectedSelectionEnd) {
                suggestionSession.clearAutomaticSpace()
            }
            expectedCursorPosition = newSelStart
            expectedSelectionEnd = newSelEnd
        }
        if (suggestionsAllowed && editorUpdateDepth == 0) {
            if (pendingEditorSelection >= 0) {
                if (newSelStart == pendingEditorSelection && newSelEnd == pendingEditorSelection) {
                    return
                }
                pendingEditorSelection = -1
            }
            if (newSelStart != expectedCursorPosition || newSelEnd != expectedSelectionEnd) {
                suggestionSession.clearAutomaticSpace()
            }
            val compositionStillActive = composingStart >= 0 &&
                newSelStart == newSelEnd &&
                newSelStart == expectedCursorPosition &&
                (candidatesStart < 0 || candidatesStart == composingStart) &&
                (candidatesEnd < 0 || candidatesEnd == expectedCursorPosition)
            if (!compositionStillActive &&
                (newSelStart != newSelEnd ||
                    (expectedCursorPosition >= 0 && newSelStart != expectedCursorPosition) ||
                    composingStart >= 0)
            ) {
                clearComposingWord()
            }
            expectedCursorPosition = newSelStart
            expectedSelectionEnd = newSelEnd
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
            beginSuggestionSession(language.locale)
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
        inputView?.resetSpaceCursor()
        inputView?.hideClipboardPanel()
        inputView?.hideEmojiPanel()
        stopClipboardMonitoring()
        keyboardSwitcher?.closing()
        suggestionSession.clearAutomaticSpace()
        if (finishingInput) clearComposingWord()
        super.onFinishInputView(finishingInput)
    }

    override fun onFinishInput() {
        gestureRequestId++
        inputView?.resetSpaceCursor()
        inputView?.hideClipboardPanel()
        inputView?.hideEmojiPanel()
        stopClipboardMonitoring()
        clearComposingWord()
        SuggestionEngine.endSession()
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
        expectedSelectionEnd = -1
    }

    override fun onWindowHidden() {
        gestureRequestId++
        inputView?.resetSpaceCursor()
        inputView?.hideClipboardPanel()
        inputView?.hideEmojiPanel()
        stopClipboardMonitoring()
        super.onWindowHidden()
        keyboardSwitcher?.onHideWindow()
        keyboardSwitcher?.closing()
        suggestionSession.clearAutomaticSpace()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        inputView?.resetSpaceCursor()
        inputView?.hideClipboardPanel()
        inputView?.hideEmojiPanel()
        stopClipboardMonitoring()
        keyboardSwitcher?.closing()
        loadedWidth = 0
        loadedHeight = 0
        super.onConfigurationChanged(newConfig)
        loadKeyboardIfMeasured()
    }

    override fun onDestroy() {
        inputView?.resetSpaceCursor()
        inputView?.hideClipboardPanel()
        inputView?.hideEmojiPanel()
        stopClipboardMonitoring()
        keyboardSwitcher?.closing()
        keyboardSwitcher?.deallocateMemory()
        inputView = null
        keyboardSwitcher = null
        preferencesListener?.let {
            getSharedPreferences("karuikey_settings", MODE_PRIVATE)
                .unregisterOnSharedPreferenceChangeListener(it)
            getSharedPreferences("karuikey_clipboard_history", MODE_PRIVATE)
                .unregisterOnSharedPreferenceChangeListener(it)
        }
        preferencesListener = null
        editorInfo = null
        SuggestionEngine.endSession()
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
        updateClipboardMonitoring()
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
        val showEmojiOnBottomRow = emojiKeyAllowed() &&
            KaruikeyPreferences.emojiKeyPlacement(this) == KaruikeyPreferences.EMOJI_PLACEMENT_BOTTOM
        emojiBottomRow = showEmojiOnBottomRow
        keyboardSwitcher?.loadKeyboard(
            editor,
            subtype,
            keyboardView.width,
            keyboardView.height,
            autoCapsMode(),
            multipleLanguages && !showEmojiOnBottomRow,
            showEmojiOnBottomRow
        )
        keyboardView.setMainDictionaryAvailability(gestureAllowed)
        // Keep the optional AOSP trail off until its visual parameters are configured for this
        // standalone surface; decoding does not depend on drawing it.
        keyboardView.setGestureHandlingEnabledByUser(gestureAllowed, false, false)
        loadedWidth = keyboardView.width
        loadedHeight = keyboardView.height
    }

    private fun cycleLanguage() {
        cycleLanguage(1)
    }

    private fun cycleLanguage(direction: Int) {
        val enabled = KaruikeyPreferences.enabledLanguages(this)
        if (enabled.size < 2) return
        val currentId = currentLanguage?.id ?: KaruikeyPreferences.activeLanguage(this).id
        val currentIndex = enabled.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
        val next = enabled[(currentIndex + direction).mod(enabled.size)]
        currentLanguage = next
        currentSubtype = subtypeFor(next)
        beginSuggestionSession(next.locale)
        gestureAllowed = editorInfo?.let { gestureEnabledFor(it, next.locale) } ?: false
        KaruikeyPreferences.setActiveLanguage(this, next)
        clearComposingWord()
        keyboardSwitcher?.resetForNewInput()
        loadedWidth = 0
        loadedHeight = 0
        loadKeyboardIfMeasured()
        refreshSuggestions()
    }

    private fun handleEmojiSearchCode(primaryCode: Int) {
        when (primaryCode) {
            Constants.CODE_DELETE -> inputView?.deleteEmojiSearchCodePoint()
            Constants.CODE_ENTER -> Unit
            Constants.CODE_SPACE -> inputView?.appendEmojiSearchText(" ")
            Constants.CODE_SHIFT,
            Constants.CODE_CAPSLOCK,
            Constants.CODE_SWITCH_ALPHA_SYMBOL,
            Constants.CODE_LANGUAGE_SWITCH,
            Constants.CODE_EMOJI -> Unit
            else -> if (primaryCode > 0) inputView?.appendEmojiSearchCodePoint(primaryCode)
        }
    }

    private fun emojiKeyAllowed(): Boolean {
        val info = editorInfo ?: return false
        if ((info.inputType and InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) return false
        return !isSensitiveInput(info)
    }

    private fun openEmojiPanel() {
        if (!emojiKeyAllowed()) return
        keyboardSwitcher?.showAlphabetKeyboard(autoCapsMode())
        inputView?.showEmojiPanel()
    }

    private fun commitEmoji(emoji: String) {
        if (!emojiKeyAllowed()) return
        val connection = currentInputConnection ?: return
        finishEditorComposition(connection)
        if (expectedCursorPosition >= 0) {
            expectedCursorPosition += emoji.length
            expectedSelectionEnd = expectedCursorPosition
        }
        duringEditorUpdate { connection.commitText(emoji, 1) }
        clearCurrentWord()
    }

    private fun beginSuggestionSession(locale: String) {
        SuggestionEngine.beginSession(locale) {
            inputView?.post {
                if (editorInfo != null && currentLanguage?.locale == locale) refreshSuggestions()
            }
        }
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
        return KaruikeyCapsMode.normalize(
            reported,
            info.inputType,
            KaruikeyPreferences.autoCapitalizationEnabled(this)
        )
    }

    private fun suggestionsEnabledFor(info: EditorInfo): Boolean {
        if (!KaruikeyPreferences.suggestionsEnabled(this)) return false
        if (!SuggestionEngine.hasDictionary(currentLanguage?.locale ?: "")) return false
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

    private fun clearComposingWord() {
        finishEditorComposition(currentInputConnection)
        suggestionSession.clear()
        pendingEditorSelection = -1
        clearCurrentWord()
    }

    private fun clearCurrentWord() {
        suggestionSession.clearCurrentWord()
        clearSuggestions()
    }

    private fun clearSuggestions() {
        suggestionRequestId++
        clearSuggestionsView()
    }

    private fun clearSuggestionsView() {
        suggestionResults.clear()
        inputView?.setSuggestions(suggestionResults, suggestionRequestId,
            suggestionSession.prefix.toString())
    }

    private fun completeCurrentWord() {
        val word = suggestionSession.prefix.toString()
        val previousWord = suggestionSession.previousWord
        suggestionSession.completeCurrentWord()
        recordCompletedWord(word, previousWord)
        clearSuggestions()
    }

    private fun completeWord(word: String) {
        val previousWord = suggestionSession.previousWord
        suggestionSession.completeWord(word)
        recordCompletedWord(word, previousWord)
        clearSuggestions()
    }

    private fun recordCompletedWord(word: String, previousWord: String?) {
        if (word.isBlank() || editorInfo?.let(::isSensitiveInput) != false) return
        PredictionHistory.record(this, currentLanguage?.locale ?: return, word, previousWord)
    }

    private fun refreshSuggestions() {
        val requestId = ++suggestionRequestId
        if (!suggestionsAllowed) {
            clearCurrentWord()
            return
        }
        if (suggestionSession.prefix.isEmpty() && (suggestionSession.previousWord == null ||
            !KaruikeyPreferences.nextWordSuggestionsEnabled(this))
        ) {
            clearSuggestionsView()
            return
        }
        val locale = currentLanguage?.locale ?: "en"
        inputView?.markSuggestionsPending(requestId, suggestionSession.prefix.toString())
        SuggestionEngine.requestFill(
            locale,
            suggestionSession.previousWord,
            suggestionSession.recentWord(1),
            suggestionSession.recentWord(2),
            suggestionSession.prefix,
            keyboardSwitcher?.getKeyboard()
        ) { results ->
            inputView?.post {
                if (requestId != suggestionRequestId || editorInfo == null ||
                    currentLanguage?.locale != locale || !suggestionsAllowed
                ) return@post
                suggestionResults.clear()
                suggestionResults.addAll(results)
                inputView?.setSuggestions(suggestionResults, requestId,
                    suggestionSession.prefix.toString())
            }
        }
    }

    private fun commitSuggestion(candidate: String) {
        val connection = currentInputConnection ?: return
        val prefix = suggestionSession.prefix
        val replacingComposition = composingStart >= 0 && prefix.isNotEmpty()
        val committingNextWord = prefix.isEmpty() && suggestionSession.previousWord != null
        if (!replacingComposition && !committingNextWord) return
        val shouldCapitalize = (prefix.isNotEmpty() && prefix[0].isUpperCase()) ||
            (prefix.isEmpty() && autoCapsMode() != 0)
        val replacement = if (shouldCapitalize && candidate.isNotEmpty()) {
            candidate[0].uppercaseChar().toString() + candidate.substring(1)
        } else {
            candidate
        }
        if (replacingComposition) {
            expectedCursorPosition = composingStart + replacement.length
            expectedSelectionEnd = expectedCursorPosition
            duringEditorUpdate { connection.setComposingText(replacement, 1) }
            finishEditorComposition(connection)
        } else {
            if (expectedCursorPosition >= 0) {
                expectedCursorPosition += replacement.length
                expectedSelectionEnd = expectedCursorPosition
            }
            duringEditorUpdate { connection.commitText(replacement, 1) }
        }
        completeWord(replacement)
        commitAutomaticSpace(connection)
        refreshSuggestions()
    }

    private fun appendToComposition(connection: InputConnection?, text: String) {
        if (connection == null) return
        suggestionSession.clearAutomaticSpace()
        if (composingStart < 0) composingStart = expectedCursorPosition.coerceAtLeast(0)
        suggestionSession.append(text)
        expectedCursorPosition = composingStart + suggestionSession.prefix.length
        expectedSelectionEnd = expectedCursorPosition
        duringEditorUpdate { connection.setComposingText(suggestionSession.prefix, 1) }
    }

    private fun handleBackspace(connection: InputConnection?) {
        if (composingStart >= 0 && suggestionSession.prefix.isNotEmpty()) {
            val start = composingStart
            val removed = suggestionSession.deleteLastCodePoint()
            if (suggestionSession.prefix.isEmpty()) {
                finishEditorComposition(connection)
                expectedCursorPosition = start
                expectedSelectionEnd = start
            } else {
                expectedCursorPosition = start + suggestionSession.prefix.length
                expectedSelectionEnd = expectedCursorPosition
                duringEditorUpdate { connection?.setComposingText(suggestionSession.prefix, 1) }
            }
            if (removed > 0) clearSuggestions()
            return
        }
        if (suggestionSession.hasAutomaticSpace) {
            duringEditorUpdate { connection?.deleteSurroundingText(1, 0) }
            suggestionSession.clearAutomaticSpace()
            if (expectedCursorPosition > 0) {
                expectedCursorPosition--
                expectedSelectionEnd = expectedCursorPosition
            }
            clearSuggestions()
            return
        }
        duringEditorUpdate { connection?.deleteSurroundingText(1, 0) }
        suggestionSession.clear()
        composingStart = -1
        if (expectedCursorPosition > 0) {
            expectedCursorPosition--
            expectedSelectionEnd = expectedCursorPosition
        }
        clearSuggestions()
    }

    private fun commitSeparator(connection: InputConnection?, separator: String) {
        if (separator == " " && suggestionSession.hasAutomaticSpace) {
            suggestionSession.clearAutomaticSpace()
            completeCurrentWord()
            return
        }
        suggestionSession.clearAutomaticSpace()
        finishEditorComposition(connection)
        if (expectedCursorPosition >= 0) {
            pendingEditorSelection = expectedCursorPosition
            expectedCursorPosition += separator.length
            expectedSelectionEnd = expectedCursorPosition
        }
        duringEditorUpdate { connection?.commitText(separator, 1) }
        completeCurrentWord()
    }

    private fun commitAutomaticSpace(connection: InputConnection?) {
        if (expectedCursorPosition >= 0) {
            pendingEditorSelection = expectedCursorPosition
            expectedCursorPosition++
            expectedSelectionEnd = expectedCursorPosition
        }
        duringEditorUpdate { connection?.commitText(" ", 1) }
        suggestionSession.markAutomaticSpace()
    }

    private fun commitPunctuationAfterAutomaticSpace(
        connection: InputConnection?,
        punctuation: String
    ) {
        finishEditorComposition(connection)
        if (expectedCursorPosition > 0) expectedCursorPosition--
        duringEditorUpdate {
            connection?.deleteSurroundingText(1, 0)
            connection?.commitText("$punctuation ", 1)
        }
        expectedSelectionEnd = expectedCursorPosition + punctuation.length + 1
        expectedCursorPosition = expectedSelectionEnd
        suggestionSession.clearAutomaticSpace()
        suggestionSession.completeCurrentWord()
    }

    private fun isAutoSpacePunctuation(code: Int): Boolean = when (code) {
        '.'.code, ','.code, '?'.code, '!'.code, ':'.code, ';'.code -> true
        else -> false
    }

    private fun moveCursorBy(steps: Int) {
        if (steps == 0 || expectedCursorPosition < 0 ||
            expectedSelectionEnd != expectedCursorPosition
        ) return
        val connection = currentInputConnection ?: return
        suggestionSession.clearAutomaticSpace()
        var position = expectedCursorPosition
        val direction = if (steps < 0) -1 else 1
        for (stepIndex in 0 until kotlin.math.abs(steps)) {
            val nearby = if (direction < 0) {
                connection.getTextBeforeCursor(2, 0)
            } else {
                connection.getTextAfterCursor(2, 0)
            } ?: break
            val width = if (direction < 0) {
                SpaceCursor.lastCodePointWidth(nearby)
            } else {
                SpaceCursor.firstCodePointWidth(nearby)
            }
            if (width == 0) break
            position += direction * width
            if (position < 0) break
            var moved = false
            duringEditorUpdate { moved = connection.setSelection(position, position) }
            if (!moved) break
            expectedCursorPosition = position
            expectedSelectionEnd = position
        }
    }

    private fun finishEditorComposition(connection: InputConnection?) {
        if (composingStart < 0) return
        duringEditorUpdate { connection?.finishComposingText() }
        composingStart = -1
    }

    private inline fun duringEditorUpdate(block: () -> Unit) {
        editorUpdateDepth++
        try {
            block()
        } finally {
            editorUpdateDepth--
        }
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
        val surfaceColor = view.surfaceBackgroundColor()
        imeWindow.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        view.applySurfaceBackground(surfaceColor)
        // The IME window is full-width. A window-level blur would include application content
        // outside the keyboard, so blur remains disabled until it can be bounded safely.
        val insetsController = androidx.core.view.WindowCompat.getInsetsController(
            imeWindow,
            view
        )
        insetsController.isAppearanceLightNavigationBars = Color.luminance(surface) > 0.5f
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            imeWindow.isNavigationBarContrastEnforced = false
        }
        imeWindow.navigationBarColor = surfaceColor
        imeWindow.navigationBarDividerColor = Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= 35) {
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(imeWindow, false)
        } else {
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
        val sensitive = editorInfo?.let(::isSensitiveInput) ?: true
        if (!sensitive && text != null) ClipboardHistory.add(this, text)
        val historyEnabled = !sensitive && ClipboardHistory.enabled(this)
        val history = if (historyEnabled) ClipboardHistory.items(this) else emptyList()
        view.showClipboard(
            clipboardPanelItems(historyEnabled, text, history),
            if (!historyEnabled && text == null && clip == null) {
                R.string.clipboard_history_off_empty
            } else {
                message
            },
            historyEnabled
        )
    }

    private fun capturePrimaryClipboard() {
        if (!clipboardCaptureAllowed()) return
        val view = inputView ?: return
        val clipboard = getSystemService(ClipboardManager::class.java)
        val clip = clipboard.primaryClip ?: return
        if (!clip.description.hasMimeType("text/*")) return
        val text = clip.getItemAt(0).coerceToText(view.keyboardContext).toString()
        ClipboardHistory.add(this, text)
    }

    private fun clipboardCaptureAllowed() =
        isInputViewShown && editorInfo?.let { !isSensitiveInput(it) } == true

    private fun isSensitiveInput(info: EditorInfo): Boolean {
        val inputType = info.inputType
        if ((inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0) return true
        return when (inputType and InputType.TYPE_MASK_VARIATION) {
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD -> true
            else -> false
        }
    }

    private fun updateClipboardMonitoring() {
        stopClipboardMonitoring()
        if (!ClipboardHistory.enabled(this) || !clipboardCaptureAllowed()) return
        getSystemService(ClipboardManager::class.java)
            .addPrimaryClipChangedListener(clipboardListener)
        clipboardListenerRegistered = true
    }

    private fun stopClipboardMonitoring() {
        if (!clipboardListenerRegistered) return
        getSystemService(ClipboardManager::class.java)
            .removePrimaryClipChangedListener(clipboardListener)
        clipboardListenerRegistered = false
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && inputView?.handleBack() == true) {
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
        private val candidateRegion = LinearLayout(keyboardContext)
        private val emojiPanel = EmojiPanel(
            keyboardContext,
            appearance,
            onEmojiSelected = ::commitEmoji,
            onClose = ::hideEmojiPanel,
            onSearchRequested = ::showEmojiSearchLayout,
            onSearchClosed = ::showEmojiCategoryLayout
        )
        private val toolbarHeight = resources.getDimensionPixelSize(R.dimen.keyboard_toolbar_height)
        private var clipboardHistory: List<ClipboardHistoryItem> = emptyList()
        private var clipboardEmptyMessage = R.string.clipboard_history_empty
        private val clipboardSelected = HashSet<Long>()
        private var clipboardEditMode = false
        private var spaceCursorKey: Key? = null
        private var spaceCursorLastX = 0
        private var spaceCursorDistance = 0
        private var spaceCursorActive = false
        private var spaceLanguageActive = false
        private val spaceCursorTrigger = dp(12)
        private val spaceCursorStep = dp(24)
        private val clipboardPanel = LinearLayout(keyboardContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(12))
            setBackgroundColor(appearance.keyboardBackground)
            visibility = View.GONE
        }
        private val clipboardHeaderTitle = TextView(keyboardContext).apply {
            text = getString(R.string.toolbar_clipboard)
            textSize = 16f
            typeface = keyboardTypeface
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(appearance.primaryText)
            setPadding(dp(12), 0, 0, 0)
        }
        private val clipboardStatus = TextView(keyboardContext).apply {
            textSize = 12f
            typeface = keyboardTypeface
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(appearance.secondaryText)
            setPadding(dp(8), 0, dp(4), 0)
        }
        private val clipboardEdit = ImageButton(keyboardContext).apply {
            contentDescription = getString(R.string.clipboard_edit)
            setImageResource(R.drawable.ic_settings_edit)
            imageTintList = ColorStateList.valueOf(appearance.primaryText)
            setBackgroundResource(R.drawable.keyboard_toolbar_button_background)
            setOnClickListener {
                clipboardEditMode = !clipboardEditMode
                clipboardSelected.clear()
                renderClipboardHistory()
            }
        }
        private val clipboardDeleteSelected = ImageButton(keyboardContext).apply {
            contentDescription = getString(R.string.clipboard_delete_selected)
            setImageResource(R.drawable.ic_settings_delete)
            imageTintList = ColorStateList.valueOf(appearance.primaryText)
            setBackgroundResource(R.drawable.keyboard_toolbar_button_background)
            visibility = View.GONE
            setOnClickListener {
                clipboardSelected.toList().forEach {
                    ClipboardHistory.remove(this@KaruikeyService, it)
                }
                clipboardSelected.clear()
                clipboardEditMode = false
                clipboardHistory = ClipboardHistory.items(this@KaruikeyService)
                renderClipboardHistory()
            }
        }
        private val clipboardItems = GridLayout(keyboardContext).apply {
            orientation = GridLayout.HORIZONTAL
            useDefaultMargins = false
        }
        private val clipboardHistoryScroll = ScrollView(keyboardContext).apply {
            addView(clipboardItems, LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        private val clipboardClearAll = TextView(keyboardContext).apply {
            text = getString(R.string.clipboard_clear_all)
            textSize = 14f
            typeface = keyboardTypeface
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setTextColor(appearance.primaryText)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundResource(R.drawable.keyboard_toolbar_button_background)
            visibility = View.GONE
            setOnClickListener {
                ClipboardHistory.clear(this@KaruikeyService)
                clipboardSelected.clear()
                clipboardEditMode = false
                clipboardHistory = emptyList()
                renderClipboardHistory()
            }
        }
        private val clipboardManageBar = LinearLayout(keyboardContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
        }
        private val candidateViews = Array(3) {
            TextView(keyboardContext).apply {
                gravity = Gravity.CENTER
                textSize = 14f
                typeface = suggestionTypeface
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                isClickable = true
                isFocusable = true
                setBackgroundResource(R.drawable.keyboard_toolbar_button_background)
                setTextColor(appearance.primaryText)
                layoutParams = LinearLayout.LayoutParams(toolbarHeight, LayoutParams.MATCH_PARENT)
            }
        }
        private val candidateQueries = arrayOfNulls<String>(3)
        private val candidateRequestIds = LongArray(3)
        private val emojiToolbarButton = toolbarButton(
            R.drawable.ic_keyboard_emoji, R.string.toolbar_emoji
        ) { showEmojiPanel() }
        private val languageToolbarButton = toolbarButton(
            R.drawable.ic_keyboard_language, R.string.toolbar_language
        ) { cycleLanguage() }
        private var navigationBottomInset = 0

        init {
            setBackgroundColor(appearance.keyboardBackground)
            keyboardView.setBackgroundColor(Color.TRANSPARENT)
            toolbar.setBackgroundColor(Color.TRANSPARENT)
            clipboardPanel.setBackgroundColor(Color.TRANSPARENT)
            suggestionToolbar.orientation = LinearLayout.HORIZONTAL
            suggestionToolbar.gravity = Gravity.CENTER_VERTICAL
            candidateRegion.orientation = LinearLayout.HORIZONTAL
            candidateRegion.gravity = Gravity.CENTER_VERTICAL
            candidateViews.forEachIndexed { index, candidate ->
                installCandidateListeners(index, candidate)
                candidateRegion.addView(candidate)
            }
            suggestionToolbar.addView(candidateRegion, LinearLayout.LayoutParams(
                0, LayoutParams.MATCH_PARENT
            ))
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
            utilityToolbar.addView(emojiToolbarButton, fixedToolbarButtonParams())
            utilityToolbar.addView(languageToolbarButton, fixedToolbarButtonParams())
            suggestionToolbar.addView(utilityToolbar, LinearLayout.LayoutParams(
                0, LayoutParams.MATCH_PARENT
            ))
            toolbar.addView(suggestionToolbar, FrameLayout.LayoutParams.MATCH_PARENT, toolbarHeight)
            val clipboardHeader = LinearLayout(keyboardContext).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            clipboardHeader.addView(ImageButton(keyboardContext).apply {
                contentDescription = getString(R.string.clipboard_back)
                setImageResource(R.drawable.ic_settings_back)
                imageTintList = ColorStateList.valueOf(appearance.primaryText)
                isClickable = true
                isFocusable = true
                setBackgroundResource(R.drawable.keyboard_toolbar_button_background)
                setOnClickListener { hideClipboardPanel() }
            }, LinearLayout.LayoutParams(toolbarHeight, toolbarHeight))
            clipboardHeader.addView(clipboardHeaderTitle, LinearLayout.LayoutParams(0, toolbarHeight, 1f))
            clipboardHeader.addView(clipboardStatus, LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, toolbarHeight
            ))
            clipboardHeader.addView(clipboardEdit, LinearLayout.LayoutParams(toolbarHeight, toolbarHeight))
            clipboardPanel.addView(clipboardHeader)
            clipboardPanel.addView(clipboardHistoryScroll, LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, 0, 1f
            ))
            clipboardManageBar.addView(clipboardDeleteSelected, LinearLayout.LayoutParams(
                toolbarHeight, toolbarHeight
            ))
            clipboardManageBar.addView(clipboardClearAll, LinearLayout.LayoutParams(
                0, LayoutParams.WRAP_CONTENT, 1f
            ))
            clipboardPanel.addView(clipboardManageBar, LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT
            ))
            addView(toolbar, LayoutParams.MATCH_PARENT, toolbarHeight)
            keyboardContent.addView(keyboardView, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            keyboardContent.addView(emojiPanel, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            keyboardContent.addView(clipboardPanel, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            emojiPanel.visibility = View.GONE
            addView(keyboardContent, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
                val bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                if (navigationBottomInset != bottom) {
                    navigationBottomInset = bottom
                    requestLayout()
                }
                insets
            }
            keyboardView.setOnTouchListener { _, event -> handleSpaceCursorTouch(event) }
        }

        private fun handleSpaceCursorTouch(event: android.view.MotionEvent): Boolean {
            if (AccessibilityUtils.getInstance().isTouchExplorationEnabled()) {
                resetSpaceCursor()
                return false
            }
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    val key = keyboardView.detectKeyForTouch(
                        event.x.toInt(), event.y.toInt()
                    )
                    if (key?.code == Constants.CODE_SPACE) {
                        spaceCursorKey = key
                        spaceCursorLastX = event.x.toInt()
                        spaceCursorDistance = 0
                    } else {
                        resetSpaceCursor()
                    }
                    return false
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    if (spaceCursorKey == null || event.pointerCount != 1) return false
                    val x = event.x.toInt()
                    spaceCursorDistance += x - spaceCursorLastX
                    spaceCursorLastX = x
                    if (spacebarLanguageGesture() &&
                        !spaceCursorActive && !spaceLanguageActive &&
                        kotlin.math.abs(spaceCursorDistance) >= spaceCursorTrigger
                    ) {
                        keyboardView.cancelAllOngoingEvents()
                        cycleLanguage(if (spaceCursorDistance > 0) 1 else -1)
                        keyboardView.performHapticFeedback(
                            android.view.HapticFeedbackConstants.KEYBOARD_TAP
                        )
                        spaceLanguageActive = true
                        spaceCursorDistance = 0
                        return true
                    }
                    if (spaceLanguageActive) return true
                    if (!spaceCursorActive && SpaceCursor.triggerReached(
                        spaceCursorDistance, spaceCursorTrigger
                        )
                    ) {
                        keyboardView.cancelAllOngoingEvents()
                        beginSpaceCursor()
                    }
                    if (spaceCursorActive) {
                        val steps = SpaceCursor.stepCount(
                            spaceCursorDistance, spaceCursorStep
                        )
                        if (steps != 0) {
                            spaceCursorDistance = SpaceCursor.remainder(
                                spaceCursorDistance, steps, spaceCursorStep
                            )
                            moveCursorBy(steps)
                        }
                        return true
                    }
                    return false
                }
                android.view.MotionEvent.ACTION_UP -> {
                    if (spaceLanguageActive) {
                        resetSpaceCursor()
                        return true
                    }
                    if (spaceCursorActive) {
                        resetSpaceCursor()
                        return true
                    }
                    resetSpaceCursor()
                    return false
                }
                android.view.MotionEvent.ACTION_CANCEL -> {
                    val wasActive = spaceCursorActive || spaceLanguageActive
                    resetSpaceCursor()
                    return wasActive
                }
            }
            return false
        }

        val isEmojiSearchActive: Boolean
            get() = emojiPanel.isSearchActive()

        fun appendEmojiSearchCodePoint(codePoint: Int) = emojiPanel.appendSearchCodePoint(codePoint)

        fun appendEmojiSearchText(text: String) = emojiPanel.appendSearchText(text)

        fun deleteEmojiSearchCodePoint() = emojiPanel.deleteSearchCodePoint()

        fun showEmojiPanel() {
            if (clipboardPanel.visibility == View.VISIBLE) hideClipboardPanel()
            restoreEmojiLayout()
            emojiPanel.exitSearch()
            keyboardView.visibility = View.GONE
            emojiPanel.visibility = View.VISIBLE
            toolbar.visibility = View.GONE
            requestLayout()
        }

        fun handleBack(): Boolean {
            if (emojiPanel.visibility == View.VISIBLE) {
                if (emojiPanel.handleBack()) return true
                hideEmojiPanel()
                return true
            }
            return hideClipboardPanel()
        }

        private fun showEmojiSearchLayout() {
            keyboardView.visibility = View.VISIBLE
            toolbar.visibility = View.GONE
            requestLayout()
            reloadKeyboardAfterLayout()
        }

        private fun showEmojiCategoryLayout() {
            restoreEmojiLayout()
            keyboardView.visibility = View.GONE
            emojiPanel.visibility = View.VISIBLE
            toolbar.visibility = View.GONE
            requestLayout()
        }

        private fun restoreEmojiLayout() {
            (keyboardView.layoutParams as FrameLayout.LayoutParams).apply {
                width = LayoutParams.MATCH_PARENT
                height = LayoutParams.MATCH_PARENT
                topMargin = 0
            }.also { keyboardView.layoutParams = it }
            (emojiPanel.layoutParams as FrameLayout.LayoutParams).apply {
                width = LayoutParams.MATCH_PARENT
                height = LayoutParams.MATCH_PARENT
                topMargin = 0
            }.also { emojiPanel.layoutParams = it }
            loadedHeight = 0
        }

        fun hideEmojiPanel(): Boolean {
            if (emojiPanel.visibility != View.VISIBLE) {
                return false
            }
            emojiPanel.exitSearch()
            restoreEmojiLayout()
            emojiPanel.visibility = View.GONE
            keyboardView.visibility = View.VISIBLE
            toolbar.visibility = if (KaruikeyPreferences.toolbarEnabled(this@KaruikeyService)) {
                View.VISIBLE
            } else View.GONE
            requestLayout()
            reloadKeyboardAfterLayout()
            return true
        }

        private fun reloadKeyboardAfterLayout() {
            loadedWidth = 0
            loadedHeight = 0
            post { loadKeyboardIfMeasured() }
        }

        private fun beginSpaceCursor() {
            clearComposingWord()
            spaceCursorActive = true
            spaceCursorKey?.onPressed()
            keyboardView.invalidateKey(spaceCursorKey)
        }

        private fun spacebarLanguageGesture(): Boolean =
            KaruikeyPreferences.spacebarSwipe(this@KaruikeyService) ==
                KaruikeyPreferences.SPACEBAR_SWIPE_LANGUAGE &&
                KaruikeyPreferences.enabledLanguages(this@KaruikeyService).size > 1

        fun resetSpaceCursor() {
            if (spaceCursorActive) {
                spaceCursorKey?.onReleased()
                keyboardView.invalidateKey(spaceCursorKey)
            }
            spaceCursorKey = null
            spaceCursorDistance = 0
            spaceCursorActive = false
            spaceLanguageActive = false
        }

        fun applyPreferences() {
            if (clipboardPanel.visibility != View.VISIBLE) {
                toolbar.visibility = if (KaruikeyPreferences.toolbarEnabled(this@KaruikeyService)) {
                    View.VISIBLE
                } else View.GONE
            }
            emojiToolbarButton.visibility = if (
                emojiKeyAllowed() && KaruikeyPreferences.emojiKeyPlacement(this@KaruikeyService) ==
                    KaruikeyPreferences.EMOJI_PLACEMENT_TOOLBAR
            ) View.VISIBLE else View.INVISIBLE
            languageToolbarButton.visibility = if (
                KaruikeyPreferences.enabledLanguages(this@KaruikeyService).size > 1
            ) View.VISIBLE else View.INVISIBLE
            applySurfaceBackground(surfaceBackgroundColor())
            keyboardView.setKeyPreviewPopupEnabled(
                KaruikeyPreferences.keyPreviewEnabled(this@KaruikeyService),
                500
            )
            requestLayout()
        }

        fun surfaceBackgroundColor(): Int {
            val configuredAlpha = KaruikeyPreferences.keyboardSurfaceAlpha(
                this@KaruikeyService
            )
            val minimumMultiplier = if (appearance.style == KaruikeyPreferences.KEYBOARD_STYLE_GLASS) {
                KaruikeyPreferences.minimumGlassSurfaceAlpha() /
                    (Color.alpha(appearance.keyboardBackground) / 255f)
            } else {
                0f
            }
            return KaruikeyPreferences.resolvedKeyboardSurfaceColor(
                appearance.keyboardBackground,
                maxOf(configuredAlpha, minimumMultiplier)
            )
        }

        fun applySurfaceBackground(color: Int) {
            setBackgroundColor(color)
            keyboardView.setBackgroundColor(Color.TRANSPARENT)
            toolbar.setBackgroundColor(Color.TRANSPARENT)
            clipboardPanel.setBackgroundColor(Color.TRANSPARENT)
        }

        fun showClipboard(
            items: List<ClipboardHistoryItem>,
            emptyMessage: Int,
            historyEnabled: Boolean
        ) {
            clipboardHistory = items
            clipboardEmptyMessage = emptyMessage
            clipboardSelected.clear()
            clipboardEditMode = false
            clipboardStatus.text = if (historyEnabled) {
                getString(R.string.clipboard_history_status, items.size)
            } else {
                getString(R.string.clipboard_history_off)
            }
            clipboardEdit.visibility = if (historyEnabled && items.isNotEmpty()) View.VISIBLE else View.GONE
            toolbar.visibility = View.GONE
            keyboardView.visibility = View.GONE
            clipboardPanel.visibility = View.VISIBLE
            renderClipboardHistory()
            requestLayout()
        }

        private fun renderClipboardHistory() {
            val columns = if (availableClipboardWidth() >= dp(360)) 2 else 1
            clipboardItems.columnCount = columns
            clipboardItems.removeAllViews()
            if (clipboardHistory.isEmpty()) {
                val emptyState = TextView(keyboardContext).apply {
                    text = getString(
                        clipboardEmptyMessage
                    )
                    textSize = 14f
                    typeface = keyboardTypeface
                    gravity = Gravity.CENTER
                    setTextColor(appearance.secondaryText)
                    setPadding(dp(16), dp(20), dp(16), dp(20))
                }
                val emptyParams = GridLayout.LayoutParams(
                    GridLayout.spec(0), GridLayout.spec(0, columns)
                ).apply {
                    width = availableClipboardWidth()
                    height = dp(96)
                }
                clipboardItems.addView(emptyState, emptyParams)
            }
            clipboardHistory.forEachIndexed { index, item ->
                val selected = clipboardSelected.contains(item.timestamp)
                val card = TextView(keyboardContext).apply {
                    text = item.text
                    textSize = 15f
                    typeface = keyboardTypeface
                    maxLines = 4
                    ellipsize = TextUtils.TruncateAt.END
                    gravity = Gravity.CENTER_VERTICAL
                    isClickable = true
                    isFocusable = true
                    isActivated = selected
                    alpha = if (selected) 0.58f else 1f
                    setTextColor(appearance.primaryText)
                    setPadding(dp(12), dp(12), dp(12), dp(12))
                    setBackgroundResource(R.drawable.keyboard_clipboard_item_background)
                    contentDescription = if (clipboardEditMode) {
                        getString(R.string.clipboard_select_item)
                    } else {
                        getString(R.string.clipboard_paste)
                    }
                    setOnClickListener {
                        if (clipboardEditMode) {
                            if (!clipboardSelected.add(item.timestamp)) {
                                clipboardSelected.remove(item.timestamp)
                            }
                            renderClipboardHistory()
                        } else {
                            pasteClipboard(item.text)
                        }
                    }
                }
                val cardWidth = (availableClipboardWidth() - dp(8) * (columns - 1)) / columns
                val params = GridLayout.LayoutParams(
                    GridLayout.spec(index / columns),
                    GridLayout.spec(index % columns)
                ).apply {
                    width = cardWidth.coerceAtLeast(1)
                    height = dp(72)
                    setMargins(0, 0, if (index % columns == columns - 1) 0 else dp(8), dp(8))
                }
                clipboardItems.addView(card, params)
            }
            val empty = clipboardHistory.isEmpty()
            clipboardClearAll.visibility = if (!empty && clipboardEditMode) View.VISIBLE else View.GONE
            clipboardDeleteSelected.visibility = if (clipboardEditMode) View.VISIBLE else View.GONE
            clipboardManageBar.visibility = if (clipboardEditMode && !empty) View.VISIBLE else View.GONE
            clipboardHeaderTitle.text = if (clipboardEditMode) {
                getString(R.string.clipboard_select_title)
            } else {
                getString(R.string.toolbar_clipboard)
            }
        }

        private fun availableClipboardWidth(): Int {
            val measured = clipboardItems.width
            return if (measured > 0) {
                measured
            } else {
                (width - clipboardPanel.paddingLeft - clipboardPanel.paddingRight).coerceAtLeast(1)
            }
        }

        private fun pasteClipboard(text: String) {
            finishEditorComposition(currentInputConnection)
            duringEditorUpdate { currentInputConnection?.commitText(text, 1) }
            clearComposingWord()
        }

        fun hideClipboardPanel(): Boolean {
            if (clipboardPanel.visibility != View.VISIBLE) return false
            clipboardItems.removeAllViews()
            clipboardHistory = emptyList()
            clipboardEmptyMessage = R.string.clipboard_history_empty
            clipboardSelected.clear()
            clipboardEditMode = false
            clipboardEdit.visibility = View.GONE
            clipboardDeleteSelected.visibility = View.GONE
            clipboardManageBar.visibility = View.GONE
            clipboardClearAll.visibility = View.GONE
            clipboardPanel.visibility = View.GONE
            keyboardView.visibility = View.VISIBLE
            toolbar.visibility = if (KaruikeyPreferences.toolbarEnabled(this@KaruikeyService)) {
                View.VISIBLE
            } else View.GONE
            setSuggestions(suggestionResults, suggestionRequestId,
                suggestionSession.prefix.toString())
            if (!KaruikeyPreferences.toolbarEnabled(this@KaruikeyService)) toolbar.visibility = View.GONE
            requestLayout()
            return true
        }

        fun markSuggestionsPending(requestId: Long, query: String) {
            for (index in candidateViews.indices) {
                candidateRequestIds[index] = requestId - 1
                candidateQueries[index] = query
            }
        }

        private fun installCandidateListeners(index: Int, candidate: TextView) {
            candidate.setOnClickListener {
                if (candidateRequestIds[index] == suggestionRequestId &&
                    candidateQueries[index] == suggestionSession.prefix.toString()
                ) {
                    (candidate.tag as? String)?.let(::commitSuggestion)
                }
            }
            candidate.setOnLongClickListener {
                if (candidateRequestIds[index] != suggestionRequestId ||
                    candidateQueries[index] != suggestionSession.prefix.toString()
                ) return@setOnLongClickListener false
                val word = candidate.tag as? String ?: return@setOnLongClickListener false
                val locale = currentLanguage?.locale ?: return@setOnLongClickListener false
                PopupMenu(this@KaruikeyService, candidate).apply {
                    menu.add(R.string.remove_suggestion)
                    setOnMenuItemClickListener {
                        SuggestionBlacklist.add(this@KaruikeyService, locale, word)
                        refreshSuggestions()
                        true
                    }
                    show()
                }
                true
            }
        }

        fun setSuggestions(suggestions: List<String>, requestId: Long, query: String) {
            candidateViews.forEachIndexed { index, candidate ->
                // Slots stay VISIBLE with fixed geometry; only changed text is touched.
                val text = suggestions.getOrNull(index)
                if (candidate.tag != text) {
                    candidate.text = text
                    candidate.tag = text
                    candidate.isEnabled = text != null
                }
                candidateQueries[index] = if (text != null) query else null
                candidateRequestIds[index] = requestId
            }
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
                layoutParams = fixedToolbarButtonParams()
            }

        private fun fixedToolbarButtonParams() = LinearLayout.LayoutParams(toolbarHeight, toolbarHeight)

        private fun View.setWidthIfChanged(width: Int) {
            val params = layoutParams
            if (params.width != width) {
                params.width = width
                layoutParams = params
            }
        }

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
            val utilityWidth = SuggestionStripGeometry.utilityWidth(width, toolbarHeight)
            val candidateWidth = SuggestionStripGeometry.candidateRegionWidth(width, toolbarHeight)
            candidateRegion.setWidthIfChanged(candidateWidth)
            utilityToolbar.setWidthIfChanged(utilityWidth)
            candidateViews.forEachIndexed { index, candidate ->
                candidate.setWidthIfChanged(
                    SuggestionStripGeometry.candidateSlotWidth(candidateWidth, index)
                )
            }
            val utilityButtonWidth = utilityWidth / SuggestionStripGeometry.UTILITY_COUNT
            for (index in 0 until utilityToolbar.childCount) {
                utilityToolbar.getChildAt(index).setWidthIfChanged(utilityButtonWidth)
            }
            val contentHeight = keyboardHeight
            val emojiHeight = contentHeight * 58 / 100
            if (emojiPanel.isSearchActive()) {
                (emojiPanel.layoutParams as FrameLayout.LayoutParams).apply {
                    this.width = LayoutParams.MATCH_PARENT
                    this.height = emojiHeight
                    topMargin = 0
                }.also { emojiPanel.layoutParams = it }
                (keyboardView.layoutParams as FrameLayout.LayoutParams).apply {
                    this.width = LayoutParams.MATCH_PARENT
                    this.height = contentHeight - emojiHeight
                    topMargin = emojiHeight
                }.also { keyboardView.layoutParams = it }
            }
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
            if (clipboardPanel.visibility == View.VISIBLE) {
                post { renderClipboardHistory() }
            }
        }
    }
}

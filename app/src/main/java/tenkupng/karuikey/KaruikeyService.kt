package tenkupng.karuikey

import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.content.Intent
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.Bitmap
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import androidx.core.graphics.ColorUtils
import kotlin.math.abs
import android.graphics.Typeface
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Rect
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.InputMethodService.Insets
import android.view.MotionEvent
import android.view.ViewOutlineProvider
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.android.inputmethod.accessibility.AccessibilityUtils
import com.android.inputmethod.compat.InputMethodSubtypeCompatUtils
import com.android.inputmethod.event.Event
import com.android.inputmethod.keyboard.KeyboardActionListener
import com.android.inputmethod.keyboard.Key
import com.android.inputmethod.keyboard.KeyboardLayoutSet
import com.android.inputmethod.keyboard.KeyboardSwitcher
import com.android.inputmethod.keyboard.KeyboardView
import com.android.inputmethod.keyboard.MainKeyboardView
import com.android.inputmethod.latin.common.Constants
import com.android.inputmethod.latin.common.InputPointers
import com.android.inputmethod.latin.common.StringUtils
import com.android.inputmethod.latin.utils.RecapitalizeStatus
import com.android.inputmethod.latin.utils.SubtypeLocaleUtils

// Enough text before the cursor for one word plus three context words.
private const val RESUME_CONTEXT_LENGTH = 64
private const val SPACE_LANGUAGE_FLICK_MS = 250L
private const val SIDE_CANDIDATE_MIN_DP = 96

// Display order of candidate slots: the best suggestion (index 0) goes in the middle.
private val CANDIDATE_DISPLAY_ORDER = intArrayOf(1, 0, 2)

class KaruikeyService : InputMethodService() {
    private val keyboardTypeface: Typeface by lazy { KaruikeyTypeface.create(this, 400) }
    private val suggestionTypeface: Typeface by lazy { KaruikeyTypeface.create(this, 475) }
    private val primarySuggestionTypeface: Typeface by lazy { KaruikeyTypeface.create(this, 650) }
    private var inputView: KaruikeyInputView? = null
    private var blurListener: java.util.function.Consumer<Boolean>? = null
    private val windowStyle = ImeWindowStyle(this)
    private val keyFeedback by lazy { KeyFeedback(this) }
    private var keyboardHost: KeyboardHost? = null
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
    // Last cursor the editor reported in agreement with us; own edits run from here to expected.
    private var confirmedCursorPosition = -1
    private var lastAutoCorrection: AutoCorrection? = null

    private data class AutoCorrection(val typed: String, val corrected: String, val separator: String)
    private var clipboardListenerRegistered = false
    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        capturePrimaryClipboard()
    }

    override fun onCreate() {
        AccessibilityUtils.init(this)
        SubtypeLocaleUtils.init(this)
        super.onCreate()
        SuggestionEngine.initialize(this)
        // Warm the emoji catalog off the main thread so the first panel open is instant.
        Thread({ EmojiCatalog.load(this) }, "emoji-catalog").start()
        val preferences = getSharedPreferences("karuikey_settings", MODE_PRIVATE)
        preferencesListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            // Dragging the floating keyboard saves its position; that needs no reload.
            if (key?.startsWith("floating_") == true) return@OnSharedPreferenceChangeListener
            inputView?.post { refreshInputViewForPreferences() }
        }
        preferences.registerOnSharedPreferenceChangeListener(preferencesListener)
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            val listener = java.util.function.Consumer<Boolean> {
                inputView?.post { refreshInputViewForPreferences() }
            }
            getSystemService(WindowManager::class.java)
                ?.addCrossWindowBlurEnabledListener(mainExecutor, listener)
            blurListener = listener
        }
        getSharedPreferences("karuikey_clipboard_history", MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(preferencesListener)
        ClipboardHistory.purge(this)
    }

    private val keyboardActionListener: KeyboardActionListener = object : KeyboardActionListener.Adapter() {
        override fun onPressKey(primaryCode: Int, repeatCount: Int, isSinglePointer: Boolean) {
            keyFeedback.onKeyPress(primaryCode)
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
            if (primaryCode != Constants.CODE_DELETE) lastAutoCorrection = null
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
                    suggestionSession.markSentenceEnd()
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
                        appendToComposition(connection, text, x, y)
                    } else if (suggestionSession.hasAutomaticSpace &&
                        isAutoSpacePunctuation(primaryCode)
                    ) {
                        commitPunctuationAfterAutomaticSpace(connection, text)
                    } else {
                        suggestionSession.clearAutomaticSpace()
                        if (isAutoSpacePunctuation(primaryCode)) {
                            lastAutoCorrection = applyAutoCorrection(connection)
                                ?.copy(separator = text)
                        }
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
                            if (isSentenceEnd(primaryCode)) {
                                suggestionSession.markSentenceEnd()
                                clearSuggestions()
                            }
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
                    refreshSuggestions()
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

    private fun createInputView(): View {
        // KeyboardLayoutSet caches icon-bearing Keyboard instances statically; a new themed
        // context must not reuse drawables created for the previous keyboard appearance.
        KeyboardLayoutSet.onKeyboardThemeChanged()
        val view = KaruikeyInputView()
        inputView = view
        keyboardSwitcher = view.keyboardSwitcher
        return KeyboardHost(this, view) { view.floating = it }.also { keyboardHost = it }
    }

    private fun floatingModeActive() =
        KaruikeyPreferences.keyboardMode(this) == KaruikeyPreferences.KEYBOARD_MODE_FLOATING

    override fun onEvaluateFullscreenMode(): Boolean =
        !floatingModeActive() && super.onEvaluateFullscreenMode()

    override fun onConfigureWindow(win: android.view.Window, isFullscreen: Boolean, isCandidatesOnly: Boolean) {
        super.onConfigureWindow(win, isFullscreen, isCandidatesOnly)
        if (keyboardHost?.floating == true) {
            win.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        }
    }

    // A floating keyboard spans the whole window but only claims its own card: the app keeps
    // its full size and receives every touch outside the keyboard.
    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        val host = keyboardHost ?: return
        if (!host.floating || !isInputViewShown) return
        val windowHeight = window?.window?.decorView?.height ?: return
        outInsets.contentTopInsets = windowHeight
        outInsets.visibleTopInsets = windowHeight
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
        outInsets.touchableRegion.set(host.keyboardBoundsInWindow())
    }

    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        gestureRequestId++
        lastAutoCorrection = null
        inputView?.resetSpaceCursor()
        inputView?.hideClipboardPanel()
        // Undo and redo can restart input in the same field; editing goes on there.
        if (!restarting) inputView?.hideTextEditPanel()
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
        confirmedCursorPosition = attribute.initialSelStart
        clearComposingWord()
        if (attribute.initialSelStart <= 0) suggestionSession.markSentenceEnd()
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
            // Updates arrive asynchronously; while typing fast, the editor still reports our
            // earlier edits. Those must not end the composition mid-word.
            if (isBelatedSelectionUpdate(oldSelStart, oldSelEnd, newSelStart, newSelEnd)) return
            val matchesExpectation =
                newSelStart == expectedCursorPosition && newSelEnd == expectedSelectionEnd
            if (!matchesExpectation) suggestionSession.clearAutomaticSpace()
            var cleared = false
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
                cleared = true
            }
            expectedCursorPosition = newSelStart
            expectedSelectionEnd = newSelEnd
            confirmedCursorPosition = newSelStart
            // Our own edits already requested suggestions; a second request would discard them.
            if (cleared || !matchesExpectation) refreshSuggestions()
        }
    }

    private fun isBelatedSelectionUpdate(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int
    ): Boolean {
        val confirmed = confirmedCursorPosition
        val expected = expectedCursorPosition
        if (confirmed < 0 || expected < 0 || expectedSelectionEnd != expected) return false
        if (oldSelStart != oldSelEnd || newSelStart != newSelEnd || newSelStart == expected) {
            return false
        }
        val low = minOf(confirmed, expected)
        val high = maxOf(confirmed, expected)
        return newSelStart in low..high && oldSelStart in low..high
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
        inputView?.hideTextEditPanel()
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
        inputView?.hideTextEditPanel()
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
        confirmedCursorPosition = -1
    }

    override fun onWindowHidden() {
        gestureRequestId++
        inputView?.resetSpaceCursor()
        inputView?.hideClipboardPanel()
        inputView?.hideTextEditPanel()
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
        inputView?.hideTextEditPanel()
        inputView?.hideEmojiPanel()
        stopClipboardMonitoring()
        keyboardSwitcher?.closing()
        loadedWidth = 0
        loadedHeight = 0
        super.onConfigurationChanged(newConfig)
        keyboardHost?.loadFloatingPosition()
        configureImeWindow()
        loadKeyboardIfMeasured()
    }

    override fun onDestroy() {
        inputView?.resetSpaceCursor()
        inputView?.hideClipboardPanel()
        inputView?.hideTextEditPanel()
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
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            blurListener?.let {
                getSystemService(WindowManager::class.java)?.removeCrossWindowBlurEnabledListener(it)
            }
        }
        blurListener = null
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
            showEmojiOnBottomRow,
            KaruikeyPreferences.keyboardMode(this) == KaruikeyPreferences.KEYBOARD_MODE_SPLIT,
            KaruikeyPreferences.numberRowEnabled(this)
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
            Constants.CODE_EMOJI -> Unit
            // Search names exist in English and Russian, so the search keyboard can switch too.
            Constants.CODE_LANGUAGE_SWITCH -> if (!emojiBottomRow) cycleLanguage()
            else -> if (primaryCode > 0) inputView?.appendEmojiSearchCodePoint(primaryCode)
        }
    }

    private fun emojiKeyAllowed(): Boolean {
        val info = editorInfo ?: return false
        if ((info.inputType and InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) return false
        return !InputFieldPolicy.isSensitive(info)
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

    private fun suggestionsEnabledFor(info: EditorInfo): Boolean =
        KaruikeyPreferences.suggestionsEnabled(this) &&
            SuggestionEngine.hasDictionary(currentLanguage?.locale ?: "") &&
            InputFieldPolicy.allowsWordInput(info)

    private fun gestureEnabledFor(info: EditorInfo, locale: String): Boolean =
        SuggestionEngine.hasDictionary(locale) && InputFieldPolicy.allowsWordInput(info)

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
        inputView?.setSuggestions(suggestionResults, suggestionSession.prefix.toString())
    }

    private fun completeCurrentWord() {
        val word = suggestionSession.prefix.toString()
        val previousWord = suggestionSession.previousWord
        suggestionSession.completeCurrentWord()
        recordCompletedWord(word, previousWord)
        // Offer next-word predictions right away; the editor's selection echo is ignored.
        refreshSuggestions()
    }

    private fun completeWord(word: String) {
        val previousWord = suggestionSession.previousWord
        suggestionSession.completeWord(word)
        recordCompletedWord(word, previousWord)
        clearSuggestions()
    }

    private fun recordCompletedWord(word: String, previousWord: String?) {
        if (word.isBlank() || editorInfo?.let(InputFieldPolicy::isIncognito) != false) return
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
        SuggestionEngine.requestFill(
            locale,
            suggestionSession.previousWord,
            suggestionSession.recentWord(1),
            suggestionSession.recentWord(2),
            suggestionSession.prefix,
            keyboardSwitcher?.getKeyboard(),
            suggestionSession.xCoordinates(),
            suggestionSession.yCoordinates(),
            suggestionSession.atSentenceStart
        ) { results ->
            inputView?.post {
                if (requestId != suggestionRequestId || editorInfo == null ||
                    currentLanguage?.locale != locale || !suggestionsAllowed
                ) return@post
                suggestionResults.clear()
                suggestionResults.addAll(results)
                inputView?.setSuggestions(suggestionResults, suggestionSession.prefix.toString())
            }
        }
    }

    private fun commitSuggestion(candidate: String) {
        lastAutoCorrection = null
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

    private fun appendToComposition(
        connection: InputConnection?,
        text: String,
        x: Int = SuggestionSession.NOT_A_COORDINATE,
        y: Int = SuggestionSession.NOT_A_COORDINATE
    ) {
        if (connection == null) return
        suggestionSession.clearAutomaticSpace()
        if (composingStart < 0) composingStart = expectedCursorPosition.coerceAtLeast(0)
        suggestionSession.append(text, x, y)
        expectedCursorPosition = composingStart + suggestionSession.prefix.length
        expectedSelectionEnd = expectedCursorPosition
        duringEditorUpdate { connection.setComposingText(suggestionSession.prefix, 1) }
    }

    private fun handleBackspace(connection: InputConnection?) {
        if (revertAutoCorrection(connection)) return
        if (composingStart >= 0 && suggestionSession.prefix.isNotEmpty()) {
            val start = composingStart
            val removed = suggestionSession.deleteLastCodePoint()
            if (suggestionSession.prefix.isEmpty()) {
                // Clear the composed text itself; finishing alone would leave the last letter.
                duringEditorUpdate { connection?.setComposingText("", 1) }
                finishEditorComposition(connection)
                expectedCursorPosition = start
                expectedSelectionEnd = start
                resumeWordBeforeCursor(connection)
                return
            } else {
                expectedCursorPosition = start + suggestionSession.prefix.length
                expectedSelectionEnd = expectedCursorPosition
                duringEditorUpdate { connection?.setComposingText(suggestionSession.prefix, 1) }
            }
            if (removed > 0) refreshSuggestions()
            return
        }
        if (suggestionSession.hasAutomaticSpace) {
            duringEditorUpdate { connection?.deleteSurroundingText(1, 0) }
            suggestionSession.clearAutomaticSpace()
            if (expectedCursorPosition > 0) {
                expectedCursorPosition--
                expectedSelectionEnd = expectedCursorPosition
            }
            resumeWordBeforeCursor(connection)
            return
        }
        duringEditorUpdate { connection?.deleteSurroundingText(1, 0) }
        suggestionSession.clear()
        composingStart = -1
        if (expectedCursorPosition > 0) {
            expectedCursorPosition--
            expectedSelectionEnd = expectedCursorPosition
        }
        resumeWordBeforeCursor(connection)
    }

    /** After Backspace, picks the word at the cursor back up so its suggestions return. */
    private fun resumeWordBeforeCursor(connection: InputConnection?) {
        val cursor = expectedCursorPosition
        val before = if (connection != null && suggestionsAllowed && cursor >= 0 &&
            expectedSelectionEnd == cursor
        ) connection.getTextBeforeCursor(RESUME_CONTEXT_LENGTH, 0) else null
        if (before == null) {
            clearSuggestions()
            return
        }
        val context = SuggestionSession.parseBeforeCursor(before)
        suggestionSession.restore(context.word, context.previousWords, context.sentenceStart)
        if (context.word.isNotEmpty() && cursor >= context.word.length) {
            val start = cursor - context.word.length
            duringEditorUpdate { connection?.setComposingRegion(start, cursor) }
            composingStart = start
        }
        refreshSuggestions()
    }

    private fun commitSeparator(connection: InputConnection?, separator: String) {
        if (separator == " " && suggestionSession.hasAutomaticSpace) {
            suggestionSession.clearAutomaticSpace()
            completeCurrentWord()
            return
        }
        suggestionSession.clearAutomaticSpace()
        val correction = applyAutoCorrection(connection)
        finishEditorComposition(connection)
        if (expectedCursorPosition >= 0) {
            pendingEditorSelection = expectedCursorPosition
            expectedCursorPosition += separator.length
            expectedSelectionEnd = expectedCursorPosition
        }
        duringEditorUpdate { connection?.commitText(separator, 1) }
        if (correction != null) lastAutoCorrection = correction.copy(separator = separator)
        completeCurrentWord()
    }

    /**
     * Replaces the composed word with the dictionary's confident correction before a separator
     * commits it. Returns what was replaced so Backspace can revert it.
     */
    private fun applyAutoCorrection(connection: InputConnection?): AutoCorrection? {
        val typed = suggestionSession.prefix.toString()
        if (connection == null || composingStart < 0 || typed.isEmpty() || !suggestionsAllowed ||
            !KaruikeyPreferences.autoCorrectionEnabled(this)
        ) return null
        val correction = SuggestionEngine.awaitAutoCorrection(
            currentLanguage?.locale ?: return null,
            suggestionSession.previousWord,
            suggestionSession.recentWord(1),
            suggestionSession.recentWord(2),
            typed,
            keyboardSwitcher?.getKeyboard(),
            suggestionSession.xCoordinates(),
            suggestionSession.yCoordinates(),
            suggestionSession.atSentenceStart
        ) ?: return null
        if (correction == typed) return null
        expectedCursorPosition = composingStart + correction.length
        expectedSelectionEnd = expectedCursorPosition
        duringEditorUpdate { connection.setComposingText(correction, 1) }
        suggestionSession.replacePrefix(correction)
        return AutoCorrection(typed, correction, "")
    }

    /** Backspace right after an auto-correction restores the word exactly as typed. */
    private fun revertAutoCorrection(connection: InputConnection?): Boolean {
        val undo = lastAutoCorrection ?: return false
        lastAutoCorrection = null
        if (connection == null || composingStart >= 0) return false
        val committed = undo.corrected + undo.separator
        val before = connection.getTextBeforeCursor(committed.length, 0)?.toString()
        if (before != committed) return false
        val restored = undo.typed + undo.separator
        duringEditorUpdate {
            connection.deleteSurroundingText(committed.length, 0)
            connection.commitText(restored, 1)
        }
        if (expectedCursorPosition >= 0) {
            expectedCursorPosition += restored.length - committed.length
            expectedSelectionEnd = expectedCursorPosition
        }
        // Insisting on the typed word teaches it, so the same word is not corrected again.
        repeat(SuggestionRanker.LEARNED_WORD_MIN_COUNT) { recordCompletedWord(undo.typed, null) }
        suggestionSession.clearAutomaticSpace()
        suggestionSession.clear()
        clearSuggestions()
        return true
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
        if (isSentenceEnd(punctuation.codePointAt(0))) suggestionSession.markSentenceEnd()
    }

    private fun isSentenceEnd(code: Int): Boolean =
        code == '.'.code || code == '!'.code || code == '?'.code

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

    // Backspace swipe: text before the cursor when the swipe began, and the cursor it ends at.
    private var deleteSwipeText: CharSequence? = null
    private var deleteSwipeAnchor = -1
    private var deleteSwipeStart = -1

    private fun beginDeleteSwipe(): Boolean {
        clearComposingWord()
        val connection = currentInputConnection ?: return false
        val anchor = expectedCursorPosition
        if (anchor <= 0 || expectedSelectionEnd != anchor) return false
        deleteSwipeText = connection.getTextBeforeCursor(minOf(anchor, 4096), 0) ?: return false
        deleteSwipeAnchor = anchor
        deleteSwipeStart = anchor
        return true
    }

    /** Selects [words] whole words back from where the swipe began; returns the selected length. */
    private fun selectWordsForDelete(words: Int): Int {
        val text = deleteSwipeText ?: return 0
        val connection = currentInputConnection ?: return 0
        var index = text.length
        repeat(words) {
            while (index > 0 && Character.isWhitespace(text[index - 1])) index--
            while (index > 0 && !Character.isWhitespace(text[index - 1])) index--
        }
        val start = deleteSwipeAnchor - (text.length - index)
        if (start != deleteSwipeStart) {
            deleteSwipeStart = start
            duringEditorUpdate { connection.setSelection(start, deleteSwipeAnchor) }
            expectedCursorPosition = start
            expectedSelectionEnd = deleteSwipeAnchor
        }
        return deleteSwipeAnchor - start
    }

    private fun finishDeleteSwipe() {
        val connection = currentInputConnection
        if (connection != null && deleteSwipeText != null && deleteSwipeStart < deleteSwipeAnchor) {
            duringEditorUpdate { connection.commitText("", 1) }
            expectedCursorPosition = deleteSwipeStart
            expectedSelectionEnd = deleteSwipeStart
            suggestionSession.clear()
            resumeWordBeforeCursor(connection)
        }
        deleteSwipeText = null
        deleteSwipeAnchor = -1
        deleteSwipeStart = -1
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
        val surfaceColor = view.surfaceBackgroundColor()
        view.applySurfaceBackground(surfaceColor)
        val floating = floatingModeActive()
        keyboardHost?.floating = floating
        windowStyle.apply(imeWindow, view, floating, view.appearance.keyboardBackground, surfaceColor)
    }

    private fun performTextEdit(action: TextEditAction, selecting: Boolean) {
        val connection = currentInputConnection ?: return
        clearComposingWord()
        suggestionSession.clearAutomaticSpace()
        fun key(code: Int, meta: Int = 0) {
            val now = android.os.SystemClock.uptimeMillis()
            connection.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0, meta))
            connection.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0, meta))
        }
        val shift = if (selecting) KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON else 0
        val ctrl = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        when (action) {
            TextEditAction.LEFT -> key(KeyEvent.KEYCODE_DPAD_LEFT, shift)
            TextEditAction.RIGHT -> key(KeyEvent.KEYCODE_DPAD_RIGHT, shift)
            TextEditAction.UP -> key(KeyEvent.KEYCODE_DPAD_UP, shift)
            TextEditAction.DOWN -> key(KeyEvent.KEYCODE_DPAD_DOWN, shift)
            TextEditAction.HOME -> key(KeyEvent.KEYCODE_MOVE_HOME, shift)
            TextEditAction.END -> key(KeyEvent.KEYCODE_MOVE_END, shift)
            TextEditAction.SELECT_ALL -> connection.performContextMenuAction(android.R.id.selectAll)
            TextEditAction.COPY -> connection.performContextMenuAction(android.R.id.copy)
            TextEditAction.CUT -> connection.performContextMenuAction(android.R.id.cut)
            TextEditAction.PASTE -> connection.performContextMenuAction(android.R.id.paste)
            // Editors take Ctrl+Z / Ctrl+Shift+Z as undo and redo.
            TextEditAction.UNDO -> key(KeyEvent.KEYCODE_Z, ctrl)
            TextEditAction.REDO -> key(KeyEvent.KEYCODE_Z, ctrl or KeyEvent.META_SHIFT_ON)
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
        val sensitive = editorInfo?.let(InputFieldPolicy::isIncognito) ?: true
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
        isInputViewShown && editorInfo?.let { !InputFieldPolicy.isIncognito(it) } == true

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
            onSearchClosed = ::showEmojiCategoryLayout,
            onKeyCode = { code ->
                keyboardActionListener.onCodeInput(code, Constants.NOT_A_COORDINATE,
                    Constants.NOT_A_COORDINATE, false)
            },
            onLanguageSwipe = { direction -> cycleLanguage(direction) },
            languageLabel = { currentLanguage?.displayName.orEmpty() }
        )
        private val toolbarHeight = resources.getDimensionPixelSize(R.dimen.keyboard_toolbar_height)
        private var spaceCursorKey: Key? = null
        private var spaceCursorLastX = 0
        private var spaceCursorDistance = 0
        private var spaceCursorDownTime = 0L
        private var spaceCursorActive = false
        private var spaceLanguageActive = false
        private var deleteSwipeKey: Key? = null
        private var deleteSwipeActive = false
        private var deleteSwipeWords = 0
        private val spaceCursorTrigger = dp(12)
        private val spaceCursorStep = dp(24)
        private val deleteSwipeStep = dp(36)
        private val textEditPanel = TextEditPanel(
            keyboardContext, appearance, keyboardTypeface, toolbarHeight,
            onBack = { hideTextEditPanel() },
            onAction = ::performTextEdit
        )
        private val clipboardPanel = ClipboardPanel(
            keyboardContext, appearance, keyboardTypeface, toolbarHeight,
            onBack = { hideClipboardPanel() },
            onPaste = ::pasteClipboard
        )
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
        private val emojiToolbarButton = toolbarButton(
            R.drawable.ic_keyboard_emoji, R.string.toolbar_emoji
        ) { showEmojiPanel() }
        private val languageToolbarButton = toolbarButton(
            R.drawable.ic_keyboard_language, R.string.toolbar_language
        ) { cycleLanguage() }
        private val modeToolbarButton = toolbarButton(
            keyboardModeIcon(KaruikeyPreferences.keyboardMode(this@KaruikeyService)),
            R.string.toolbar_keyboard_mode
        ) { showKeyboardModeMenu() }
        private var navigationBottomInset = 0
        private val dragHandleHeight = dp(24)
        var floating = false
            set(value) {
                if (field == value) return
                field = value
                dragHandle.visibility = if (value) View.VISIBLE else View.GONE
                invalidateOutline()
                requestLayout()
            }
        private val dragHandle = FloatingDragHandle(keyboardContext, appearance) { keyboardHost }
        private val bottomInset get() = if (floating) dragHandleHeight else navigationBottomInset

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
            }
            // The best suggestion sits in the middle slot and is set in a heavier weight.
            candidateViews[0].typeface = primarySuggestionTypeface
            for (index in CANDIDATE_DISPLAY_ORDER) candidateRegion.addView(candidateViews[index])
            candidateRegion.visibility = View.GONE
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
                    R.drawable.ic_keyboard_text_edit,
                    R.string.toolbar_text_edit
                ) { showTextEditPanel() },
                fixedToolbarButtonParams()
            )
            utilityToolbar.addView(
                toolbarButton(
                    R.drawable.ic_keyboard_settings,
                    R.string.toolbar_settings
                ) { openSettings() },
                fixedToolbarButtonParams()
            )
            utilityToolbar.addView(modeToolbarButton, fixedToolbarButtonParams())
            utilityToolbar.addView(emojiToolbarButton, fixedToolbarButtonParams())
            utilityToolbar.addView(languageToolbarButton, fixedToolbarButtonParams())
            suggestionToolbar.addView(utilityToolbar, LinearLayout.LayoutParams(
                0, LayoutParams.MATCH_PARENT
            ))
            toolbar.addView(suggestionToolbar, FrameLayout.LayoutParams.MATCH_PARENT, toolbarHeight)
            addView(toolbar, LayoutParams.MATCH_PARENT, toolbarHeight)
            keyboardContent.addView(keyboardView, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            keyboardContent.addView(emojiPanel, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            keyboardContent.addView(clipboardPanel, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            keyboardContent.addView(textEditPanel, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            emojiPanel.visibility = View.GONE
            addView(keyboardContent, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            dragHandle.contentDescription = getString(R.string.keyboard_mode_floating)
            dragHandle.visibility = View.GONE
            addView(dragHandle, LayoutParams.MATCH_PARENT, dragHandleHeight)
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    // Docked, only the top corners round: the outline runs past the bottom edge.
                    val radius = if (floating) dp(16) else dp(12)
                    val bottom = if (floating) view.height else view.height + radius
                    outline.setRoundRect(0, 0, view.width, bottom, radius.toFloat())
                }
            }
            clipToOutline = true
            ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
                val bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                    .takeIf { it > 0 } ?: imeNavigationBarHeight()
                if (navigationBottomInset != bottom) {
                    navigationBottomInset = bottom
                    requestLayout()
                }
                insets
            }
            keyboardView.setOnTouchListener { _, event -> handleSpaceCursorTouch(event) }
            keyboardView.setOnKeyboardChangedListener { previous, keyboard ->
                // Only letters <-> symbols: shift changes and reloads keep the same page.
                if (previous != null && !isEmojiSearchActive &&
                    previous.mId.mWidth == keyboard.mId.mWidth &&
                    previous.mId.mHeight == keyboard.mId.mHeight &&
                    previous.mId.isAlphabetKeyboard != keyboard.mId.isAlphabetKeyboard
                ) KeyboardMotion.layoutSwitch(keyboardView)
            }
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
                    if (key?.code == Constants.CODE_DELETE) {
                        resetSpaceCursor()
                        deleteSwipeKey = key
                        spaceCursorLastX = event.x.toInt()
                        spaceCursorDistance = 0
                        return false
                    }
                    if (key?.code == Constants.CODE_SPACE) {
                        spaceCursorKey = key
                        spaceCursorLastX = event.x.toInt()
                        spaceCursorDownTime = event.downTime
                        spaceCursorDistance = 0
                    } else {
                        resetSpaceCursor()
                    }
                    return false
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    if (deleteSwipeKey != null && event.pointerCount == 1) {
                        return handleDeleteSwipeMove(event.x.toInt())
                    }
                    if (spaceCursorKey == null || event.pointerCount != 1) return false
                    val x = event.x.toInt()
                    spaceCursorDistance += x - spaceCursorLastX
                    spaceCursorLastX = x
                    if (spacebarLanguageGesture(event.eventTime) &&
                        !spaceCursorActive && !spaceLanguageActive &&
                        kotlin.math.abs(spaceCursorDistance) >= spaceCursorTrigger
                    ) {
                        keyboardView.cancelAllOngoingEvents()
                        cycleLanguage(if (spaceCursorDistance > 0) 1 else -1)
                        keyFeedback.vibrate()
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
                    if (deleteSwipeActive) {
                        finishDeleteSwipe()
                        resetSpaceCursor()
                        return true
                    }
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
                    // A cancelled swipe keeps the text: the selection is just collapsed back.
                    if (deleteSwipeActive) {
                        selectWordsForDelete(0)
                        deleteSwipeText = null
                    }
                    val wasActive = spaceCursorActive || spaceLanguageActive || deleteSwipeActive
                    resetSpaceCursor()
                    return wasActive
                }
            }
            return false
        }

        // Sliding left from Backspace selects whole words; lifting the finger deletes them.
        private fun handleDeleteSwipeMove(x: Int): Boolean {
            spaceCursorDistance += x - spaceCursorLastX
            spaceCursorLastX = x
            if (!deleteSwipeActive) {
                if (spaceCursorDistance > -spaceCursorTrigger) return false
                keyboardView.cancelAllOngoingEvents()
                if (!beginDeleteSwipe()) {
                    deleteSwipeKey = null
                    return true
                }
                deleteSwipeActive = true
                deleteSwipeWords = 0
            }
            // Sliding back toward Backspace gives words back; past the start selects none.
            val travelled = -spaceCursorDistance - spaceCursorTrigger
            val words = if (travelled < 0) 0 else travelled / deleteSwipeStep + 1
            if (words != deleteSwipeWords) {
                val before = deleteSwipeWords
                deleteSwipeWords = words
                if (selectWordsForDelete(words) > 0 || before > 0) keyFeedback.vibrate()
            }
            return true
        }

        val isEmojiSearchActive: Boolean
            get() = emojiPanel.isSearchActive()

        fun appendEmojiSearchCodePoint(codePoint: Int) = emojiPanel.appendSearchCodePoint(codePoint)

        fun appendEmojiSearchText(text: String) = emojiPanel.appendSearchText(text)

        fun deleteEmojiSearchCodePoint() = emojiPanel.deleteSearchCodePoint()

        fun showEmojiPanel() {
            if (clipboardPanel.visibility == View.VISIBLE) hideClipboardPanel()
            hideTextEditPanel()
            restoreEmojiLayout()
            emojiPanel.exitSearch()
            keyboardView.visibility = View.GONE
            emojiPanel.visibility = View.VISIBLE
            toolbar.visibility = View.GONE
            KeyboardMotion.panelIn(emojiPanel)
            requestLayout()
        }

        fun handleBack(): Boolean {
            if (emojiPanel.visibility == View.VISIBLE) {
                if (emojiPanel.handleBack()) return true
                hideEmojiPanel()
                return true
            }
            return hideClipboardPanel() || hideTextEditPanel()
        }

        private fun showEmojiSearchLayout() {
            keyboardView.visibility = View.VISIBLE
            toolbar.visibility = View.GONE
            // The letters rise into place under the search strip instead of popping in.
            KeyboardMotion.panelIn(keyboardView)
            requestLayout()
            reloadKeyboardAfterLayout()
        }

        private fun showEmojiCategoryLayout() {
            keyboardView.animate().cancel()
            keyboardView.alpha = 1f
            keyboardView.translationY = 0f
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
            KeyboardMotion.panelIn(keyboardView)
            KeyboardMotion.fadeIn(toolbar)
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

        // A flick that crosses the trigger soon after touch-down switches language; a slower
        // drag (finger rested first) falls through to cursor movement.
        private fun spacebarLanguageGesture(eventTime: Long): Boolean =
            eventTime - spaceCursorDownTime < SPACE_LANGUAGE_FLICK_MS &&
                KaruikeyPreferences.spacebarLanguageSwipe(this@KaruikeyService) &&
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
            deleteSwipeKey = null
            deleteSwipeActive = false
            deleteSwipeWords = 0
        }

        fun applyPreferences() {
            applySuggestionPosition()
            keyboardView.setFunctionalKeyTint(
                if (KaruikeyPreferences.accentFunctionKeys(this@KaruikeyService)) {
                    ColorStateList(
                        arrayOf(
                            intArrayOf(android.R.attr.state_pressed),
                            intArrayOf(android.R.attr.state_checked),
                            intArrayOf()
                        ),
                        intArrayOf(
                            appearance.pressedSurface,
                            appearance.shiftLockedSurface,
                            ColorUtils.blendARGB(
                                appearance.functionalKeySurface, appearance.shiftLockedSurface, 0.6f
                            )
                        )
                    )
                } else null
            )
            keyboardView.setKeyBackgroundAlpha(
                KaruikeyPreferences.keyBackgroundAlpha(this@KaruikeyService)
            )
            val service = this@KaruikeyService
            keyboardView.setKeyPressStyle(when (KaruikeyPreferences.keyPressStyle(service)) {
                KaruikeyPreferences.KEY_PRESS_OFF -> KeyboardView.KEY_PRESS_OFF
                KaruikeyPreferences.KEY_PRESS_BOUNCE -> KeyboardView.KEY_PRESS_BOUNCE
                else -> KeyboardView.KEY_PRESS_MORPH
            })
            keyboardView.setKeyShape(
                dp(KaruikeyPreferences.keyCornerRadius(service)).toFloat(),
                dp(KaruikeyPreferences.keyGap(service))
            )
            keyboardView.setLabelScale(KaruikeyPreferences.labelScale(service) / 100f)
            // Panels own the toolbar slot; a preference write (e.g. a language swipe) must not
            // bring the toolbar back over them.
            if (clipboardPanel.visibility != View.VISIBLE && emojiPanel.visibility != View.VISIBLE &&
                textEditPanel.visibility != View.VISIBLE
            ) {
                toolbar.visibility = if (KaruikeyPreferences.toolbarEnabled(this@KaruikeyService)) {
                    View.VISIBLE
                } else View.GONE
            }
            emojiToolbarButton.visibility = if (
                emojiKeyAllowed() && KaruikeyPreferences.emojiKeyPlacement(this@KaruikeyService) ==
                    KaruikeyPreferences.EMOJI_PLACEMENT_TOOLBAR
            ) View.VISIBLE else View.GONE
            modeToolbarButton.setImageResource(
                keyboardModeIcon(KaruikeyPreferences.keyboardMode(this@KaruikeyService))
            )
            languageToolbarButton.visibility = if (
                KaruikeyPreferences.enabledLanguages(this@KaruikeyService).size > 1
            ) View.VISIBLE else View.GONE
            applySurfaceBackground(surfaceBackgroundColor())
            keyboardView.setKeyPreviewPopupEnabled(
                KaruikeyPreferences.keyPreviewEnabled(this@KaruikeyService),
                500
            )
            requestLayout()
        }

        // Blur only exposes a radius, so grain and contrast are layered over the surface color:
        // a dark or light veil, then tiled monochrome noise.
        private fun surfaceDrawable(color: Int): Drawable {
            val service = this@KaruikeyService
            KeyboardBackground.bitmap(service)?.let { picture ->
                // A black veil at the chosen strength keeps labels readable over busy pictures.
                val dim = KaruikeyPreferences.backgroundDim(service) * 255 / 100
                return LayerDrawable(arrayOf(
                    ColorDrawable(appearance.keyboardBackground),
                    CenterCropDrawable(picture),
                    ColorDrawable(Color.argb(dim, 0, 0, 0))
                ))
            }
            if (!KaruikeyPreferences.blurActive(service)) return ColorDrawable(color)
            val contrast = KaruikeyPreferences.blurContrast(service)
            val grain = KaruikeyPreferences.blurGrain(service)
            val veilAlpha = abs(contrast) * 255 / 100
            val veil = ColorDrawable(if (contrast > 0) Color.argb(veilAlpha, 0, 0, 0) else Color.argb(veilAlpha, 255, 255, 255))
            val noise = BitmapDrawable(resources, grainBitmap).apply {
                setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
                alpha = grain * 90 / 100
            }
            return LayerDrawable(arrayOf(ColorDrawable(color), veil, noise))
        }

        private val grainBitmap by lazy {
            val size = 128
            val random = java.util.Random(7)
            val pixels = IntArray(size * size) {
                val light = random.nextBoolean()
                Color.argb(random.nextInt(256), if (light) 255 else 0, if (light) 255 else 0, if (light) 255 else 0)
            }
            Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
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
            background = surfaceDrawable(color)
            keyboardView.setBackgroundColor(Color.TRANSPARENT)
            toolbar.setBackgroundColor(Color.TRANSPARENT)
            clipboardPanel.setBackgroundColor(Color.TRANSPARENT)
        }

        fun showTextEditPanel() {
            hideClipboardPanel()
            toolbar.visibility = View.GONE
            keyboardView.visibility = View.GONE
            textEditPanel.show()
            KeyboardMotion.panelIn(textEditPanel)
            requestLayout()
        }

        fun hideTextEditPanel(): Boolean {
            if (textEditPanel.visibility != View.VISIBLE) return false
            textEditPanel.reset()
            keyboardView.visibility = View.VISIBLE
            KeyboardMotion.panelIn(keyboardView)
            toolbar.visibility = if (KaruikeyPreferences.toolbarEnabled(this@KaruikeyService)) {
                View.VISIBLE
            } else View.GONE
            requestLayout()
            return true
        }

        fun showClipboard(
            items: List<ClipboardHistoryItem>,
            emptyMessage: Int,
            historyEnabled: Boolean
        ) {
            hideTextEditPanel()
            toolbar.visibility = View.GONE
            keyboardView.visibility = View.GONE
            clipboardPanel.show(items, emptyMessage, historyEnabled)
            KeyboardMotion.panelIn(clipboardPanel)
            requestLayout()
        }

        private fun pasteClipboard(text: String) {
            finishEditorComposition(currentInputConnection)
            duringEditorUpdate { currentInputConnection?.commitText(text, 1) }
            clearComposingWord()
        }

        fun hideClipboardPanel(): Boolean {
            if (clipboardPanel.visibility != View.VISIBLE) return false
            clipboardPanel.reset()
            keyboardView.visibility = View.VISIBLE
            KeyboardMotion.panelIn(keyboardView)
            toolbar.visibility = if (KaruikeyPreferences.toolbarEnabled(this@KaruikeyService)) {
                View.VISIBLE
            } else View.GONE
            setSuggestions(suggestionResults, suggestionSession.prefix.toString())
            if (!KaruikeyPreferences.toolbarEnabled(this@KaruikeyService)) toolbar.visibility = View.GONE
            requestLayout()
            return true
        }

        private var suggestionPosition = KaruikeyPreferences.SUGGESTION_POSITION_FULL

        private fun applySuggestionPosition() {
            val position = KaruikeyPreferences.suggestionPosition(this@KaruikeyService)
            suggestionPosition = position
            // Candidates on the right means the icons come first.
            val first = if (position == KaruikeyPreferences.SUGGESTION_POSITION_RIGHT) utilityToolbar
                else candidateRegion
            if (suggestionToolbar.getChildAt(0) !== first) {
                val second = suggestionToolbar.getChildAt(0)
                suggestionToolbar.removeView(second)
                suggestionToolbar.addView(second)
            }
            val typing = candidateRegion.visibility == View.VISIBLE
            if (position == KaruikeyPreferences.SUGGESTION_POSITION_FULL) {
                if (candidateRegion.visibility == View.INVISIBLE) candidateRegion.visibility = View.GONE
                utilityToolbar.visibility = if (typing) View.GONE else View.VISIBLE
            } else {
                // The candidate side keeps its space even before typing.
                if (!typing) candidateRegion.visibility = View.INVISIBLE
                utilityToolbar.visibility = View.VISIBLE
            }
            requestLayout()
        }

        private fun showCandidates(show: Boolean) {
            val shown = candidateRegion.visibility == View.VISIBLE
            if (show == shown) return
            if (suggestionPosition != KaruikeyPreferences.SUGGESTION_POSITION_FULL) {
                // Side mode: the icons stay packed on their side; only the candidates fade.
                candidateRegion.visibility = if (show) View.VISIBLE else View.INVISIBLE
                if (show) KeyboardMotion.fadeIn(candidateRegion)
                return
            }
            val incoming = if (show) candidateRegion else utilityToolbar
            (if (show) utilityToolbar else candidateRegion).visibility = View.GONE
            incoming.visibility = View.VISIBLE
            KeyboardMotion.fadeIn(incoming)
        }

        /** A slot stays tappable while newer results load if its word still fits the prefix. */
        private fun candidateMatchesInput(index: Int, word: String): Boolean {
            val prefix = suggestionSession.prefix.toString()
            if (candidateQueries[index] == prefix) return true
            return prefix.isNotEmpty() && word.startsWith(prefix, ignoreCase = true)
        }

        private fun installCandidateListeners(index: Int, candidate: TextView) {
            candidate.setOnClickListener {
                val word = candidate.tag as? String
                if (word != null && candidateMatchesInput(index, word)) commitSuggestion(word)
            }
            candidate.setOnLongClickListener {
                val word = candidate.tag as? String ?: return@setOnLongClickListener false
                if (!candidateMatchesInput(index, word)) return@setOnLongClickListener false
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

        fun setSuggestions(suggestions: List<String>, query: String) {
            candidateViews.forEachIndexed { index, candidate ->
                // Slots stay VISIBLE with fixed geometry; only changed text is touched.
                val text = suggestions.getOrNull(index)
                if (candidate.tag != text) {
                    candidate.text = text
                    candidate.tag = text
                    candidate.isEnabled = text != null
                    if (text != null && candidateRegion.visibility == View.VISIBLE) {
                        KeyboardMotion.candidateChanged(candidate)
                    }
                }
                candidateQueries[index] = if (text != null) query else null
            }
            // Keep the candidates up for the whole word so pending results do not flicker icons.
            showCandidates(suggestions.isNotEmpty() || query.isNotEmpty())
        }

        private fun showKeyboardModeMenu() {
            showKeyboardModeMenu(
                keyboardContext, modeToolbarButton, appearance,
                keyboardTypeface, primarySuggestionTypeface
            ) { mode ->
                KaruikeyPreferences.setKeyboardMode(this@KaruikeyService, mode)
                modeToolbarButton.setImageResource(keyboardModeIcon(mode))
                reloadKeyboardAfterLayout()
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
                // Material 3 icon buttons keep a 24dp glyph inside the larger touch target.
                val inset = ((toolbarHeight - dp(24)) / 2).coerceAtLeast(0)
                setPadding(inset, inset, inset, inset)
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                setOnClickListener { action() }
                layoutParams = fixedToolbarButtonParams()
            }

        // The taskbar hands IME windows a zero navigation inset while still drawing its back and
        // switcher buttons over them, so reserve the system's IME navigation bar height ourselves.
        private fun imeNavigationBarHeight(): Int {
            if (android.os.Build.VERSION.SDK_INT < 35) return 0
            val system = android.content.res.Resources.getSystem()
            val id = system.getIdentifier("navigation_bar_frame_height", "dimen", "android")
            return if (id != 0) system.getDimensionPixelSize(id) else 0
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
            ) * KaruikeyPreferences.heightPercent(this@KaruikeyService) / 100 *
                (if (floating) 85 else 100) / 100
            val visibleToolbarHeight = if (toolbar.visibility == View.VISIBLE) toolbarHeight else 0
            // Emoji browsing takes the toolbar's place as well; search adds its strip above the
            // full-size keyboard instead of squeezing the keys.
            val emojiShown = emojiPanel.visibility == View.VISIBLE
            val searchStrip = if (emojiShown && emojiPanel.isSearchActive()) {
                emojiPanel.searchStripHeight
            } else 0
            // On tablets the emoji browser grows above the keyboard like Gboard's, so several
            // rows of emoji fit instead of one and a half.
            val emojiTall = if (resources.configuration.smallestScreenWidthDp >= 600) {
                defaultKeyboardHeight * 3 / 5
            } else 0
            val emojiExtra = if (searchStrip > 0) searchStrip
                else if (emojiShown) toolbarHeight + emojiTall else 0
            val desiredHeight = defaultKeyboardHeight + visibleToolbarHeight + emojiExtra +
                bottomInset
            val height = when (MeasureSpec.getMode(heightMeasureSpec)) {
                MeasureSpec.EXACTLY -> MeasureSpec.getSize(heightMeasureSpec)
                MeasureSpec.AT_MOST -> desiredHeight.coerceAtMost(MeasureSpec.getSize(heightMeasureSpec))
                else -> desiredHeight
            }
            val keyboardHeight = (height - visibleToolbarHeight - bottomInset).coerceAtLeast(1)
            setMeasuredDimension(width, height)
            val utilityButtons = (0 until utilityToolbar.childCount).map(utilityToolbar::getChildAt)
                .filter { it.visibility != View.GONE }
            val utilityEdge = dp(8)
            // Side mode: icons always pack tightly on one side, candidates take the rest.
            val sideCandidates = suggestionPosition != KaruikeyPreferences.SUGGESTION_POSITION_FULL
            val packedGap = dp(2)
            val utilityWidth = if (sideCandidates) {
                2 * utilityEdge + utilityButtons.size * toolbarHeight +
                    (utilityButtons.size - 1).coerceAtLeast(0) * packedGap
            } else width
            val candidateWidth = if (sideCandidates) (width - utilityWidth).coerceAtLeast(0) else width
            candidateRegion.setWidthIfChanged(candidateWidth)
            utilityToolbar.setWidthIfChanged(utilityWidth)
            // Two candidates on a side; a third only when each still gets a comfortable width.
            val slots = if (sideCandidates && candidateWidth < 3 * dp(SIDE_CANDIDATE_MIN_DP)) 2 else 3
            val order = if (slots == 2) intArrayOf(0, 1) else CANDIDATE_DISPLAY_ORDER
            candidateViews.forEachIndexed { index, candidate ->
                val slot = order.indexOf(index)
                if (slot < 0) {
                    if (candidate.visibility != View.GONE) candidate.visibility = View.GONE
                } else {
                    if (candidate.visibility != View.VISIBLE) candidate.visibility = View.VISIBLE
                    val base = candidateWidth / slots
                    candidate.setWidthIfChanged(
                        if (slot == slots - 1) candidateWidth - base * (slots - 1) else base
                    )
                }
            }
            if (order.indices.any { candidateRegion.getChildAt(it) !== candidateViews[order[it]] }) {
                candidateRegion.removeAllViews()
                order.forEach { candidateRegion.addView(candidateViews[it]) }
                candidateViews.filterIndexed { i, _ -> i !in order }.forEach(candidateRegion::addView)
            }
            // Square icon buttons spread edge to edge, like Gboard: hidden buttons leave no gap and
            // the pressed ripple stays a circle instead of a stretched pill.
            val utilityGap = if (sideCandidates) packedGap else if (utilityButtons.size > 1) {
                (width - 2 * utilityEdge - utilityButtons.size * toolbarHeight) / (utilityButtons.size - 1)
            } else 0
            utilityButtons.forEachIndexed { index, button ->
                button.setWidthIfChanged(toolbarHeight)
                val params = button.layoutParams as LinearLayout.LayoutParams
                val start = if (index == 0) utilityEdge else utilityGap.coerceAtLeast(0)
                if (params.leftMargin != start) {
                    params.leftMargin = start
                    button.layoutParams = params
                }
            }
            if (searchStrip > 0) {
                (emojiPanel.layoutParams as FrameLayout.LayoutParams).apply {
                    this.width = LayoutParams.MATCH_PARENT
                    this.height = searchStrip
                    topMargin = 0
                }.also { emojiPanel.layoutParams = it }
                (keyboardView.layoutParams as FrameLayout.LayoutParams).apply {
                    this.width = LayoutParams.MATCH_PARENT
                    this.height = (keyboardHeight - searchStrip).coerceAtLeast(1)
                    topMargin = searchStrip
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
            dragHandle.measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dragHandleHeight, MeasureSpec.EXACTLY)
            )
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            val visibleToolbarHeight = if (toolbar.visibility == View.VISIBLE) toolbarHeight else 0
            toolbar.layout(0, 0, width, visibleToolbarHeight)
            keyboardContent.layout(0, visibleToolbarHeight, width, height - bottomInset)
            dragHandle.layout(0, height - dragHandleHeight, width, height)
        }

        override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
            super.onSizeChanged(width, height, oldWidth, oldHeight)
            loadKeyboardIfMeasured()
        }
    }
}

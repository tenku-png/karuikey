package tenkupng.karuikey

import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.text.TextUtils
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodSubtype
import android.widget.FrameLayout
import com.android.inputmethod.accessibility.AccessibilityUtils
import com.android.inputmethod.compat.InputMethodSubtypeCompatUtils
import com.android.inputmethod.event.Event
import com.android.inputmethod.keyboard.KeyboardActionListener
import com.android.inputmethod.keyboard.KeyboardSwitcher
import com.android.inputmethod.keyboard.MainKeyboardView
import com.android.inputmethod.latin.common.Constants
import com.android.inputmethod.latin.common.StringUtils
import com.android.inputmethod.latin.utils.SubtypeLocaleUtils

class KaruikeyService : InputMethodService() {
    private var inputView: KaruikeyInputView? = null
    private var keyboardSwitcher: KeyboardSwitcher? = null
    private var editorInfo: EditorInfo? = null
    private var currentSubtype: InputMethodSubtype? = null
    private var loadedWidth = 0
    private var loadedHeight = 0

    override fun onCreate() {
        AccessibilityUtils.init(this)
        SubtypeLocaleUtils.init(this)
        super.onCreate()
    }

    private val keyboardActionListener = object : KeyboardActionListener.Adapter() {
        override fun onPressKey(primaryCode: Int, repeatCount: Int, isSinglePointer: Boolean) {
            keyboardSwitcher?.onPressKey(primaryCode, isSinglePointer, autoCapsMode())
        }

        override fun onReleaseKey(primaryCode: Int, withSliding: Boolean) {
            keyboardSwitcher?.onReleaseKey(primaryCode, withSliding, autoCapsMode())
        }

        override fun onCodeInput(
            primaryCode: Int,
            x: Int,
            y: Int,
            isKeyRepeat: Boolean
        ) {
            val connection = currentInputConnection
            when (primaryCode) {
                Constants.CODE_DELETE -> connection?.deleteSurroundingText(1, 0)
                Constants.CODE_SPACE -> connection?.commitText(" ", 1)
                Constants.CODE_ENTER -> if (connection != null) sendEnter(connection)
                Constants.CODE_SHIFT,
                Constants.CODE_CAPSLOCK,
                Constants.CODE_SWITCH_ALPHA_SYMBOL -> Unit
                else -> if (primaryCode > 0) {
                    connection?.commitText(StringUtils.newSingleCodePointString(primaryCode), 1)
                }
            }
            keyboardSwitcher?.onEvent(
                createKeyEvent(primaryCode, x, y, isKeyRepeat),
                autoCapsMode()
            )
        }

        override fun onTextInput(text: String) {
            currentInputConnection?.commitText(text, 1)
            keyboardSwitcher?.onEvent(
                Event.createSoftwareTextEvent(text, Constants.CODE_OUTPUT_TEXT),
                autoCapsMode()
            )
        }

        override fun onFinishSlidingInput() {
            keyboardSwitcher?.onFinishSlidingInput(autoCapsMode())
        }
    }

    override fun onCreateInputView(): View {
        val view = KaruikeyInputView()
        inputView = view
        keyboardSwitcher = view.keyboardSwitcher
        return view
    }

    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        editorInfo = attribute
        if (currentSubtype == null) currentSubtype = englishSubtype()
        loadedWidth = 0
        loadedHeight = 0
        keyboardSwitcher?.resetForNewInput()
    }

    override fun onStartInputView(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInputView(attribute, restarting)
        editorInfo = attribute
        if (currentSubtype == null) currentSubtype = englishSubtype()
        loadKeyboardIfMeasured()
    }

    override fun onCurrentInputMethodSubtypeChanged(newSubtype: InputMethodSubtype) {
        super.onCurrentInputMethodSubtypeChanged(newSubtype)
        currentSubtype = newSubtype
        if (editorInfo != null) {
            keyboardSwitcher?.resetForNewInput()
            loadedWidth = 0
            loadedHeight = 0
            loadKeyboardIfMeasured()
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        keyboardSwitcher?.closing()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        keyboardSwitcher?.closing()
        keyboardSwitcher?.resetForNewInput()
        editorInfo = null
        loadedWidth = 0
        loadedHeight = 0
    }

    override fun onWindowHidden() {
        keyboardSwitcher?.onHideWindow()
        keyboardSwitcher?.closing()
        super.onWindowHidden()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        keyboardSwitcher?.closing()
        loadedWidth = 0
        loadedHeight = 0
        super.onConfigurationChanged(newConfig)
        loadKeyboardIfMeasured()
    }

    override fun onDestroy() {
        keyboardSwitcher?.closing()
        keyboardSwitcher?.deallocateMemory()
        inputView = null
        keyboardSwitcher = null
        editorInfo = null
        super.onDestroy()
    }

    private fun loadKeyboardIfMeasured() {
        val view = inputView ?: return
        if (view.width <= 0 || view.height <= 0) {
            view.post { loadKeyboardIfMeasured() }
            return
        }
        if (view.width == loadedWidth && view.height == loadedHeight) return
        val editor = editorInfo ?: return
        val subtype = currentSubtype ?: englishSubtype()
        keyboardSwitcher?.loadKeyboard(editor, subtype, view.width, view.height, autoCapsMode())
        loadedWidth = view.width
        loadedHeight = view.height
    }

    private fun autoCapsMode(): Int {
        val capsModes = TextUtils.CAP_MODE_CHARACTERS or
            TextUtils.CAP_MODE_WORDS or TextUtils.CAP_MODE_SENTENCES
        return currentInputConnection?.getCursorCapsMode(capsModes) ?: 0
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

    private fun englishSubtype(): InputMethodSubtype =
        InputMethodSubtypeCompatUtils.newInputMethodSubtype(
            0,
            0,
            "en_US",
            Constants.Subtype.KEYBOARD_MODE,
            "KeyboardLayoutSet=qwerty,AsciiCapable",
            false,
            false,
            1
        )

    private inner class KaruikeyInputView : FrameLayout(this@KaruikeyService) {
        val keyboardView = MainKeyboardView(context, null).also {
            it.setKeyboardActionListener(keyboardActionListener)
        }
        val keyboardSwitcher = KeyboardSwitcher(context, keyboardView)
        private val density = resources.displayMetrics.density

        init {
            addView(
                keyboardView,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val desiredHeight = resources.getDimensionPixelSize(
                R.dimen.config_default_keyboard_height
            ).coerceAtLeast((205 * density).toInt())
            val height = when (MeasureSpec.getMode(heightMeasureSpec)) {
                MeasureSpec.EXACTLY -> MeasureSpec.getSize(heightMeasureSpec)
                MeasureSpec.AT_MOST -> desiredHeight.coerceAtMost(MeasureSpec.getSize(heightMeasureSpec))
                else -> desiredHeight
            }
            setMeasuredDimension(width, height)
            keyboardView.measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
            )
        }

        override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
            super.onSizeChanged(width, height, oldWidth, oldHeight)
            loadKeyboardIfMeasured()
        }
    }
}

package tenkupng.karuikey

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

internal enum class TextEditAction {
    LEFT, RIGHT, UP, DOWN, HOME, END, SELECT_ALL, COPY, CUT, PASTE, UNDO, REDO
}

/**
 * Cursor pad and editing actions shown in place of the keys. While Select is on, cursor moves
 * extend the selection.
 */
internal class TextEditPanel(
    context: Context,
    private val appearance: KeyboardAppearance,
    typeface: Typeface,
    private val buttonSize: Int,
    private val onBack: () -> Unit,
    private val onAction: (action: TextEditAction, selecting: Boolean) -> Unit
) : LinearLayout(context) {
    var selecting = false
        private set
    private val grid = GridLayout(context).apply {
        columnCount = 4
        rowCount = 4
        useDefaultMargins = false
    }
    private val buttons = ArrayList<View>()
    private lateinit var selectButton: View

    init {
        orientation = VERTICAL
        setPadding(context.dp(16), context.dp(8), context.dp(16), context.dp(12))
        visibility = View.GONE
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(ImageButton(context).apply {
            contentDescription = context.getString(R.string.clipboard_back)
            setImageResource(R.drawable.ic_settings_back)
            imageTintList = ColorStateList.valueOf(appearance.primaryText)
            setBackgroundResource(R.drawable.keyboard_toolbar_button_background)
            setOnClickListener { onBack() }
        }, LayoutParams(buttonSize, buttonSize))
        header.addView(TextView(context).apply {
            text = context.getString(R.string.toolbar_text_edit)
            textSize = 16f
            this.typeface = typeface
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(appearance.primaryText)
            setPadding(context.dp(12), 0, 0, 0)
        }, LayoutParams(0, buttonSize, 1f))
        addView(header)
        addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        // Cursor pad on the left three columns, clipboard actions down the right one.
        add(0, 0, R.drawable.ic_edit_home, R.string.edit_home, TextEditAction.HOME)
        add(0, 1, R.drawable.ic_edit_up, R.string.edit_up, TextEditAction.UP)
        add(0, 2, R.drawable.ic_edit_end, R.string.edit_end, TextEditAction.END)
        add(0, 3, R.drawable.ic_edit_select_all, R.string.edit_select_all, TextEditAction.SELECT_ALL)
        add(1, 0, R.drawable.ic_edit_left, R.string.edit_left, TextEditAction.LEFT)
        selectButton = add(1, 1, R.drawable.ic_edit_select, R.string.edit_select, null)
        add(1, 2, R.drawable.ic_edit_right, R.string.edit_right, TextEditAction.RIGHT)
        add(1, 3, R.drawable.ic_edit_copy, R.string.edit_copy, TextEditAction.COPY)
        add(2, 0, R.drawable.ic_edit_undo, R.string.edit_undo, TextEditAction.UNDO)
        add(2, 1, R.drawable.ic_edit_down, R.string.edit_down, TextEditAction.DOWN)
        add(2, 2, R.drawable.ic_edit_redo, R.string.edit_redo, TextEditAction.REDO)
        add(2, 3, R.drawable.ic_edit_cut, R.string.edit_cut, TextEditAction.CUT)
        add(3, 3, R.drawable.ic_keyboard_clipboard, R.string.clipboard_paste, TextEditAction.PASTE)
    }

    private fun add(row: Int, column: Int, icon: Int, description: Int, action: TextEditAction?): View {
        val button = ImageView(context).apply {
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(appearance.primaryText)
            scaleType = ImageView.ScaleType.CENTER
            contentDescription = context.getString(description)
            isClickable = true
            isFocusable = true
            setBackgroundResource(R.drawable.keyboard_clipboard_item_background)
            background.mutate().alpha = KaruikeyPreferences.keyBackgroundAlpha(context)
            if (action == null) {
                setOnClickListener { setSelecting(!selecting) }
            } else {
                setOnClickListener {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onAction(action, selecting)
                    // Clipboard actions end a selection, as they do in text field menus.
                    if (action in setOf(TextEditAction.COPY, TextEditAction.CUT,
                            TextEditAction.PASTE, TextEditAction.SELECT_ALL)) setSelecting(false)
                }
                if (action in setOf(TextEditAction.LEFT, TextEditAction.RIGHT,
                        TextEditAction.UP, TextEditAction.DOWN)) {
                    setOnTouchListener(RepeatTouch { onAction(action, selecting) })
                }
            }
        }
        grid.addView(button, GridLayout.LayoutParams(
            GridLayout.spec(row, 1f), GridLayout.spec(column, 1f)
        ).apply {
            width = 0
            height = 0
            setMargins(context.dp(4), context.dp(4), context.dp(4), context.dp(4))
        })
        buttons.add(button)
        return button
    }

    private fun setSelecting(value: Boolean) {
        selecting = value
        selectButton.isActivated = value
    }

    fun show() {
        setSelecting(false)
        visibility = View.VISIBLE
    }

    fun reset() {
        setSelecting(false)
        visibility = View.GONE
    }

    /** Holding an arrow keeps moving the cursor, like a hardware key. */
    private class RepeatTouch(private val step: () -> Unit) : OnTouchListener {
        private var view: View? = null
        private val repeat = object : Runnable {
            override fun run() {
                step()
                view?.postDelayed(this, 50)
            }
        }

        override fun onTouch(v: View, event: android.view.MotionEvent): Boolean {
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    view = v
                    v.isPressed = true
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    step()
                    v.postDelayed(repeat, 400)
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    v.removeCallbacks(repeat)
                    view = null
                }
            }
            return true
        }
    }
}

package tenkupng.karuikey

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Clipboard history cards with a select-and-delete mode, shown in place of the keys. */
internal class ClipboardPanel(
    context: Context,
    private val appearance: KeyboardAppearance,
    private val typeface: Typeface,
    private val buttonSize: Int,
    private val onBack: () -> Unit,
    private val onPaste: (String) -> Unit
) : LinearLayout(context) {
    private var history: List<ClipboardHistoryItem> = emptyList()
    private var emptyMessage = R.string.clipboard_history_empty
    private val selected = HashSet<Long>()
    private var editMode = false
    private var historyEnabled = false

    private val headerTitle = TextView(context).apply {
        text = context.getString(R.string.toolbar_clipboard)
        textSize = 16f
        typeface = this@ClipboardPanel.typeface
        gravity = Gravity.CENTER_VERTICAL
        setTextColor(appearance.primaryText)
        setPadding(context.dp(12), 0, 0, 0)
    }
    private val status = TextView(context).apply {
        textSize = 12f
        typeface = this@ClipboardPanel.typeface
        gravity = Gravity.CENTER_VERTICAL
        setTextColor(appearance.secondaryText)
        setPadding(context.dp(8), 0, context.dp(4), 0)
    }
    private val edit = iconButton(R.drawable.ic_settings_edit, R.string.clipboard_edit) {
        editMode = !editMode
        selected.clear()
        render()
    }
    private val deleteSelected = iconButton(
        R.drawable.ic_settings_delete, R.string.clipboard_delete_selected
    ) {
        selected.toList().forEach { ClipboardHistory.remove(context, it) }
        selected.clear()
        editMode = false
        history = ClipboardHistory.items(context)
        render()
    }.apply { visibility = View.GONE }
    private val items = GridLayout(context).apply {
        orientation = GridLayout.HORIZONTAL
        useDefaultMargins = false
    }
    private val scroll = ScrollView(context).apply {
        addView(items, LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    }
    private val clearAll = TextView(context).apply {
        text = context.getString(R.string.clipboard_clear_all)
        textSize = 14f
        typeface = this@ClipboardPanel.typeface
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        setTextColor(appearance.primaryText)
        setPadding(context.dp(12), context.dp(8), context.dp(12), context.dp(8))
        setBackgroundResource(R.drawable.keyboard_toolbar_button_background)
        visibility = View.GONE
        setOnClickListener {
            ClipboardHistory.clear(context, keepPinned = true)
            selected.clear()
            editMode = false
            history = ClipboardHistory.items(context)
            render()
        }
    }
    private val manageBar = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        visibility = View.GONE
    }

    init {
        orientation = VERTICAL
        setPadding(context.dp(16), context.dp(8), context.dp(16), context.dp(12))
        visibility = View.GONE
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(iconButton(R.drawable.ic_settings_back, R.string.clipboard_back) { onBack() },
            LayoutParams(buttonSize, buttonSize))
        header.addView(headerTitle, LayoutParams(0, buttonSize, 1f))
        header.addView(status, LayoutParams(LayoutParams.WRAP_CONTENT, buttonSize))
        header.addView(edit, LayoutParams(buttonSize, buttonSize))
        addView(header)
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        manageBar.addView(deleteSelected, LayoutParams(buttonSize, buttonSize))
        manageBar.addView(clearAll, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(manageBar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    private fun iconButton(icon: Int, description: Int, action: () -> Unit) =
        ImageButton(context).apply {
            contentDescription = context.getString(description)
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(appearance.primaryText)
            isClickable = true
            isFocusable = true
            setBackgroundResource(R.drawable.keyboard_toolbar_button_background)
            setOnClickListener { action() }
        }

    fun show(items: List<ClipboardHistoryItem>, emptyMessage: Int, historyEnabled: Boolean) {
        history = items
        this.historyEnabled = historyEnabled
        this.emptyMessage = emptyMessage
        selected.clear()
        editMode = false
        status.text = if (historyEnabled) {
            context.getString(R.string.clipboard_history_status, items.size)
        } else {
            context.getString(R.string.clipboard_history_off)
        }
        edit.visibility = if (historyEnabled && items.isNotEmpty()) View.VISIBLE else View.GONE
        visibility = View.VISIBLE
        render()
    }

    /** Drops the shown history so no clipboard text lingers in the view tree. */
    fun reset() {
        items.removeAllViews()
        history = emptyList()
        emptyMessage = R.string.clipboard_history_empty
        selected.clear()
        editMode = false
        edit.visibility = View.GONE
        deleteSelected.visibility = View.GONE
        manageBar.visibility = View.GONE
        clearAll.visibility = View.GONE
        visibility = View.GONE
    }

    fun render() {
        val available = availableWidth()
        val columns = if (available >= context.dp(360)) 2 else 1
        items.columnCount = columns
        items.removeAllViews()
        if (history.isEmpty()) {
            val emptyState = TextView(context).apply {
                text = context.getString(emptyMessage)
                textSize = 14f
                typeface = this@ClipboardPanel.typeface
                gravity = Gravity.CENTER
                setTextColor(appearance.secondaryText)
                setPadding(context.dp(16), context.dp(20), context.dp(16), context.dp(20))
            }
            val emptyParams = GridLayout.LayoutParams(
                GridLayout.spec(0), GridLayout.spec(0, columns)
            ).apply {
                width = available
                height = context.dp(96)
            }
            items.addView(emptyState, emptyParams)
        }
        val keyAlpha = KaruikeyPreferences.keyBackgroundAlpha(context)
        history.forEachIndexed { index, item ->
            val isSelected = selected.contains(item.timestamp)
            val card = TextView(context).apply {
                text = item.text
                textSize = 15f
                typeface = this@ClipboardPanel.typeface
                maxLines = 4
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                isFocusable = true
                isActivated = isSelected
                alpha = if (isSelected) 0.58f else 1f
                setTextColor(appearance.primaryText)
                setPadding(context.dp(12), context.dp(12), context.dp(12), context.dp(12))
                setBackgroundResource(R.drawable.keyboard_clipboard_item_background)
                background.mutate().alpha = keyAlpha
                contentDescription = context.getString(
                    if (editMode) R.string.clipboard_select_item else R.string.clipboard_paste
                )
                setOnClickListener {
                    if (editMode) {
                        if (!selected.add(item.timestamp)) selected.remove(item.timestamp)
                        render()
                    } else {
                        onPaste(item.text)
                    }
                }
                if (item.pinned) {
                    val pin = context.getDrawable(R.drawable.ic_clipboard_pin)?.mutate()?.apply {
                        setTint(appearance.secondaryText)
                        setBounds(0, 0, context.dp(16), context.dp(16))
                    }
                    setCompoundDrawablesRelative(null, null, pin, null)
                    compoundDrawablePadding = context.dp(8)
                }
                // Long press pins or unpins the item; it is then kept past retention and Clear all.
                if (historyEnabled && item.timestamp > 0) setOnLongClickListener {
                    if (editMode) return@setOnLongClickListener false
                    ClipboardHistory.setPinned(context, item.timestamp, !item.pinned)
                    history = ClipboardHistory.items(context)
                    performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                    render()
                    true
                }
                androidx.core.view.ViewCompat.setStateDescription(
                    this, if (item.pinned) context.getString(R.string.clipboard_pinned) else null
                )
            }
            val cardWidth = (available - context.dp(8) * (columns - 1)) / columns
            val params = GridLayout.LayoutParams(
                GridLayout.spec(index / columns),
                GridLayout.spec(index % columns)
            ).apply {
                width = cardWidth.coerceAtLeast(1)
                height = context.dp(72)
                setMargins(0, 0, if (index % columns == columns - 1) 0 else context.dp(8), context.dp(8))
            }
            items.addView(card, params)
        }
        val empty = history.isEmpty()
        clearAll.visibility = if (!empty && editMode) View.VISIBLE else View.GONE
        deleteSelected.visibility = if (editMode) View.VISIBLE else View.GONE
        manageBar.visibility = if (editMode && !empty) View.VISIBLE else View.GONE
        headerTitle.text = context.getString(
            if (editMode) R.string.clipboard_select_title else R.string.toolbar_clipboard
        )
    }

    // Cards are sized from the panel width, which is unknown until the first layout.
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (visibility == View.VISIBLE && w != oldw) post { render() }
    }

    private fun availableWidth(): Int = (width - paddingLeft - paddingRight).coerceAtLeast(1)
}

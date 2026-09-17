package tenkupng.karuikey

import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.view.ViewGroup

import com.android.inputmethod.latin.common.StringUtils

/** A small in-IME emoji browser. It uses system emoji glyphs and local names only. */
internal class EmojiPanel(
    private val serviceContext: android.content.Context,
    private val appearance: KeyboardAppearance,
    private val onEmojiSelected: (String) -> Unit,
    private val onClose: () -> Unit,
    private val onSearchRequested: () -> Unit,
    private val onSearchClosed: () -> Unit
) : LinearLayout(serviceContext) {
    private val dp = { value: Int -> (value * resources.displayMetrics.density).toInt() }
    private val categoryScroll = HorizontalScrollView(serviceContext)
    private val categoryRow = LinearLayout(serviceContext)
    private val variantScroll = HorizontalScrollView(serviceContext)
    private val variantRow = LinearLayout(serviceContext)
    private val searchButton = TextView(serviceContext)
    private val grid = android.widget.GridLayout(serviceContext)
    private val gridScroll = ScrollView(serviceContext)
    private var category = EmojiCategory.FACES
    private var searchMode = false
    private var query = ""
    private var columns = 0

    init {
        orientation = VERTICAL
        setBackgroundColor(android.graphics.Color.TRANSPARENT)

        val header = LinearLayout(serviceContext).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, dp(4), 0)
        }
        header.addView(ImageButton(serviceContext).apply {
            contentDescription = "Back to keyboard"
            setImageResource(tenkupng.karuikey.R.drawable.ic_settings_back)
            imageTintList = android.content.res.ColorStateList.valueOf(appearance.primaryText)
            setBackgroundResource(tenkupng.karuikey.R.drawable.keyboard_toolbar_button_background)
            setOnClickListener {
                if (searchMode) {
                    exitSearch()
                    onSearchClosed()
                } else onClose()
            }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        searchButton.apply {
            text = "Search emoji"
            textSize = 15f
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(appearance.secondaryText)
            setPadding(dp(12), 0, dp(12), 0)
            setBackgroundResource(tenkupng.karuikey.R.drawable.keyboard_toolbar_button_background)
            contentDescription = "Search emoji"
            setOnClickListener {
                if (!searchMode) {
                    searchMode = true
                    query = ""
                    categoryScroll.visibility = GONE
                    variantScroll.visibility = GONE
                    onSearchRequested()
                    render()
                }
            }
        }
        header.addView(searchButton, LinearLayout.LayoutParams(0, dp(48), 1f))
        addView(header, LinearLayout.LayoutParams.MATCH_PARENT, dp(48))

        variantRow.orientation = HORIZONTAL
        variantRow.gravity = Gravity.CENTER_VERTICAL
        variantScroll.isHorizontalScrollBarEnabled = false
        variantScroll.addView(variantRow, ViewGroup.LayoutParams.WRAP_CONTENT, dp(48))
        variantScroll.visibility = GONE
        addView(variantScroll, LinearLayout.LayoutParams.MATCH_PARENT, dp(48))

        categoryRow.orientation = HORIZONTAL
        categoryRow.gravity = Gravity.CENTER_VERTICAL
        categoryScroll.isHorizontalScrollBarEnabled = false
        categoryScroll.addView(categoryRow, ViewGroup.LayoutParams.WRAP_CONTENT, dp(44))
        addView(categoryScroll, LinearLayout.LayoutParams.MATCH_PARENT, dp(44))

        grid.columnCount = 8
        grid.alignmentMode = android.widget.GridLayout.ALIGN_BOUNDS
        grid.useDefaultMargins = false
        gridScroll.addView(grid, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        addView(gridScroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        buildCategories()
        render()
    }

    fun isSearchActive(): Boolean = searchMode

    fun handleBack(): Boolean {
        if (!searchMode) return false
        exitSearch()
        onSearchClosed()
        return true
    }

    fun appendSearchCodePoint(codePoint: Int) {
        if (codePoint <= 0 || codePoint == com.android.inputmethod.latin.common.Constants.CODE_ENTER) return
        query += StringUtils.newSingleCodePointString(codePoint)
        render()
    }

    fun appendSearchText(text: String) {
        if (text.isNotEmpty()) {
            query += text
            render()
        }
    }

    fun deleteSearchCodePoint() {
        if (query.isEmpty()) return
        val count = Character.charCount(Character.codePointBefore(query, query.length))
        query = query.dropLast(count)
        render()
    }

    fun exitSearch() {
        searchMode = false
        query = ""
        categoryScroll.visibility = VISIBLE
        variantScroll.visibility = GONE
        searchButton.text = "Search emoji"
        render()
    }

    private fun buildCategories() {
        EmojiCategory.entries.forEach { item ->
            categoryRow.addView(TextView(serviceContext).apply {
                text = item.tab
                textSize = 20f
                gravity = Gravity.CENTER
                setTextColor(appearance.primaryText)
                contentDescription = item.title
                setPadding(dp(13), 0, dp(13), 0)
                setBackgroundResource(tenkupng.karuikey.R.drawable.keyboard_toolbar_button_background)
                setOnClickListener {
                    category = item
                    variantScroll.visibility = GONE
                    render()
                }
            }, LinearLayout.LayoutParams(dp(54), dp(44)))
        }
    }

    private fun render() {
        if (searchMode) {
            searchButton.text = if (query.isEmpty()) "Type emoji name" else query
            renderEntries(EmojiCatalog.search(query))
        } else {
            searchButton.text = "Search emoji"
            renderEntries(EmojiCatalog.entries(category, serviceContext))
        }
    }

    private fun renderEntries(entries: List<EmojiEntry>) {
        if (width > 0) {
            val newColumns = (width / dp(48).coerceAtLeast(1)).coerceIn(4, 10)
            if (newColumns != columns) {
                columns = newColumns
                grid.columnCount = columns
            }
        }
        grid.removeAllViews()
        if (entries.isEmpty()) {
            grid.addView(TextView(serviceContext).apply {
                text = if (searchMode) "No matching emoji" else "No recent emoji"
                gravity = Gravity.CENTER
                setTextColor(appearance.secondaryText)
            }, android.widget.GridLayout.LayoutParams().apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = dp(72)
                columnSpec = android.widget.GridLayout.spec(0, columns.coerceAtLeast(1))
            })
            return
        }
        entries.forEach { entry ->
            grid.addView(TextView(serviceContext).apply {
                text = entry.emoji
                textSize = 26f
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                contentDescription = entry.name
                setTextColor(appearance.primaryText)
                background = GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(appearance.keySurface)
                }
                setPadding(dp(2), dp(2), dp(2), dp(2))
                setOnClickListener { select(entry.emoji) }
                setOnLongClickListener {
                    if (entry.variants.isEmpty()) return@setOnLongClickListener false
                    showVariants(entry.variants)
                    true
                }
            }, android.widget.GridLayout.LayoutParams().apply {
                width = 0
                height = dp(48)
                columnSpec = android.widget.GridLayout.spec(
                    android.widget.GridLayout.UNDEFINED, 1f
                )
                setMargins(dp(2), dp(2), dp(2), dp(2))
            })
        }
    }

    private fun showVariants(variants: List<String>) {
        variantRow.removeAllViews()
        variants.forEach { emoji ->
            variantRow.addView(TextView(serviceContext).apply {
                text = emoji
                textSize = 24f
                gravity = Gravity.CENTER
                contentDescription = "Emoji variant $emoji"
                setTextColor(appearance.primaryText)
                setBackgroundResource(tenkupng.karuikey.R.drawable.keyboard_toolbar_button_background)
                setOnClickListener { select(emoji) }
            }, LinearLayout.LayoutParams(dp(52), dp(48)))
        }
        variantScroll.visibility = VISIBLE
    }

    private fun select(emoji: String) {
        EmojiHistory.record(serviceContext, emoji)
        onEmojiSelected(emoji)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        render()
    }
}

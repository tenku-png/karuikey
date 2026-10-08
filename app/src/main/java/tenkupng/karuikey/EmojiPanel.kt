package tenkupng.karuikey

import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
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
    // Only text labels use Roboto Flex; emoji glyphs stay on the system emoji font.
    private val labelTypeface = KaruikeyTypeface.create(serviceContext, 400)
    private val grid = EmojiGridView(serviceContext, appearance, labelTypeface)
    private val tabs = HashMap<EmojiCategory, TextView>()
    private var preferredVariants: Map<String, String> = emptyMap()
    private var searchMode = false
    private var query = ""

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
            typeface = labelTypeface
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

        grid.displayFor = { entry -> preferredVariants[entry.emoji] ?: entry.emoji }
        grid.onEmojiClick = { _, emoji ->
            variantScroll.visibility = GONE
            select(emoji)
        }
        grid.onEmojiLongClick = { entry -> showVariants(entry) }
        grid.onSectionVisible = { category -> highlightTab(category) }
        addView(grid, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        buildCategories()
        render(resetScroll = true)
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
                    variantScroll.visibility = GONE
                    grid.scrollToCategory(item)
                }
            }.also { tabs[item] = it }, LinearLayout.LayoutParams(dp(54), dp(44)))
        }
    }

    private fun render(resetScroll: Boolean = false) {
        preferredVariants = EmojiHistory.preferredVariants(serviceContext)
        if (searchMode) {
            searchButton.text = if (query.isEmpty()) "Type emoji name" else query
            grid.emptyText = if (query.isEmpty()) "" else "No matching emoji"
            grid.setSections(listOf(EmojiGridView.Section(null, "",
                EmojiCatalog.search(query, serviceContext))), resetScroll = true)
        } else {
            searchButton.text = "Search emoji"
            grid.emptyText = "No emoji available"
            grid.setSections(EmojiCategory.entries.map { category ->
                EmojiGridView.Section(category, category.title,
                    EmojiCatalog.entries(category, serviceContext))
            }, resetScroll)
        }
    }

    private fun highlightTab(category: EmojiCategory?) {
        tabs.forEach { (item, tab) -> tab.alpha = if (item == category) 1f else 0.55f }
    }

    private fun showVariants(entry: EmojiEntry) {
        variantRow.removeAllViews()
        (listOf(entry.emoji) + entry.variants).forEach { emoji ->
            variantRow.addView(TextView(serviceContext).apply {
                text = emoji
                textSize = 24f
                gravity = Gravity.CENTER
                contentDescription = "Emoji variant $emoji"
                setTextColor(appearance.primaryText)
                setBackgroundResource(tenkupng.karuikey.R.drawable.keyboard_toolbar_button_background)
                setOnClickListener {
                    EmojiHistory.setPreferredVariant(serviceContext, entry.emoji, emoji)
                    variantScroll.visibility = GONE
                    select(emoji)
                    render()
                }
            }, LinearLayout.LayoutParams(dp(52), dp(48)))
        }
        variantScroll.visibility = VISIBLE
    }

    private fun select(emoji: String) {
        EmojiHistory.record(serviceContext, emoji)
        onEmojiSelected(emoji)
    }

}

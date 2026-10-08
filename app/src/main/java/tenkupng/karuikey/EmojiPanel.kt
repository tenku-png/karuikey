package tenkupng.karuikey

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView

import com.android.inputmethod.latin.common.Constants
import com.android.inputmethod.latin.common.StringUtils

/**
 * Gboard-style emoji browser: a search pill on top, one scrolling grid of every category, the
 * category tabs and an ABC / space / Backspace row at the bottom. Searching shrinks the panel to
 * the pill and a results strip above the full-size keyboard.
 */
internal class EmojiPanel(
    private val serviceContext: android.content.Context,
    private val appearance: KeyboardAppearance,
    private val onEmojiSelected: (String) -> Unit,
    private val onClose: () -> Unit,
    private val onSearchRequested: () -> Unit,
    private val onSearchClosed: () -> Unit,
    private val onKeyCode: (Int) -> Unit,
    private val onLanguageSwipe: (Int) -> Unit,
    private val languageLabel: () -> String
) : LinearLayout(serviceContext) {
    private val dp = { value: Int -> (value * resources.displayMetrics.density).toInt() }
    // Only text labels use Roboto Flex; emoji glyphs stay on the system emoji font.
    private val labelTypeface = KaruikeyTypeface.create(serviceContext, 400)
    private val mediumTypeface = KaruikeyTypeface.create(serviceContext, 500)

    private val searchBar = LinearLayout(serviceContext)
    private val searchLeading = ImageButton(serviceContext)
    private val searchText = TextView(serviceContext)
    private val searchClear = ImageButton(serviceContext)
    private val resultsScroll = HorizontalScrollView(serviceContext)
    private val resultsRow = LinearLayout(serviceContext)
    private val grid = EmojiGridView(serviceContext, appearance, mediumTypeface)
    private val tabsBar = FrameLayout(serviceContext)
    private val tabIndicator = View(serviceContext)
    private val tabsRow = LinearLayout(serviceContext)
    private val tabs = LinkedHashMap<EmojiCategory, ImageButton>()
    private val bottomRow = LinearLayout(serviceContext)
    private val spaceKey = TextView(serviceContext)
    private val deleteRepeat = object : Runnable {
        override fun run() {
            onKeyCode(Constants.CODE_DELETE)
            postDelayed(this, DELETE_REPEAT_MS)
        }
    }

    private var preferredVariants: Map<String, String> = emptyMap()
    private var activeTab: EmojiCategory? = null
    private var searchMode = false
    private var query = ""

    /** Height of the pill plus results strip that sits above the keyboard while searching. */
    val searchStripHeight: Int get() = dp(SEARCH_BAR_DP + RESULTS_DP)

    init {
        orientation = VERTICAL
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        buildSearchBar()
        addView(searchBar, LayoutParams(LayoutParams.MATCH_PARENT, dp(SEARCH_BAR_DP)))
        buildResults()
        addView(resultsScroll, LayoutParams(LayoutParams.MATCH_PARENT, dp(RESULTS_DP)))

        grid.displayFor = { entry -> preferredVariants[entry.emoji] ?: entry.emoji }
        grid.onEmojiClick = { _, emoji -> select(emoji) }
        grid.onVariantChosen = { entry, variant ->
            EmojiHistory.setPreferredVariant(serviceContext, entry.emoji, variant)
            select(variant)
            render()
        }
        grid.onSectionVisible = { category -> if (category != null) selectTab(category) }
        grid.emptyText = serviceContext.getString(R.string.emoji_no_results)
        addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        buildTabs()
        addView(tabsBar, LayoutParams(LayoutParams.MATCH_PARENT, dp(TABS_DP)))
        buildBottomRow()
        addView(bottomRow, LayoutParams(LayoutParams.MATCH_PARENT, dp(BOTTOM_DP)))
        applyMode()
        render(resetScroll = true)
    }

    fun isSearchActive(): Boolean = searchMode

    fun handleBack(): Boolean {
        if (grid.dismissBubble()) return true
        if (!searchMode) return false
        exitSearch()
        onSearchClosed()
        return true
    }

    fun appendSearchCodePoint(codePoint: Int) {
        if (codePoint <= 0 || codePoint == Constants.CODE_ENTER) return
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
        spaceKey.text = languageLabel()
        applyMode()
        render()
    }

    private fun startSearch() {
        if (searchMode) return
        searchMode = true
        query = ""
        grid.dismissBubble()
        applyMode()
        onSearchRequested()
        render()
    }

    private fun applyMode() {
        val browse = if (searchMode) GONE else VISIBLE
        grid.visibility = browse
        tabsBar.visibility = browse
        bottomRow.visibility = browse
        resultsScroll.visibility = if (searchMode) VISIBLE else GONE
        searchLeading.setImageResource(
            if (searchMode) R.drawable.ic_settings_back else R.drawable.ic_emoji_search
        )
        searchLeading.contentDescription = serviceContext.getString(
            if (searchMode) R.string.emoji_back_to_keyboard else R.string.emoji_search_hint
        )
    }

    // M3 search pill: surfaceContainerHigh, leading icon, Roboto Flex hint, clear button.
    private fun buildSearchBar() {
        searchBar.orientation = HORIZONTAL
        searchBar.gravity = Gravity.CENTER_VERTICAL
        searchBar.setPadding(dp(8), dp(6), dp(8), dp(2))
        val pill = LinearLayout(serviceContext).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ripple(GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(appearance.surfaceContainerHigh)
            })
            setOnClickListener { startSearch() }
        }
        searchLeading.apply {
            imageTintList = ColorStateList.valueOf(appearance.secondaryText)
            background = borderlessRipple()
            setOnClickListener {
                if (searchMode) {
                    exitSearch()
                    onSearchClosed()
                } else startSearch()
            }
        }
        pill.addView(searchLeading, LayoutParams(dp(40), dp(36)))
        searchText.apply {
            textSize = 15f
            typeface = labelTypeface
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.START
        }
        pill.addView(searchText, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        searchClear.apply {
            setImageResource(R.drawable.ic_emoji_close)
            imageTintList = ColorStateList.valueOf(appearance.secondaryText)
            contentDescription = serviceContext.getString(R.string.emoji_clear_search)
            background = borderlessRipple()
            setOnClickListener {
                query = ""
                render()
            }
        }
        pill.addView(searchClear, LayoutParams(dp(40), dp(36)))
        searchBar.addView(pill, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
    }

    private fun buildResults() {
        resultsRow.orientation = HORIZONTAL
        resultsRow.gravity = Gravity.CENTER_VERTICAL
        resultsRow.setPadding(dp(4), 0, dp(4), 0)
        resultsScroll.isHorizontalScrollBarEnabled = false
        resultsScroll.addView(resultsRow, LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
    }

    private fun buildTabs() {
        tabIndicator.background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(appearance.secondaryContainer)
        }
        tabsBar.addView(tabIndicator, FrameLayout.LayoutParams(0, dp(32), Gravity.CENTER_VERTICAL))
        tabsRow.orientation = HORIZONTAL
        tabsRow.gravity = Gravity.CENTER_VERTICAL
        tabsRow.setPadding(dp(4), 0, dp(4), 0)
        EmojiCategory.entries.forEach { category ->
            val tab = ImageButton(serviceContext).apply {
                setImageResource(category.iconRes)
                imageTintList = ColorStateList.valueOf(appearance.secondaryText)
                contentDescription = serviceContext.getString(category.titleRes)
                background = borderlessRipple()
                setOnClickListener {
                    grid.dismissBubble()
                    selectTab(category)
                    grid.scrollToCategory(category)
                }
            }
            tabs[category] = tab
            tabsRow.addView(tab, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        }
        tabsBar.addView(tabsRow, FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT)
        tabsRow.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            activeTab?.let { moveIndicator(it, animate = false) }
        }
    }

    // Active tab: secondaryContainer pill under an onSecondaryContainer icon; others are muted.
    private fun selectTab(category: EmojiCategory) {
        if (activeTab == category) return
        val animate = activeTab != null
        activeTab = category
        tabs.forEach { (item, tab) ->
            tab.imageTintList = ColorStateList.valueOf(
                if (item == category) appearance.onSecondaryContainer else appearance.secondaryText
            )
        }
        moveIndicator(category, animate)
    }

    private fun moveIndicator(category: EmojiCategory, animate: Boolean) {
        val tab = tabs[category] ?: return
        if (tab.width == 0) return
        val indicatorWidth = minOf(tab.width - dp(4), dp(48))
        if (tabIndicator.layoutParams.width != indicatorWidth) {
            tabIndicator.layoutParams = tabIndicator.layoutParams.apply { width = indicatorWidth }
        }
        val target = (tabsRow.paddingLeft + tab.left + (tab.width - indicatorWidth) / 2).toFloat()
        tabIndicator.animate().cancel()
        if (animate) {
            tabIndicator.animate().translationX(target).setDuration(260)
                .setInterpolator(OvershootInterpolator(1.2f)).start()
        } else {
            tabIndicator.translationX = target
        }
    }

    // ABC, a space bar that also switches language with a horizontal swipe, and Backspace.
    private fun buildBottomRow() {
        bottomRow.orientation = HORIZONTAL
        bottomRow.gravity = Gravity.CENTER_VERTICAL
        bottomRow.setPadding(dp(4), dp(2), dp(4), dp(6))
        val abc = TextView(serviceContext).apply {
            text = "ABC"
            textSize = 14f
            typeface = mediumTypeface
            gravity = Gravity.CENTER
            setTextColor(appearance.functionalText)
            contentDescription = serviceContext.getString(R.string.emoji_back_to_keyboard)
            background = keyBackground(appearance.functionalKeySurface)
            setOnClickListener { onClose() }
        }
        bottomRow.addView(abc, keyParams(1.4f))
        spaceKey.apply {
            textSize = 13f
            typeface = labelTypeface
            gravity = Gravity.CENTER
            setTextColor(appearance.secondaryText)
            background = keyBackground(appearance.keySurface)
            setOnTouchListener(SpaceSwipeListener())
            text = languageLabel()
        }
        bottomRow.addView(spaceKey, keyParams(5f))
        val delete = ImageButton(serviceContext).apply {
            setImageResource(R.drawable.ic_keyboard_delete)
            imageTintList = ColorStateList.valueOf(appearance.functionalText)
            contentDescription = "Delete"
            background = keyBackground(appearance.functionalKeySurface)
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        view.isPressed = true
                        onKeyCode(Constants.CODE_DELETE)
                        postDelayed(deleteRepeat, DELETE_REPEAT_START_MS)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        view.isPressed = false
                        removeCallbacks(deleteRepeat)
                    }
                }
                true
            }
        }
        bottomRow.addView(delete, keyParams(1.4f))
    }

    private fun keyParams(weight: Float) =
        LayoutParams(0, LayoutParams.MATCH_PARENT, weight).apply { setMargins(dp(3), 0, dp(3), 0) }

    private fun keyBackground(color: Int) = ripple(GradientDrawable().apply {
        cornerRadius = dp(10).toFloat()
        setColor(color)
    })

    private fun ripple(content: GradientDrawable) =
        RippleDrawable(ColorStateList.valueOf(appearance.pressedSurface), content, content)

    private fun borderlessRipple() =
        RippleDrawable(ColorStateList.valueOf(appearance.pressedSurface), null, null)

    private inner class SpaceSwipeListener : OnTouchListener {
        private var downX = 0f
        private var swiped = false

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    swiped = false
                    view.isPressed = true
                }
                MotionEvent.ACTION_MOVE -> {
                    val distance = event.x - downX
                    if (!swiped && kotlin.math.abs(distance) >= dp(SWIPE_DP)) {
                        swiped = true
                        view.isPressed = false
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onLanguageSwipe(if (distance > 0) 1 else -1)
                        spaceKey.text = languageLabel()
                    }
                }
                MotionEvent.ACTION_UP -> {
                    view.isPressed = false
                    if (!swiped) onKeyCode(Constants.CODE_SPACE)
                }
                MotionEvent.ACTION_CANCEL -> view.isPressed = false
            }
            return true
        }
    }

    private fun render(resetScroll: Boolean = false) {
        preferredVariants = EmojiHistory.preferredVariants(serviceContext)
        searchClear.visibility = if (searchMode && query.isNotEmpty()) VISIBLE else GONE
        if (searchMode) {
            searchText.text = query.ifEmpty { serviceContext.getString(R.string.emoji_search_hint) }
            searchText.setTextColor(
                if (query.isEmpty()) appearance.secondaryText else appearance.primaryText
            )
            renderResults()
            return
        }
        searchText.text = serviceContext.getString(R.string.emoji_search_hint)
        searchText.setTextColor(appearance.secondaryText)
        grid.setSections(EmojiCategory.entries.map { category ->
            EmojiGridView.Section(category, serviceContext.getString(category.titleRes),
                EmojiCatalog.entries(category, serviceContext))
        }, resetScroll)
    }

    // One scrolling row of matches; recent emoji while the query is still empty.
    private fun renderResults() {
        resultsRow.removeAllViews()
        resultsScroll.scrollTo(0, 0)
        val results = if (query.isEmpty()) {
            EmojiCatalog.entries(EmojiCategory.RECENT, serviceContext)
        } else {
            EmojiCatalog.rank(EmojiCatalog.allEntries(serviceContext), query,
                EmojiHistory.counts(serviceContext), MAX_RESULTS)
        }
        if (results.isEmpty() && query.isNotEmpty()) {
            resultsRow.addView(TextView(serviceContext).apply {
                text = serviceContext.getString(R.string.emoji_no_results)
                textSize = 13f
                typeface = labelTypeface
                gravity = Gravity.CENTER_VERTICAL
                setTextColor(appearance.secondaryText)
                setPadding(dp(12), 0, dp(12), 0)
            }, LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
            return
        }
        results.forEach { entry ->
            val emoji = preferredVariants[entry.emoji] ?: entry.emoji
            resultsRow.addView(TextView(serviceContext).apply {
                text = emoji
                textSize = 26f
                gravity = Gravity.CENTER
                contentDescription = entry.name
                background = borderlessRipple()
                setOnClickListener { select(emoji) }
            }, LayoutParams(dp(RESULTS_DP), dp(RESULTS_DP)))
        }
    }

    private fun select(emoji: String) {
        EmojiHistory.record(serviceContext, emoji)
        onEmojiSelected(emoji)
    }

    private companion object {
        const val SEARCH_BAR_DP = 44
        const val RESULTS_DP = 52
        const val TABS_DP = 40
        const val BOTTOM_DP = 46
        const val MAX_RESULTS = 60
        const val DELETE_REPEAT_START_MS = 400L
        const val DELETE_REPEAT_MS = 50L
        const val SWIPE_DP = 24
    }
}

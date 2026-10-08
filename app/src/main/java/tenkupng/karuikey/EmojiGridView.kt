package tenkupng.karuikey

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.OverScroller
import kotlin.math.max
import kotlin.math.min

/**
 * Canvas emoji grid: one view draws only the visible rows of every section, so thousands of
 * emoji cost no child views. Sections scroll as one list with titled headers; long-press shows
 * a variant bubble above the cell that can be picked by tapping or by sliding the same finger.
 */
internal class EmojiGridView(
    context: Context,
    private val appearance: KeyboardAppearance,
    headerTypeface: Typeface,
    /** Fixed column count, e.g. one row of search results; 0 fits columns to the width. */
    private val fixedColumns: Int = 0
) : View(context) {
    class Section(val category: EmojiCategory?, val title: String, val entries: List<EmojiEntry>)

    var onEmojiClick: (EmojiEntry, String) -> Unit = { _, _ -> }
    var onVariantChosen: (EmojiEntry, String) -> Unit = { _, _ -> }
    var onSectionVisible: (EmojiCategory?) -> Unit = {}
    /** Emoji actually drawn for an entry, e.g. with the remembered skin tone. */
    var displayFor: (EmojiEntry) -> String = { it.emoji }
    var emptyText = ""

    private val density = resources.displayMetrics.density
    private val headerHeight = (26 * density).toInt()
    private val minCellWidth = 44 * density
    private var cellSize = 48 * density
    private val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    // labelMedium: 12sp, medium weight, onSurfaceVariant.
    private val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = headerTypeface
        textSize = 12 * resources.displayMetrics.scaledDensity
        color = appearance.secondaryText
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = appearance.popupSurface
        setShadowLayer(6 * density, 0f, 2 * density, 0x40000000)
    }
    private val bubbleRect = RectF()

    private var sections: List<Section> = emptyList()
    /** Top offset of each section header; the section's cells follow it. */
    private var sectionTops = IntArray(0)
    private var contentHeight = 0
    private var columns = 8
    private var scrollOffset = 0f
    private var visibleSection = -1

    private val scroller = OverScroller(context)
    private var pressedSection = -1
    private var pressedIndex = -1
    private var highlightSection = -1
    private var highlightIndex = -1
    private var highlight = 0f
    private var highlightAnimator: ValueAnimator? = null

    private var bubbleEntry: EmojiEntry? = null
    private var bubbleOptions: List<String> = emptyList()
    private var bubbleColumns = 0
    private var bubbleChoice = -1
    private var bubbleTracking = false

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(event: MotionEvent): Boolean {
            scroller.forceFinished(true)
            val hit = hitTest(event.x, event.y)
            if (hit != null) press(hit.first, hit.second)
            return true
        }

        override fun onScroll(first: MotionEvent?, event: MotionEvent, dx: Float, dy: Float): Boolean {
            release()
            scrollTo(scrollOffset + dy)
            return true
        }

        override fun onFling(first: MotionEvent?, event: MotionEvent, vx: Float, vy: Float): Boolean {
            scroller.fling(0, scrollOffset.toInt(), 0, -vy.toInt(), 0, 0, 0, maxScroll())
            postInvalidateOnAnimation()
            return true
        }

        override fun onSingleTapUp(event: MotionEvent): Boolean {
            val entry = pressedEntry() ?: return false
            onEmojiClick(entry, displayFor(entry))
            return true
        }

        override fun onLongPress(event: MotionEvent) {
            val entry = pressedEntry() ?: return
            if (entry.variants.isEmpty()) return
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            showBubble(entry, pressedSection, pressedIndex)
            bubbleTracking = true
        }
    })

    fun setSections(newSections: List<Section>, resetScroll: Boolean) {
        // Keep the visible section anchored when sections above it grow, e.g. Recent.
        val anchor = sections.getOrNull(visibleSection)?.category
        val anchorOffset = if (anchor != null) scrollOffset - sectionTops[visibleSection] else 0f
        sections = newSections.filter { it.entries.isNotEmpty() }
        dismissBubble()
        release(animate = false)
        relayoutSections()
        visibleSection = -1
        if (resetScroll) {
            scroller.forceFinished(true)
            scrollOffset = 0f
        } else {
            val index = sections.indexOfFirst { it.category == anchor }
            if (index >= 0) scrollOffset = sectionTops[index] + anchorOffset
        }
        scrollTo(scrollOffset)
        invalidate()
    }

    /** Smoothly scrolls so the section of [category] starts at the top. */
    fun scrollToCategory(category: EmojiCategory) {
        val index = sections.indexOfFirst { it.category == category }
        if (index < 0) return
        dismissBubble()
        val target = min(sectionTops[index], maxScroll())
        scroller.forceFinished(true)
        scroller.startScroll(0, scrollOffset.toInt(), 0, target - scrollOffset.toInt(), 350)
        postInvalidateOnAnimation()
    }

    fun dismissBubble(): Boolean {
        if (bubbleEntry == null) return false
        bubbleEntry = null
        bubbleTracking = false
        invalidate()
        return true
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        columns = if (fixedColumns > 0) fixedColumns
            else (width / minCellWidth).toInt().coerceIn(8, 12)
        cellSize = width.toFloat() / columns
        emojiPaint.textSize = cellSize * 0.56f
        relayoutSections()
        scrollTo(scrollOffset)
    }

    private fun relayoutSections() {
        sectionTops = IntArray(sections.size)
        var top = 0
        sections.forEachIndexed { index, section ->
            sectionTops[index] = top
            top += headerFor(section) + (rowsFor(section) * cellSize).toInt()
        }
        contentHeight = top
    }

    private fun headerFor(section: Section) = if (section.title.isEmpty()) 0 else headerHeight

    private fun rowsFor(section: Section) = (section.entries.size + columns - 1) / columns

    private fun maxScroll() = max(0, contentHeight - height)

    private fun scrollTo(offset: Float) {
        scrollOffset = offset.coerceIn(0f, maxScroll().toFloat())
        updateVisibleSection()
        invalidate()
    }

    private fun updateVisibleSection() {
        var index = sectionTops.indexOfLast { it <= scrollOffset + 1 }
        if (index < 0) index = 0
        if (index != visibleSection && sections.isNotEmpty()) {
            visibleSection = index
            onSectionVisible(sections[index].category)
        }
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollTo(scroller.currY.toFloat())
            postInvalidateOnAnimation()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (bubbleEntry != null && handleBubbleTouch(event)) return true
        val handled = gestures.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        ) release()
        return handled || super.onTouchEvent(event)
    }

    /** Returns true when the bubble consumed the event. */
    private fun handleBubbleTouch(event: MotionEvent): Boolean {
        val choice = bubbleChoiceAt(event.x, event.y)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (choice < 0) {
                    dismissBubble()
                    return true
                }
                bubbleTracking = true
                setBubbleChoice(choice)
            }
            MotionEvent.ACTION_MOVE -> if (bubbleTracking) setBubbleChoice(choice) else return false
            MotionEvent.ACTION_UP -> {
                if (!bubbleTracking) return false
                bubbleTracking = false
                release(animate = false)
                val entry = bubbleEntry
                val option = bubbleOptions.getOrNull(choice)
                if (entry != null && option != null) {
                    dismissBubble()
                    onVariantChosen(entry, option)
                }
            }
            MotionEvent.ACTION_CANCEL -> dismissBubble()
        }
        return true
    }

    private fun setBubbleChoice(choice: Int) {
        if (choice == bubbleChoice) return
        bubbleChoice = choice
        invalidate()
    }

    private fun showBubble(entry: EmojiEntry, section: Int, index: Int) {
        bubbleEntry = entry
        bubbleOptions = listOf(entry.emoji) + entry.variants
        bubbleColumns = min(bubbleOptions.size, BUBBLE_MAX_COLUMNS)
        bubbleChoice = bubbleOptions.indexOf(displayFor(entry)).coerceAtLeast(0)
        val rows = (bubbleOptions.size + bubbleColumns - 1) / bubbleColumns
        val padding = 6 * density
        val bubbleWidth = bubbleColumns * cellSize + 2 * padding
        val bubbleHeight = rows * cellSize + 2 * padding
        val cellLeft = (index % columns) * cellSize
        val cellTop = sectionTops[section] + headerFor(sections[section]) +
            (index / columns) * cellSize - scrollOffset
        val left = (cellLeft + cellSize / 2 - bubbleWidth / 2).coerceIn(0f, max(0f, width - bubbleWidth))
        // Above the cell when it fits, otherwise below it.
        val top = if (cellTop - bubbleHeight >= 0) cellTop - bubbleHeight
            else min(cellTop + cellSize, height - bubbleHeight)
        bubbleRect.set(left, max(0f, top), left + bubbleWidth, max(0f, top) + bubbleHeight)
        invalidate()
    }

    private fun bubbleChoiceAt(x: Float, y: Float): Int {
        val padding = 6 * density
        val column = ((x - bubbleRect.left - padding) / cellSize).toInt()
        val row = ((y - bubbleRect.top - padding) / cellSize).toInt()
        if (!bubbleRect.contains(x, y) || column !in 0 until bubbleColumns || row < 0) return -1
        val choice = row * bubbleColumns + column
        return if (choice < bubbleOptions.size) choice else -1
    }

    private fun hitTest(x: Float, y: Float): Pair<Int, Int>? {
        val contentY = y + scrollOffset
        val section = sectionTops.indexOfLast { it <= contentY }
        if (section < 0) return null
        val cellsTop = sectionTops[section] + headerFor(sections[section])
        if (contentY < cellsTop) return null
        val row = ((contentY - cellsTop) / cellSize).toInt()
        val column = (x / cellSize).toInt().coerceIn(0, columns - 1)
        val index = row * columns + column
        return if (index < sections[section].entries.size) section to index else null
    }

    private fun pressedEntry(): EmojiEntry? =
        sections.getOrNull(pressedSection)?.entries?.getOrNull(pressedIndex)

    // Borderless ripple: a circle swells from the centre with a spring and fades on release.
    private fun press(section: Int, index: Int) {
        pressedSection = section
        pressedIndex = index
        highlightSection = section
        highlightIndex = index
        animateHighlight(1f, 140, OvershootInterpolator(2f))
    }

    private fun release(animate: Boolean = true) {
        if (pressedIndex < 0 && highlight == 0f) return
        pressedSection = -1
        pressedIndex = -1
        if (animate) {
            animateHighlight(0f, 180, DecelerateInterpolator())
        } else {
            highlightAnimator?.cancel()
            highlight = 0f
            invalidate()
        }
    }

    private fun animateHighlight(target: Float, duration: Long,
            interpolator: android.animation.TimeInterpolator) {
        highlightAnimator?.cancel()
        highlightAnimator = ValueAnimator.ofFloat(highlight, target).apply {
            this.duration = duration
            this.interpolator = interpolator
            addUpdateListener {
                highlight = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (sections.isEmpty()) {
            if (emptyText.isNotEmpty()) {
                headerPaint.textAlign = Paint.Align.CENTER
                canvas.drawText(emptyText, width / 2f, min(height / 2f, 40 * density), headerPaint)
                headerPaint.textAlign = Paint.Align.LEFT
            }
            return
        }
        val emojiOffset = (emojiPaint.descent() + emojiPaint.ascent()) / 2
        sections.forEachIndexed { sectionIndex, section ->
            val sectionTop = sectionTops[sectionIndex] - scrollOffset
            val header = headerFor(section)
            val sectionBottom = sectionTop + header + rowsFor(section) * cellSize
            if (sectionBottom < 0 || sectionTop > height) return@forEachIndexed
            if (header > 0 && sectionTop + header > 0) {
                canvas.drawText(section.title, 12 * density, sectionTop + header - 9 * density,
                    headerPaint)
            }
            val cellsTop = sectionTop + header
            val firstRow = max(0, ((-cellsTop) / cellSize).toInt())
            val lastRow = min(rowsFor(section) - 1, ((height - cellsTop) / cellSize).toInt())
            for (row in firstRow..lastRow) {
                for (column in 0 until columns) {
                    val index = row * columns + column
                    val entry = section.entries.getOrNull(index) ?: break
                    val centerX = column * cellSize + cellSize / 2
                    val centerY = cellsTop + row * cellSize + cellSize / 2
                    if (sectionIndex == highlightSection && index == highlightIndex && highlight > 0f) {
                        highlightPaint.color = appearance.secondaryContainer
                        highlightPaint.alpha = (255 * highlight.coerceAtMost(1f)).toInt()
                        canvas.drawCircle(centerX, centerY, cellSize * 0.46f * highlight, highlightPaint)
                    }
                    canvas.drawText(displayFor(entry), centerX, centerY - emojiOffset, emojiPaint)
                }
            }
        }
        drawBubble(canvas, emojiOffset)
    }

    private fun drawBubble(canvas: Canvas, emojiOffset: Float) {
        if (bubbleEntry == null) return
        val radius = 20 * density
        canvas.drawRoundRect(bubbleRect, radius, radius, bubblePaint)
        val padding = 6 * density
        bubbleOptions.forEachIndexed { index, option ->
            val centerX = bubbleRect.left + padding + (index % bubbleColumns) * cellSize + cellSize / 2
            val centerY = bubbleRect.top + padding + (index / bubbleColumns) * cellSize + cellSize / 2
            if (index == bubbleChoice) {
                highlightPaint.color = appearance.secondaryContainer
                highlightPaint.alpha = 255
                canvas.drawCircle(centerX, centerY, cellSize * 0.46f, highlightPaint)
            }
            canvas.drawText(option, centerX, centerY - emojiOffset, emojiPaint)
        }
    }

    private companion object {
        const val BUBBLE_MAX_COLUMNS = 6
    }
}

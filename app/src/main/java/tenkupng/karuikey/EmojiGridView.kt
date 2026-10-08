package tenkupng.karuikey

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.OverScroller
import kotlin.math.max
import kotlin.math.min

/**
 * Canvas emoji grid: one view draws only the visible rows of every section, so thousands of
 * emoji cost no child views. Sections scroll as one list with titled headers.
 */
internal class EmojiGridView(
    context: Context,
    private val appearance: KeyboardAppearance,
    labelTypeface: Typeface
) : View(context) {
    class Section(val category: EmojiCategory?, val title: String, val entries: List<EmojiEntry>)

    var onEmojiClick: (EmojiEntry, String) -> Unit = { _, _ -> }
    var onEmojiLongClick: (EmojiEntry) -> Unit = {}
    var onSectionVisible: (EmojiCategory?) -> Unit = {}
    /** Emoji actually drawn for an entry, e.g. with the remembered skin tone. */
    var displayFor: (EmojiEntry) -> String = { it.emoji }
    var emptyText = ""

    private val density = resources.displayMetrics.density
    private val cellHeight = 48 * density
    private val headerHeight = 32 * density
    private val cellInset = 2 * density
    private val cellRadius = 12 * density
    private val pressedExtraRadius = 6 * density
    private val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 26 * resources.displayMetrics.scaledDensity
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = labelTypeface
        textSize = 13 * resources.displayMetrics.scaledDensity
        color = appearance.secondaryText
    }
    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cellRect = RectF()

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
    private var pressProgress = 0f
    private var pressAnimator: ValueAnimator? = null
    private var longPressed = false

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(event: MotionEvent): Boolean {
            scroller.forceFinished(true)
            longPressed = false
            val hit = hitTest(event.x, event.y)
            if (hit != null) setPressed(hit.first, hit.second)
            return true
        }

        override fun onScroll(first: MotionEvent?, event: MotionEvent, dx: Float, dy: Float): Boolean {
            clearPressed()
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
            longPressed = true
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            onEmojiLongClick(entry)
        }
    })

    fun setSections(newSections: List<Section>, resetScroll: Boolean) {
        // Keep the visible section anchored when sections above it grow, e.g. Recent.
        val anchor = sections.getOrNull(visibleSection)?.category
        val anchorOffset = if (anchor != null) scrollOffset - sectionTops[visibleSection] else 0f
        sections = newSections.filter { it.entries.isNotEmpty() }
        clearPressed()
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
        val target = min(sectionTops[index], maxScroll())
        scroller.forceFinished(true)
        scroller.startScroll(0, scrollOffset.toInt(), 0, target - scrollOffset.toInt(), 350)
        postInvalidateOnAnimation()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        columns = (width / (48 * density)).toInt().coerceIn(4, 10)
        relayoutSections()
        scrollTo(scrollOffset)
    }

    private fun relayoutSections() {
        sectionTops = IntArray(sections.size)
        var top = 0
        sections.forEachIndexed { index, section ->
            sectionTops[index] = top
            top += headerFor(section) + rowsFor(section) * cellHeight.toInt()
        }
        contentHeight = top
    }

    private fun headerFor(section: Section) = if (section.title.isEmpty()) 0 else headerHeight.toInt()

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
        val handled = gestures.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        ) clearPressed()
        return handled || super.onTouchEvent(event)
    }

    private fun hitTest(x: Float, y: Float): Pair<Int, Int>? {
        val contentY = y + scrollOffset
        val section = sectionTops.indexOfLast { it <= contentY }
        if (section < 0) return null
        val cellsTop = sectionTops[section] + headerFor(sections[section])
        if (contentY < cellsTop) return null
        val row = ((contentY - cellsTop) / cellHeight).toInt()
        val column = (x / (width.toFloat() / columns)).toInt().coerceIn(0, columns - 1)
        val index = row * columns + column
        return if (index < sections[section].entries.size) section to index else null
    }

    private fun pressedEntry(): EmojiEntry? =
        sections.getOrNull(pressedSection)?.entries?.getOrNull(pressedIndex)

    // Same expressive press as keys: tone switches at once, corners round out with a spring.
    private fun setPressed(section: Int, index: Int) {
        pressedSection = section
        pressedIndex = index
        animatePress(1f, 120, OvershootInterpolator(2.5f))
    }

    private fun clearPressed() {
        if (pressedIndex < 0) return
        pressedSection = -1
        pressedIndex = -1
        pressAnimator?.cancel()
        pressProgress = 0f
        invalidate()
    }

    private fun animatePress(target: Float, duration: Long, interpolator: android.animation.TimeInterpolator) {
        pressAnimator?.cancel()
        pressAnimator = ValueAnimator.ofFloat(pressProgress, target).apply {
            this.duration = duration
            this.interpolator = interpolator
            addUpdateListener {
                pressProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (sections.isEmpty()) {
            if (emptyText.isNotEmpty()) {
                labelPaint.textAlign = Paint.Align.CENTER
                canvas.drawText(emptyText, width / 2f, min(height / 2f, 64 * density), labelPaint)
                labelPaint.textAlign = Paint.Align.LEFT
            }
            return
        }
        val cellWidth = width.toFloat() / columns
        val emojiOffset = (emojiPaint.descent() + emojiPaint.ascent()) / 2
        sections.forEachIndexed { sectionIndex, section ->
            val sectionTop = sectionTops[sectionIndex] - scrollOffset
            val header = headerFor(section)
            val sectionBottom = sectionTop + header + rowsFor(section) * cellHeight
            if (sectionBottom < 0 || sectionTop > height) return@forEachIndexed
            if (header > 0 && sectionTop + header > 0) {
                canvas.drawText(section.title, 8 * density,
                    sectionTop + header - 10 * density, labelPaint)
            }
            val cellsTop = sectionTop + header
            val firstRow = max(0, ((-cellsTop) / cellHeight).toInt())
            val lastRow = min(rowsFor(section) - 1, ((height - cellsTop) / cellHeight).toInt())
            for (row in firstRow..lastRow) {
                for (column in 0 until columns) {
                    val index = row * columns + column
                    val entry = section.entries.getOrNull(index) ?: break
                    val left = column * cellWidth
                    val top = cellsTop + row * cellHeight
                    val pressed = sectionIndex == pressedSection && index == pressedIndex
                    cellRect.set(left + cellInset, top + cellInset,
                        left + cellWidth - cellInset, top + cellHeight - cellInset)
                    cellPaint.color = if (pressed) appearance.pressedSurface else appearance.keySurface
                    val radius = cellRadius + if (pressed) pressedExtraRadius * pressProgress else 0f
                    canvas.drawRoundRect(cellRect, radius, radius, cellPaint)
                    canvas.drawText(displayFor(entry), cellRect.centerX(),
                        cellRect.centerY() - emojiOffset, emojiPaint)
                }
            }
        }
    }
}

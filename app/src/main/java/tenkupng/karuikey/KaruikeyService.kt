package tenkupng.karuikey

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import kotlin.math.roundToInt

internal fun calculateKeyBounds(
    width: Float,
    offset: Float,
    gap: Float,
    keyWidths: FloatArray,
    bounds: FloatArray
) {
    var totalWeight = 0f
    for (keyWidth in keyWidths) totalWeight += keyWidth

    val leftEdge = (offset * width / 10f + gap / 2f).coerceIn(0f, width)
    val rightEdge = (width - gap / 2f).coerceIn(leftEdge, width)
    val availableWidth = (rightEdge - leftEdge - gap * (keyWidths.size - 1)).coerceAtLeast(0f)
    val unitWidth = if (totalWeight > 0f) availableWidth / totalWeight else 0f
    var left = leftEdge
    for (column in keyWidths.indices) {
        val right = (left + keyWidths[column] * unitWidth).coerceIn(left, width)
        bounds[column * 2] = left
        bounds[column * 2 + 1] = right
        left = (right + gap).coerceAtMost(width)
    }
}

internal fun keyColumnAt(
    x: Float,
    bounds: FloatArray
): Int {
    for (column in bounds.indices step 2) {
        if (x >= bounds[column] && x < bounds[column + 1]) return column / 2
    }
    return -1
}

class KaruikeyService : InputMethodService() {
    private var keyboardView: KaruikeyView? = null

    override fun onCreateInputView(): View = KaruikeyView(this).also { keyboardView = it }

    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        keyboardView?.resetForInputSession()
    }

    override fun onFinishInput() {
        keyboardView?.resetForInputSession()
        super.onFinishInput()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        keyboardView?.clearPressedKey()
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        keyboardView = null
        super.onDestroy()
    }
}

private class KaruikeyView(private val service: InputMethodService) : View(service) {
    private class Key(
        val label: String,
        val width: Float,
        val action: Int = TYPE,
        val text: String = label,
        val shiftedText: String = text
    )

    private companion object {
        const val TYPE = 0
        const val SHIFT = 1
        const val BACKSPACE = 2
        const val SPACE = 3
        const val ENTER = 4
        const val SYMBOLS = 5
        const val LETTERS = 6
    }

    private val letterRows = arrayOf(
        arrayOf(
            Key("q", 1f, text = "q", shiftedText = "Q"),
            Key("w", 1f, text = "w", shiftedText = "W"),
            Key("e", 1f, text = "e", shiftedText = "E"),
            Key("r", 1f, text = "r", shiftedText = "R"),
            Key("t", 1f, text = "t", shiftedText = "T"),
            Key("y", 1f, text = "y", shiftedText = "Y"),
            Key("u", 1f, text = "u", shiftedText = "U"),
            Key("i", 1f, text = "i", shiftedText = "I"),
            Key("o", 1f, text = "o", shiftedText = "O"),
            Key("p", 1f, text = "p", shiftedText = "P")
        ),
        arrayOf(
            Key("a", 1f, text = "a", shiftedText = "A"),
            Key("s", 1f, text = "s", shiftedText = "S"),
            Key("d", 1f, text = "d", shiftedText = "D"),
            Key("f", 1f, text = "f", shiftedText = "F"),
            Key("g", 1f, text = "g", shiftedText = "G"),
            Key("h", 1f, text = "h", shiftedText = "H"),
            Key("j", 1f, text = "j", shiftedText = "J"),
            Key("k", 1f, text = "k", shiftedText = "K"),
            Key("l", 1f, text = "l", shiftedText = "L")
        ),
        arrayOf(
            Key("⇧", 1.5f, SHIFT),
            Key("z", 1f, text = "z", shiftedText = "Z"),
            Key("x", 1f, text = "x", shiftedText = "X"),
            Key("c", 1f, text = "c", shiftedText = "C"),
            Key("v", 1f, text = "v", shiftedText = "V"),
            Key("b", 1f, text = "b", shiftedText = "B"),
            Key("n", 1f, text = "n", shiftedText = "N"),
            Key("m", 1f, text = "m", shiftedText = "M"),
            Key("⌫", 1.5f, BACKSPACE)
        ),
        arrayOf(
            Key("123", 1.6f, SYMBOLS),
            Key(",", 1f),
            Key("Space", 4f, SPACE),
            Key(".", 1f),
            Key("↵", 1.6f, ENTER)
        )
    )
    private val symbolRows = arrayOf(
        arrayOf(
            Key("1", 1f),
            Key("2", 1f),
            Key("3", 1f),
            Key("4", 1f),
            Key("5", 1f),
            Key("6", 1f),
            Key("7", 1f),
            Key("8", 1f),
            Key("9", 1f),
            Key("0", 1f)
        ),
        arrayOf(
            Key("!", 1f),
            Key("@", 1f),
            Key("#", 1f),
            Key("$", 1f),
            Key("%", 1f),
            Key("&", 1f),
            Key("*", 1f),
            Key("(", 1f),
            Key(")", 1f),
            Key("?", 1f)
        ),
        arrayOf(
            Key(";", 1f),
            Key("-", 1f),
            Key("_", 1f),
            Key("+", 1f),
            Key("=", 1f),
            Key("/", 1f),
            Key("\\", 1f),
            Key(":", 1f),
            Key("⌫", 1.5f, BACKSPACE)
        ),
        arrayOf(
            Key("ABC", 1.6f, LETTERS),
            Key(",", 1f),
            Key("Space", 4f, SPACE),
            Key(".", 1f),
            Key("↵", 1.6f, ENTER)
        )
    )
    private val offsets = floatArrayOf(0f, 0.5f, 0f, 0.4f)
    private val letterWidths = widthsFor(letterRows)
    private val symbolWidths = widthsFor(symbolRows)
    private val letterBounds = boundsFor(letterRows)
    private val symbolBounds = boundsFor(symbolRows)
    private var rows = letterRows
    private var rowWidths = letterWidths
    private var bounds = letterBounds
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val button = RectF()
    private val density = resources.displayMetrics.density
    private var shifted = false
    private var pressedRow = -1
    private var pressedColumn = -1

    private fun widthsFor(rows: Array<Array<Key>>) =
        Array(rows.size) { row ->
            FloatArray(rows[row].size) { column -> rows[row][column].width }
        }

    private fun boundsFor(rows: Array<Array<Key>>) =
        Array(rows.size) { row -> FloatArray(rows[row].size * 2) }

    private val keyInset: Float
        get() = 2 * density

    private val keyGap: Float
        get() = keyInset * 2

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val desiredHeight = (260 * density).roundToInt()
        setMeasuredDimension(width, resolveSize(desiredHeight, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val rowHeight = height / rows.size.toFloat()
        paint.textSize = 20 * density
        for (row in rows.indices) {
            calculateKeyBounds(width.toFloat(), offsets[row], keyGap, rowWidths[row], bounds[row])
            for (column in rows[row].indices) {
                val key = rows[row][column]
                button.set(
                    bounds[row][column * 2],
                    row * rowHeight + keyInset,
                    bounds[row][column * 2 + 1],
                    (row + 1) * rowHeight - keyInset
                )
                paint.color = if (row == pressedRow && column == pressedColumn) {
                    0xffbdbdbd.toInt()
                } else {
                    0xffeeeeee.toInt()
                }
                canvas.drawRoundRect(button, 6 * density, 6 * density, paint)
                paint.color = Color.BLACK
                val label = if (shifted && key.action == TYPE) key.shiftedText else key.label
                canvas.drawText(
                    label,
                    button.centerX(),
                    button.centerY() - (paint.ascent() + paint.descent()) / 2,
                    paint
                )
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val rowHeight = height / rows.size.toFloat()
        val row = rowAt(event.y, rowHeight)
        val column = if (row in rows.indices) columnAt(row, event.x) else -1
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedRow = if (column >= 0) row else -1
                pressedColumn = if (column >= 0) column else -1
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                if (pressedRow >= 0 && row == pressedRow && column == pressedColumn) {
                    press(row, column)
                }
                pressedRow = -1
                pressedColumn = -1
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedRow = -1
                pressedColumn = -1
                invalidate()
            }
        }
        return true
    }

    private fun rowAt(y: Float, rowHeight: Float): Int {
        if (height <= 0 || y < 0f || y >= height) return -1
        val row = (y / rowHeight).toInt()
        val top = row * rowHeight + keyInset
        val bottom = (row + 1) * rowHeight - keyInset
        return if (y >= top && y < bottom) row else -1
    }

    private fun columnAt(row: Int, x: Float): Int =
        if (row in rows.indices) {
            calculateKeyBounds(width.toFloat(), offsets[row], keyGap, rowWidths[row], bounds[row])
            keyColumnAt(x, bounds[row])
        } else {
            -1
        }

    fun clearPressedKey() {
        pressedRow = -1
        pressedColumn = -1
        invalidate()
    }

    fun resetForInputSession() {
        rows = letterRows
        rowWidths = letterWidths
        bounds = letterBounds
        shifted = false
        clearPressedKey()
    }

    private fun press(row: Int, column: Int) {
        if (row !in rows.indices || column !in rows[row].indices) return
        val key = rows[row][column]
        when (key.action) {
            SHIFT -> {
                shifted = !shifted
                invalidate()
                return
            }
            SYMBOLS -> {
                rows = symbolRows
                rowWidths = symbolWidths
                bounds = symbolBounds
                invalidate()
                return
            }
            LETTERS -> {
                rows = letterRows
                rowWidths = letterWidths
                bounds = letterBounds
                invalidate()
                return
            }
        }
        val connection = service.currentInputConnection ?: return
        when (key.action) {
            TYPE -> connection.commitText(if (shifted) key.shiftedText else key.text, 1)
            SPACE -> connection.commitText(" ", 1)
            BACKSPACE -> connection.deleteSurroundingText(1, 0)
            ENTER -> {
                connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            }
        }
    }
}

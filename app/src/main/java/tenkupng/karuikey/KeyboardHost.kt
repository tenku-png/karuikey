package tenkupng.karuikey

import android.content.Context
import android.graphics.Rect
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

internal fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

/**
 * Hosts the keyboard: wraps it while docked, and spans the window in floating mode so the
 * compact keyboard can be dragged anywhere inside the system-bar-safe area.
 */
internal class KeyboardHost(
    context: Context,
    private val keyboard: View,
    private val onFloatingChanged: (Boolean) -> Unit
) : FrameLayout(context) {
    private var positionX = 0.5f
    private var positionY = 1f
    private var widthScale = -1f
    private var heightScale = -1f
    // Top-left corner to keep while a resize settles into the new size.
    private var resizeAnchor: Pair<Float, Float>? = null
    private val location = IntArray(2)
    var floating = false
        set(value) {
            if (field == value) return
            field = value
            onFloatingChanged(value)
            if (value) {
                loadFloatingPosition()
            } else {
                // A docked keyboard must not keep the floating offset.
                keyboard.translationX = 0f
                keyboard.translationY = 0f
            }
            requestLayout()
        }

    init {
        addView(keyboard)
    }

    fun loadFloatingPosition() {
        val (x, y) = KaruikeyPreferences.floatingPosition(context)
        positionX = x
        positionY = y
        val (widthFraction, heightFraction) = KaruikeyPreferences.floatingSizeScale(context)
        widthScale = widthFraction
        heightScale = heightFraction
        requestLayout()
    }

    fun saveFloatingPosition() {
        KaruikeyPreferences.setFloatingPosition(context, positionX, positionY)
        if (widthScale > 0f && heightScale > 0f) {
            KaruikeyPreferences.setFloatingSizeScale(context, widthScale, heightScale)
        }
    }

    // Resize limits: 280dp to 80% of the window wide, 180dp to 65% of it tall.
    private fun sizeRange(total: Int, minimum: Int, maxFraction: Float): IntRange {
        val low = minOf(minimum, total)
        return low..maxOf(low, (total * maxFraction).toInt())
    }

    fun resizeFloatingBy(dx: Float, dy: Float, fromLeft: Boolean) {
        if (width <= 0 || height <= 0) return
        val currentWidth = keyboard.width
        val newWidth = (currentWidth + if (fromLeft) -dx else dx).toInt()
            .coerceIn(sizeRange(width, context.dp(280), 0.8f))
        val newHeight = (keyboard.height + dy).toInt()
            .coerceIn(sizeRange(height, context.dp(180), 0.65f))
        // Dragging the left corner moves the left edge and keeps the right edge in place.
        val left = keyboard.translationX + if (fromLeft) currentWidth - newWidth else 0
        resizeAnchor = left to keyboard.translationY
        widthScale = newWidth.toFloat() / width
        heightScale = newHeight.toFloat() / height
        requestLayout()
    }

    fun keyboardBoundsInWindow(): Rect {
        keyboard.getLocationInWindow(location)
        return Rect(location[0], location[1],
            location[0] + keyboard.width, location[1] + keyboard.height)
    }

    fun moveFloatingBy(dx: Float, dy: Float) {
        val safe = safeArea()
        positionX = fraction(keyboard.translationX + dx, safe.left, safe.right)
        positionY = fraction(keyboard.translationY + dy, safe.top, safe.bottom)
        applyFloatingPosition()
        // Relayout re-runs onComputeInsets so the touchable region follows the keyboard.
        requestLayout()
    }

    private fun fraction(value: Float, min: Int, max: Int): Float =
        if (max <= min) 0.5f else ((value - min) / (max - min)).coerceIn(0f, 1f)

    // Translation range keeping the keyboard clear of status bar, cutout and navigation bar.
    private fun safeArea(): Rect {
        val root = rootView
        val insets = ViewCompat.getRootWindowInsets(this)?.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        getLocationInWindow(location)
        val margin = context.dp(8)
        val left = maxOf(0, (insets?.left ?: 0) - location[0]) + margin
        val top = maxOf(0, (insets?.top ?: 0) - location[1]) + margin
        val right = width - maxOf(0, (insets?.right ?: 0) -
            (root.width - location[0] - width)) - margin - keyboard.width
        val bottom = height - maxOf(0, (insets?.bottom ?: 0) -
            (root.height - location[1] - height)) - margin - keyboard.height
        return Rect(left, top, maxOf(left, right), maxOf(top, bottom))
    }

    private fun applyFloatingPosition() {
        val safe = safeArea()
        keyboard.translationX = safe.left + positionX * (safe.right - safe.left)
        keyboard.translationY = safe.top + positionY * (safe.bottom - safe.top)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (!floating) {
            keyboard.measure(widthMeasureSpec, heightMeasureSpec)
            setMeasuredDimension(keyboard.measuredWidth, keyboard.measuredHeight)
            return
        }
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            resources.displayMetrics.heightPixels
        } else MeasureSpec.getSize(heightMeasureSpec)
        val keyboardWidth = if (widthScale > 0f) {
            (width * widthScale).toInt().coerceIn(sizeRange(width, context.dp(280), 0.8f))
        } else (width * 0.6f).toInt().coerceAtLeast(context.dp(320)).coerceAtMost(width)
        val heightSpec = if (heightScale > 0f) {
            MeasureSpec.makeMeasureSpec(
                (height * heightScale).toInt().coerceIn(sizeRange(height, context.dp(180), 0.65f)),
                MeasureSpec.EXACTLY
            )
        } else MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST)
        keyboard.measure(MeasureSpec.makeMeasureSpec(keyboardWidth, MeasureSpec.EXACTLY), heightSpec)
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        keyboard.layout(0, 0, keyboard.measuredWidth, keyboard.measuredHeight)
        if (floating) {
            resizeAnchor?.let { (x, y) ->
                val safe = safeArea()
                positionX = fraction(x, safe.left, safe.right)
                positionY = fraction(y, safe.top, safe.bottom)
                resizeAnchor = null
            }
            applyFloatingPosition()
        } else {
            keyboard.translationX = 0f
            keyboard.translationY = 0f
        }
    }
}

package tenkupng.karuikey

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View

/** The pill under a floating keyboard: drag to move it, drag a bottom corner to resize it. */
internal class FloatingDragHandle(
    context: Context,
    appearance: KeyboardAppearance,
    private val host: () -> KeyboardHost?
) : View(context) {
    private val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = appearance.secondaryText }
    private val corner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = appearance.secondaryText
        style = Paint.Style.STROKE
        strokeWidth = context.dp(2).toFloat()
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val cornerPath = Path()
    private val resizeZone = context.dp(32)
    // 0 moves the keyboard; -1 / 1 resize from the bottom-left / bottom-right corner.
    private var gesture = 0
    private var lastX = 0f
    private var lastY = 0f

    override fun onDraw(canvas: Canvas) {
        val pillWidth = context.dp(32).toFloat()
        val pillHeight = context.dp(4).toFloat()
        val left = (width - pillWidth) / 2
        val top = (height - pillHeight) / 2
        canvas.drawRoundRect(left, top, left + pillWidth, top + pillHeight,
            pillHeight / 2, pillHeight / 2, pill)
        // Corner ticks mark the resize zones.
        val inset = context.dp(10).toFloat()
        val arm = context.dp(8).toFloat()
        val bottom = height - context.dp(8).toFloat()
        cornerPath.reset()
        cornerPath.moveTo(inset, bottom - arm)
        cornerPath.lineTo(inset, bottom)
        cornerPath.lineTo(inset + arm, bottom)
        cornerPath.moveTo(width - inset, bottom - arm)
        cornerPath.lineTo(width - inset, bottom)
        cornerPath.lineTo(width - inset - arm, bottom)
        canvas.drawPath(cornerPath, corner)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gesture = when {
                    event.x < resizeZone -> -1
                    event.x > width - resizeZone -> 1
                    else -> 0
                }
                lastX = event.rawX
                lastY = event.rawY
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - lastX
                val dy = event.rawY - lastY
                if (gesture == 0) {
                    host()?.moveFloatingBy(dx, dy)
                } else {
                    host()?.resizeFloatingBy(dx, dy, fromLeft = gesture < 0)
                }
                lastX = event.rawX
                lastY = event.rawY
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> host()?.saveFloatingPosition()
        }
        return true
    }
}

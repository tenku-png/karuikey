package tenkupng.karuikey

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.net.Uri
import java.io.File

/** A user picture behind the keys, copied into app storage so it survives the source going away. */
internal object KeyboardBackground {
    private const val FILE = "keyboard_background.jpg"
    private const val MAX_SIDE = 1600
    private var cached: Pair<Long, Bitmap>? = null

    private fun file(context: Context) = File(context.filesDir, FILE)

    fun exists(context: Context) = file(context).isFile

    /** Downscales and stores the picture; returns false if it could not be decoded. */
    fun save(context: Context, uri: Uri): Boolean {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return false
        val target = file(context)
        val temp = File(context.filesDir, "$FILE.tmp")
        temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        if (!temp.renameTo(target)) return false
        KaruikeyPreferences.setBackgroundImageVersion(context, target.lastModified())
        return true
    }

    fun clear(context: Context) {
        file(context).delete()
        cached = null
        KaruikeyPreferences.setBackgroundImageVersion(context, 0L)
    }

    fun bitmap(context: Context): Bitmap? {
        val target = file(context)
        if (!target.isFile) return null
        val version = target.lastModified()
        cached?.let { (cachedVersion, bitmap) -> if (cachedVersion == version) return bitmap }
        val bitmap = BitmapFactory.decodeFile(target.path) ?: return null
        cached = version to bitmap
        return bitmap
    }
}

/** Draws a bitmap scaled to cover its bounds, cropping the overflow evenly. */
internal class CenterCropDrawable(private val bitmap: Bitmap) : Drawable() {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val matrix = Matrix()

    override fun onBoundsChange(bounds: android.graphics.Rect) {
        val scale = maxOf(
            bounds.width() / bitmap.width.toFloat(), bounds.height() / bitmap.height.toFloat()
        )
        matrix.setScale(scale, scale)
        matrix.postTranslate(
            bounds.left + (bounds.width() - bitmap.width * scale) / 2,
            bounds.top + (bounds.height() - bitmap.height * scale) / 2
        )
    }

    override fun draw(canvas: Canvas) {
        canvas.save()
        canvas.clipRect(bounds)
        canvas.drawBitmap(bitmap, matrix, paint)
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

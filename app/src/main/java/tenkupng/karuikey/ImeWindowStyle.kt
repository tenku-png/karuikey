package tenkupng.karuikey

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.core.view.WindowCompat

/** Sizes, blurs and tints the IME window around the keyboard view. */
internal class ImeWindowStyle(private val context: Context) {
    // The IME window height chosen by the system, restored when blur is turned off.
    private var savedWindowHeight: Int? = null
    private var dockedWindowHeight: Int? = null

    // Window blur takes its corner radius from the window background outline, which is empty
    // until the drawable has bounds, so it starts sized and is set once to keep them.
    private val roundedBackground by lazy {
        GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            cornerRadius = context.dp(12).toFloat()
            val metrics = context.resources.displayMetrics
            setBounds(0, 0, metrics.widthPixels, metrics.heightPixels)
        }
    }

    fun apply(window: Window, view: View, floating: Boolean, surface: Int, surfaceColor: Int) {
        if (window.decorView.background !== roundedBackground) {
            window.setBackgroundDrawable(roundedBackground)
        }
        if (floating) {
            if (dockedWindowHeight == null) dockedWindowHeight = window.attributes.height
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            if (Build.VERSION.SDK_INT >= 31) window.setBackgroundBlurRadius(0)
        } else {
            dockedWindowHeight?.let { window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, it) }
            dockedWindowHeight = null
            applyBlur(window)
        }
        WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars =
            Color.luminance(surface) > 0.5f
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        window.navigationBarColor = if (floating) Color.TRANSPARENT else surfaceColor
        window.navigationBarDividerColor = Color.TRANSPARENT
        WindowCompat.setDecorFitsSystemWindows(window, Build.VERSION.SDK_INT < 35)
    }

    /**
     * Window blur covers the whole window surface. While blur is on, the window wraps the
     * keyboard so only the area behind it blurs; otherwise the system layout is left as is.
     */
    private fun applyBlur(window: Window) {
        if (Build.VERSION.SDK_INT < 31) return
        val blur = KaruikeyPreferences.blurActive(context)
        if (blur) {
            if (savedWindowHeight == null) savedWindowHeight = window.attributes.height
            if (window.attributes.height != WindowManager.LayoutParams.WRAP_CONTENT) {
                window.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT
                )
            }
        } else {
            savedWindowHeight?.let { height ->
                if (window.attributes.height != height) {
                    window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, height)
                }
            }
            savedWindowHeight = null
        }
        val radius = if (blur) {
            (KaruikeyPreferences.blurRadius(context) * context.resources.displayMetrics.density).toInt()
        } else 0
        window.setBackgroundBlurRadius(radius)
    }
}

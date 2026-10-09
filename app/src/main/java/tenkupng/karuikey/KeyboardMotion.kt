package tenkupng.karuikey

import android.animation.ValueAnimator
import android.content.Context
import android.view.View
import android.view.animation.PathInterpolator

/**
 * Short M3-style transitions for the keyboard surface. Each one only animates an element
 * coming in; whatever it replaces is already gone, so layout never waits on a running animation.
 */
internal object KeyboardMotion {
    // M3 emphasized-decelerate easing: a fast start that settles softly.
    private val emphasized = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
    private const val PANEL_MS = 260L
    private const val LAYOUT_MS = 180L
    private const val CANDIDATE_MS = 140L

    fun enabled(context: Context) =
        KaruikeyPreferences.animationsEnabled(context) && ValueAnimator.areAnimatorsEnabled()

    private fun View.settle() {
        animate().cancel()
        alpha = 1f
        translationY = 0f
        scaleX = 1f
        scaleY = 1f
    }

    /** A panel (emoji, clipboard, the keys coming back) rises a little and fades in. */
    fun panelIn(view: View) {
        view.settle()
        if (!enabled(view.context)) return
        view.alpha = 0f
        view.translationY = view.context.dp(24).toFloat()
        view.animate().alpha(1f).translationY(0f)
            .setDuration(PANEL_MS).setInterpolator(emphasized).start()
    }

    /** Letters and symbols swap with a slight zoom so the change reads as a new page. */
    fun layoutSwitch(view: View) {
        view.settle()
        if (!enabled(view.context)) return
        view.alpha = 0.35f
        view.scaleX = 0.97f
        view.scaleY = 0.97f
        view.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(LAYOUT_MS).setInterpolator(emphasized).start()
    }

    /** A suggestion whose word changed lifts into place. */
    fun candidateChanged(view: View) {
        if (!enabled(view.context)) return
        view.animate().cancel()
        view.alpha = 0.2f
        view.translationY = view.context.dp(6).toFloat()
        view.animate().alpha(1f).translationY(0f)
            .setDuration(CANDIDATE_MS).setInterpolator(emphasized).start()
    }

    /** Toolbar or candidate strip swap: a plain crossfade-in. */
    fun fadeIn(view: View) {
        view.animate().cancel()
        if (!enabled(view.context)) {
            view.alpha = 1f
            return
        }
        view.alpha = 0f
        view.animate().alpha(1f).setDuration(CANDIDATE_MS).setInterpolator(emphasized).start()
    }
}

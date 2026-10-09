package tenkupng.karuikey

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.android.inputmethod.latin.common.Constants

/** Vibration and click sound for key presses, at the strength and volume set in settings. */
internal class KeyFeedback(private val context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }
    private val audio: AudioManager? = context.getSystemService(AudioManager::class.java)

    fun onKeyPress(code: Int) {
        vibrate()
        playClick(code)
    }

    fun vibrate() {
        if (!KaruikeyPreferences.keyVibrationEnabled(context)) return
        val vibrator = vibrator?.takeIf { it.hasVibrator() } ?: return
        val strength = KaruikeyPreferences.vibrationStrength(context)
        // Motors with amplitude control get a short, crisp tick of varying force; the rest
        // can only vary how long they buzz.
        val effect = if (vibrator.hasAmplitudeControl()) {
            VibrationEffect.createOneShot(12, (strength * 255 / 100).coerceIn(1, 255))
        } else {
            VibrationEffect.createOneShot(4L + strength * 26L / 100, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        vibrator.vibrate(effect)
    }

    private fun playClick(code: Int) {
        if (!KaruikeyPreferences.keySoundEnabled(context)) return
        val audio = audio ?: return
        // A silenced or vibrating phone stays quiet, as with the system keyboard sounds.
        if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val effect = when (code) {
            Constants.CODE_DELETE -> AudioManager.FX_KEYPRESS_DELETE
            Constants.CODE_ENTER, Constants.CODE_SHIFT_ENTER -> AudioManager.FX_KEYPRESS_RETURN
            Constants.CODE_SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
            else -> AudioManager.FX_KEYPRESS_STANDARD
        }
        audio.playSoundEffect(effect, KaruikeyPreferences.soundVolume(context) / 100f)
    }
}

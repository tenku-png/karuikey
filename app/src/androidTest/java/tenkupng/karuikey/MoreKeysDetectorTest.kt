package tenkupng.karuikey

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.inputmethod.keyboard.Key
import com.android.inputmethod.keyboard.Keyboard
import com.android.inputmethod.keyboard.MoreKeysDetector
import com.android.inputmethod.keyboard.internal.KeyboardParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MoreKeysDetectorTest {
    // A one-row panel of three 100x100 keys, the way a popup sits right above the finger.
    private val keys = listOf("a", "b", "c").mapIndexed { index, label ->
        Key(label, 0, label[0].code, null, null, 0, Key.BACKGROUND_TYPE_NORMAL,
            index * 100, 0, 100, 100, 0, 0)
    }
    private val detector = MoreKeysDetector(60f).apply {
        val params = KeyboardParams().apply {
            mOccupiedWidth = 300
            mOccupiedHeight = 100
            mBaseWidth = 300
            mBaseHeight = 100
            mMostCommonKeyWidth = 100
            mMostCommonKeyHeight = 100
            GRID_WIDTH = 3
            GRID_HEIGHT = 1
        }
        keys.forEach(params::onAddKey)
        setKeyboard(Keyboard(params), 0f, 0f)
    }

    @Test
    fun touchJustBelowPanelSelectsNearestKey() {
        assertEquals(keys[1], detector.detectHitKey(150, 130))
    }

    @Test
    fun touchJustOutsideSideSelectsEdgeKey() {
        assertEquals(keys[2], detector.detectHitKey(330, 50))
    }

    @Test
    fun farTouchSelectsNothing() {
        assertNull(detector.detectHitKey(150, 400))
    }
}

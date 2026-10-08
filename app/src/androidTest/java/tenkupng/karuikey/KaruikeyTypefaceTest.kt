package tenkupng.karuikey

import android.graphics.Paint
import android.graphics.Typeface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KaruikeyTypefaceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val sample = "Клавиатура keyboard"

    private fun width(typeface: Typeface) = Paint().apply {
        textSize = 48f
        this.typeface = typeface
    }.measureText(sample)

    private fun robotoFlex(width: Float) = Typeface.Builder(context.assets, "roboto_flex.ttf")
        .setFontVariationSettings("'wdth' $width, 'wght' 400")
        .build()

    @Test
    fun widthAxisChangesMeasuredText() {
        assertTrue(width(robotoFlex(75f)) < width(robotoFlex(100f)))
    }

    @Test
    fun keyboardTypefaceIsNotTheSystemDefault() {
        val keyboard = KaruikeyTypeface.create(context, 400)
        assertNotEquals(Typeface.DEFAULT, keyboard)
        assertNotEquals(width(Typeface.DEFAULT), width(keyboard), 0.5f)
    }
}

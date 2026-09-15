package tenkupng.karuikey

import com.android.inputmethod.keyboard.internal.PopupGeometry
import org.junit.Assert.assertEquals
import org.junit.Test

class PopupGeometryTest {
    @Test
    fun positionsFitAtBothEdgesAndInTheCenter() {
        assertEquals(0, PopupGeometry.clampPosition(-20, 80, 320))
        assertEquals(120, PopupGeometry.clampPosition(120, 80, 320))
        assertEquals(240, PopupGeometry.clampPosition(400, 80, 320))
    }

    @Test
    fun oversizedPopupDoesNotProduceNegativePlacement() {
        assertEquals(0, PopupGeometry.clampPosition(40, 400, 320))
    }
}

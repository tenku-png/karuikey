package tenkupng.karuikey

import com.android.inputmethod.keyboard.internal.PopupGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun popupBoundsStayInsideAvailableWidth() {
        val availableWidths = intArrayOf(240, 320, 411, 1080)
        val popupWidths = intArrayOf(48, 80, 128)
        val desiredPositions = intArrayOf(-40, 0, 120, 400, 1200)

        for (availableWidth in availableWidths) {
            for (popupWidth in popupWidths) {
                for (desiredPosition in desiredPositions) {
                    val left = PopupGeometry.clampPosition(
                        desiredPosition, popupWidth, availableWidth)
                    assertTrue(left >= 0)
                    assertTrue(left + popupWidth <= availableWidth)
                }
            }
        }
    }

    @Test
    fun previewHeightFollowsKeyHeightAndStaysCompact() {
        for (keyHeight in intArrayOf(85, 100, 115)) {
            val previewHeight = PopupGeometry.getPreviewHeight(keyHeight, 160)
            assertTrue(previewHeight >= keyHeight * 1.10f)
            assertTrue(previewHeight <= keyHeight * 1.25f)
        }
        assertTrue(
            PopupGeometry.getPreviewHeight(100, 160)
                != PopupGeometry.getPreviewHeight(85, 160)
        )
    }

    @Test
    fun oneCharacterPreviewWidthStaysCloseToItsKey() {
        for (keyWidth in intArrayOf(60, 100, 180, 320)) {
            val previewWidth = PopupGeometry.clampPreviewWidth(keyWidth * 2, keyWidth)
            assertTrue(previewWidth >= keyWidth)
            assertTrue(previewWidth <= keyWidth * 1.25f)
        }
    }

    @Test
    fun previewTextSizeIsBoundedByTheKeyLabelAndIgnoresPopupHeight() {
        for (keyLabelSize in intArrayOf(30, 55, 80, 120)) {
            val previewTextSize = PopupGeometry.getPreviewTextSize(keyLabelSize)
            assertTrue(previewTextSize >= keyLabelSize * 1.20f)
            assertTrue(previewTextSize <= keyLabelSize * 1.35f)
            assertEquals(
                previewTextSize,
                PopupGeometry.getPreviewTextSize(keyLabelSize)
            )
        }
        assertTrue(
            PopupGeometry.getPreviewTextSize(80) < PopupGeometry.getPreviewHeight(100, 160)
        )
    }
}

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
    fun multiOptionPopupFitsAtEdgesAndKeepsBothItemsVisible() {
        val availableWidth = 320
        val containerPadding = 4
        val contentWidth = availableWidth - containerPadding * 2
        val optionCounts = intArrayOf(2, 3, 4, 7)
        val touchPositions = intArrayOf(0, availableWidth / 2, availableWidth - 1)

        for (optionCount in optionCounts) {
            val columns = minOf(optionCount, 5)
            val divider = PopupGeometry.fitPopupDividerWidth(columns, 16, contentWidth)
            val keyWidth = PopupGeometry.fitPopupKeyWidth(96, columns, divider, contentWidth)
            val popupContentWidth = PopupGeometry.getPopupWidth(columns, keyWidth, divider)
            val popupWidth = popupContentWidth + containerPadding * 2

            for (touchX in touchPositions) {
                val left = PopupGeometry.getPanelLeft(
                    touchX, keyWidth / 2, popupWidth, availableWidth, containerPadding
                )
                assertTrue(left >= 0)
                assertTrue(left + popupWidth <= availableWidth)
                assertTrue(left + containerPadding >= 0)
                assertTrue(left + containerPadding + popupContentWidth <= availableWidth - containerPadding)

                val contentLeft = left + containerPadding
                val translatedTouch = touchX - contentLeft
                assertTrue(translatedTouch >= -keyWidth)
                assertTrue(translatedTouch <= popupContentWidth + keyWidth)
            }
        }
    }

    @Test
    fun popupColumnAndKeyGeometryNeverExceedsAvailableWidth() {
        for (availableWidth in intArrayOf(96, 160, 240, 320, 411, 1080)) {
            for (optionCount in intArrayOf(2, 3, 4, 7)) {
                val requestedColumns = minOf(optionCount, 5)
                val columns = PopupGeometry.fitPopupColumnCount(
                    optionCount, requestedColumns, 24, availableWidth
                )
                val divider = PopupGeometry.fitPopupDividerWidth(columns, 24, availableWidth)
                val keyWidth = PopupGeometry.fitPopupKeyWidth(
                    120, columns, divider, availableWidth
                )
                assertTrue(PopupGeometry.getPopupWidth(columns, keyWidth, divider) <= availableWidth)
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

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
    fun popupLayoutsFitCellsWithinBothViewportDimensions() {
        for (availableWidth in intArrayOf(180, 320, 411, 1080)) {
            for (availableHeight in intArrayOf(180, 260, 420, 760)) {
                for (optionCount in intArrayOf(2, 3, 4, 5, 6, 8, 10, 12)) {
                    val viewportWidth = availableWidth - 8
                    val viewportHeight = availableHeight - 8
                    val layout = PopupGeometry.fitPopupLayout(
                        optionCount, 5, 120, 150, 12, 4,
                        6, 6, 6, 6, viewportWidth, viewportHeight
                    )
                    assertTrue(layout.width + 8 <= availableWidth)
                    assertTrue(layout.height + 8 <= availableHeight)
                    assertTrue(layout.columns >= 1)
                    assertTrue(layout.rows >= 1)
                    assertTrue(layout.rows * layout.columns >= optionCount)
                    for (index in 0 until optionCount) {
                        val row = index / layout.columns
                        val column = index % layout.columns
                        val left = 6 + column * (layout.keyWidth + layout.dividerWidth)
                        val top = 6 + row * layout.rowHeight
                        assertTrue(left >= 6)
                        assertTrue(left + layout.keyWidth <= layout.width - 6)
                        assertTrue(top >= 6)
                        val cellHeight = layout.rowHeight - 4
                        assertTrue(top + cellHeight <= layout.height - 6)
                    }
                }
            }
        }
    }

    @Test
    fun popupLayoutClampsPaddingWhenViewportIsSmallerThanTheNormalInsets() {
        for (availableWidth in intArrayOf(1, 2, 7, 20)) {
            for (availableHeight in intArrayOf(20, 40)) {
                val layout = PopupGeometry.fitPopupLayout(
                    12, 5, 120, 150, 12, 4,
                    6, 6, 6, 6, availableWidth, availableHeight
                )
                assertTrue(layout.width <= availableWidth)
                assertTrue(layout.height <= availableHeight)
            }
        }
    }

    @Test
    fun popupOriginUsesTheSameClampedPanelForDrawingAndTouch() {
        for (availableWidth in intArrayOf(240, 411, 1080)) {
            val layout = PopupGeometry.fitPopupLayout(
                12, 5, 160, 150, 12, 4,
                6, 6, 6, 6, availableWidth - 8, 420 - 8
            )
            for (touchX in intArrayOf(0, availableWidth / 2, availableWidth - 1)) {
                val outerWidth = layout.width + 8
                val panelLeft = PopupGeometry.getPanelLeft(
                    touchX, 6 + layout.keyWidth / 2, outerWidth, availableWidth, 4
                )
                assertTrue(panelLeft >= 0)
                assertTrue(panelLeft + outerWidth <= availableWidth)
                val origin = panelLeft + 4 + 6
                assertTrue(touchX - origin <= layout.width)
                assertEquals(panelLeft, PopupGeometry.clampPosition(
                    touchX - (6 + layout.keyWidth / 2) - 4,
                    outerWidth, availableWidth
                ))
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

    @Test
    fun topRowPanelRisesIntoRoomAboveTheKeys() {
        // Desired top above the keys: allowed as far as the toolbar area reaches.
        assertEquals(-80, PopupGeometry.getPanelTop(-80, 120, 600, 150))
        assertEquals(-150, PopupGeometry.getPanelTop(-400, 120, 600, 150))
        // Without room above the old clamp to the keyboard top applies.
        assertEquals(0, PopupGeometry.getPanelTop(-80, 120, 600, 0))
        // The bottom edge still stays inside the keyboard.
        assertEquals(480, PopupGeometry.getPanelTop(560, 120, 600, 150))
    }
}

package tenkupng.karuikey

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpaceCursorTest {
    @Test
    fun tinyMovementDoesNotEnterCursorMode() {
        assertFalse(SpaceCursor.triggerReached(11, 12))
        assertTrue(SpaceCursor.triggerReached(12, 12))
    }

    @Test
    fun distanceProducesWholeStepsAndKeepsRemainder() {
        assertEquals(2, SpaceCursor.stepCount(58, 24))
        assertEquals(10, SpaceCursor.remainder(58, 2, 24))
        assertEquals(-2, SpaceCursor.stepCount(-58, 24))
        assertEquals(-10, SpaceCursor.remainder(-58, -2, 24))
    }

    @Test
    fun codePointWidthsHandleSurrogatePairs() {
        assertEquals(2, SpaceCursor.lastCodePointWidth("a\uD83D\uDE00"))
        assertEquals(2, SpaceCursor.firstCodePointWidth("\uD83D\uDE00b"))
        assertEquals(0, SpaceCursor.lastCodePointWidth(""))
        assertEquals(0, SpaceCursor.firstCodePointWidth(""))
    }
}

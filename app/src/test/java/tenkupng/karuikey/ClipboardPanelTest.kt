package tenkupng.karuikey

import org.junit.Assert.assertEquals
import org.junit.Test

class ClipboardPanelTest {
    @Test
    fun disabledHistoryShowsOnlyCurrentClipboardWithoutDuplication() {
        val current = ClipboardHistoryItem("current", 0)
        assertEquals(
            listOf(current),
            clipboardPanelItems(false, "current", listOf(ClipboardHistoryItem("old", 1)))
        )
    }

    @Test
    fun enabledHistoryKeepsNewestCurrentTextOnce() {
        val history = listOf(ClipboardHistoryItem("current", 10), ClipboardHistoryItem("old", 1))
        assertEquals(history, clipboardPanelItems(true, "current", history))
    }
}

package tenkupng.karuikey

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClipboardHistoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences = context.getSharedPreferences(
        "karuikey_clipboard_history", Context.MODE_PRIVATE
    )

    @Before
    fun clearBeforeTest() {
        preferences.edit().clear().commit()
    }

    @After
    fun clearAfterTest() {
        preferences.edit().clear().commit()
    }

    @Test
    fun historyIsOptInAndSensitiveTextIsRejected() {
        assertFalse(ClipboardHistory.add(context, "private", 0))
        assertTrue(ClipboardHistory.items(context).isEmpty())

        ClipboardHistory.setEnabled(context, true)
        assertFalse(ClipboardHistory.add(context, "password", 0, sensitive = true))
        assertTrue(ClipboardHistory.add(context, "safe", 0))
        assertEquals(listOf("safe"), ClipboardHistory.items(context, 1).map { it.text })
    }

    @Test
    fun duplicateMovesToNewestAndMaxCountIsBounded() {
        ClipboardHistory.setEnabled(context, true)
        ClipboardHistory.setMaxItems(context, 5)
        for (index in 0..5) ClipboardHistory.add(context, "item$index", index.toLong())
        ClipboardHistory.add(context, "item2", 10)

        val items = ClipboardHistory.items(context, 10)
        assertEquals(5, items.size)
        assertEquals("item2", items.first().text)
        assertEquals(10L, items.first().timestamp)
        assertEquals(5, items.map { it.text }.toSet().size)
    }

    @Test
    fun allRetentionOptionsExpireWithoutWaiting() {
        ClipboardHistory.setEnabled(context, true)
        for (hours in ClipboardHistory.retentionOptions) {
            ClipboardHistory.clear(context)
            ClipboardHistory.setRetentionHours(context, hours)
            ClipboardHistory.add(context, "item", 0)
            val expiry = hours * 60L * 60L * 1000L
            assertTrue(ClipboardHistory.items(context, expiry).isEmpty())
        }
    }

    @Test
    fun clearAndOversizedEntriesAreSafe() {
        ClipboardHistory.setEnabled(context, true)
        assertFalse(ClipboardHistory.add(context, " ", 0))
        assertFalse(ClipboardHistory.add(
            context,
            "x".repeat(ClipboardHistory.MAX_STORED_TEXT_LENGTH + 1),
            0
        ))
        ClipboardHistory.add(context, "kept", 0)
        ClipboardHistory.clear(context)
        assertTrue(ClipboardHistory.items(context, 1).isEmpty())
    }
}

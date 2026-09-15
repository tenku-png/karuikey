package tenkupng.karuikey

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ClipboardHistoryItem(
    val text: String,
    val timestamp: Long
)

object ClipboardHistory {
    const val RETENTION_HOUR = 1
    const val RETENTION_DAY = 24
    const val RETENTION_THREE_DAYS = 24 * 3
    const val RETENTION_WEEK = 24 * 7
    const val RETENTION_MONTH = 24 * 30
    const val DEFAULT_MAX_ITEMS = 20
    const val MAX_STORED_TEXT_LENGTH = 16 * 1024

    private const val PREFS = "karuikey_clipboard_history"
    private const val ENABLED = "enabled"
    private const val RETENTION_HOURS = "retention_hours"
    private const val MAX_ITEMS = "max_items"
    private const val ITEMS = "items"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabled(context: Context) = prefs(context).getBoolean(ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(ENABLED, enabled).apply()
    }

    fun retentionHours(context: Context) = prefs(context).getInt(RETENTION_HOURS, RETENTION_DAY)
        .takeIf { it in retentionOptions } ?: RETENTION_DAY

    fun setRetentionHours(context: Context, hours: Int) {
        if (hours in retentionOptions) {
            prefs(context).edit().putInt(RETENTION_HOURS, hours).apply()
        }
    }

    fun maxItems(context: Context) = prefs(context).getInt(MAX_ITEMS, DEFAULT_MAX_ITEMS)
        .coerceIn(5, 50)

    fun setMaxItems(context: Context, count: Int) {
        prefs(context).edit().putInt(MAX_ITEMS, count.coerceIn(5, 50)).apply()
    }

    fun items(context: Context, now: Long = System.currentTimeMillis()): List<ClipboardHistoryItem> {
        val stored = read(context)
        val valid = stored.filter { !isExpired(context, it.timestamp, now) }
        if (valid.size != stored.size) write(context, valid)
        return valid
    }

    fun add(
        context: Context,
        text: String,
        now: Long = System.currentTimeMillis(),
        sensitive: Boolean = false
    ): Boolean {
        if (!enabled(context) || sensitive || text.isBlank() ||
            text.length > MAX_STORED_TEXT_LENGTH
        ) return false
        val updated = items(context, now).filterNot { it.text == text }.toMutableList()
        updated.add(0, ClipboardHistoryItem(text, now))
        write(context, updated.take(maxItems(context)))
        return true
    }

    fun remove(context: Context, timestamp: Long) {
        val updated = read(context).toMutableList()
        val index = updated.indexOfFirst { it.timestamp == timestamp }
        if (index >= 0) updated.removeAt(index)
        write(context, updated)
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(ITEMS).apply()
    }

    fun purge(context: Context, now: Long = System.currentTimeMillis()) {
        items(context, now)
    }

    val retentionOptions = intArrayOf(
        RETENTION_HOUR,
        RETENTION_DAY,
        RETENTION_THREE_DAYS,
        RETENTION_WEEK,
        RETENTION_MONTH
    )

    private fun isExpired(context: Context, timestamp: Long, now: Long): Boolean =
        timestamp > now || now - timestamp >= retentionHours(context) * 60L * 60L * 1000L

    private fun read(context: Context): List<ClipboardHistoryItem> {
        val encoded = prefs(context).getString(ITEMS, null) ?: return emptyList()
        val result = ArrayList<ClipboardHistoryItem>()
        try {
            val array = JSONArray(encoded)
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val text = item.optString("text", "")
                val timestamp = item.optLong("timestamp", -1)
                if (text.isNotBlank() && text.length <= MAX_STORED_TEXT_LENGTH && timestamp >= 0) {
                    result.add(ClipboardHistoryItem(text, timestamp))
                }
            }
        } catch (_: Exception) {
            return emptyList()
        }
        return result
    }

    private fun write(context: Context, items: List<ClipboardHistoryItem>) {
        val array = JSONArray()
        items.take(maxItems(context)).forEach { item ->
            array.put(JSONObject().put("text", item.text).put("timestamp", item.timestamp))
        }
        prefs(context).edit().putString(ITEMS, array.toString()).apply()
    }
}

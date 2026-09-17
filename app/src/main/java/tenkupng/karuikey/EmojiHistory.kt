package tenkupng.karuikey

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal data class EmojiHistoryEntry(
    val emoji: String,
    val count: Int,
    val lastUsed: Long
)

/** Small local emoji-only history; it never stores surrounding typed text. */
internal object EmojiHistory {
    private const val PREFS = "karuikey_emoji_history"
    private const val ITEMS = "items"
    private const val MAX_ITEMS = 40

    @Synchronized
    fun recent(context: Context): List<String> = read(context)
        .sortedWith(compareByDescending<EmojiHistoryEntry> { it.count }
            .thenByDescending { it.lastUsed })
        .map { it.emoji }

    @Synchronized
    fun record(context: Context, emoji: String) {
        if (emoji.isEmpty()) return
        val now = System.currentTimeMillis()
        val entries = read(context).associateBy { it.emoji }.toMutableMap()
        val old = entries[emoji]
        entries[emoji] = EmojiHistoryEntry(emoji, (old?.count ?: 0) + 1, now)
        val array = JSONArray()
        entries.values.sortedWith(compareByDescending<EmojiHistoryEntry> { it.count }
            .thenByDescending { it.lastUsed })
            .take(MAX_ITEMS)
            .forEach { entry ->
                array.put(JSONObject().put("emoji", entry.emoji)
                    .put("count", entry.count).put("lastUsed", entry.lastUsed))
            }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(ITEMS, array.toString()).apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(ITEMS).apply()
    }

    private fun read(context: Context): List<EmojiHistoryEntry> {
        val encoded = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(ITEMS, null) ?: return emptyList()
        return try {
            val array = JSONArray(encoded)
            buildList(array.length()) {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val emoji = item.optString("emoji")
                    val count = item.optInt("count", 0)
                    val lastUsed = item.optLong("lastUsed", 0L)
                    if (emoji.isNotEmpty() && count > 0 && lastUsed > 0) {
                        add(EmojiHistoryEntry(emoji, count, lastUsed))
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

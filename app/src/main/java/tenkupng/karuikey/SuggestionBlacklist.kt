package tenkupng.karuikey

import android.content.Context
import java.util.Locale

internal object SuggestionBlacklist {
    private const val PREFS = "karuikey_suggestion_blacklist"

    fun add(context: Context, locale: String, word: String) {
        val key = key(locale)
        val values = values(context, key)
        values.add(word)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putStringSet(key, values).apply()
    }

    fun contains(context: Context, locale: String, word: String): Boolean =
        values(context, key(locale)).any { it.equals(word, ignoreCase = true) }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun values(context: Context, key: String): MutableSet<String> =
        (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(key, emptySet()) ?: emptySet()).toMutableSet()

    private fun key(locale: String) = locale.lowercase(Locale.ROOT)
}

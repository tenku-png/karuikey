package tenkupng.karuikey

import android.content.Context
import java.util.Locale

/** Explicit opt-in local word and next-word counts; nothing leaves the device. */
internal object PredictionHistory {
    private const val PREFS = "karuikey_prediction_history"
    private const val DATA = "entries"
    private val models = HashMap<String, HistoryModel>()

    fun record(context: Context, locale: String, word: String, previousWord: String?) {
        if (!KaruikeyPreferences.personalizedSuggestionsEnabled(context) || word.isBlank()) return
        val model = model(context, locale)
        model.record(word, previousWord)
        persist(context, locale, model)
    }

    fun fill(context: Context, locale: String, prefix: CharSequence, previousWord: String?,
            out: MutableList<String>) {
        if (!KaruikeyPreferences.personalizedSuggestionsEnabled(context)) return
        model(context, locale).fill(prefix, previousWord, out)
    }

    /** Learned words starting with [prefix] with their use counts, most used first. */
    fun matching(context: Context, locale: String, prefix: CharSequence): List<Pair<String, Int>> {
        if (!KaruikeyPreferences.personalizedSuggestionsEnabled(context)) return emptyList()
        return model(context, locale).matching(prefix)
    }

    fun count(context: Context, locale: String, word: String): Int =
        if (!KaruikeyPreferences.personalizedSuggestionsEnabled(context)) 0
        else model(context, locale).count(word)

    fun nextCount(context: Context, locale: String, previousWord: String?, word: String): Int =
        if (!KaruikeyPreferences.personalizedSuggestionsEnabled(context) || previousWord == null) 0
        else model(context, locale).nextCount(previousWord, word)

    fun clear(context: Context) {
        synchronized(models) { models.clear() }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun model(context: Context, locale: String): HistoryModel {
        val key = "${context.packageName}:$locale"
        synchronized(models) {
            return models.getOrPut(key) {
                HistoryModel().also { history ->
                    val entries = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .getStringSet(key, emptySet()) ?: emptySet()
                    history.restore(entries)
                }
            }
        }
    }

    private fun persist(context: Context, locale: String, model: HistoryModel) {
        val key = "${context.packageName}:$locale"
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putStringSet(key, model.serialize())
            .apply()
    }
}

internal class HistoryModel {
    private companion object {
        const val MAX_MATCHES = 8
    }

    private val wordCounts = HashMap<String, Int>()
    private val nextCounts = HashMap<String, HashMap<String, Int>>()

    @Synchronized
    fun record(word: String, previousWord: String?) {
        val cleanWord = word.trim().lowercase(Locale.ROOT)
        if (cleanWord.isEmpty()) return
        wordCounts[cleanWord] = (wordCounts[cleanWord] ?: 0) + 1
        val previous = previousWord?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (previous.isNotEmpty()) {
            val next = nextCounts.getOrPut(previous) { HashMap() }
            next[cleanWord] = (next[cleanWord] ?: 0) + 1
        }
    }

    @Synchronized
    fun fill(prefix: CharSequence, previousWord: String?, out: MutableList<String>) {
        val wanted = prefix.toString().lowercase(Locale.ROOT)
        if (wanted.isEmpty() && !previousWord.isNullOrBlank()) {
            appendSorted(nextCounts[previousWord.lowercase(Locale.ROOT)], wanted, out)
        }
        appendSorted(wordCounts, wanted, out)
    }

    @Synchronized
    fun matching(prefix: CharSequence): List<Pair<String, Int>> {
        val wanted = prefix.toString().lowercase(Locale.ROOT)
        return wordCounts.entries.filter { it.key.startsWith(wanted) }
            .sortedByDescending { it.value }
            .take(MAX_MATCHES)
            .map { it.key to it.value }
    }

    @Synchronized
    fun count(word: String): Int = wordCounts[word.lowercase(Locale.ROOT)] ?: 0

    @Synchronized
    fun nextCount(previousWord: String, word: String): Int =
        nextCounts[previousWord.lowercase(Locale.ROOT)]?.get(word.lowercase(Locale.ROOT)) ?: 0

    @Synchronized
    fun serialize(): Set<String> {
        val entries = HashSet<String>(wordCounts.size + nextCounts.size)
        wordCounts.forEach { (word, count) -> entries.add("w\t$word\t$count") }
        nextCounts.forEach { (previous, words) ->
            words.forEach { (word, count) -> entries.add("n\t$previous\t$word\t$count") }
        }
        return entries
    }

    @Synchronized
    fun restore(entries: Set<String>) {
        entries.forEach { entry ->
            val fields = entry.split('\t')
            val count = fields.lastOrNull()?.toIntOrNull() ?: return@forEach
            if (count <= 0) return@forEach
            when {
                fields.size == 3 && fields[0] == "w" -> wordCounts[fields[1]] = count
                fields.size == 4 && fields[0] == "n" ->
                    nextCounts.getOrPut(fields[1]) { HashMap() }[fields[2]] = count
            }
        }
    }

    private fun appendSorted(counts: Map<String, Int>?, prefix: String, out: MutableList<String>) {
        counts?.entries
            ?.asSequence()
            ?.filter { it.key.lowercase(Locale.ROOT).startsWith(prefix) }
            ?.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }
                .thenBy { it.key })
            ?.forEach { entry ->
                if (out.size < 3 && !out.contains(entry.key)) out.add(entry.key)
            }
    }
}

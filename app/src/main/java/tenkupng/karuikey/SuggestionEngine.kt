package tenkupng.karuikey

import android.content.Context
import android.content.res.AssetManager
import com.android.inputmethod.keyboard.Keyboard
import com.android.inputmethod.latin.common.InputPointers

/** Offline dictionary access with one lazily loaded active-language index. */
object SuggestionEngine {
    private data class DictionaryAsset(val languagePrefix: String, val assetName: String)

    private val dictionaryAssets = arrayOf(
        DictionaryAsset("en", "dictionaries/en_us.krd"),
        DictionaryAsset("ru", "dictionaries/ru.krd")
    )

    @Volatile
    private var assetManager: AssetManager? = null
    @Volatile
    private var activeAssetName: String? = null
    @Volatile
    private var activeDictionary: LocalDictionary? = null
    @Volatile
    private var loadingAssetName: String? = null
    private var loadReadyCallback: (() -> Unit)? = null
    private var loadGeneration = 0
    private var loader: Thread? = null

    @JvmStatic
    @Synchronized
    fun initialize(context: Context) {
        if (assetManager == null) assetManager = context.applicationContext.assets
    }

    /** Starts loading only the dictionary needed by the current input session. */
    @Synchronized
    fun beginSession(locale: String, onReady: (() -> Unit)? = null) {
        val assetName = assetNameForLocale(locale)
        if (assetName == null) {
            loadGeneration++
            activeAssetName = null
            activeDictionary = null
            loadingAssetName = null
            loadReadyCallback = null
            return
        }
        if (activeAssetName == assetName && activeDictionary != null) {
            onReady?.invoke()
            return
        }
        if (loadingAssetName == assetName) {
            loadReadyCallback = onReady
            return
        }

        loadGeneration++
        val generation = loadGeneration
        activeAssetName = null
        activeDictionary = null
        loadingAssetName = assetName
        loadReadyCallback = onReady
        val manager = assetManager ?: run {
            loadingAssetName = null
            loadReadyCallback = null
            return
        }
        loader = Thread({
            val dictionary = try {
                manager.open(assetName, AssetManager.ACCESS_STREAMING).use { input ->
                    LocalDictionary(input.readBytes())
                }
            } catch (_: Exception) {
                null
            }
            var readyCallback: (() -> Unit)? = null
            synchronized(this) {
                if (generation == loadGeneration && loadingAssetName == assetName) {
                    activeDictionary = dictionary
                    activeAssetName = if (dictionary == null) null else assetName
                    loadingAssetName = null
                    loader = null
                    readyCallback = loadReadyCallback
                    loadReadyCallback = null
                }
            }
            readyCallback?.invoke()
        }, "KaruikeyDictionaryLoader").apply {
            isDaemon = true
            start()
        }
    }

    /** Releases the active-language bytes and invalidates an in-flight load. */
    @Synchronized
    fun endSession() {
        loadGeneration++
        activeAssetName = null
        activeDictionary = null
        loadingAssetName = null
        loadReadyCallback = null
    }

    /** Fills prefix or next-word candidates from the current active-language dictionary. */
    fun fill(locale: String, prefix: CharSequence, out: MutableList<String>) {
        fill(locale, null, null, null, prefix, out)
    }

    fun fill(
        locale: String,
        previousWord: String?,
        prefix: CharSequence,
        out: MutableList<String>
    ) {
        fill(locale, previousWord, null, null, prefix, out)
    }

    fun fill(
        locale: String,
        previousWord: String?,
        secondPreviousWord: String?,
        thirdPreviousWord: String?,
        prefix: CharSequence,
        out: MutableList<String>
    ) {
        out.clear()
        dictionaryFor(locale)?.fill(
            previousWord, secondPreviousWord, thirdPreviousWord, prefix, out
        )
    }

    fun hasDictionary(locale: String): Boolean = assetNameForLocale(locale) != null

    fun findGestureCandidate(locale: String, sequence: CharSequence): String? =
        dictionaryFor(locale)?.findGestureCandidate(sequence)

    fun findGestureCandidate(locale: String, keyboard: Keyboard?, points: InputPointers): String? =
        findGestureCandidate(locale, keyboard, points, null)

    fun findGestureCandidate(
        locale: String,
        keyboard: Keyboard?,
        points: InputPointers,
        previousWord: String?
    ): String? {
        // The current gesture decoder produces an exact or one-repeated-letter sequence. The
        // indexed dictionary resolves that sequence without materializing the whole vocabulary.
        return dictionaryFor(locale)?.findGestureCandidate(GestureDecoder.decode(keyboard, points))
    }

    internal fun isReady(locale: String): Boolean = dictionaryFor(locale) != null

    /** Synchronous test hook; production input sessions always use beginSession(). */
    internal fun loadForTests(context: Context, locale: String) {
        initialize(context)
        beginSession(locale)
        val thread = synchronized(this) { loader }
        thread?.join(10_000)
    }

    private fun dictionaryFor(locale: String): LocalDictionary? {
        val assetName = assetNameForLocale(locale) ?: return null
        return activeDictionary?.takeIf { activeAssetName == assetName }
    }

    private fun assetNameForLocale(locale: String): String? {
        for (asset in dictionaryAssets) {
            if (locale.startsWith(asset.languagePrefix, ignoreCase = true)) {
                return asset.assetName
            }
        }
        return null
    }
}

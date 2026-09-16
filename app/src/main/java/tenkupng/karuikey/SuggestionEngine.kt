package tenkupng.karuikey

import android.content.Context
import android.content.res.AssetManager
import android.net.Uri
import com.android.inputmethod.keyboard.Keyboard
import com.android.inputmethod.latin.common.InputPointers

/** Offline dictionary access with one lazily loaded active-language index. */
object SuggestionEngine {
    enum class DictionarySource { NONE, BUNDLED, EXTERNAL }

    private data class DictionaryAsset(val languagePrefix: String, val assetName: String)

    private val dictionaryAssets = arrayOf(
        DictionaryAsset("en", "dictionaries/en_us.krd"),
        DictionaryAsset("ru", "dictionaries/ru.krd")
    )

    @Volatile
    private var assetManager: AssetManager? = null
    @Volatile
    private var appContext: Context? = null
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
        if (assetManager == null) {
            val application = context.applicationContext
            appContext = application
            assetManager = application.assets
        }
    }

    /** Starts loading only the dictionary needed by the current input session. */
    @Synchronized
    fun beginSession(locale: String, onReady: (() -> Unit)? = null) {
        val sourceName = sourceNameForLocale(locale)
        if (sourceName == null) {
            loadGeneration++
            activeAssetName = null
            activeDictionary = null
            loadingAssetName = null
            loadReadyCallback = null
            return
        }
        if (activeAssetName == sourceName && activeDictionary != null) {
            onReady?.invoke()
            return
        }
        if (loadingAssetName == sourceName) {
            loadReadyCallback = onReady
            return
        }

        loadGeneration++
        val generation = loadGeneration
        activeAssetName = null
        activeDictionary = null
        loadingAssetName = sourceName
        loadReadyCallback = onReady
        val manager = assetManager ?: run {
            loadingAssetName = null
            loadReadyCallback = null
            return
        }
        loader = Thread({
            val dictionary = try {
                if (sourceName.startsWith(EXTERNAL_SOURCE_PREFIX)) {
                    val uri = Uri.parse(sourceName.removePrefix(EXTERNAL_SOURCE_PREFIX))
                    appContext?.contentResolver?.openInputStream(uri)?.use(LocalDictionary::read)
                } else {
                    manager.open(sourceName, AssetManager.ACCESS_STREAMING).use(LocalDictionary::read)
                }
            } catch (_: Exception) {
                null
            }
            var readyCallback: (() -> Unit)? = null
            synchronized(this) {
                if (generation == loadGeneration && loadingAssetName == sourceName) {
                    activeDictionary = dictionary
                    activeAssetName = if (dictionary == null) null else sourceName
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
        val sourceName = sourceNameForLocale(locale) ?: return null
        return activeDictionary?.takeIf { activeAssetName == sourceName }
    }

    fun dictionarySource(locale: String): DictionarySource = when {
        appContext?.let { KaruikeyPreferences.dictionaryUriString(it, locale) }
            ?.let { Uri.parse(it).scheme == CONTENT_URI_SCHEME } == true ->
            DictionarySource.EXTERNAL
        assetNameForLocale(locale) != null -> DictionarySource.BUNDLED
        else -> DictionarySource.NONE
    }

    fun hasDictionary(locale: String): Boolean = dictionarySource(locale) != DictionarySource.NONE

    private fun sourceNameForLocale(locale: String): String? {
        val external = appContext?.let { KaruikeyPreferences.dictionaryUriString(it, locale) }
        if (!external.isNullOrBlank() && Uri.parse(external).scheme == CONTENT_URI_SCHEME) {
            return EXTERNAL_SOURCE_PREFIX + external
        }
        return assetNameForLocale(locale)
    }

    private fun assetNameForLocale(locale: String): String? {
        for (asset in dictionaryAssets) {
            if (locale.startsWith(asset.languagePrefix, ignoreCase = true)) {
                return asset.assetName
            }
        }
        return null
    }

    private const val EXTERNAL_SOURCE_PREFIX = "uri:"
    private const val CONTENT_URI_SCHEME = "content"
}

package tenkupng.karuikey

import android.content.Context
import android.content.res.AssetManager
import android.net.Uri
import com.android.inputmethod.keyboard.Keyboard
import com.android.inputmethod.latin.BinaryDictionary
import com.android.inputmethod.latin.common.InputPointers
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** One active-language, offline dictionary facade for the IME. */
object SuggestionEngine {
    enum class DictionarySource { NONE, BUNDLED, EXTERNAL }

    private data class DictionaryAsset(
        val languagePrefix: String,
        val matureAssetName: String
    )

    private data class SessionRequest(
        val generation: Long,
        val locale: String,
        val sourceName: String?
    )

    private data class FillRequest(
        val generation: Long,
        val sourceName: String?,
        val locale: String,
        val previousWord: String?,
        val secondPreviousWord: String?,
        val thirdPreviousWord: String?,
        val prefix: String,
        val keyboard: Keyboard?,
        val xCoordinates: IntArray?,
        val yCoordinates: IntArray?,
        val sentenceStart: Boolean,
        val onResult: (List<String>) -> Unit
    )

    private data class GestureRequest(
        val generation: Long,
        val sourceName: String?,
        val locale: String,
        val keyboard: Keyboard?,
        val points: InputPointers,
        val previousWord: String?,
        val onResult: (String?) -> Unit
    )

    private val dictionaryAssets = arrayOf(
        DictionaryAsset("en", "dictionaries/main_en_us.dict"),
        DictionaryAsset("ru", "dictionaries/main_ru.dict")
    )

    @Volatile private var assetManager: AssetManager? = null
    @Volatile private var appContext: Context? = null
    @Volatile private var activeSourceName: String? = null
    @Volatile private var readySourceName: String? = null

    // All active dictionary handles and native calls are owned by this worker.
    private val worker: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "KaruikeySuggestionWorker").apply { isDaemon = true }
    }
    private val workLock = Any()
    private var workerScheduled = false
    private var pendingSession: SessionRequest? = null
    private var pendingFill: FillRequest? = null
    private var pendingGesture: GestureRequest? = null
    private var readyCallback: (() -> Unit)? = null
    private var sessionGeneration = 0L
    private var requestedSourceName: String? = null

    private var activeDictionary: BinaryDictionary? = null
    private var activeLegacyDictionary: LocalDictionary? = null
    private val candidates = ArrayList<BinaryDictionary.Candidate>(18)

    @JvmStatic
    @Synchronized
    fun initialize(context: Context) {
        if (assetManager == null) {
            val application = context.applicationContext
            appContext = application
            assetManager = application.assets
        }
    }

    /** Starts loading the selected dictionary without blocking the IME thread. */
    fun beginSession(locale: String, onReady: (() -> Unit)? = null) {
        val sourceName = sourceNameForLocale(locale)
        var callback: (() -> Unit)? = null
        synchronized(workLock) {
            sessionGeneration++
            val generation = sessionGeneration
            requestedSourceName = sourceName
            readyCallback = onReady
            pendingFill = null
            pendingGesture = null
            pendingSession = SessionRequest(generation, locale, sourceName)
            readySourceName = null
            if (sourceName != null && sourceName == activeSourceName &&
                (activeDictionary != null || activeLegacyDictionary != null)
            ) {
                readySourceName = sourceName
                pendingSession = null
                callback = readyCallback
                readyCallback = null
            }
            scheduleWorkerLocked()
        }
        callback?.invoke()
    }

    fun endSession() {
        synchronized(workLock) {
            sessionGeneration++
            requestedSourceName = null
            pendingFill = null
            pendingGesture = null
            pendingSession = SessionRequest(sessionGeneration, "", null)
            readyCallback = null
            readySourceName = null
            scheduleWorkerLocked()
        }
    }

    /** Queues only the newest prefix/context snapshot. The callback runs off the IME thread. */
    fun requestFill(
        locale: String,
        previousWord: String?,
        secondPreviousWord: String?,
        thirdPreviousWord: String?,
        prefix: CharSequence,
        keyboard: Keyboard?,
        xCoordinates: IntArray? = null,
        yCoordinates: IntArray? = null,
        sentenceStart: Boolean = false,
        onResult: (List<String>) -> Unit
    ) {
        val sourceName = sourceNameForLocale(locale)
        synchronized(workLock) {
            pendingFill = FillRequest(
                sessionGeneration,
                sourceName,
                locale,
                previousWord,
                secondPreviousWord,
                thirdPreviousWord,
                prefix.toString(),
                keyboard,
                xCoordinates,
                yCoordinates,
                sentenceStart,
                onResult
            )
            pendingGesture = null
            scheduleWorkerLocked()
        }
    }

    /** Queues gesture recognition so it cannot block key delivery or race dictionary close. */
    fun requestGestureCandidate(
        locale: String,
        keyboard: Keyboard?,
        points: InputPointers,
        previousWord: String?,
        onResult: (String?) -> Unit
    ) {
        val sourceName = sourceNameForLocale(locale)
        synchronized(workLock) {
            pendingGesture = GestureRequest(
                sessionGeneration, sourceName, locale, keyboard, points, previousWord, onResult
            )
            pendingFill = null
            scheduleWorkerLocked()
        }
    }

    fun fill(locale: String, prefix: CharSequence, out: MutableList<String>) =
        fill(locale, null, null, null, prefix, out, null)

    fun fill(locale: String, previousWord: String?, prefix: CharSequence,
            out: MutableList<String>) =
        fill(locale, previousWord, null, null, prefix, out, null)

    /** Synchronous compatibility wrapper for tests and non-IME callers. */
    fun fill(
        locale: String,
        previousWord: String?,
        secondPreviousWord: String?,
        thirdPreviousWord: String?,
        prefix: CharSequence,
        out: MutableList<String>,
        keyboard: Keyboard? = null
    ) {
        val result = ArrayList<String>(3)
        val ready = CountDownLatch(1)
        requestFill(locale, previousWord, secondPreviousWord, thirdPreviousWord, prefix, keyboard) {
            result.addAll(it)
            ready.countDown()
        }
        if (!ready.await(10, TimeUnit.SECONDS)) result.clear()
        out.clear()
        out.addAll(result)
    }

    fun findGestureCandidate(locale: String, sequence: CharSequence): String? =
        findGestureCandidate(locale, null, sequence)

    private fun findGestureCandidate(
        locale: String,
        keyboard: Keyboard?,
        sequence: CharSequence
    ): String? {
        val result = arrayOfNulls<String>(1)
        val ready = CountDownLatch(1)
        val sourceName = sourceNameForLocale(locale)
        synchronized(workLock) {
            val generation = sessionGeneration
            worker.execute {
                if (isCurrent(generation, sourceName)) {
                    result[0] = activeLegacyDictionary
                        ?.takeIf { activeSourceName == sourceName }
                        ?.findGestureCandidate(sequence)
                }
                ready.countDown()
            }
        }
        ready.await(10, TimeUnit.SECONDS)
        return result[0]
    }

    fun findGestureCandidate(locale: String, keyboard: Keyboard?, points: InputPointers): String? =
        findGestureCandidate(locale, keyboard, points, null)

    fun findGestureCandidate(
        locale: String,
        keyboard: Keyboard?,
        points: InputPointers,
        previousWord: String?
    ): String? {
        val result = arrayOfNulls<String>(1)
        val ready = CountDownLatch(1)
        requestGestureCandidate(locale, keyboard, points, previousWord) {
            result[0] = it
            ready.countDown()
        }
        ready.await(10, TimeUnit.SECONDS)
        return result[0]
    }

    internal fun validateDictionary(context: Context, uri: Uri, locale: String): Boolean {
        val temporary = try {
            File.createTempFile("dictionary-validation-", ".dict", context.cacheDir)
        } catch (_: Exception) {
            return false
        }
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return false
            input.use { stream ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var total = 0L
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_DICTIONARY_BYTES) return false
                        output.write(buffer, 0, count)
                    }
                }
            }
            try {
                BinaryDictionary(temporary, Locale.forLanguageTag(locale.replace('_', '-')))
                    .also { it.close() }
                true
            } catch (_: Exception) {
                FileInputStream(temporary).use { LocalDictionary.read(it) != null }
            }
        } catch (_: Exception) {
            false
        } finally {
            temporary.delete()
        }
    }

    internal fun isReady(locale: String): Boolean =
        readySourceName == sourceNameForLocale(locale)

    internal fun loadForTests(context: Context, locale: String) {
        initialize(context)
        val ready = CountDownLatch(1)
        beginSession(locale) { ready.countDown() }
        ready.await(10, TimeUnit.SECONDS)
    }

    fun dictionarySource(locale: String): DictionarySource = when {
        appContext?.let { KaruikeyPreferences.dictionaryUriString(it, locale) }
            ?.let { Uri.parse(it).scheme == CONTENT_URI_SCHEME } == true ->
            DictionarySource.EXTERNAL
        assetNameForLocale(locale) != null -> DictionarySource.BUNDLED
        else -> DictionarySource.NONE
    }

    fun hasDictionary(locale: String): Boolean = dictionarySource(locale) != DictionarySource.NONE

    private fun scheduleWorkerLocked() {
        if (workerScheduled) return
        workerScheduled = true
        worker.execute(::drainWork)
    }

    private fun drainWork() {
        while (true) {
            val session: SessionRequest?
            val fill: FillRequest?
            val gesture: GestureRequest?
            synchronized(workLock) {
                session = pendingSession.also { pendingSession = null }
                fill = if (session == null) pendingFill.also { pendingFill = null } else {
                    pendingFill = null
                    null
                }
                gesture = if (session == null && fill == null) {
                    pendingGesture.also { pendingGesture = null }
                } else {
                    pendingGesture = null
                    null
                }
                if (session == null && fill == null && gesture == null) {
                    workerScheduled = false
                    return
                }
            }
            session?.let(::activateSession)
            fill?.let(::runFill)
            gesture?.let(::runGesture)
        }
    }

    private fun activateSession(request: SessionRequest) {
        closeActiveDictionary()
        activeSourceName = null
        readySourceName = null
        val sourceName = request.sourceName ?: return
        val manager = assetManager ?: return
        var dictionary: BinaryDictionary? = null
        var legacy: LocalDictionary? = null
        try {
            val file = materializeDictionary(sourceName, manager)
            dictionary = BinaryDictionary(file, Locale.forLanguageTag(
                request.locale.replace('_', '-')
            ))
        } catch (_: Exception) {
            // External KRD1 files remain readable during migration; bundled production data
            // is the AOSP binary dictionary above.
            legacy = try {
                if (sourceName.startsWith(EXTERNAL_SOURCE_PREFIX)) {
                    materializeDictionary(sourceName, manager).let(::FileInputStream)
                        .use(LocalDictionary::read)
                } else null
            } catch (_: Exception) {
                null
            }
        }
        if (!isCurrent(request.generation, sourceName)) {
            dictionary?.close()
            return
        }
        activeDictionary = dictionary
        activeLegacyDictionary = legacy
        activeSourceName = if (dictionary != null || legacy != null) sourceName else null
        if (activeSourceName == sourceName) {
            readySourceName = sourceName
            val callback = synchronized(workLock) {
                readyCallback.also { readyCallback = null }
            }
            callback?.invoke()
        }
    }

    private fun runFill(request: FillRequest) {
        val results = ArrayList<String>(3)
        if (isCurrent(request.generation, request.sourceName)) {
            fillActive(request, results)
        }
        request.onResult(results)
    }

    private fun runGesture(request: GestureRequest) {
        var candidate: String? = null
        if (isCurrent(request.generation, request.sourceName)) {
            val binary = activeDictionary?.takeIf { activeSourceName == request.sourceName }
            if (binary != null && request.keyboard != null) {
                val decoded = GestureDecoder.decode(request.keyboard, request.points)
                val candidates = ArrayList<String>(3)
                binary.getSuggestions(
                    decoded,
                    request.keyboard,
                    request.previousWord?.lowercase(Locale.ROOT),
                    null,
                    null,
                    candidates
                )
                candidate = candidates.firstOrNull { it.equals(decoded, ignoreCase = true) }
                    ?: candidates.firstOrNull()
            } else {
                candidate = activeLegacyDictionary
                    ?.takeIf { activeSourceName == request.sourceName }
                    ?.findGestureCandidate(GestureDecoder.decode(request.keyboard, request.points))
            }
        }
        request.onResult(candidate)
    }

    private fun fillActive(request: FillRequest, out: MutableList<String>) {
        // Never ask a binary dictionary for generic predictions in a fresh empty field.
        if (request.prefix.isEmpty() && request.previousWord == null) return
        val binary = activeDictionary?.takeIf { activeSourceName == request.sourceName }
        if (binary != null && request.prefix.isEmpty()) {
            binary.getSuggestions(
                request.prefix,
                request.keyboard,
                request.previousWord,
                request.secondPreviousWord,
                request.thirdPreviousWord,
                out
            )
            removeBlocked(appContext, request.locale, out)
            fillFromHistory(request, out)
            if (out.size > 3) out.subList(3, out.size).clear()
            return
        }
        if (binary != null && request.keyboard != null) {
            rankTyped(binary, request, out)
            removeBlocked(appContext, request.locale, out)
            fillFromHistory(request, out)
            if (out.size > 3) out.subList(3, out.size).clear()
            return
        }
        if (binary == null) {
            activeLegacyDictionary?.takeIf { activeSourceName == request.sourceName }
                ?.fill(request.previousWord, request.secondPreviousWord,
                    request.thirdPreviousWord, request.prefix, out)
            removeBlocked(appContext, request.locale, out)
            fillFromHistory(request, out)
        }
    }

    private fun rankTyped(binary: BinaryDictionary, request: FillRequest,
            out: MutableList<String>) {
        val codePoints = request.prefix.codePoints().toArray()
        val count = codePoints.size
        val xs = request.xCoordinates?.takeIf { it.size == count } ?: IntArray(count) { -1 }
        val ys = request.yCoordinates?.takeIf { it.size == count } ?: IntArray(count) { -1 }
        val previous = arrayOf(request.previousWord, request.secondPreviousWord,
            request.thirdPreviousWord)
        val previousCount = previous.indexOfFirst { it == null }.let { if (it < 0) 3 else it }
        binary.getCandidates(codePoints, xs, ys, count, request.keyboard, previous,
            previousCount, request.sentenceStart, candidates)
        val locale = Locale.forLanguageTag(request.locale.replace('_', '-'))
        val ranked = SuggestionRanker.rank(
            request.prefix,
            candidates.map {
                SuggestionRanker.ScoredWord(it.word, it.score, it.isWhitelisted,
                    it.isAppropriateForAutoCorrection)
            },
            binary.isValidWord(request.prefix),
            locale,
            // Spare slots so blacklisted words can be dropped without emptying the strip.
            maxResults = 6
        )
        out.addAll(ranked.words)
    }

    private fun fillFromHistory(request: FillRequest, out: MutableList<String>) {
        if (out.size >= 3) return
        val history = ArrayList<String>(3)
        appContext?.let {
            PredictionHistory.fill(it, request.locale, request.prefix, request.previousWord, history)
        }
        appendAllowed(request.locale, request.prefix, history, out)
    }

    private fun isCurrent(generation: Long, sourceName: String?): Boolean =
        synchronized(workLock) {
            generation == sessionGeneration && sourceName == requestedSourceName
        }

    private fun materializeDictionary(sourceName: String, manager: AssetManager): File {
        val context = checkNotNull(appContext)
        val directory = File(context.filesDir, "dictionaries")
        if (!directory.exists() && !directory.mkdirs()) throw IllegalStateException("dictionary dir")
        val sourceKey = Integer.toHexString(sourceName.hashCode())
        val file = File(directory, "${sourceName.replace('/', '_').replace('-', '_').lowercase(Locale.ROOT)}_$sourceKey.dict")
        if (file.length() > 0) return file
        val temporary = File(directory, "${file.name}.tmp")
        val input = if (sourceName.startsWith(EXTERNAL_SOURCE_PREFIX)) {
            context.contentResolver.openInputStream(Uri.parse(sourceName.removePrefix(EXTERNAL_SOURCE_PREFIX)))
        } else {
            manager.open(sourceName, AssetManager.ACCESS_STREAMING)
        } ?: throw IllegalStateException("dictionary unavailable")
        input.use { stream ->
            FileOutputStream(temporary).use { output ->
                val buffer = ByteArray(32 * 1024)
                var total = 0L
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_DICTIONARY_BYTES) throw IllegalArgumentException("dictionary too large")
                    output.write(buffer, 0, count)
                }
                output.fd.sync()
            }
        }
        if (!temporary.renameTo(file)) throw IllegalStateException("dictionary install failed")
        return file
    }

    private fun closeActiveDictionary() {
        activeDictionary?.close()
        activeDictionary = null
        activeLegacyDictionary = null
    }

    private fun removeBlocked(context: Context?, locale: String, out: MutableList<String>) {
        if (context == null) return
        out.removeAll { SuggestionBlacklist.contains(context, locale, it) }
    }

    private fun appendAllowed(locale: String, prefix: CharSequence, candidates: List<String>,
            out: MutableList<String>) {
        val context = appContext ?: return
        for (candidate in candidates) {
            if (out.size == 3) return
            if (candidate.startsWith(prefix.toString(), ignoreCase = true) &&
                !SuggestionBlacklist.contains(context, locale, candidate) &&
                !out.contains(candidate)
            ) out.add(candidate)
        }
    }

    private fun sourceNameForLocale(locale: String): String? {
        val external = appContext?.let { KaruikeyPreferences.dictionaryUriString(it, locale) }
        if (!external.isNullOrBlank() && Uri.parse(external).scheme == CONTENT_URI_SCHEME) {
            return EXTERNAL_SOURCE_PREFIX + external
        }
        return assetNameForLocale(locale)
    }

    private fun assetNameForLocale(locale: String): String? = dictionaryAssets.firstOrNull {
        locale.startsWith(it.languagePrefix, ignoreCase = true)
    }?.matureAssetName

    private const val MAX_DICTIONARY_BYTES = 32L * 1024L * 1024L
    private const val EXTERNAL_SOURCE_PREFIX = "uri:"
    private const val CONTENT_URI_SCHEME = "content"
}

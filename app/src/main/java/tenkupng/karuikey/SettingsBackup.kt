package tenkupng.karuikey

import android.content.Context
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream

/**
 * Keyboard settings as a JSON file. Only preferences travel: learned words, clipboard history
 * and dictionary file grants stay on the device.
 */
internal object SettingsBackup {
    private const val PREFS = "karuikey_settings"
    private const val FORMAT = "karuikey-settings"
    private const val VERSION = 1
    // Content URIs are only readable with this device's grants; the picture stays in app files.
    private val localOnly = listOf("dictionary_uri_", "background_image_version")

    private fun portable(key: String) = localOnly.none { key.startsWith(it) }

    fun export(context: Context, output: OutputStream) {
        val values = JSONObject()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all.forEach { (key, value) ->
            if (!portable(key)) return@forEach
            val typed = when (value) {
                is Boolean -> JSONObject().put("type", "boolean").put("value", value)
                is Int -> JSONObject().put("type", "int").put("value", value)
                is Long -> JSONObject().put("type", "long").put("value", value)
                is Float -> JSONObject().put("type", "float").put("value", value.toDouble())
                is String -> JSONObject().put("type", "string").put("value", value)
                is Set<*> -> JSONObject().put("type", "set")
                    .put("value", org.json.JSONArray(value.filterIsInstance<String>()))
                else -> null
            } ?: return@forEach
            values.put(key, typed)
        }
        val root = JSONObject().put("format", FORMAT).put("version", VERSION).put("settings", values)
        output.bufferedWriter().use { it.write(root.toString(2)) }
    }

    /** Replaces the portable settings with the file's; returns false for a foreign file. */
    fun import(context: Context, input: InputStream): Boolean {
        val root = try {
            JSONObject(input.bufferedReader().use { it.readText() })
        } catch (_: Exception) {
            return false
        }
        if (root.optString("format") != FORMAT || root.optInt("version") > VERSION) return false
        val values = root.optJSONObject("settings") ?: return false
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        prefs.all.keys.filter(::portable).forEach(editor::remove)
        values.keys().forEach { key ->
            if (!portable(key)) return@forEach
            val entry = values.optJSONObject(key) ?: return@forEach
            when (entry.optString("type")) {
                "boolean" -> editor.putBoolean(key, entry.optBoolean("value"))
                "int" -> editor.putInt(key, entry.optInt("value"))
                "long" -> editor.putLong(key, entry.optLong("value"))
                "float" -> editor.putFloat(key, entry.optDouble("value").toFloat())
                "string" -> editor.putString(key, entry.optString("value"))
                "set" -> entry.optJSONArray("value")?.let { array ->
                    editor.putStringSet(key, (0 until array.length()).map(array::optString).toSet())
                }
            }
        }
        // Commit, not apply: the settings screen is recreated right after and reads them back.
        return editor.commit()
    }
}

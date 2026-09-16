package tenkupng.karuikey

import android.content.Intent
import android.os.Bundle
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableIntStateOf

class MainActivity : AppCompatActivity() {
    private val refreshVersion = mutableIntStateOf(0)
    private var pendingDictionaryLanguage: KaruikeyLanguage? = null
    private val dictionaryPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val language = pendingDictionaryLanguage
        pendingDictionaryLanguage = null
        if (uri != null && language != null) validateAndInstallDictionary(uri, language)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        KaruikeyPreferences.applyActivityTheme(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SuggestionEngine.initialize(this)
        setContent {
            KaruikeySettingsApp(refreshVersion.intValue) { language ->
                pendingDictionaryLanguage = language
                dictionaryPicker.launch(arrayOf("*/*"))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshVersion.intValue++
    }

    private fun validateAndInstallDictionary(uri: Uri, language: KaruikeyLanguage) {
        Thread({
            val valid = try {
                contentResolver.openInputStream(uri)?.use { LocalDictionary.read(it) != null } == true
            } catch (_: Exception) {
                false
            }
            if (!valid) {
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Unsupported dictionary. Select a Karuikey KRD1 file.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                return@Thread
            }
            val persisted = try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                true
            } catch (_: SecurityException) {
                false
            }
            if (!persisted) {
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Could not keep access to that dictionary.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                return@Thread
            }
            KaruikeyPreferences.setDictionaryUri(this, language, uri)
            runOnUiThread { refreshVersion.intValue++ }
        }, "KaruikeyDictionaryImport").apply {
            isDaemon = true
            start()
        }
    }
}

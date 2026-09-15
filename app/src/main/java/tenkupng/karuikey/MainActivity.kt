package tenkupng.karuikey

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableIntStateOf

class MainActivity : AppCompatActivity() {
    private val refreshVersion = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        KaruikeyPreferences.applyActivityTheme(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { KaruikeySettingsApp(refreshVersion.intValue) }
    }

    override fun onResume() {
        super.onResume()
        refreshVersion.intValue++
    }
}

package tenkupng.karuikey

import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class LanguageListActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        KaruikeyPreferences.applyActivityTheme(this)
        super.onCreate(savedInstanceState)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(32), dp(24), dp(24))
        }
        content.addView(TextView(this).apply {
            text = getString(R.string.available_languages)
            textSize = 24f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, wrapContent())
        val enabled = KaruikeyPreferences.enabledLanguages(this).map { it.id }.toSet()
        val available = KaruikeyPreferences.languages.filterNot { enabled.contains(it.id) }
        if (available.isEmpty()) {
            content.addView(TextView(this).apply {
                text = getString(R.string.keyboard_settings_note)
                textSize = 16f
                setPadding(0, dp(16), 0, 0)
            }, wrapContent())
        } else {
            for (language in available) {
                content.addView(MaterialButton(this).apply {
                    text = "${language.displayName}\n${language.nativeName} · ${language.layoutName}"
                    contentDescription = getString(
                        R.string.language_entry_description,
                        language.displayName,
                        language.nativeName,
                        language.layoutName
                    )
                    setOnClickListener {
                        KaruikeyPreferences.setLanguageEnabled(this@LanguageListActivity, language, true)
                        finish()
                    }
                }, wrapContent())
            }
        }
        setContentView(content)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun wrapContent() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )
}

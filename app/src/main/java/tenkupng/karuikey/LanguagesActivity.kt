package tenkupng.karuikey

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class LanguagesActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        KaruikeyPreferences.applyActivityTheme(this)
        super.onCreate(savedInstanceState)
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(32), dp(24), dp(24))
        }
        content.addView(TextView(this).apply {
            text = getString(R.string.languages_title)
            textSize = 28f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, wrapContent())
        content.addView(TextView(this).apply {
            text = getString(R.string.enabled_languages)
            textSize = 15f
            setPadding(0, dp(18), 0, dp(4))
        }, wrapContent())

        val enabledIds = KaruikeyPreferences.enabledLanguages(this).map { it.id }.toSet()
        for (language in KaruikeyPreferences.languages) {
            if (!enabledIds.contains(language.id)) continue
            content.addView(CheckBox(this).apply {
                text = "${language.displayName} · ${language.layoutName}"
                contentDescription = getString(
                    R.string.language_entry_description,
                    language.displayName,
                    language.nativeName,
                    language.layoutName
                )
                isChecked = true
                setOnCheckedChangeListener { button, checked ->
                    if (!KaruikeyPreferences.setLanguageEnabled(
                            this@LanguagesActivity, language, checked
                        )
                    ) {
                        button.isChecked = true
                    }
                }
            }, wrapContent())
        }

        content.addView(MaterialButton(this).apply {
            text = getString(R.string.add_language)
            setOnClickListener {
                startActivity(Intent(this@LanguagesActivity, LanguageListActivity::class.java))
            }
        }, matchWidth())
        setContentView(ScrollView(this).apply { addView(content) })
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun wrapContent() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun matchWidth() = wrapContent().apply { topMargin = dp(12) }
}

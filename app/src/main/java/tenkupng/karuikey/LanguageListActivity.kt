package tenkupng.karuikey

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import android.widget.ScrollView
import android.widget.TextView

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
        val search = EditText(this).apply {
            hint = getString(R.string.search_languages_hint)
            isSingleLine = true
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        content.addView(search, wrapContent())
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(results, wrapContent())
        renderAvailable(results, "")
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
                renderAvailable(results, text?.toString().orEmpty())
            }
            override fun afterTextChanged(text: Editable?) = Unit
        })
        setContentView(ScrollView(this).apply { addView(content) })
    }

    private fun renderAvailable(container: LinearLayout, query: String) {
        container.removeAllViews()
        val enabled = KaruikeyPreferences.enabledLanguages(this).map { it.id }.toSet()
        val available = KaruikeyPreferences.languages.filterNot { enabled.contains(it.id) }
            .filter {
                query.isBlank() || it.displayName.contains(query, true) ||
                    it.nativeName.contains(query, true) || it.locale.contains(query, true)
            }
        if (available.isEmpty()) {
            container.addView(TextView(this).apply {
                text = getString(if (query.isBlank()) R.string.keyboard_settings_note else R.string.no_languages_found)
                textSize = 16f
                setPadding(0, dp(16), 0, 0)
            }, wrapContent())
            return
        }
        for (language in available) {
            container.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(14), 0, dp(14))
                isClickable = true
                isFocusable = true
                setBackgroundResource(android.R.drawable.list_selector_background)
                contentDescription = getString(
                    R.string.language_entry_description,
                    language.displayName,
                    language.nativeName,
                    language.layoutName
                )
                addView(TextView(this@LanguageListActivity).apply {
                    text = language.displayName
                    textSize = 17f
                }, wrapContent())
                addView(TextView(this@LanguageListActivity).apply {
                    text = "${language.nativeName} · ${language.layoutName}"
                    textSize = 14f
                    setPadding(0, dp(3), 0, 0)
                }, wrapContent())
                setOnClickListener {
                    KaruikeyPreferences.setLanguageEnabled(this@LanguageListActivity, language, true)
                    finish()
                }
            }, wrapContent())
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun wrapContent() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )
}

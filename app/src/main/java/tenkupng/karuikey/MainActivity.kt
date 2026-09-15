package tenkupng.karuikey

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {
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
            text = getString(R.string.app_name)
            textSize = 32f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, wrapContent())
        content.addView(TextView(this).apply {
            text = getString(R.string.settings_description)
            textSize = 18f
            setPadding(0, dp(12), 0, dp(20))
        }, wrapContent())
        content.addView(MaterialButton(this).apply {
            text = getString(R.string.open_keyboard_settings)
            setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        }, matchWidth())
        content.addView(MaterialButton(this).apply {
            text = getString(R.string.show_keyboard_picker)
            setOnClickListener {
                getSystemService(InputMethodManager::class.java).showInputMethodPicker()
            }
        }, matchWidth())

        content.addView(navigationRow(
            R.string.languages_title,
            getString(
                R.string.languages_summary,
                KaruikeyPreferences.enabledLanguages(this).joinToString { it.displayName }
            ),
            LanguagesActivity::class.java
        ), sectionParams())
        content.addView(navigationRow(
            R.string.appearance_title,
            getString(
                R.string.appearance_summary,
                themeLabel(), KaruikeyPreferences.heightPercent(this)
            ),
            AppearanceActivity::class.java
        ), wrapContent())
        content.addView(navigationRow(
            R.string.about_title,
            getString(R.string.about_summary),
            AboutActivity::class.java
        ), wrapContent())

        setContentView(ScrollView(this).apply { addView(content) })
    }

    private fun navigationRow(title: Int, summary: String, activity: Class<*>): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(14), 0, dp(14))
            isClickable = true
            isFocusable = true
            setBackgroundResource(android.R.drawable.list_selector_background)
            contentDescription = getString(title)
            addView(TextView(this@MainActivity).apply {
                text = getString(title)
                textSize = 18f
            }, wrapContent())
            addView(TextView(this@MainActivity).apply {
                text = summary
                textSize = 14f
                setPadding(0, dp(3), 0, 0)
            }, wrapContent())
            setOnClickListener { startActivity(Intent(this@MainActivity, activity)) }
        }

    private fun themeLabel() = when (KaruikeyPreferences.theme(this)) {
        KaruikeyPreferences.THEME_LIGHT -> getString(R.string.theme_light)
        KaruikeyPreferences.THEME_DARK -> getString(R.string.theme_dark)
        else -> getString(R.string.theme_system)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun wrapContent() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun sectionParams() = wrapContent().apply { topMargin = dp(12) }

    private fun matchWidth() = wrapContent().apply { bottomMargin = dp(4) }
}

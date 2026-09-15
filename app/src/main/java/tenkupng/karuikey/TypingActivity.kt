package tenkupng.karuikey

import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.materialswitch.MaterialSwitch

class TypingActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        KaruikeyPreferences.applyActivityTheme(this)
        super.onCreate(savedInstanceState)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(32), dp(24), dp(24))
        }
        content.addView(TextView(this).apply {
            text = getString(R.string.typing_title)
            textSize = 28f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, wrapContent())
        content.addView(MaterialSwitch(this).apply {
            text = getString(R.string.suggestions_setting)
            isChecked = KaruikeyPreferences.suggestionsEnabled(this@TypingActivity)
            setOnCheckedChangeListener { _, checked ->
                KaruikeyPreferences.setSuggestionsEnabled(this@TypingActivity, checked)
            }
        }, sectionParams())
        content.addView(TextView(this).apply {
            text = getString(R.string.suggestions_description)
            textSize = 13f
        }, wrapContent())
        setContentView(ScrollView(this).apply { addView(content) })
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun wrapContent() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun sectionParams() = wrapContent().apply { topMargin = dp(18) }
}

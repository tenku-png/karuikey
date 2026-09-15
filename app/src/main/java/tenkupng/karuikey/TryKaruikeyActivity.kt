package tenkupng.karuikey

import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class TryKaruikeyActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        KaruikeyPreferences.applyActivityTheme(this)
        super.onCreate(savedInstanceState)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(32), dp(24), dp(24))
        }
        content.addView(TextView(this).apply {
            text = getString(R.string.try_keyboard_title)
            textSize = 28f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, wrapContent())
        content.addView(field(R.string.try_normal_hint, InputType.TYPE_CLASS_TEXT), fieldParams())
        content.addView(field(
            R.string.try_sentence_hint,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        ), fieldParams())
        content.addView(field(
            R.string.try_multiline_hint,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        ).apply { minLines = 3 }, fieldParams())
        content.addView(field(
            R.string.try_email_hint,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        ), fieldParams())
        content.addView(field(
            R.string.try_number_hint,
            InputType.TYPE_CLASS_NUMBER
        ), fieldParams())
        setContentView(ScrollView(this).apply { addView(content) })
    }

    private fun field(hintResource: Int, type: Int) = EditText(this).apply {
        hint = getString(hintResource)
        inputType = type
        setPadding(dp(4), dp(8), dp(4), dp(8))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun fieldParams() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(12) }

    private fun wrapContent() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )
}

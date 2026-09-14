package tenkupng.karuikey

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.DynamicColors

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(40), dp(24), dp(24))
        }
        content.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 32f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, wrapContent())
        content.addView(TextView(this).apply {
            text = getString(R.string.settings_description)
            textSize = 18f
            setPadding(0, dp(12), 0, dp(28))
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
        setContentView(content)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun wrapContent() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun matchWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = dp(8) }
}

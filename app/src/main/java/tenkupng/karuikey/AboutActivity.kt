package tenkupng.karuikey

import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        KaruikeyPreferences.applyActivityTheme(this)
        super.onCreate(savedInstanceState)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(32), dp(24), dp(24))
        }
        content.addView(TextView(this).apply {
            text = getString(R.string.about_title)
            textSize = 28f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, wrapContent())
        content.addView(TextView(this).apply {
            val version = packageManager.getPackageInfo(packageName, 0).versionName ?: ""
            text = getString(R.string.about_text, version)
            textSize = 16f
            setPadding(0, dp(20), 0, 0)
        }, wrapContent())
        setContentView(ScrollView(this).apply { addView(content) })
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun wrapContent() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )
}

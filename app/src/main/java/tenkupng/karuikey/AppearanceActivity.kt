package tenkupng.karuikey

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.materialswitch.MaterialSwitch

class AppearanceActivity : AppCompatActivity() {
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
            text = getString(R.string.appearance_title)
            textSize = 28f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, wrapContent())

        content.addView(TextView(this).apply {
            text = getString(R.string.theme_setting)
            textSize = 15f
            setPadding(0, dp(18), 0, dp(4))
        }, wrapContent())
        val themeIds = HashMap<Int, String>()
        val themeGroup = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            for ((label, value) in listOf(
                R.string.theme_system to KaruikeyPreferences.THEME_SYSTEM,
                R.string.theme_light to KaruikeyPreferences.THEME_LIGHT,
                R.string.theme_dark to KaruikeyPreferences.THEME_DARK
            )) {
                val radio = RadioButton(this@AppearanceActivity).apply {
                    id = View.generateViewId()
                    text = getString(label)
                    isChecked = KaruikeyPreferences.theme(this@AppearanceActivity) == value
                }
                themeIds[radio.id] = value
                addView(radio, RadioGroup.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
            setOnCheckedChangeListener { _, checkedId ->
                themeIds[checkedId]?.let {
                    KaruikeyPreferences.setTheme(this@AppearanceActivity, it)
                    KaruikeyPreferences.applyAppTheme(this@AppearanceActivity)
                    recreate()
                }
            }
        }
        content.addView(themeGroup, wrapContent())

        content.addView(MaterialSwitch(this).apply {
            text = getString(R.string.dynamic_colors_setting)
            isChecked = KaruikeyPreferences.dynamicColorsEnabled(this@AppearanceActivity)
            isEnabled = Build.VERSION.SDK_INT >= 31
            contentDescription = getString(R.string.dynamic_colors_description)
            setOnCheckedChangeListener { _, checked ->
                KaruikeyPreferences.setDynamicColorsEnabled(this@AppearanceActivity, checked)
                recreate()
            }
        }, wrapContent())
        content.addView(TextView(this).apply {
            text = getString(R.string.dynamic_colors_description)
            textSize = 13f
        }, wrapContent())

        content.addView(MaterialSwitch(this).apply {
            text = getString(R.string.toolbar_setting)
            isChecked = KaruikeyPreferences.toolbarEnabled(this@AppearanceActivity)
            setOnCheckedChangeListener { _, checked ->
                KaruikeyPreferences.setToolbarEnabled(this@AppearanceActivity, checked)
            }
        }, sectionParams())
        content.addView(MaterialSwitch(this).apply {
            text = getString(R.string.key_preview_setting)
            isChecked = KaruikeyPreferences.keyPreviewEnabled(this@AppearanceActivity)
            setOnCheckedChangeListener { _, checked ->
                KaruikeyPreferences.setKeyPreviewEnabled(this@AppearanceActivity, checked)
            }
        }, wrapContent())

        val heightLabel = TextView(this).apply { textSize = 16f }
        content.addView(heightLabel, sectionParams())
        content.addView(SeekBar(this).apply {
            max = 30
            progress = KaruikeyPreferences.heightPercent(this@AppearanceActivity) - 85
            contentDescription = getString(
                R.string.keyboard_height_setting,
                KaruikeyPreferences.heightPercent(this@AppearanceActivity)
            )
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    val percent = progress + 85
                    KaruikeyPreferences.setHeightPercent(this@AppearanceActivity, percent)
                    heightLabel.text = getString(R.string.keyboard_height_setting, percent)
                    seekBar.contentDescription = heightLabel.text
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
            })
        }, matchWidth())
        heightLabel.text = getString(
            R.string.keyboard_height_setting,
            KaruikeyPreferences.heightPercent(this)
        )
        content.addView(TextView(this).apply {
            text = getString(R.string.appearance_note)
            textSize = 13f
            setPadding(0, dp(8), 0, 0)
        }, wrapContent())

        setContentView(ScrollView(this).apply { addView(content) })
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun wrapContent() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun sectionParams() = wrapContent().apply { topMargin = dp(12) }

    private fun matchWidth() = wrapContent().apply { bottomMargin = dp(4) }
}

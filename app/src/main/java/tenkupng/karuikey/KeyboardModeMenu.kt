package tenkupng.karuikey

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView

internal fun keyboardModeIcon(mode: String) = when (mode) {
    KaruikeyPreferences.KEYBOARD_MODE_SPLIT -> R.drawable.ic_keyboard_mode_split
    KaruikeyPreferences.KEYBOARD_MODE_FLOATING -> R.drawable.ic_keyboard_mode_floating
    else -> R.drawable.ic_keyboard_mode_standard
}

/**
 * M3 Expressive menu: rounded surfaceContainer card, 48dp rows with a leading icon, the current
 * mode filled with secondaryContainer and a trailing check.
 */
internal fun showKeyboardModeMenu(
    context: Context,
    anchor: View,
    appearance: KeyboardAppearance,
    typeface: Typeface,
    selectedTypeface: Typeface,
    onModeSelected: (String) -> Unit
) {
    val current = KaruikeyPreferences.keyboardMode(context)
    val modes = buildList {
        add(KaruikeyPreferences.KEYBOARD_MODE_STANDARD to R.string.keyboard_mode_standard)
        if (KaruikeyPreferences.splitModeAvailable(context)) {
            add(KaruikeyPreferences.KEYBOARD_MODE_SPLIT to R.string.keyboard_mode_split)
        }
        add(KaruikeyPreferences.KEYBOARD_MODE_FLOATING to R.string.keyboard_mode_floating)
    }
    val popup = PopupWindow(context)
    val list = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.dp(4), context.dp(4), context.dp(4), context.dp(4))
        background = GradientDrawable().apply {
            cornerRadius = context.dp(16).toFloat()
            setColor(appearance.popupSurface)
        }
        clipToOutline = true
    }
    modes.forEach { (mode, title) ->
        val selected = mode == current
        val color = if (selected) appearance.onSecondaryContainer else appearance.popupText
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(12), 0, context.dp(12), 0)
            val shape = GradientDrawable().apply {
                cornerRadius = context.dp(12).toFloat()
                setColor(if (selected) appearance.secondaryContainer else Color.TRANSPARENT)
            }
            val mask = GradientDrawable().apply {
                cornerRadius = context.dp(12).toFloat()
                setColor(Color.WHITE)
            }
            background = RippleDrawable(ColorStateList.valueOf(appearance.pressedSurface), shape, mask)
            isSelected = selected
            setOnClickListener {
                popup.dismiss()
                if (mode != current) onModeSelected(mode)
            }
        }
        row.addView(ImageView(context).apply {
            setImageResource(keyboardModeIcon(mode))
            imageTintList = ColorStateList.valueOf(color)
        }, LinearLayout.LayoutParams(context.dp(24), context.dp(24)))
        row.addView(TextView(context).apply {
            text = context.getString(title)
            textSize = 15f
            this.typeface = if (selected) selectedTypeface else typeface
            setTextColor(color)
            setPadding(context.dp(12), 0, context.dp(16), 0)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_settings_check)
            imageTintList = ColorStateList.valueOf(color)
            visibility = if (selected) View.VISIBLE else View.INVISIBLE
        }, LinearLayout.LayoutParams(context.dp(20), context.dp(20)))
        list.addView(row, LinearLayout.LayoutParams(context.dp(208), context.dp(48)).apply {
            setMargins(0, context.dp(1), 0, context.dp(1))
        })
    }
    popup.apply {
        contentView = list
        width = LinearLayout.LayoutParams.WRAP_CONTENT
        height = LinearLayout.LayoutParams.WRAP_CONTENT
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        isOutsideTouchable = true
        isFocusable = false
        elevation = context.dp(3).toFloat()
        animationStyle = android.R.style.Animation_Dialog
        showAsDropDown(anchor, 0, context.dp(4))
    }
}

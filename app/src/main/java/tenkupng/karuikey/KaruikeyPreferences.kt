package tenkupng.karuikey

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.color.DynamicColors
import java.util.LinkedHashSet

data class KaruikeyLanguage(
    val id: String,
    val locale: String,
    val layoutSet: String,
    val displayName: String,
    val nativeName: String,
    val layoutName: String,
    val spacebarLabel: String
)

data class KeyboardAppearance(
    val isDark: Boolean,
    val usesDynamicColors: Boolean,
    val keyboardBackground: Int,
    val keySurface: Int,
    val functionalKeySurface: Int,
    val actionSurface: Int,
    val primaryText: Int,
    val secondaryText: Int,
    val functionalText: Int,
    val actionText: Int,
    val disabledText: Int,
    val popupSurface: Int,
    val popupText: Int,
    val pressedSurface: Int,
    val shiftLockedSurface: Int
)

object KaruikeyPreferences {
    const val ENGLISH_ID = "en-US"
    const val RUSSIAN_ID = "ru-RU"
    const val THEME_SYSTEM = "system"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"

    // This is the AOSP subtype list filtered to the alphabetic layout sets bundled by Karuikey.
    // Languages with dictionaries are intentionally not required for basic layout input.
    val languages = listOf(
        KaruikeyLanguage(ENGLISH_ID, "en_US", "qwerty", "English (US)", "English", "QWERTY", "English"),
        KaruikeyLanguage("en-GB", "en_GB", "qwerty", "English (UK)", "English", "QWERTY", "English"),
        KaruikeyLanguage("en-IN", "en_IN", "qwerty", "English (India)", "English", "QWERTY", "English"),
        KaruikeyLanguage("af", "af", "qwerty", "Afrikaans", "Afrikaans", "QWERTY", "Afrikaans"),
        KaruikeyLanguage("az-AZ", "az_AZ", "qwerty", "Azerbaijani", "Azərbaycan dili", "QWERTY", "Azərbaycan dili"),
        KaruikeyLanguage("be-BY", "be_BY", "east_slavic", "Belarusian", "Беларуская", "East Slavic", "Беларуская"),
        KaruikeyLanguage("eo", "eo", "qwerty", "Esperanto", "Esperanto", "QWERTY", "Esperanto"),
        KaruikeyLanguage("fr-CA", "fr_CA", "qwerty", "French (Canada)", "Français (Canada)", "QWERTY", "Français"),
        KaruikeyLanguage("hi-ZZ", "hi_ZZ", "qwerty", "Hinglish", "Hinglish", "QWERTY", "Hinglish"),
        KaruikeyLanguage("id-ID", "in", "qwerty", "Indonesian", "Bahasa Indonesia", "QWERTY", "Indonesia"),
        KaruikeyLanguage("is", "is", "qwerty", "Icelandic", "Íslenska", "QWERTY", "Íslenska"),
        KaruikeyLanguage("it", "it", "qwerty", "Italian", "Italiano", "QWERTY", "Italiano"),
        KaruikeyLanguage("kk", "kk", "east_slavic", "Kazakh", "Қазақша", "East Slavic", "Қазақша"),
        KaruikeyLanguage("ky", "ky", "east_slavic", "Kyrgyz", "Кыргызча", "East Slavic", "Кыргызча"),
        KaruikeyLanguage("lt", "lt", "qwerty", "Lithuanian", "Lietuvių", "QWERTY", "Lietuvių"),
        KaruikeyLanguage("lv", "lv", "qwerty", "Latvian", "Latviešu", "QWERTY", "Latviešu"),
        KaruikeyLanguage("ms-MY", "ms_MY", "qwerty", "Malay", "Melayu", "QWERTY", "Melayu"),
        KaruikeyLanguage("nl", "nl", "qwerty", "Dutch", "Nederlands", "QWERTY", "Nederlands"),
        KaruikeyLanguage("pl", "pl", "qwerty", "Polish", "Polski", "QWERTY", "Polski"),
        KaruikeyLanguage("pt-BR", "pt_BR", "qwerty", "Portuguese (Brazil)", "Português (Brasil)", "QWERTY", "Português"),
        KaruikeyLanguage("pt-PT", "pt_PT", "qwerty", "Portuguese (Portugal)", "Português (Portugal)", "QWERTY", "Português"),
        KaruikeyLanguage("ro", "ro", "qwerty", "Romanian", "Română", "QWERTY", "Română"),
        KaruikeyLanguage(RUSSIAN_ID, "ru_RU", "east_slavic", "Russian", "Русский", "East Slavic", "Русский"),
        KaruikeyLanguage("sk", "sk", "qwerty", "Slovak", "Slovenčina", "QWERTY", "Slovenčina"),
        KaruikeyLanguage("sw", "sw", "qwerty", "Swahili", "Kiswahili", "QWERTY", "Kiswahili"),
        KaruikeyLanguage("tr", "tr", "qwerty", "Turkish", "Türkçe", "QWERTY", "Türkçe"),
        KaruikeyLanguage("uk", "uk", "east_slavic", "Ukrainian", "Українська", "East Slavic", "Українська"),
        KaruikeyLanguage("vi", "vi", "qwerty", "Vietnamese", "Tiếng Việt", "QWERTY", "Tiếng Việt"),
        KaruikeyLanguage("zu", "zu", "qwerty", "Zulu", "isiZulu", "QWERTY", "isiZulu"),
        KaruikeyLanguage("zz", "zz", "qwerty", "QWERTY", "QWERTY", "QWERTY", "QWERTY")
    )

    private const val PREFS = "karuikey_settings"
    private const val ENABLED_LANGUAGES = "enabled_languages"
    private const val ACTIVE_LANGUAGE = "active_language"
    private const val TOOLBAR = "toolbar"
    private const val KEY_PREVIEW = "key_preview"
    private const val THEME = "theme"
    private const val DYNAMIC_COLORS = "dynamic_colors"
    private const val HEIGHT_PERCENT = "height_percent"
    private const val TRANSPARENCY = "transparency"
    private const val TRANSPARENCY_AMOUNT = "transparency_amount"
    private const val BLUR = "blur"
    private const val SUGGESTIONS = "suggestions"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabledLanguages(context: Context): List<KaruikeyLanguage> {
        val ids = prefs(context).getStringSet(
            ENABLED_LANGUAGES,
            setOf(ENGLISH_ID, RUSSIAN_ID)
        ) ?: setOf(ENGLISH_ID)
        // Filtering through the immutable catalog gives deterministic order even though
        // SharedPreferences stores StringSet without an ordering guarantee.
        val enabled = languages.filter { ids.contains(it.id) }
        return enabled.ifEmpty { listOf(languages.first()) }
    }

    fun setLanguageEnabled(context: Context, language: KaruikeyLanguage, enabled: Boolean): Boolean {
        val ids = LinkedHashSet(enabledLanguages(context).map { it.id })
        if (enabled) {
            ids.add(language.id)
        } else {
            if (ids.size == 1 && ids.contains(language.id)) return false
            ids.remove(language.id)
        }
        prefs(context).edit().putStringSet(ENABLED_LANGUAGES, ids).apply()
        if (!enabled && activeLanguage(context).id == language.id) {
            setActiveLanguage(context, enabledLanguages(context).first())
        }
        return true
    }

    fun activeLanguage(context: Context): KaruikeyLanguage {
        val activeId = prefs(context).getString(ACTIVE_LANGUAGE, ENGLISH_ID)
        return enabledLanguages(context).firstOrNull { it.id == activeId }
            ?: enabledLanguages(context).first()
    }

    fun setActiveLanguage(context: Context, language: KaruikeyLanguage) {
        prefs(context).edit().putString(ACTIVE_LANGUAGE, language.id).apply()
    }

    fun toolbarEnabled(context: Context) = prefs(context).getBoolean(TOOLBAR, true)

    fun setToolbarEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(TOOLBAR, enabled).apply()
    }

    fun keyPreviewEnabled(context: Context) = prefs(context).getBoolean(KEY_PREVIEW, true)

    fun setKeyPreviewEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_PREVIEW, enabled).apply()
    }

    fun theme(context: Context) = prefs(context).getString(THEME, THEME_SYSTEM) ?: THEME_SYSTEM

    fun setTheme(context: Context, value: String) {
        prefs(context).edit().putString(THEME, value).apply()
    }

    fun dynamicColorsEnabled(context: Context) =
        prefs(context).getBoolean(DYNAMIC_COLORS, Build.VERSION.SDK_INT >= 31)

    fun setDynamicColorsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(DYNAMIC_COLORS, enabled).apply()
    }

    fun applyAppTheme(context: Context) {
        AppCompatDelegate.setDefaultNightMode(
            when (theme(context)) {
                THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    fun applyActivityTheme(activity: AppCompatActivity) {
        applyAppTheme(activity)
        if (dynamicColorsEnabled(activity)) {
            DynamicColors.applyToActivityIfAvailable(activity)
        }
    }

    fun heightPercent(context: Context) =
        prefs(context).getInt(HEIGHT_PERCENT, 100).coerceIn(85, 115)

    fun setHeightPercent(context: Context, percent: Int) {
        prefs(context).edit().putInt(HEIGHT_PERCENT, percent.coerceIn(85, 115)).apply()
    }

    fun transparencyEnabled(context: Context) = prefs(context).getBoolean(TRANSPARENCY, false)

    fun setTransparencyEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(TRANSPARENCY, enabled).apply()
    }

    /** 0 is opaque; the upper bound keeps key contrast usable over arbitrary app content. */
    fun transparencyAmount(context: Context) =
        prefs(context).getInt(TRANSPARENCY_AMOUNT, 0).coerceIn(0, 35)

    fun setTransparencyAmount(context: Context, amount: Int) {
        prefs(context).edit().putInt(TRANSPARENCY_AMOUNT, amount.coerceIn(0, 35)).apply()
    }

    fun keyboardSurfaceAlpha(context: Context) =
        if (transparencyEnabled(context)) 1f - transparencyAmount(context) / 100f else 1f

    fun blurEnabled(context: Context) = prefs(context).getBoolean(BLUR, false)

    fun setBlurEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(BLUR, enabled).apply()
    }

    fun blurSupported() = Build.VERSION.SDK_INT >= 31

    fun suggestionsEnabled(context: Context) = prefs(context).getBoolean(SUGGESTIONS, false)

    fun setSuggestionsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(SUGGESTIONS, enabled).apply()
    }

    fun resolveKeyboardAppearance(
        context: Context,
        themeMode: String = theme(context),
        dynamicEnabled: Boolean = dynamicColorsEnabled(context)
    ): KeyboardAppearance {
        val systemDark = (context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val isDark = when (themeMode) {
            THEME_LIGHT -> false
            THEME_DARK -> true
            else -> systemDark
        }
        return if (dynamicEnabled && Build.VERSION.SDK_INT >= 31) {
            dynamicAppearance(context, isDark)
        } else {
            fallbackAppearance(context, isDark)
        }
    }

    fun keyboardContext(context: Context): Context {
        val appearance = resolveKeyboardAppearance(context)
        val style = when {
            appearance.usesDynamicColors && appearance.isDark -> R.style.Theme_Karuikey_Keyboard_Dynamic_Dark
            appearance.usesDynamicColors -> R.style.Theme_Karuikey_Keyboard_Dynamic_Light
            appearance.isDark -> R.style.Theme_Karuikey_Keyboard_Fallback_Dark
            else -> R.style.Theme_Karuikey_Keyboard_Fallback_Light
        }
        val themed = android.view.ContextThemeWrapper(context, style)
        return if (appearance.usesDynamicColors) DynamicColors.wrapContextIfAvailable(themed)
        else themed
    }

    private fun fallbackAppearance(context: Context, isDark: Boolean) = if (isDark) {
        KeyboardAppearance(
            true, false,
            context.getColor(R.color.keyboard_dark_surface),
            context.getColor(R.color.keyboard_dark_key),
            context.getColor(R.color.keyboard_dark_functional),
            context.getColor(R.color.keyboard_dark_action),
            context.getColor(R.color.keyboard_dark_text),
            context.getColor(R.color.keyboard_dark_secondary_text),
            context.getColor(R.color.keyboard_dark_functional_text),
            context.getColor(R.color.keyboard_dark_action_text),
            context.getColor(R.color.keyboard_dark_disabled_text),
            context.getColor(R.color.keyboard_dark_popup),
            context.getColor(R.color.keyboard_dark_text),
            context.getColor(R.color.keyboard_dark_pressed),
            context.getColor(R.color.keyboard_dark_shift_locked)
        )
    } else {
        KeyboardAppearance(
            false, false,
            context.getColor(R.color.keyboard_light_surface),
            context.getColor(R.color.keyboard_light_key),
            context.getColor(R.color.keyboard_light_functional),
            context.getColor(R.color.keyboard_light_action),
            context.getColor(R.color.keyboard_light_text),
            context.getColor(R.color.keyboard_light_secondary_text),
            context.getColor(R.color.keyboard_light_functional_text),
            context.getColor(R.color.keyboard_light_action_text),
            context.getColor(R.color.keyboard_light_disabled_text),
            context.getColor(R.color.keyboard_light_popup),
            context.getColor(R.color.keyboard_light_text),
            context.getColor(R.color.keyboard_light_pressed),
            context.getColor(R.color.keyboard_light_shift_locked)
        )
    }

    private fun dynamicAppearance(context: Context, isDark: Boolean): KeyboardAppearance {
        val color = context::getColor
        val neutralSurface = color(if (isDark) android.R.color.system_neutral1_900
            else android.R.color.system_neutral1_10)
        val keySurface = color(if (isDark) android.R.color.system_neutral2_800
            else android.R.color.system_neutral2_50)
        val functionalSurface = color(if (isDark) android.R.color.system_neutral2_700
            else android.R.color.system_neutral2_100)
        val actionSurface = color(if (isDark) android.R.color.system_accent1_200
            else android.R.color.system_accent1_600)
        val primaryText = color(if (isDark) android.R.color.system_neutral1_10
            else android.R.color.system_neutral1_900)
        val secondaryText = color(if (isDark) android.R.color.system_neutral2_200
            else android.R.color.system_neutral2_700)
        val actionText = color(if (isDark) android.R.color.system_accent1_900
            else android.R.color.system_accent1_10)
        val pressed = color(if (isDark) android.R.color.system_accent2_700
            else android.R.color.system_accent2_100)
        val locked = color(if (isDark) android.R.color.system_accent3_700
            else android.R.color.system_accent3_100)
        val disabled = color(if (isDark) android.R.color.system_neutral2_300
            else android.R.color.system_neutral2_500)
        return KeyboardAppearance(
            isDark, true, neutralSurface, keySurface, functionalSurface, actionSurface,
            primaryText, secondaryText, primaryText, actionText, disabled,
            keySurface, primaryText, pressed, locked
        )
    }
}

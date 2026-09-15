package tenkupng.karuikey

import android.content.Context
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class KaruikeyPreferencesTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences = context.getSharedPreferences("karuikey_settings", Context.MODE_PRIVATE)
    private var original: Map<String, Any?> = emptyMap()

    @Before
    fun savePreferences() {
        original = preferences.all
        preferences.edit().clear().commit()
    }

    @After
    fun restorePreferences() {
        val editor = preferences.edit().clear()
        original.forEach { entry ->
            val key = entry.key
            when (val value = entry.value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        editor.commit()
    }

    @Test
    fun languagesPersistAndTheLastEnabledLanguageCannotBeRemoved() {
        val english = KaruikeyPreferences.languages.first()
        val russian = KaruikeyPreferences.languages.first {
            it.id == KaruikeyPreferences.RUSSIAN_ID
        }
        assertTrue(KaruikeyPreferences.setLanguageEnabled(context, russian, false))
        assertEquals(listOf(english.id), KaruikeyPreferences.enabledLanguages(context).map { it.id })
        assertFalse(KaruikeyPreferences.setLanguageEnabled(context, english, false))
        assertEquals(english.id, KaruikeyPreferences.enabledLanguages(context).single().id)
    }

    @Test
    fun activeLanguagePersistsLocally() {
        val russian = KaruikeyPreferences.languages.first {
            it.id == KaruikeyPreferences.RUSSIAN_ID
        }
        KaruikeyPreferences.setActiveLanguage(context, russian)
        assertEquals(russian.id, KaruikeyPreferences.activeLanguage(context).id)
    }

    @Test
    fun activeLanguageFallsBackWhenItIsDisabled() {
        val russian = KaruikeyPreferences.languages.first {
            it.id == KaruikeyPreferences.RUSSIAN_ID
        }
        KaruikeyPreferences.setActiveLanguage(context, russian)

        assertTrue(KaruikeyPreferences.setLanguageEnabled(context, russian, false))
        assertEquals(
            KaruikeyPreferences.ENGLISH_ID,
            KaruikeyPreferences.activeLanguage(context).id
        )
    }

    @Test
    fun forcedKeyboardThemesIgnoreSystemTheme() {
        val light = KaruikeyPreferences.resolveKeyboardAppearance(
            context, KaruikeyPreferences.THEME_LIGHT, false
        )
        val dark = KaruikeyPreferences.resolveKeyboardAppearance(
            context, KaruikeyPreferences.THEME_DARK, false
        )

        assertFalse(light.isDark)
        assertTrue(dark.isDark)
        assertEquals(context.getColor(R.color.keyboard_light_surface), light.keyboardBackground)
        assertEquals(context.getColor(R.color.keyboard_dark_surface), dark.keyboardBackground)
        assertTrue(light.actionText != light.actionSurface)
        assertTrue(dark.actionText != dark.actionSurface)
    }

    @Test
    fun systemThemeResolutionUsesTheRequestedMode() {
        val system = KaruikeyPreferences.resolveKeyboardAppearance(
            context, KaruikeyPreferences.THEME_SYSTEM, false
        )
        assertEquals(
            (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES,
            system.isDark
        )
    }

    @Test
    fun dynamicPaletteUsesFrameworkSystemColorsWhenAvailable() {
        if (Build.VERSION.SDK_INT < 31) return
        val appearance = KaruikeyPreferences.resolveKeyboardAppearance(
            context, KaruikeyPreferences.THEME_LIGHT, true
        )
        assertTrue(appearance.usesDynamicColors)
        assertEquals(context.getColor(android.R.color.system_accent1_600), appearance.actionSurface)
        assertTrue(appearance.actionSurface != context.getColor(R.color.keyboard_light_action))
    }

    @Test
    fun keyboardHeightPreferenceIsClampedToTheSupportedRange() {
        KaruikeyPreferences.setHeightPercent(context, 85)
        assertEquals(85, KaruikeyPreferences.heightPercent(context))
        KaruikeyPreferences.setHeightPercent(context, 115)
        assertEquals(115, KaruikeyPreferences.heightPercent(context))
        KaruikeyPreferences.setHeightPercent(context, 200)
        assertEquals(115, KaruikeyPreferences.heightPercent(context))
    }

    @Test
    fun visualAndTypingPreferencesUseSafeDefaultsAndBounds() {
        assertFalse(KaruikeyPreferences.transparencyEnabled(context))
        assertEquals(0, KaruikeyPreferences.transparencyAmount(context))
        assertFalse(KaruikeyPreferences.blurEnabled(context))
        assertFalse(KaruikeyPreferences.suggestionsEnabled(context))

        KaruikeyPreferences.setTransparencyEnabled(context, true)
        KaruikeyPreferences.setTransparencyAmount(context, 100)
        assertEquals(35, KaruikeyPreferences.transparencyAmount(context))
        assertTrue(KaruikeyPreferences.keyboardSurfaceAlpha(context) >= 0.65f)
    }
}

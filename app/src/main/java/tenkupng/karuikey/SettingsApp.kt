package tenkupng.karuikey

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

enum class SettingsPage {
    HOME, LANGUAGES, ADD_LANGUAGE, APPEARANCE, TYPING, CLIPBOARD, TRY, ABOUT, LICENSE
}

@Composable
fun KaruikeySettingsApp(refreshVersion: Int) {
    val context = LocalContext.current
    var page by rememberSaveable { mutableStateOf(SettingsPage.HOME) }
    var navigationDirection by remember { mutableStateOf(1) }
    var themeMode by remember(refreshVersion) {
        mutableStateOf(KaruikeyPreferences.theme(context))
    }
    var dynamicColors by remember(refreshVersion) {
        mutableStateOf(KaruikeyPreferences.dynamicColorsEnabled(context))
    }

    KaruikeyComposeTheme(themeMode, dynamicColors) {
        val back = {
            navigationDirection = -1
            page = when (page) {
                SettingsPage.ADD_LANGUAGE -> SettingsPage.LANGUAGES
                SettingsPage.LICENSE -> SettingsPage.ABOUT
                else -> SettingsPage.HOME
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
        ) {
            AnimatedContent(
                modifier = Modifier.fillMaxSize(),
                targetState = page,
                transitionSpec = {
                    (slideInHorizontally(tween(180)) { width -> navigationDirection * width / 8 } +
                        fadeIn(tween(180))).togetherWith(
                        slideOutHorizontally(tween(180)) { width -> -navigationDirection * width / 8 } +
                            fadeOut(tween(120))
                    ).using(SizeTransform(clip = false))
                },
                label = "settings page transition"
            ) { currentPage ->
                when (currentPage) {
            SettingsPage.HOME -> SettingsScaffold("", true, back) { contentPadding ->
                HomePage(context, refreshVersion, contentPadding) {
                    navigationDirection = 1
                    page = it
                }
            }
            SettingsPage.LANGUAGES -> SettingsScaffold("Languages", false, back) { padding ->
                LanguagesPage(refreshVersion, padding) { page = it }
            }
            SettingsPage.ADD_LANGUAGE -> SettingsScaffold("Add language", false, back) { padding ->
                AddLanguagePage(refreshVersion, padding) { page = SettingsPage.LANGUAGES }
            }
            SettingsPage.APPEARANCE -> SettingsScaffold("Appearance", false, back) { padding ->
                AppearancePage(refreshVersion, padding,
                    onThemeChanged = { themeMode = it },
                    onDynamicColorsChanged = { dynamicColors = it })
            }
            SettingsPage.TYPING -> SettingsScaffold("Typing", false, back) { padding ->
                TypingPage(refreshVersion, padding)
            }
            SettingsPage.CLIPBOARD -> SettingsScaffold("Clipboard", false, back) { padding ->
                ClipboardPage(refreshVersion, padding)
            }
            SettingsPage.TRY -> SettingsScaffold("Try Karuikey", false, back) { padding ->
                TryPage(padding)
            }
            SettingsPage.ABOUT -> SettingsScaffold("About", false, back) { padding ->
                AboutPage(padding) { page = SettingsPage.LICENSE }
            }
            SettingsPage.LICENSE -> SettingsScaffold("Licenses", false, back) { padding ->
                LicensePage(padding)
            }
                }
            }
        }
    }
}

@Composable
private fun HomePage(
    context: Context,
    refreshVersion: Int,
    contentPadding: Modifier,
    onNavigate: (SettingsPage) -> Unit
) {
    val languages = remember(refreshVersion) { KaruikeyPreferences.enabledLanguages(context) }
    val theme = when (KaruikeyPreferences.theme(context)) {
        KaruikeyPreferences.THEME_LIGHT -> "Light"
        KaruikeyPreferences.THEME_DARK -> "Dark"
        else -> "System"
    }
    val clipboardSummary = if (ClipboardHistory.enabled(context)) {
        "${ClipboardHistory.retentionHours(context)} h retention · ${ClipboardHistory.items(context).size} saved"
    } else {
        "History off"
    }
    PageColumn(modifier = contentPadding.verticalScroll(rememberScrollState())) {
        SettingsHero(
            "Private by design",
            "Offline keyboard settings for English and Russian",
            KaruikeySymbol.KEYBOARD
        )
        SectionLabel("Keyboard")
        SettingsGroup {
            SettingsRow(KaruikeySymbol.LANGUAGE, "Languages",
                languages.joinToString { it.displayName }) { onNavigate(SettingsPage.LANGUAGES) }
            GroupDivider()
            SettingsRow(KaruikeySymbol.PALETTE, "Appearance", "$theme · ${KaruikeyPreferences.heightPercent(context)}% height") {
                onNavigate(SettingsPage.APPEARANCE)
            }
            GroupDivider()
            SettingsRow(KaruikeySymbol.KEYBOARD, "Typing",
                "Suggestions · ${if (KaruikeyPreferences.suggestionsEnabled(context)) "On" else "Off"}") {
                onNavigate(SettingsPage.TYPING)
            }
            GroupDivider()
            SettingsRow(KaruikeySymbol.CONTENT_PASTE, "Clipboard", clipboardSummary) {
                onNavigate(SettingsPage.CLIPBOARD)
            }
            GroupDivider()
            SettingsRow(KaruikeySymbol.KEYBOARD_ALT, "Try Karuikey",
                "Test text, email, multiline, numeric, and search fields") {
                onNavigate(SettingsPage.TRY)
            }
            GroupDivider()
            SettingsRow(KaruikeySymbol.INFO, "About", "Version, licenses, and privacy") {
                onNavigate(SettingsPage.ABOUT)
            }
        }
        SectionLabel("System")
        SettingsGroup {
            SettingsRow(KaruikeySymbol.SETTINGS, "Keyboard setup", "Open Android keyboard settings") {
                context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }
            GroupDivider()
            SettingsRow(KaruikeySymbol.KEYBOARD, "Input method picker", "Choose the active keyboard") {
                context.getSystemService(InputMethodManager::class.java).showInputMethodPicker()
            }
        }
    }
}

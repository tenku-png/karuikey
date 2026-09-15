package tenkupng.karuikey

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Text

enum class SettingsPage {
    HOME, LANGUAGES, ADD_LANGUAGE, APPEARANCE, TYPING, CLIPBOARD, TRY, ABOUT, LICENSE
}

@Composable
fun KaruikeySettingsApp(refreshVersion: Int) {
    val context = LocalContext.current
    var page by rememberSaveable { mutableStateOf(SettingsPage.HOME) }
    var themeMode by remember(refreshVersion) {
        mutableStateOf(KaruikeyPreferences.theme(context))
    }
    var dynamicColors by remember(refreshVersion) {
        mutableStateOf(KaruikeyPreferences.dynamicColorsEnabled(context))
    }

    KaruikeyComposeTheme(themeMode, dynamicColors) {
        val back = {
            page = if (page == SettingsPage.ADD_LANGUAGE) SettingsPage.LANGUAGES
            else SettingsPage.HOME
        }
        when (page) {
            SettingsPage.HOME -> SettingsScaffold("", true, back) { contentPadding ->
                HomePage(context, refreshVersion, contentPadding) { page = it }
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
        Text(
            "Private, offline keyboard settings",
            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge
        )
        SectionLabel("Keyboard")
        SettingsGroup {
            SettingsRow(painterResource(R.drawable.ic_keyboard_language), "Languages",
                languages.joinToString { it.displayName }) { onNavigate(SettingsPage.LANGUAGES) }
            GroupDivider()
            SettingsRow(painterResource(R.drawable.ic_settings_palette), "Appearance", "$theme · ${KaruikeyPreferences.heightPercent(context)}% height") {
                onNavigate(SettingsPage.APPEARANCE)
            }
            GroupDivider()
            SettingsRow(painterResource(R.drawable.ic_keyboard_settings), "Typing",
                "Suggestions · ${if (KaruikeyPreferences.suggestionsEnabled(context)) "On" else "Off"}") {
                onNavigate(SettingsPage.TYPING)
            }
            GroupDivider()
            SettingsRow(painterResource(R.drawable.ic_keyboard_clipboard), "Clipboard", clipboardSummary) {
                onNavigate(SettingsPage.CLIPBOARD)
            }
            GroupDivider()
            SettingsRow(painterResource(R.drawable.ic_settings_keyboard), "Try Karuikey",
                "Test text, email, multiline, numeric, and search fields") {
                onNavigate(SettingsPage.TRY)
            }
            GroupDivider()
            SettingsRow(painterResource(R.drawable.ic_settings_info), "About", "Version, licenses, and privacy") {
                onNavigate(SettingsPage.ABOUT)
            }
        }
        SectionLabel("System")
        SettingsGroup {
            SettingsRow(painterResource(R.drawable.ic_keyboard_settings), "Keyboard setup", "Open Android keyboard settings") {
                context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }
            GroupDivider()
            SettingsRow(painterResource(R.drawable.ic_settings_keyboard), "Input method picker", "Choose the active keyboard") {
                context.getSystemService(InputMethodManager::class.java).showInputMethodPicker()
            }
        }
    }
}

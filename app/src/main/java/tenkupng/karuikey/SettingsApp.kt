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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun KaruikeySettingsApp(
    refreshVersion: Int,
    onAddDictionary: (KaruikeyLanguage) -> Unit = {}
) {
    val context = LocalContext.current
    var page by rememberSaveable { mutableStateOf(SettingsPage.HOME) }
    var pendingLanguageAnimation by rememberSaveable { mutableStateOf<String?>(null) }
    var navigationDirection by remember { mutableStateOf(1) }
    var themeMode by remember(refreshVersion) {
        mutableStateOf(KaruikeyPreferences.theme(context))
    }
    var dynamicColors by remember(refreshVersion) {
        mutableStateOf(KaruikeyPreferences.dynamicColorsEnabled(context))
    }

    KaruikeyComposeTheme(themeMode, dynamicColors) {
        val motion = MaterialTheme.motionScheme
        val back = {
            navigationDirection = -1
            page = when (page) {
                SettingsPage.ADD_LANGUAGE -> SettingsPage.LANGUAGES
                SettingsPage.LICENSE -> SettingsPage.ABOUT
                else -> SettingsPage.HOME
            }
            if (page == SettingsPage.HOME) pendingLanguageAnimation = null
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
                    (slideInHorizontally(
                        animationSpec = motion.fastSpatialSpec(),
                        initialOffsetX = { width -> navigationDirection * width / 8 }
                    ) + fadeIn(animationSpec = motion.fastEffectsSpec())).togetherWith(
                        slideOutHorizontally(
                            animationSpec = motion.fastSpatialSpec(),
                            targetOffsetX = { width -> -navigationDirection * width / 8 }
                        ) + fadeOut(animationSpec = motion.fastEffectsSpec())
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
                LanguagesPage(refreshVersion, padding, pendingLanguageAnimation, onAddDictionary) {
                    pendingLanguageAnimation = null
                    page = it
                }
            }
            SettingsPage.ADD_LANGUAGE -> SettingsScaffold("Add language", false, back) { padding ->
                AddLanguagePage(refreshVersion, padding) { languageId ->
                    pendingLanguageAnimation = languageId
                    page = SettingsPage.LANGUAGES
                }
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
        SettingsGroup {
            SettingsRow(KaruikeySymbol.LANGUAGE, "Languages",
                languages.joinToString { it.displayName }) { onNavigate(SettingsPage.LANGUAGES) }
        }
        SettingsGroup {
            SettingsRow(KaruikeySymbol.PALETTE, "Appearance", "$theme · ${KaruikeyPreferences.heightPercent(context)}% height") {
                onNavigate(SettingsPage.APPEARANCE)
            }
            SettingsRow(KaruikeySymbol.KEYBOARD, "Typing",
                "Suggestions · ${if (KaruikeyPreferences.suggestionsEnabled(context)) "On" else "Off"}") {
                onNavigate(SettingsPage.TYPING)
            }
        }
        SettingsGroup {
            SettingsRow(KaruikeySymbol.CONTENT_PASTE, "Clipboard", clipboardSummary) {
                onNavigate(SettingsPage.CLIPBOARD)
            }
        }
        SettingsGroup {
            SettingsRow(KaruikeySymbol.KEYBOARD_ALT, "Try Karuikey",
                "Test text, email, multiline, numeric, and search fields") {
                onNavigate(SettingsPage.TRY)
            }
            SettingsRow(KaruikeySymbol.INFO, "About", "Version and open-source notices") {
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

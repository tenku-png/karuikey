package tenkupng.karuikey

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.zIndex
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

enum class SettingsPage(val tab: SettingsPage? = null) {
    HOME, APPEARANCE, SETTINGS,
    LANGUAGES(SETTINGS), ADD_LANGUAGE(SETTINGS), TYPING(SETTINGS), CLIPBOARD(SETTINGS),
    ABOUT(SETTINGS), LICENSE(SETTINGS), TRY(HOME);

    val isTab get() = tab == null
    val depth: Int get() = if (isTab) 0 else parent.depth + 1
    val parent: SettingsPage
        get() = when (this) {
            ADD_LANGUAGE -> LANGUAGES
            LICENSE -> ABOUT
            else -> tab ?: HOME
        }
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
    var themeMode by remember(refreshVersion) {
        mutableStateOf(KaruikeyPreferences.theme(context))
    }
    var dynamicColors by remember(refreshVersion) {
        mutableStateOf(KaruikeyPreferences.dynamicColorsEnabled(context))
    }

    KaruikeyComposeTheme(themeMode, dynamicColors) {
        val motion = MaterialTheme.motionScheme
        val navigate = { target: SettingsPage -> page = target }
        // Bumped whenever a sub-page closes so the kept-alive tabs re-read what it changed.
        var tabRefresh by remember { mutableIntStateOf(0) }
        val back = {
            page = page.parent
            if (page.isTab) {
                pendingLanguageAnimation = null
                tabRefresh++
            }
        }
        BackHandler(enabled = page != SettingsPage.HOME, onBack = back)
        var visitedTabs by remember { mutableStateOf(setOf(SettingsPage.HOME)) }
        if (page.isTab && page !in visitedTabs) visitedTabs = visitedTabs + page
        LaunchedEffect(Unit) {
            // Warm the other tabs one at a time once launch has settled, so even the first
            // switch is a pure layer animation.
            delay(600)
            TAB_PAGES.forEach { tab ->
                withFrameNanos { }
                visitedTabs = visitedTabs + tab
                delay(150)
            }
        }
        val pageContent: @Composable (SettingsPage) -> Unit = { target ->
            SettingsPageContent(
                target, refreshVersion + tabRefresh, pendingLanguageAnimation, onAddDictionary,
                navigate, back,
                onThemeChanged = { themeMode = it },
                onDynamicColorsChanged = { dynamicColors = it },
                onLanguageAdded = { pendingLanguageAnimation = it },
                onLanguageAnimationShown = { pendingLanguageAnimation = null }
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            CompositionLocalProvider(LocalFloatingBarSpace provides FLOATING_BAR_SPACE) {
                // Tabs stay composed once visited, so switching only animates their layers
                // instead of composing and measuring a whole page in a single frame.
                TAB_PAGES.forEach { tab ->
                    if (tab in visitedTabs) {
                        key(tab) {
                            KeptAliveTab(visible = page == tab) { pageContent(tab) }
                        }
                    }
                }
                AnimatedContent(
                    modifier = Modifier.fillMaxSize().zIndex(2f),
                    targetState = page.takeUnless { it.isTab },
                    transitionSpec = {
                        val direction = if ((targetState?.depth ?: 0) > (initialState?.depth ?: 0)) 1 else -1
                        (slideInHorizontally(motion.defaultSpatialSpec()) { direction * it / 5 } +
                            fadeIn(motion.defaultEffectsSpec())).togetherWith(
                            slideOutHorizontally(motion.defaultSpatialSpec()) { -direction * it / 5 } +
                                fadeOut(motion.fastEffectsSpec())
                        ).using(SizeTransform(clip = false))
                    },
                    label = "settings sub-page transition"
                ) { subPage ->
                    if (subPage != null) pageContent(subPage)
                }
            }
            AnimatedVisibility(
                visible = page.isTab,
                modifier = Modifier
                    .zIndex(3f)
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
                enter = slideInVertically(motion.defaultSpatialSpec()) { it * 2 } +
                    fadeIn(motion.defaultEffectsSpec()),
                exit = slideOutVertically(motion.fastSpatialSpec()) { it * 2 } +
                    fadeOut(motion.fastEffectsSpec())
            ) {
                FloatingNavBar(
                    items = listOf(
                        FloatingNavItem(SettingsPage.HOME, KaruikeySymbol.HOME, "Home"),
                        FloatingNavItem(SettingsPage.APPEARANCE, KaruikeySymbol.PALETTE, "Appearance"),
                        FloatingNavItem(SettingsPage.SETTINGS, KaruikeySymbol.SETTINGS, "Settings")
                    ),
                    selected = page.tab ?: page,
                    onSelect = navigate
                )
            }
        }
    }
}

private val TAB_PAGES = listOf(SettingsPage.HOME, SettingsPage.APPEARANCE, SettingsPage.SETTINGS)

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun KeptAliveTab(visible: Boolean, content: @Composable () -> Unit) {
    val motion = MaterialTheme.motionScheme
    val progress = remember { Animatable(if (visible) 1f else 0f) }
    LaunchedEffect(visible) {
        progress.animateTo(
            if (visible) 1f else 0f,
            if (visible) motion.defaultEffectsSpec() else motion.fastEffectsSpec()
        )
    }
    Box(
        Modifier
            .fillMaxSize()
            .zIndex(if (visible) 1f else 0f)
            .then(
                if (visible) Modifier
                else Modifier
                    .clearAndSetSemantics {}
                    .pointerInput(Unit) {
                        // A fading-out tab must not take taps meant for the incoming one.
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                            }
                        }
                    }
            )
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    // Hidden tabs stay placed at alpha 0: the render thread skips them, and their
                    // recorded display lists survive, so showing one again needs no re-record.
                    placeable.placeWithLayer(0, 0) {
                        val p = progress.value
                        alpha = p
                        val scale = 0.94f + 0.06f * p
                        scaleX = scale
                        scaleY = scale
                    }
                }
            }
    ) { content() }
}

@Composable
private fun SettingsPageContent(
    page: SettingsPage,
    refreshVersion: Int,
    pendingLanguageAnimation: String?,
    onAddDictionary: (KaruikeyLanguage) -> Unit,
    navigate: (SettingsPage) -> Unit,
    back: () -> Unit,
    onThemeChanged: (String) -> Unit,
    onDynamicColorsChanged: (Boolean) -> Unit,
    onLanguageAdded: (String) -> Unit,
    onLanguageAnimationShown: () -> Unit
) {
    val context = LocalContext.current
    when (page) {
        SettingsPage.HOME -> SettingsScaffold("Karuikey", false, back) { padding ->
            HomePage(context, refreshVersion, padding, navigate)
        }
        SettingsPage.APPEARANCE -> SettingsScaffold("Appearance", false, back) { padding ->
            AppearancePage(refreshVersion, padding, onThemeChanged, onDynamicColorsChanged)
        }
        SettingsPage.SETTINGS -> SettingsScaffold("Settings", false, back) { padding ->
            SettingsHubPage(context, refreshVersion, padding, navigate)
        }
        SettingsPage.LANGUAGES -> SettingsScaffold("Languages", true, back) { padding ->
            LanguagesPage(refreshVersion, padding, pendingLanguageAnimation, onAddDictionary) {
                onLanguageAnimationShown()
                navigate(it)
            }
        }
        SettingsPage.ADD_LANGUAGE -> SettingsScaffold("Add language", true, back) { padding ->
            AddLanguagePage(refreshVersion, padding) { languageId ->
                onLanguageAdded(languageId)
                navigate(SettingsPage.LANGUAGES)
            }
        }
        SettingsPage.TYPING -> SettingsScaffold("Typing", true, back) { padding ->
            TypingPage(refreshVersion, padding)
        }
        SettingsPage.CLIPBOARD -> SettingsScaffold("Clipboard", true, back) { padding ->
            ClipboardPage(refreshVersion, padding)
        }
        SettingsPage.TRY -> SettingsScaffold("Try Karuikey", true, back) { padding ->
            TryPage(padding)
        }
        SettingsPage.ABOUT -> SettingsScaffold("About", true, back) { padding ->
            AboutPage(padding) { navigate(SettingsPage.LICENSE) }
        }
        SettingsPage.LICENSE -> SettingsScaffold("Licenses", true, back) { padding ->
            LicensePage(padding)
        }
    }
}

private enum class SetupState { NOT_ENABLED, NOT_SELECTED, READY }

private fun setupState(context: Context): SetupState {
    val imm = context.getSystemService(InputMethodManager::class.java)
    val enabled = imm.enabledInputMethodList.any { it.packageName == context.packageName }
    if (!enabled) return SetupState.NOT_ENABLED
    val current = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
    return if (current?.startsWith(context.packageName + "/") == true) SetupState.READY
    else SetupState.NOT_SELECTED
}

@Composable
private fun HomePage(
    context: Context,
    refreshVersion: Int,
    contentPadding: Modifier,
    onNavigate: (SettingsPage) -> Unit
) {
    val languages = remember(refreshVersion) { KaruikeyPreferences.enabledLanguages(context) }
    val setup = remember(refreshVersion) { setupState(context) }
    // Preference and clipboard reads hit disk-backed stores; keep them out of recomposition.
    val suggestionsOn = remember(refreshVersion) { KaruikeyPreferences.suggestionsEnabled(context) }
    val heightPercent = remember(refreshVersion) { KaruikeyPreferences.heightPercent(context) }
    val clipboardSummary = remember(refreshVersion) {
        if (ClipboardHistory.enabled(context)) "${ClipboardHistory.items(context).size} saved"
        else "History off"
    }
    var sample by rememberSaveable { mutableStateOf("") }
    PageColumn(modifier = contentPadding.verticalScroll(rememberScrollState())) {
        when (setup) {
            SetupState.NOT_ENABLED -> HeroCard(
                KaruikeySymbol.WARNING, "Turn on Karuikey",
                "Enable the keyboard in Android settings to start typing with it.",
                "Open keyboard settings", enterIndex = 0
            ) { context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
            SetupState.NOT_SELECTED -> HeroCard(
                KaruikeySymbol.KEYBOARD, "Almost there",
                "Karuikey is enabled. Make it the active keyboard.",
                "Choose keyboard", enterIndex = 0
            ) { context.getSystemService(InputMethodManager::class.java).showInputMethodPicker() }
            SetupState.READY -> HeroCard(
                KaruikeySymbol.CHECK_CIRCLE, "Ready to type",
                "Karuikey is your active keyboard.",
                null, enterIndex = 0
            )
        }
        SectionLabel("Try it", enterIndex = 1)
        SettingsGroup(enterIndex = 2) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = sample,
                    onValueChange = { sample = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Type something…") },
                    shape = MaterialTheme.shapes.large,
                    minLines = 2
                )
            }
            GroupDivider()
            SettingsRow(KaruikeySymbol.TEXT_FIELDS, "More test fields",
                "Email, numbers, search and multiline") { onNavigate(SettingsPage.TRY) }
        }
        SectionLabel("Quick access", enterIndex = 3)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickTile(KaruikeySymbol.LANGUAGE, "Languages",
                languages.joinToString { it.displayName }, Modifier.weight(1f), enterIndex = 4) {
                onNavigate(SettingsPage.LANGUAGES)
            }
            QuickTile(KaruikeySymbol.SPELLCHECK, "Typing",
                if (suggestionsOn) "Suggestions on" else "Suggestions off",
                Modifier.weight(1f), enterIndex = 5) {
                onNavigate(SettingsPage.TYPING)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickTile(KaruikeySymbol.PALETTE, "Appearance",
                "$heightPercent% height", Modifier.weight(1f),
                enterIndex = 6) {
                onNavigate(SettingsPage.APPEARANCE)
            }
            QuickTile(KaruikeySymbol.CONTENT_PASTE, "Clipboard",
                clipboardSummary,
                Modifier.weight(1f), enterIndex = 7) {
                onNavigate(SettingsPage.CLIPBOARD)
            }
        }
    }
}

@Composable
private fun SettingsHubPage(
    context: Context,
    refreshVersion: Int,
    contentPadding: Modifier,
    onNavigate: (SettingsPage) -> Unit
) {
    val languages = remember(refreshVersion) { KaruikeyPreferences.enabledLanguages(context) }
    var spacebarLanguageSwipe by remember(refreshVersion) {
        mutableStateOf(KaruikeyPreferences.spacebarLanguageSwipe(context))
    }
    val clipboardSummary = remember(refreshVersion) {
        if (ClipboardHistory.enabled(context)) {
            "${ClipboardHistory.retentionHours(context)} h retention · ${ClipboardHistory.items(context).size} saved"
        } else {
            "History off"
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val saved = runCatching {
            context.contentResolver.openOutputStream(uri)!!.use { SettingsBackup.export(context, it) }
        }.isSuccess
        Toast.makeText(context, if (saved) "Settings exported" else "Could not export settings",
            Toast.LENGTH_SHORT).show()
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val restored = runCatching {
            context.contentResolver.openInputStream(uri)!!.use { SettingsBackup.import(context, it) }
        }.getOrDefault(false)
        Toast.makeText(context, if (restored) "Settings imported" else "Not a Karuikey settings file",
            Toast.LENGTH_SHORT).show()
        // Theme and every page read preferences on creation; start over with the new ones.
        if (restored) (context as? android.app.Activity)?.recreate()
    }
    PageColumn(modifier = contentPadding.verticalScroll(rememberScrollState())) {
        SectionLabel("Input", enterIndex = 0)
        SettingsGroup(enterIndex = 1) {
            SettingsRow(KaruikeySymbol.LANGUAGE, "Languages",
                languages.joinToString { it.displayName }) { onNavigate(SettingsPage.LANGUAGES) }
            GroupDivider()
            SettingsRow(KaruikeySymbol.SPELLCHECK, "Typing",
                "Suggestions, auto-correction, capitalization") { onNavigate(SettingsPage.TYPING) }
            GroupDivider()
            SettingsRow(KaruikeySymbol.CONTENT_PASTE, "Clipboard", clipboardSummary) {
                onNavigate(SettingsPage.CLIPBOARD)
            }
        }
        SectionLabel("Gestures", enterIndex = 2)
        SettingsGroup(enterIndex = 3) {
            SettingsSwitchRow(
                KaruikeySymbol.SWIPE,
                "Switch language by swipe",
                if (spacebarLanguageSwipe) "Quick flick switches language, hold and slide moves the cursor"
                else "Sliding on the spacebar moves the cursor",
                spacebarLanguageSwipe
            ) { enabled ->
                spacebarLanguageSwipe = enabled
                KaruikeyPreferences.setSpacebarLanguageSwipe(context, enabled)
            }
        }
        SectionLabel("System", enterIndex = 4)
        SettingsGroup(enterIndex = 5) {
            SettingsRow(KaruikeySymbol.OPEN_IN_NEW, "Keyboard setup", "Open Android keyboard settings") {
                context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }
            GroupDivider()
            SettingsRow(KaruikeySymbol.KEYBOARD, "Input method picker", "Choose the active keyboard") {
                context.getSystemService(InputMethodManager::class.java).showInputMethodPicker()
            }
            GroupDivider()
            SettingsRow(KaruikeySymbol.ARROW_FORWARD, "Export settings",
                "Save keyboard settings to a file") {
                exportLauncher.launch("karuikey-settings.json")
            }
            GroupDivider()
            SettingsRow(KaruikeySymbol.OPEN_IN_NEW, "Import settings",
                "Restore keyboard settings from a file") {
                importLauncher.launch(arrayOf("application/json", "text/*"))
            }
            GroupDivider()
            SettingsRow(KaruikeySymbol.INFO, "About", "Version and open-source notices") {
                onNavigate(SettingsPage.ABOUT)
            }
        }
    }
}

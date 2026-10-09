package tenkupng.karuikey

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.first

@Composable
fun AppearancePage(
    refreshVersion: Int,
    contentPadding: Modifier,
    onThemeChanged: (String) -> Unit,
    onDynamicColorsChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var theme by remember(refreshVersion) { mutableStateOf(KaruikeyPreferences.theme(context)) }
    var dynamic by remember(refreshVersion) {
        mutableStateOf(KaruikeyPreferences.dynamicColorsEnabled(context))
    }
    var transparency by remember(refreshVersion) {
        mutableStateOf(KaruikeyPreferences.transparencyEnabled(context))
    }
    var transparencyAmount by remember(refreshVersion) {
        mutableStateOf(KaruikeyPreferences.transparencyAmount(context).toFloat())
    }
    var height by remember(refreshVersion) {
        mutableStateOf(KaruikeyPreferences.heightPercent(context).toFloat())
    }
    var keyTransparency by remember(refreshVersion) {
        mutableStateOf(KaruikeyPreferences.keyTransparency(context).toFloat())
    }
    val blurSupported = remember(refreshVersion) { KaruikeyPreferences.blurSupported(context) }
    var blur by remember(refreshVersion) {
        mutableStateOf(KaruikeyPreferences.blurEnabled(context) && blurSupported)
    }
    var blurRadius by remember(refreshVersion) {
        mutableStateOf(KaruikeyPreferences.blurRadius(context).toFloat())
    }
    var blurGrain by remember(refreshVersion) {
        mutableStateOf(KaruikeyPreferences.blurGrain(context).toFloat())
    }
    var blurContrast by remember(refreshVersion) {
        mutableStateOf(KaruikeyPreferences.blurContrast(context).toFloat())
    }
    var accentFunctionKeys by remember(refreshVersion) {
        mutableStateOf(KaruikeyPreferences.accentFunctionKeys(context))
    }
    var toolbar by remember(refreshVersion) {
        mutableStateOf(KaruikeyPreferences.toolbarEnabled(context))
    }
    var keyPreview by remember(refreshVersion) {
        mutableStateOf(KaruikeyPreferences.keyPreviewEnabled(context))
    }
    var suggestionPosition by remember(refreshVersion) {
        mutableStateOf(KaruikeyPreferences.suggestionPosition(context))
    }
    var emojiPlacement by remember(refreshVersion) {
        mutableStateOf(KaruikeyPreferences.emojiKeyPlacement(context))
    }
    val multipleLanguages = remember(refreshVersion) {
        KaruikeyPreferences.enabledLanguages(context).size > 1
    }
    PageColumn(modifier = contentPadding.verticalScroll(rememberScrollState())) {
        SectionLabel("Theme")
        ChoiceButtonGroup(
            listOf(
                "System" to KaruikeyPreferences.THEME_SYSTEM,
                "Light" to KaruikeyPreferences.THEME_LIGHT,
                "Dark" to KaruikeyPreferences.THEME_DARK
            ),
            theme,
            icons = listOf(KaruikeySymbol.BRIGHTNESS_AUTO, KaruikeySymbol.LIGHT_MODE, KaruikeySymbol.DARK_MODE)
        ) { value ->
            theme = value
            KaruikeyPreferences.setTheme(context, value)
            onThemeChanged(value)
        }
        SettingsGroup {
            SettingsSwitchRow(KaruikeySymbol.AUTO_AWESOME, "Dynamic colors",
                if (Build.VERSION.SDK_INT >= 31) "Use the Android Material You palette" else "Requires Android 12 or newer",
                dynamic, Build.VERSION.SDK_INT >= 31) {
                dynamic = it
                KaruikeyPreferences.setDynamicColorsEnabled(context, it)
                onDynamicColorsChanged(it)
            }
        }
        SectionLabel("Layout")
        SettingsGroup {
            SettingsSliderRow(KaruikeySymbol.HEIGHT, "Keyboard height", "${height.toInt()}%", height,
                KaruikeyPreferences.MIN_HEIGHT_PERCENT.toFloat()..KaruikeyPreferences.MAX_HEIGHT_PERCENT.toFloat()
            ) {
                height = it
                KaruikeyPreferences.setHeightPercent(context, it.toInt())
            }
            GroupDivider()
            SettingsSwitchRow(KaruikeySymbol.SETTINGS, "Toolbar", "Show clipboard and settings actions",
                toolbar) {
                toolbar = it
                KaruikeyPreferences.setToolbarEnabled(context, it)
            }
            GroupDivider()
            SettingsSwitchRow(KaruikeySymbol.KEYBOARD, "Key preview", "Show the pressed key preview",
                keyPreview) {
                keyPreview = it
                KaruikeyPreferences.setKeyPreviewEnabled(context, it)
            }
            GroupDivider()
            SettingsSwitchRow(KaruikeySymbol.PALETTE, "Highlight function keys",
                "Tint shift, backspace and punctuation keys", accentFunctionKeys) {
                accentFunctionKeys = it
                KaruikeyPreferences.setAccentFunctionKeys(context, it)
            }
        }
        SectionLabel("Suggestion strip")
        ChoiceButtonGroup(
            listOf(
                "Full bar" to KaruikeyPreferences.SUGGESTION_POSITION_FULL,
                "Left" to KaruikeyPreferences.SUGGESTION_POSITION_LEFT,
                "Right" to KaruikeyPreferences.SUGGESTION_POSITION_RIGHT
            ),
            suggestionPosition
        ) { value ->
            suggestionPosition = value
            KaruikeyPreferences.setSuggestionPosition(context, value)
        }
        SectionLabel("Emoji key")
        ChoiceButtonGroup(
            listOf(
                "Toolbar" to KaruikeyPreferences.EMOJI_PLACEMENT_TOOLBAR,
                "Bottom row" to KaruikeyPreferences.EMOJI_PLACEMENT_BOTTOM,
                "Off" to KaruikeyPreferences.EMOJI_PLACEMENT_OFF
            ),
            emojiPlacement
        ) { value ->
            emojiPlacement = value
            KaruikeyPreferences.setEmojiKeyPlacement(context, value)
        }
        AnimatedVisibility(
            visible = multipleLanguages &&
                emojiPlacement == KaruikeyPreferences.EMOJI_PLACEMENT_BOTTOM,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Text(
                "Language switching stays available in the toolbar; Spacebar swipe can also switch languages.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
        SectionLabel("Keyboard surface")
        SettingsGroup {
            SettingsSwitchRow(KaruikeySymbol.PALETTE, "Transparency",
                "Let the app behind show through the keyboard", transparency) {
                transparency = it
                KaruikeyPreferences.setTransparencyEnabled(context, it)
            }
            AnimatedVisibility(
                visible = transparency,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    GroupDivider()
                    SettingsSliderRow(KaruikeySymbol.TUNE, "Transparency",
                        "${transparencyAmount.toInt()}%", transparencyAmount,
                        0f..(if (blur) KaruikeyPreferences.MAX_BLUR_TRANSPARENCY else 35).toFloat()
                    ) {
                        transparencyAmount = it
                        KaruikeyPreferences.setTransparencyAmount(context, it.toInt())
                    }
                    GroupDivider()
                    SettingsSliderRow(KaruikeySymbol.KEYBOARD, "Key transparency",
                        "${keyTransparency.toInt()}%", keyTransparency,
                        0f..KaruikeyPreferences.MAX_KEY_TRANSPARENCY.toFloat()
                    ) {
                        keyTransparency = it
                        KaruikeyPreferences.setKeyTransparency(context, it.toInt())
                    }
                }
            }
            GroupDivider()
            SettingsSwitchRow(KaruikeySymbol.BLUR_ON, "Blur",
                when {
                    Build.VERSION.SDK_INT < 31 -> "Requires Android 12 or newer"
                    !blurSupported -> "Turned off by the system or not supported"
                    else -> "Blur the app behind the keyboard"
                },
                blur, blurSupported) {
                blur = it
                KaruikeyPreferences.setBlurEnabled(context, it)
                if (!it) {
                    // Without blur the surface returns to the tighter transparency limit.
                    transparencyAmount = transparencyAmount.coerceAtMost(35f)
                }
            }
            AnimatedVisibility(
                visible = blur,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    GroupDivider()
                    SettingsRow(KaruikeySymbol.AUTO_AWESOME, "Frosted preset",
                        "Heavy matte blur with translucent keys") {
                        KaruikeyPreferences.applyFrostedPreset(context)
                        transparency = true
                        transparencyAmount = KaruikeyPreferences.transparencyAmount(context).toFloat()
                        keyTransparency = KaruikeyPreferences.keyTransparency(context).toFloat()
                        blurRadius = KaruikeyPreferences.blurRadius(context).toFloat()
                        blurGrain = KaruikeyPreferences.blurGrain(context).toFloat()
                        blurContrast = KaruikeyPreferences.blurContrast(context).toFloat()
                    }
                    GroupDivider()
                    SettingsSliderRow(KaruikeySymbol.BLUR_ON, "Blur strength", "${blurRadius.toInt()} dp",
                        blurRadius,
                        KaruikeyPreferences.MIN_BLUR_RADIUS.toFloat()..KaruikeyPreferences.MAX_BLUR_RADIUS.toFloat(),
                        onValueChangeFinished = { KaruikeyPreferences.setBlurRadius(context, blurRadius.toInt()) }
                    ) { blurRadius = it }
                    GroupDivider()
                    SettingsSliderRow(KaruikeySymbol.TUNE, "Grain", "${blurGrain.toInt()}%", blurGrain,
                        0f..100f,
                        onValueChangeFinished = { KaruikeyPreferences.setBlurGrain(context, blurGrain.toInt()) }
                    ) { blurGrain = it }
                    GroupDivider()
                    SettingsSliderRow(KaruikeySymbol.BRIGHTNESS_AUTO, "Contrast",
                        "${if (blurContrast > 0) "+" else ""}${blurContrast.toInt()}", blurContrast,
                        -KaruikeyPreferences.MAX_BLUR_CONTRAST.toFloat()..KaruikeyPreferences.MAX_BLUR_CONTRAST.toFloat(),
                        onValueChangeFinished = { KaruikeyPreferences.setBlurContrast(context, blurContrast.toInt()) }
                    ) { blurContrast = it }
                }
            }
        }
    }
}

@Composable
fun LanguagesPage(
    refreshVersion: Int,
    contentPadding: Modifier,
    animateLanguageId: String? = null,
    onAddDictionary: (KaruikeyLanguage) -> Unit = {},
    onNavigate: (SettingsPage) -> Unit
) {
    val context = LocalContext.current
    val catalog = remember(refreshVersion) { KaruikeyPreferences.languages(context) }
    val initiallyEnabled = remember(refreshVersion, animateLanguageId) {
        KaruikeyPreferences.enabledLanguages(context).map { it.id }.toSet()
    }
    var enabledIds by remember(refreshVersion) {
        mutableStateOf(initiallyEnabled)
    }
    var visibleIds by remember(refreshVersion, animateLanguageId) {
        mutableStateOf(initiallyEnabled - animateLanguageId)
    }
    LaunchedEffect(animateLanguageId, initiallyEnabled) {
        val id = animateLanguageId ?: return@LaunchedEffect
        if (initiallyEnabled.contains(id)) visibleIds = visibleIds + id
    }
    PageColumn(modifier = contentPadding.verticalScroll(rememberScrollState())) {
        SectionLabel("Enabled")
        SettingsGroup {
            val visibleLanguages = catalog.filter { visibleIds.contains(it.id) }
            visibleLanguages.forEachIndexed { index, language ->
                val source = remember(refreshVersion, language.locale) {
                    SuggestionEngine.dictionarySource(language.locale)
                }
                AnimatedLanguageRow(
                    language = language,
                    summary = "${language.nativeName} · ${language.layoutName} · " +
                        suggestionStatus(source),
                    checked = enabledIds.contains(language.id),
                    canChange = enabledIds.size > 1 || !enabledIds.contains(language.id),
                    onCheckedChange = { checked ->
                        if (KaruikeyPreferences.setLanguageEnabled(context, language, checked)) {
                            enabledIds = KaruikeyPreferences.enabledLanguages(context)
                                .map { it.id }.toSet()
                        }
                    },
                    onExitFinished = {
                        if (!enabledIds.contains(language.id)) {
                            visibleIds = visibleIds - language.id
                        }
                    }
                )
                if (source != SuggestionEngine.DictionarySource.BUNDLED) {
                    GroupDivider()
                    SettingsRow(
                        KaruikeySymbol.EDIT,
                        "Suggestions",
                        if (source == SuggestionEngine.DictionarySource.EXTERNAL) {
                            "Local suggestions installed"
                        } else {
                            "Suggestions unavailable · Add suggestion data"
                        }
                    ) { onAddDictionary(language) }
                }
                if (index < visibleLanguages.lastIndex) GroupDivider()
            }
        }
        Button(
            onClick = { onNavigate(SettingsPage.ADD_LANGUAGE) },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Add language", style = MaterialTheme.typography.titleMedium)
        }
    }
}

internal fun suggestionStatus(source: SuggestionEngine.DictionarySource): String = when (source) {
    SuggestionEngine.DictionarySource.BUNDLED -> "Suggestions ready"
    SuggestionEngine.DictionarySource.EXTERNAL -> "Local suggestions installed"
    SuggestionEngine.DictionarySource.NONE -> "Suggestions unavailable"
}

@Composable
private fun AnimatedLanguageRow(
    language: KaruikeyLanguage,
    summary: String,
    checked: Boolean,
    canChange: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onExitFinished: () -> Unit
) {
    val motion = MaterialTheme.motionScheme
    val visibleState = remember(language.id) {
        androidx.compose.animation.core.MutableTransitionState(false)
    }
    visibleState.targetState = checked
    LaunchedEffect(visibleState, checked) {
        if (!checked) {
            snapshotFlow { visibleState.isIdle && !visibleState.currentState }
                .first { it }
            onExitFinished()
        }
    }
    AnimatedVisibility(
        visibleState = visibleState,
        enter = fadeIn(motion.fastEffectsSpec()) +
            expandVertically(motion.fastSpatialSpec()),
        exit = fadeOut(motion.fastEffectsSpec()) +
            shrinkVertically(motion.fastSpatialSpec()) +
            slideOutVertically(motion.fastSpatialSpec()),
        label = "language row visibility"
    ) {
        SettingsSwitchRow(
            KaruikeySymbol.LANGUAGE,
            language.displayName,
            summary,
            checked,
            enabled = canChange,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun AddLanguagePage(refreshVersion: Int, contentPadding: Modifier, onAdded: (String) -> Unit) {
    val context = LocalContext.current
    val catalog = remember(refreshVersion) { KaruikeyPreferences.languages(context) }
    val enabled = remember(refreshVersion) {
        KaruikeyPreferences.enabledLanguages(context).map { it.id }.toSet()
    }
    var query by rememberSaveable { mutableStateOf("") }
    val available = catalog.filterNot { enabled.contains(it.id) }.filter {
        query.isBlank() || it.displayName.contains(query, true) ||
            it.nativeName.contains(query, true) || it.locale.contains(query, true)
    }
    PageColumn(modifier = contentPadding.verticalScroll(rememberScrollState())) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search languages") },
            leadingIcon = { MaterialSymbolIcon(KaruikeySymbol.SEARCH, contentDescription = null) },
            singleLine = true
        )
        SectionLabel("Available")
        SettingsGroup {
            available.forEachIndexed { index, language ->
                SettingsRow(KaruikeySymbol.LANGUAGE, language.displayName,
                    "${language.nativeName} · ${language.layoutName}") {
                    KaruikeyPreferences.setLanguageEnabled(context, language, true)
                    onAdded(language.id)
                }
                if (index < available.size - 1) GroupDivider()
            }
            if (available.isEmpty()) {
                Text("No matching languages", modifier = Modifier.padding(20.dp))
            }
        }
    }
}

@Composable
fun TypingPage(refreshVersion: Int, contentPadding: Modifier) {
    val context = LocalContext.current
    var suggestions by remember(refreshVersion) {
        mutableStateOf(KaruikeyPreferences.suggestionsEnabled(context))
    }
    var autoCapitalization by remember(refreshVersion) {
        mutableStateOf(KaruikeyPreferences.autoCapitalizationEnabled(context))
    }
    var nextWordSuggestions by remember(refreshVersion) {
        mutableStateOf(KaruikeyPreferences.nextWordSuggestionsEnabled(context))
    }
    var autoCorrection by remember(refreshVersion) {
        mutableStateOf(KaruikeyPreferences.autoCorrectionEnabled(context))
    }
    var personalizedSuggestions by remember(refreshVersion) {
        mutableStateOf(KaruikeyPreferences.personalizedSuggestionsEnabled(context))
    }
    PageColumn(modifier = contentPadding.verticalScroll(rememberScrollState())) {
        SectionLabel("Typing assistance")
        SettingsGroup {
            SettingsSwitchRow(KaruikeySymbol.SPELLCHECK, "Suggestions",
                "Show locally available word suggestions", suggestions) {
                suggestions = it
                KaruikeyPreferences.setSuggestionsEnabled(context, it)
            }
            GroupDivider()
            SettingsSwitchRow(KaruikeySymbol.ARROW_FORWARD, "Next-word suggestions",
                "Suggest words after a completed word", nextWordSuggestions, suggestions) {
                nextWordSuggestions = it
                KaruikeyPreferences.setNextWordSuggestionsEnabled(context, it)
            }
            GroupDivider()
            SettingsSwitchRow(KaruikeySymbol.EDIT, "Auto-correction",
                "Fix a mistyped word on space; Backspace undoes it", autoCorrection, suggestions) {
                autoCorrection = it
                KaruikeyPreferences.setAutoCorrectionEnabled(context, it)
            }
            GroupDivider()
            SettingsSwitchRow(KaruikeySymbol.AUTO_AWESOME, "Personalized suggestions",
                "Learn from typing on this device", personalizedSuggestions, suggestions) {
                personalizedSuggestions = it
                KaruikeyPreferences.setPersonalizedSuggestionsEnabled(context, it)
            }
            if (personalizedSuggestions) {
                GroupDivider()
                SettingsRow(KaruikeySymbol.DELETE_SWEEP, "Clear learned suggestions",
                    "Forget words learned on this device") { PredictionHistory.clear(context) }
            }
            GroupDivider()
            SettingsSwitchRow(KaruikeySymbol.TEXT_FIELDS, "Auto-capitalization",
                "Use normal sentence and word capitalization flags", autoCapitalization) {
                autoCapitalization = it
                KaruikeyPreferences.setAutoCapitalizationEnabled(context, it)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipboardPage(refreshVersion: Int, contentPadding: Modifier) {
    val context = LocalContext.current
    var enabled by remember(refreshVersion) { mutableStateOf(ClipboardHistory.enabled(context)) }
    var retention by remember(refreshVersion) { mutableStateOf(ClipboardHistory.retentionHours(context)) }
    var maxItems by remember(refreshVersion) { mutableStateOf(ClipboardHistory.maxItems(context)) }
    var retentionMenu by remember { mutableStateOf(false) }
    val savedCount = remember(refreshVersion) { ClipboardHistory.items(context).size }
    var clearConfirmation by remember { mutableStateOf(false) }
    PageColumn(modifier = contentPadding.verticalScroll(rememberScrollState())) {
        Text("Clipboard history is stored only on this device and automatically deleted after the selected period.",
            style = MaterialTheme.typography.bodyLarge)
        SettingsGroup {
            SettingsSwitchRow(KaruikeySymbol.CONTENT_PASTE, "Clipboard history",
                if (enabled) "Opt-in local text history" else "Off by default; current clipboard remains explicit-only",
                enabled) {
                enabled = it
                ClipboardHistory.setEnabled(context, it)
            }
            GroupDivider()
            ListItem(
                headlineContent = { Text("Retention") },
                supportingContent = { Text(retentionLabel(retention)) },
                leadingContent = { MaterialSymbolIcon(KaruikeySymbol.CONTENT_PASTE, contentDescription = null) },
                trailingContent = {
                    Button(onClick = { retentionMenu = true }, enabled = enabled) { Text("Change") }
                    DropdownMenu(expanded = retentionMenu, onDismissRequest = { retentionMenu = false }) {
                        ClipboardHistory.retentionOptions.forEach { hours ->
                            DropdownMenuItem(
                                text = { Text(retentionLabel(hours)) },
                                onClick = {
                                    retention = hours
                                    ClipboardHistory.setRetentionHours(context, hours)
                                    retentionMenu = false
                                }
                            )
                        }
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
            GroupDivider()
            Text("Maximum items", style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(10, 20, 40).forEach { count ->
                    FilterChip(
                        selected = maxItems == count,
                        onClick = {
                            maxItems = count
                            ClipboardHistory.setMaxItems(context, count)
                        },
                        label = { Text(count.toString()) }
                    )
                }
            }
            Text("$savedCount saved", style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(20.dp))
        }
        Button(
            onClick = { clearConfirmation = true },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        ) {
            MaterialSymbolIcon(KaruikeySymbol.DELETE_SWEEP, contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer)
            Text("Clear clipboard history", modifier = Modifier.padding(start = 8.dp))
        }
    }
    if (clearConfirmation) {
        AlertDialog(
            onDismissRequest = { clearConfirmation = false },
            title = { Text("Clear clipboard history?") },
            text = { Text("All saved text will be removed from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    ClipboardHistory.clear(context)
                    clearConfirmation = false
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { clearConfirmation = false }) { Text("Cancel") } }
        )
    }
}

private fun retentionLabel(hours: Int) = when (hours) {
    ClipboardHistory.RETENTION_HOUR -> "1 hour"
    ClipboardHistory.RETENTION_DAY -> "24 hours"
    ClipboardHistory.RETENTION_THREE_DAYS -> "3 days"
    ClipboardHistory.RETENTION_WEEK -> "1 week"
    else -> "1 month"
}

@Composable
fun TryPage(contentPadding: Modifier) {
    var normal by rememberSaveable { mutableStateOf("") }
    var sentence by rememberSaveable { mutableStateOf("") }
    var multiline by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var number by rememberSaveable { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("") }
    PageColumn(modifier = contentPadding.verticalScroll(rememberScrollState())) {
        Text("These fields intentionally exercise different Android EditorInfo semantics.",
            style = MaterialTheme.typography.bodyLarge)
        TryField("Normal text", normal, { normal = it }, KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Default
        ))
        TryField("Sentence capitalization", sentence, { sentence = it }, KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Next
        ))
        TryField("Multiline text", multiline, { multiline = it }, KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Default
        ), minLines = 3)
        TryField("Email address", email, { email = it }, KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next
        ))
        TryField("Numbers", number, { number = it }, KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Next
        ))
        TryField("Search", search, { search = it }, KeyboardOptions(
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Search
        ))
    }
}

@Composable
private fun TryField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardOptions: KeyboardOptions,
    minLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        keyboardOptions = keyboardOptions,
        minLines = minLines,
        singleLine = minLines == 1
    )
}

@Composable
fun AboutPage(contentPadding: Modifier, onLicenses: () -> Unit) {
    val context = LocalContext.current
    val packageInfo = remember {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    PageColumn(modifier = contentPadding.verticalScroll(rememberScrollState())) {
    Text("Karuikey", style = MaterialTheme.typography.displayMediumEmphasized)
    Text("Version ${packageInfo.versionName ?: ""}", style = MaterialTheme.typography.bodyLarge)
    SettingsGroup {
        SettingsRow(
            KaruikeySymbol.INFO,
            "Open source",
            "GPL-3.0, AOSP Apache 2.0, and bundled font notices",
            onLicenses
        )
    }
}
}

@Composable
fun LicensePage(contentPadding: Modifier) {
    val context = LocalContext.current
    val gpl = remember { readLicense(context, "GPL-3.0.txt") }
    val notice = remember { readLicense(context, "NOTICE") }
    Column(
        modifier = contentPadding
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(
                horizontal = KaruikeySettingsTokens.pageHorizontal,
                vertical = KaruikeySettingsTokens.pageVertical
            ),
        verticalArrangement = Arrangement.spacedBy(KaruikeySettingsTokens.groupGap)
    ) {
        Text("GPL-3.0", style = MaterialTheme.typography.headlineSmall)
        androidx.compose.foundation.text.selection.SelectionContainer {
            Text(gpl, style = MaterialTheme.typography.bodySmall)
        }
        Text("NOTICE / AOSP attribution", style = MaterialTheme.typography.headlineSmall)
        androidx.compose.foundation.text.selection.SelectionContainer {
            Text(notice, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun readLicense(context: android.content.Context, name: String): String = try {
    context.assets.open(name).bufferedReader().use { it.readText() }
} catch (_: Exception) {
    "The complete $name text is included in the source distribution."
}

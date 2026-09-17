package tenkupng.karuikey

import android.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasAnyAncestor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test

class SettingsComponentsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @After
    fun restoreLanguagePreferences() {
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("karuikey_settings", 0).edit()
            .remove("enabled_languages")
            .remove("active_language")
            .apply()
    }

    @Test
    fun switchRowAcceptsDirectAndRowClicksExactlyOnce() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        KaruikeyPreferences.setToolbarEnabled(context, false)
        KaruikeyPreferences.setKeyPreviewEnabled(context, false)
        composeRule.onNodeWithText("Appearance").performClick()
        val toolbarSwitch = composeRule.onNode(
            isToggleable() and hasAnyAncestor(hasText("Toolbar"))
        )
        val keyPreviewSwitch = composeRule.onNode(
            isToggleable() and hasAnyAncestor(hasText("Key preview"))
        )

        toolbarSwitch.performScrollTo()
        toolbarSwitch.performClick().assertIsOn()
        composeRule.onAllNodesWithText("Toolbar").assertCountEquals(2)[1].performClick()
        toolbarSwitch.assertIsOff()

        composeRule.onNodeWithText("Key preview").performScrollTo()
        keyPreviewSwitch.performClick().assertIsOn()
        composeRule.onNodeWithText("Key preview").performClick()
        keyPreviewSwitch.assertIsOff()
    }

    @Test
    fun sliderRemainsInteractiveInsideSettingsGroup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        KaruikeyPreferences.setHeightPercent(context, 100)
        composeRule.onNodeWithText("Appearance").performClick()

        val slider = composeRule.onAllNodes(hasKeyboardHeightRange)
        slider.assertCountEquals(1)
        slider[0].performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
            setProgress(85f)
        }
        composeRule.runOnIdle {
            assertTrue(KaruikeyPreferences.heightPercent(context) < 100)
        }
    }

    @Test
    fun rootAndAboutNavigationUseTheIntendedDestinations() {
        composeRule.onNodeWithText("Karuikey Keyboard").assertExists()
        composeRule.onAllNodesWithText("Keyboard").assertCountEquals(0)

        composeRule.onNodeWithText("About").performClick()
        composeRule.onAllNodesWithText("Privacy").assertCountEquals(0)
        composeRule.onNodeWithText("Open source").performClick()
        composeRule.onNodeWithText("Licenses").assertExists()
        composeRule.onNodeWithText("GPL-3.0").assertExists()
    }

    @Test
    fun languageRowsPersistImmediatelyAndRespectTheLastLanguageGuard() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("karuikey_settings", 0).edit()
            .putStringSet("enabled_languages", setOf(KaruikeyPreferences.ENGLISH_ID,
                KaruikeyPreferences.RUSSIAN_ID))
            .putString("active_language", KaruikeyPreferences.ENGLISH_ID)
            .apply()
        composeRule.onNodeWithText("Languages").performClick()

        composeRule.onNodeWithText("English (US)").performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(KaruikeyPreferences.RUSSIAN_ID),
                KaruikeyPreferences.enabledLanguages(context).map { it.id })
        }
        composeRule.onAllNodesWithText("English (US)").assertCountEquals(0)

        composeRule.onNodeWithText("Add language").performClick()
        composeRule.onNodeWithText("English (US)").performClick()
        composeRule.onNodeWithText("English (US)").assertExists()

        context.getSharedPreferences("karuikey_settings", 0).edit()
            .putStringSet("enabled_languages", setOf(KaruikeyPreferences.ENGLISH_ID))
            .apply()
        composeRule.onNodeWithText("English (US)").performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(KaruikeyPreferences.ENGLISH_ID),
                KaruikeyPreferences.enabledLanguages(context).map { it.id })
        }
    }

    @Test
    fun transparencyResolvesOneSharedSurfaceAlpha() {
        val surface = Color.rgb(18, 52, 86)
        val resolved = KaruikeyPreferences.resolvedKeyboardSurfaceColor(surface, 0.5f)

        assertEquals(Color.argb(127, 18, 52, 86), resolved)
        assertEquals(resolved, KaruikeyPreferences.resolvedKeyboardSurfaceColor(surface, 0.5f))
    }

    private companion object {
        val hasKeyboardHeightRange = SemanticsMatcher("has keyboard height range") { node ->
            node.config.contains(SemanticsProperties.ProgressBarRangeInfo) &&
                node.config[SemanticsProperties.ProgressBarRangeInfo].range == 85f..115f
        }
    }
}

package tenkupng.karuikey

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class SettingsUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun floatingBarAndSubpagesNavigateAndReturn() {
        composeRule.onNodeWithText("Quick access").assertExists()

        composeRule.onNodeWithContentDescription("Appearance").performClick()
        composeRule.onNodeWithText("Theme").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithText("Languages").performClick()
        composeRule.onNodeWithText("Enabled").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()

        composeRule.onNodeWithText("Typing").performClick()
        composeRule.onNodeWithText("Auto-capitalization").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()

        composeRule.onNodeWithText("Clipboard").performClick()
        composeRule.onNodeWithText("Clipboard history").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()

        composeRule.onNodeWithText("About").performClick()
        composeRule.onNodeWithText("Open source").performClick()
        composeRule.onNodeWithText("GPL-3.0").assertIsDisplayed()
        composeRule.onNodeWithText("NOTICE / AOSP attribution").assertExists()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithContentDescription("Back").performClick()

        composeRule.onNodeWithContentDescription("Home").performClick()
        composeRule.onNodeWithText("More test fields").performClick()
        composeRule.onNodeWithText("Normal text").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()
    }
}

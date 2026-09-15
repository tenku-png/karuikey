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
    fun rootNavigatesToEverySettingsPageAndBack() {
        composeRule.onNodeWithText("Languages").performClick()
        composeRule.onNodeWithText("Enabled").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()

        composeRule.onNodeWithText("Appearance").performClick()
        composeRule.onNodeWithText("Keyboard surface").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()

        composeRule.onNodeWithText("Typing").performClick()
        composeRule.onNodeWithText("Auto-capitalization").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()

        composeRule.onNodeWithText("Clipboard").performClick()
        composeRule.onNodeWithText("Clipboard history").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()

        composeRule.onNodeWithText("Try Karuikey").performClick()
        composeRule.onNodeWithText("Normal text").assertIsDisplayed()
        composeRule.onNodeWithText("Search").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()

        composeRule.onNodeWithText("About").performClick()
        composeRule.onNodeWithText("View GPL license and NOTICE").assertIsDisplayed()
        composeRule.onNodeWithText("View GPL license and NOTICE").performClick()
        composeRule.onNodeWithText("GPL-3.0").assertIsDisplayed()
        composeRule.onNodeWithText("NOTICE / AOSP attribution").assertExists()
        composeRule.onNodeWithContentDescription("Back").performClick()
    }
}

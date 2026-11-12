package com.lume.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Starts the real app on a fresh install and walks through the main screens. Nothing here needs
 * the network: it only checks screens that draw from the empty local database, and the Home
 * search field, which is shown whether or not the feed loads.
 */
@RunWith(AndroidJUnit4::class)
class SmokeTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun text(id: Int): String = rule.activity.getString(id)

    private fun waitForText(value: String) {
        rule.waitUntil(timeoutMillis = 15_000) {
            rule.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun homeLibraryAndSettingsOpenAndCloseAgain() {
        // Home is the start screen.
        waitForText(text(R.string.search_hint_quick))

        // Library: empty on a fresh install.
        rule.onAllNodesWithText(text(R.string.nav_library)).onFirst().performClick()
        waitForText(text(R.string.library_empty))

        // Settings, from the menu.
        rule.onAllNodesWithText(text(R.string.nav_menu)).onFirst().performClick()
        rule.onNodeWithText(text(R.string.menu_settings)).performClick()
        waitForText(text(R.string.settings_search_hint))

        // Back out of Settings, then to Home with the tab bar.
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.onNodeWithContentDescription(text(R.string.nav_home)).performClick()
        waitForText(text(R.string.search_hint_quick))
    }
}

package com.cheku.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun performanceToolSupportsSystemBackHierarchy() {
        composeRule.onNodeWithText("\u5de5\u5177").performClick()
        composeRule.onNodeWithText("\u8f66\u8f86\u6027\u80fd\u6d4b\u8bd5").performClick()
        composeRule.onNodeWithText("\u6027\u80fd\u6d4b\u8bd5").fetchSemanticsNode()

        composeRule.activityRule.scenario.onActivity {
            it.onBackPressedDispatcher.onBackPressed()
        }

        composeRule.onNodeWithText("\u5e38\u7528\u8f66\u8f86\u6570\u636e\u5de5\u5177").fetchSemanticsNode()
    }
}

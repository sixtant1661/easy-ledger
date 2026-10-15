package com.easyledger.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.easyledger.app.ui.navigation.EasyLedgerApp
import com.easyledger.app.ui.theme.EasyLedgerTheme
import org.junit.Rule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LargeFontNavigationTest {
    @get:Rule val rule = createComposeRule()

    @Test fun primaryActionAndNavigationRemainReachableAtDoubleFontScale() {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                EasyLedgerTheme { EasyLedgerApp() }
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("跳过").fetchSemanticsNodes().isNotEmpty() || rule.onAllNodesWithText("查看项目").fetchSemanticsNodes().isNotEmpty() }
        if (rule.onAllNodesWithText("跳过").fetchSemanticsNodes().isNotEmpty()) rule.onNodeWithText("跳过").performScrollTo().performClick()
        rule.onNodeWithText("查看项目").performScrollTo().assertIsDisplayed()
            .assertHeightIsAtLeast(56.dp).performClick()
        try {
            rule.waitUntil(10_000) { rule.onAllNodesWithTag("project_list").fetchSemanticsNodes().isNotEmpty() }
        } catch (failure: Throwable) {
            throw AssertionError("Large-font navigation did not reach projects. UI snapshot:\n" + rule.onRoot().printToString().take(2500), failure)
        }
        rule.onNodeWithTag("project_list").performScrollToNode(hasText("暂无项目"))
        rule.onNodeWithText("暂无项目").assertIsDisplayed()
        rule.onNodeWithTag("nav_settings").performClick()
        rule.onNodeWithText("文字大小").performScrollTo().assertIsDisplayed()
    }
}

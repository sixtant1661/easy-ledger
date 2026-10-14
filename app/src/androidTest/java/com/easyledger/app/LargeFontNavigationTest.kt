package com.easyledger.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.easyledger.app.ui.navigation.EasyLedgerApp
import com.easyledger.app.ui.theme.EasyLedgerTheme
import org.junit.Rule
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
        rule.onNodeWithText("查看项目").performScrollTo().assertIsDisplayed()
            .assertHeightIsAtLeast(56.dp).performClick()
        rule.onNodeWithText("暂无项目").assertIsDisplayed()
        rule.onNodeWithTag("nav_settings").performClick()
        rule.onNodeWithText("文字大小").assertIsDisplayed()
    }
}

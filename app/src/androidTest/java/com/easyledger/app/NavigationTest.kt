package com.easyledger.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun homeProjectsAndSettingsAreReachable() {
        rule.onNodeWithText("简单记账助手").assertIsDisplayed()
        rule.onNodeWithText("查看项目").performClick()
        rule.onNodeWithText("暂无项目").assertIsDisplayed()
        rule.onNodeWithTag("nav_projects").assertIsSelected()
        rule.onNodeWithTag("nav_settings").performClick()
        rule.onNodeWithText("文字大小").assertIsDisplayed()
        rule.onNodeWithTag("nav_home").performClick()
        rule.onNodeWithText("查看项目").assertIsDisplayed()
    }

    @Test fun guidanceCanBeOpenedAndClosed() {
        rule.onNodeWithText("展开使用说明").performClick()
        rule.onNodeWithText("使用说明").assertIsDisplayed()
        rule.onNodeWithText("收起使用说明").performClick()
        rule.onNodeWithText("展开使用说明").assertIsDisplayed()
    }
}

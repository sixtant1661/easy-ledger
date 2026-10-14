package com.easyledger.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.After
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import java.util.UUID
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LedgerUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val projectName = "界面测试-" + UUID.randomUUID().toString().take(8)
    @After fun removeOnlyOurSyntheticProject() = runBlocking {
        val repo = ApplicationProvider.getApplicationContext<LedgerApplication>().repository
        repo.projects("").first().filter { it.name == projectName }.forEach { repo.deleteProject(it.id) }
    }
    private fun skipGuide() {
        rule.waitUntil(15_000) { rule.onAllNodesWithText("跳过").fetchSemanticsNodes().isNotEmpty() || rule.onAllNodesWithText("查看项目").fetchSemanticsNodes().isNotEmpty() }
        if (rule.onAllNodesWithText("跳过").fetchSemanticsNodes().isNotEmpty()) rule.onNodeWithText("跳过").performClick()
    }
    @Test fun createRecordSearchEditAndConfirmDelete() {
        skipGuide()
        rule.onNodeWithTag("nav_projects").performClick()
        rule.onNodeWithText("创建项目").performClick()
        rule.onNodeWithText("项目名称").performTextInput(projectName)
        rule.onNodeWithText("默认单价（元）").performTextInput("20")
        rule.onNodeWithText("保存项目").performScrollTo().performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText(projectName).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("nav_home").performClick()
        rule.onNodeWithText("新增工作记录").performScrollTo().performClick()
        rule.onNodeWithText("项目：请选择 ▾").performClick()
        rule.onNodeWithText(projectName).performClick()
        rule.onNodeWithText("计费数量").performScrollTo().performTextInput("7.5")
        rule.onNodeWithText("备注（可不填）").performScrollTo().performTextInput("合成测试备注")
        rule.onNodeWithText("核对并保存").performScrollTo().performClick()
        rule.onNodeWithText("确认保存").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("新增工作记录").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("历史记录").performScrollTo().performClick()
        rule.onNodeWithText("搜索项目名称或记录备注").performTextInput("合成测试备注")
        rule.onNodeWithText(projectName).performClick()
        rule.onNodeWithText("修改记录").performScrollTo().performClick()
        rule.onNodeWithText("计费数量").performScrollTo().performTextReplacement("8")
        rule.onNodeWithText("核对并保存").performScrollTo().performClick()
        rule.onNodeWithText("确认保存").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("删除记录").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("删除记录").performScrollTo().performClick()
        rule.onNodeWithText("取消").performClick()
        rule.onNodeWithText("删除记录").performScrollTo().performClick()
        rule.onNodeWithText("确认删除").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("没有找到记录").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("没有找到记录").assertIsDisplayed()
    }
    @Test fun guideCanBeReplayedAndCompletionSurvivesRecreation() {
        skipGuide()
        rule.onNodeWithTag("nav_settings").performClick()
        rule.onNodeWithText("重新查看新手引导").performScrollTo().performClick()
        rule.onNodeWithText("下一步").performClick()
        rule.onNodeWithText("上一步").performClick()
        rule.onNodeWithText("下一步").performClick()
        rule.onNodeWithText("下一步").performClick()
        rule.onNodeWithText("完成").performClick()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithTag("nav_home").assertExists()
        rule.onNodeWithText("跳过").assertDoesNotExist()
    }
}

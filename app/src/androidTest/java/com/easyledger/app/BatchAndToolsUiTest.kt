package com.easyledger.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.easyledger.app.domain.model.BillingMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class BatchAndToolsUiTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private val name="批量界面合成-${UUID.randomUUID().toString().take(8)}"
    private val repo get()=ApplicationProvider.getApplicationContext<LedgerApplication>().repository
    private fun home() {
        rule.waitUntil(15000){ rule.onAllNodesWithText("跳过").fetchSemanticsNodes().isNotEmpty() || rule.onAllNodesWithText("新增工作记录").fetchSemanticsNodes().isNotEmpty() }
        if(rule.onAllNodesWithText("跳过").fetchSemanticsNodes().isNotEmpty())rule.onNodeWithText("跳过").performScrollTo().performClick()
        rule.onNodeWithTag("nav_home").performClick()
    }
    @After fun cleanup()=runBlocking {
        repo.records(name).first().forEach { repo.deleteRecord(it.record.id) }
        repo.projects(name).first().forEach { repo.deleteProject(it.id) }
    }
    @Test fun batchPreviewSavesRealDataAndBackupEntryIsReachable() {
        runBlocking { repo.saveProject(null,name,"仅合成数据",BillingMode.DAY,"天","25") }
        home()
        rule.onNodeWithText("批量补录").performScrollTo().performClick()
        rule.onNodeWithText("项目：请选择 ▾").performScrollTo().performClick()
        rule.onNodeWithText(name).performClick()
        rule.onNodeWithText("每天计费数量").performScrollTo().performTextInput("2")
        rule.onNodeWithText("每天备注").performScrollTo().performTextInput(name)
        rule.onNodeWithText("核对整批记录").performScrollTo().performClick()
        rule.onNodeWithText("确认保存整批").performClick()
        rule.waitUntil(10000){rule.onAllNodesWithText("新增工作记录").fetchSemanticsNodes().isNotEmpty()}
        runBlocking { assertEquals("50.00",repo.records(name).first().single().record.amount) }
        rule.onNodeWithText("结算查询与导出").performScrollTo().performClick()
        rule.onNodeWithText("导出 Excel（XLSX）").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("导出 PDF").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("导出 PNG 图片（ZIP）").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("返回").performScrollTo().performClick()
        rule.onNodeWithTag("nav_settings").performClick()
        rule.onNodeWithText("备份与恢复").performScrollTo().performClick()
        rule.onNodeWithText("保存本地备份").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("选择备份并恢复").performScrollTo().assertIsDisplayed()
    }
}

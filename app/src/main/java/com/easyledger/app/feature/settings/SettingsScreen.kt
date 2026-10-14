package com.easyledger.app.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.easyledger.app.ui.components.InformationCard
import com.easyledger.app.ui.components.PageContent

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    PageContent("设置", modifier) {
        InformationCard("文字大小", "文字跟随手机系统字体大小。三星手机通常可在系统“设置 → 显示 → 字体大小和样式”调整；不同 One UI 版本入口可能不同。")
        InformationCard("隐私与数据", "无需登录，不包含广告或数据收集 SDK。此版本尚未提供业务数据存储、备份或导出。")
        InformationCard("关于应用", "简单记账助手 0.1.0\n基础工程预览版")
    }
}

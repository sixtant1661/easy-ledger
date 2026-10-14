package com.easyledger.app.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.easyledger.app.ui.components.InformationCard
import com.easyledger.app.ui.components.PageContent

@Composable
fun SettingsScreen(modifier: Modifier = Modifier, onGuide: () -> Unit = {}) {
    PageContent("设置", modifier) {
        InformationCard("文字大小", "文字跟随手机系统字体大小。三星手机通常可在系统“设置 → 显示 → 字体大小和样式”调整；不同 One UI 版本入口可能不同。")
        InformationCard("隐私与数据", "无需登录，不包含广告或数据收集 SDK。工作记录保存在手机本地。卸载应用、清除应用数据或丢失手机会导致数据丢失。目前尚无导出备份，长期使用前应等待备份恢复功能。")
        com.easyledger.app.feature.ledger.MainButton("重新查看新手引导", onClick = onGuide)
        InformationCard("关于应用", "简单记账助手 0.2.0\n本地工作记账测试版")
    }
}

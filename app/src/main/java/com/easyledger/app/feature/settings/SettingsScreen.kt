package com.easyledger.app.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.easyledger.app.ui.components.InformationCard
import com.easyledger.app.ui.components.PageContent

@Composable
fun SettingsScreen(modifier: Modifier = Modifier, onGuide: () -> Unit = {}, onBackup: () -> Unit = {}) {
    PageContent("设置", modifier) {
        InformationCard("文字大小", "文字跟随手机系统字体大小。三星手机通常可在系统“设置 → 显示 → 字体大小和样式”调整；不同 One UI 版本入口可能不同。")
        InformationCard("隐私与数据", "无需登录，不包含广告或数据收集 SDK。工作记录保存在手机本地。请定期保存备份并复制到另一安全位置；卸载、清除数据或丢失手机会导致未备份记录丢失。备份未加密，请勿公开上传。")
        com.easyledger.app.feature.ledger.MainButton("备份与恢复", onClick = onBackup)
        com.easyledger.app.feature.ledger.MainButton("重新查看新手引导", onClick = onGuide)
        InformationCard("关于应用", "易记账 0.4.2\n本地工作记账；新版固定签名，可更新同签名新版；与旧签名应用独立共存。")
    }
}

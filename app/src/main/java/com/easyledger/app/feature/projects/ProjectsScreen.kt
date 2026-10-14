package com.easyledger.app.feature.projects

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.easyledger.app.ui.components.InformationCard
import com.easyledger.app.ui.components.PageContent

@Composable
fun ProjectsScreen(modifier: Modifier = Modifier) {
    PageContent("项目列表", modifier) {
        InformationCard("暂无项目", "项目管理功能正在准备中。后续可以创建多个项目，分别查看记录和结算金额。")
        InformationCard("当前版本说明", "此页面为基础占位页面，不展示虚构的项目或金额，也不会保存业务数据。")
    }
}

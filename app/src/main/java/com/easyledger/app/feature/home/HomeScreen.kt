package com.easyledger.app.feature.home

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.easyledger.app.ui.components.InformationCard
import com.easyledger.app.ui.components.PageContent

@Composable
fun HomeScreen(onOpenProjects: () -> Unit, modifier: Modifier = Modifier, model: HomeViewModel = viewModel()) {
    val expanded by model.guidanceExpanded.collectAsStateWithLifecycle()
    PageContent("简单记账助手", modifier) {
        InformationCard("工作记账，清楚安心", "按项目整理工作，核对更方便。当前版本提供基础页面，记账功能将在后续开放。")
        Button(onClick = onOpenProjects, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text("查看项目")
        }
        TextButton(onClick = model::toggleGuidance, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(if (expanded) "收起使用说明" else "展开使用说明")
        }
        if (expanded) {
            InformationCard("使用说明", "通过下方导航切换首页、项目和设置。文字大小跟随手机系统设置；当前版本尚不能保存业务记录。")
        }
    }
}

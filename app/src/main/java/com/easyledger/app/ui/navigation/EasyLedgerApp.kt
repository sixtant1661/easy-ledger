package com.easyledger.app.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.dp
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import com.easyledger.app.feature.ledger.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.easyledger.app.feature.home.HomeScreen
import com.easyledger.app.feature.projects.ProjectsScreen
import com.easyledger.app.feature.settings.SettingsScreen

@Composable
fun EasyLedgerApp() {
    val model: LedgerViewModel = viewModel()
    val onboarding by model.onboardingComplete.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    var toolsBusy by remember { mutableStateOf(false) }
    val readError by model.readError.collectAsStateWithLifecycle()
    val allProjects by model.allProjects.collectAsStateWithLifecycle()
    val allRecords by model.allRecords.collectAsStateWithLifecycle()
    val allSegments by model.segments.collectAsStateWithLifecycle()
    val guideRequested by model.replayGuide.collectAsStateWithLifecycle()
    val showGuide = onboarding == false || guideRequested
    var guideStep by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(guideRequested) { if(guideRequested) guideStep=0 }
    val snackbar = remember { SnackbarHostState() }
    var snackbarHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    LaunchedEffect(model) { model.notices.collect { snackbar.showSnackbar(it) } }
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route
    val editing = currentRoute?.startsWith("record/") == true || currentRoute?.startsWith("project/") == true || currentRoute == "batch" || currentRoute == "backup"
    fun open(destination: Destination) {
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    Scaffold(
        modifier = Modifier.imePadding(),
        snackbarHost = { SnackbarHost(snackbar, Modifier.onSizeChanged { snackbarHeight = it.height }) },
        bottomBar = {
            if (!editing && onboarding != null && readError == null) NavigationBar {
                Destination.entries.forEach { destination ->
                    NavigationBarItem(
                        modifier = Modifier.testTag("nav_${destination.route}"),
                        selected = currentRoute == destination.route,
                        enabled = !busy && !toolsBusy,
                        onClick = { open(destination) },
                        icon = {
                            Icon(
                                imageVector = when (destination) {
                                    Destination.HOME -> Icons.Default.Home
                                    Destination.LEDGER -> Icons.Default.DateRange
                                    Destination.PROJECTS -> Icons.AutoMirrored.Filled.List
                                    Destination.SETTINGS -> Icons.Default.Settings
                                },
                                contentDescription = null,
                            )
                        },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { padding ->
        if (readError != null) { com.easyledger.app.ui.components.InformationCard("无法打开记账本", readError!!) }
        else if (onboarding == null) { com.easyledger.app.ui.components.InformationCard("正在打开记账本", "请稍候，数据保存在手机本地。") }
        else Column(Modifier.padding(padding).fillMaxSize()) {
            if(showGuide) {
                val titles=listOf("开始使用易记账","① 创建第一个项目","② 新增一条工作记录","③ 打开账本","④ 导出与分享")
                val hints=listOf("跟着页面操作，不会生成演示记录。","填写项目名称和常用单价，点保存。","选择日期，添加时间段，核对自动金额后保存。","选择项目和月份，查看表格与合计。","点导出表格、图片或 PDF，再选择保存或分享。")
                Surface(color=androidx.compose.ui.graphics.Color(0xFFE6F2EC)) {
                    Column(Modifier.fillMaxWidth().heightIn(max=220.dp).verticalScroll(rememberScrollState()).padding(12.dp)) {
                        Text(titles[guideStep],style=MaterialTheme.typography.titleMedium)
                        Text(hints[guideStep],fontSize=16.sp)
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                            if(guideStep>0) TextButton({guideStep--; when(guideStep) {1->navController.navigate("projects");2->navController.navigate("record/new");3,4->navController.navigate("settlement")} }) {Text("上一步")}
                            TextButton({model.finishOnboarding {} }) {Text("跳过")}
                            TextButton({
                                if(guideStep==4) model.finishOnboarding {} else {
                                    guideStep++
                                    when(guideStep) {
                                        1 -> if(allProjects.any {it.deletedAt==null}) {guideStep=2;navController.navigate("record/new")} else navController.navigate("project/new")
                                        2 -> navController.navigate("record/new")
                                        3,4 -> navController.navigate("settlement")
                                    }
                                }
                            },enabled=!busy && (guideStep!=1 || allProjects.any {it.deletedAt==null}) && (guideStep!=2 || allRecords.isNotEmpty())) {Text(if(guideStep==0) "开始使用" else if(guideStep==4) "完成" else "下一步")}
                        }
                    }
                }
            }
            NavHost(navController, startDestination = Destination.HOME.route, modifier = Modifier.weight(1f).padding(bottom = with(density) { snackbarHeight.toDp() } + if (snackbarHeight > 0) 8.dp else 0.dp).imePadding()) {
            composable(Destination.HOME.route) { HomeScreen({ open(Destination.PROJECTS) }, Modifier.fillMaxSize(), onNewRecord = { navController.navigate("record/new") }, onHistory = { navController.navigate("history") }, onBatch = { navController.navigate("batch") }, onSettlement = { navController.navigate("settlement") }, onCreateProject = { navController.navigate("project/new") }, onSettings = { open(Destination.SETTINGS) }, onRecord = { navController.navigate("detail/$it") }, ledger=model) }
            composable(Destination.PROJECTS.route) { ProjectList(model, { navController.navigate("project/new") }, { navController.navigate("project/$it") }, Modifier.fillMaxSize()) }
            composable(Destination.SETTINGS.route) { SettingsScreen(Modifier.fillMaxSize(), onGuide = { model.replayGuide.value = true }, onBackup = { navController.navigate("backup") }) }
            composable("batch") { BatchScreen(model, { navController.popBackStack() }, Modifier.fillMaxSize(), onHistory={navController.navigate("history")}) }
            composable("backup") { com.easyledger.app.feature.tools.ToolsScreen({ navController.popBackStack() }, Modifier.fillMaxSize(), backupOnly = true) }
            composable("settlement") { com.easyledger.app.feature.tools.ToolsScreen({ navController.popBackStack() }, Modifier.fillMaxSize(), onBusyChange={toolsBusy=it},onRecord={navController.navigate("detail/$it")}) }
            composable("history") { HistoryScreen(model, { navController.navigate("detail/$it") }, { navController.popBackStack() }, Modifier.fillMaxSize()) }
            composable("project/{id}") { entry ->
                val id = entry.arguments?.getString("id")
                val project = allProjects.firstOrNull { it.id == id }
                if (id == "new" || project != null) ProjectEditor(project, model, { navController.popBackStack() }, Modifier.fillMaxSize())
            }
            composable("record/{id}") { entry ->
                val id = entry.arguments?.getString("id")
                val record = allRecords.firstOrNull { it.record.id == id }?.record
                if ((id == "new" || record != null) && allSegments != null) RecordEditor(record, model, { navController.navigate("project/new") }, { navController.popBackStack() }, Modifier.fillMaxSize(), allSegments.orEmpty().filter { it.recordId == id }.map { com.easyledger.app.domain.time.TimeSpan(it.startDateTime, it.endDateTime) })
            }
            composable("detail/{id}") { entry ->
                val id = entry.arguments?.getString("id")
                val record = allRecords.firstOrNull { it.record.id == id }
                if (record != null) RecordDetail(record, model, { navController.navigate("record/$id") }, { navController.popBackStack() }, Modifier.fillMaxSize(), allSegments.orEmpty().filter { it.recordId == id }.map { com.easyledger.app.domain.time.TimeSpan(it.startDateTime, it.endDateTime) })
            }
        }
        }
    }
}

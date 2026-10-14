package com.easyledger.app.ui.navigation

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
    val readError by model.readError.collectAsStateWithLifecycle()
    val allProjects by model.allProjects.collectAsStateWithLifecycle()
    val allRecords by model.allRecords.collectAsStateWithLifecycle()
    val allSegments by model.segments.collectAsStateWithLifecycle()
    val guideRequested by model.replayGuide.collectAsStateWithLifecycle()
    val showGuide = onboarding == false || guideRequested
    val snackbar = remember { SnackbarHostState() }
    var snackbarHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    LaunchedEffect(model) { model.notices.collect { snackbar.showSnackbar(it) } }
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route
    val editing = currentRoute?.startsWith("record/") == true || currentRoute?.startsWith("project/") == true || currentRoute == "batch" || currentRoute == "backup" || currentRoute == "settlement"
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
            if (!showGuide && !editing && onboarding != null && readError == null) NavigationBar {
                Destination.entries.forEach { destination ->
                    NavigationBarItem(
                        modifier = Modifier.testTag("nav_${destination.route}"),
                        selected = currentRoute == destination.route,
                        enabled = !busy,
                        onClick = { open(destination) },
                        icon = {
                            Icon(
                                imageVector = when (destination) {
                                    Destination.HOME -> Icons.Default.Home
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
        else if (showGuide) { OnboardingScreen(model, { model.replayGuide.value = false }, Modifier.padding(padding).fillMaxSize()) }
        else NavHost(navController, startDestination = Destination.HOME.route, modifier = Modifier.padding(padding).padding(bottom = with(density) { snackbarHeight.toDp() } + if (snackbarHeight > 0) 8.dp else 0.dp).imePadding()) {
            composable(Destination.HOME.route) { HomeScreen({ open(Destination.PROJECTS) }, Modifier.fillMaxSize(), onNewRecord = { navController.navigate("record/new") }, onHistory = { navController.navigate("history") }, onBatch = { navController.navigate("batch") }, onSettlement = { navController.navigate("settlement") }) }
            composable(Destination.PROJECTS.route) { ProjectList(model, { navController.navigate("project/new") }, { navController.navigate("project/$it") }, Modifier.fillMaxSize()) }
            composable(Destination.SETTINGS.route) { SettingsScreen(Modifier.fillMaxSize(), onGuide = { model.replayGuide.value = true }, onBackup = { navController.navigate("backup") }) }
            composable("batch") { BatchScreen(model, { navController.popBackStack() }, Modifier.fillMaxSize()) }
            composable("backup") { com.easyledger.app.feature.tools.ToolsScreen({ navController.popBackStack() }, Modifier.fillMaxSize(), backupOnly = true) }
            composable("settlement") { com.easyledger.app.feature.tools.ToolsScreen({ navController.popBackStack() }, Modifier.fillMaxSize()) }
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

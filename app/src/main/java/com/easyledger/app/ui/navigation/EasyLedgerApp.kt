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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route
    fun open(destination: Destination) {
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    Scaffold(
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { destination ->
                    NavigationBarItem(
                        modifier = Modifier.testTag("nav_${destination.route}"),
                        selected = currentRoute == destination.route,
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
        NavHost(navController, startDestination = Destination.HOME.route, modifier = Modifier.padding(padding)) {
            composable(Destination.HOME.route) { HomeScreen({ open(Destination.PROJECTS) }, Modifier.fillMaxSize()) }
            composable(Destination.PROJECTS.route) { ProjectsScreen(Modifier.fillMaxSize()) }
            composable(Destination.SETTINGS.route) { SettingsScreen(Modifier.fillMaxSize()) }
        }
    }
}

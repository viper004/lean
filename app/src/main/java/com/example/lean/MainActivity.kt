package com.example.lean

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.lean.data.RideEntity
import com.example.lean.data.RideState
import com.example.lean.ui.LeanViewModel
import com.example.lean.ui.screens.DebugSensorScreen
import com.example.lean.ui.screens.HomeScreen
import com.example.lean.ui.screens.LiveRideScreen
import com.example.lean.ui.screens.PreparationScreen
import com.example.lean.ui.screens.RideHistoryScreen
import com.example.lean.ui.screens.RideSummaryScreen
import com.example.lean.ui.screens.SettingsScreen
import com.example.lean.ui.screens.SplashScreen
import com.example.lean.ui.theme.LeanTheme
import com.example.lean.ui.theme.primaryCyan
import com.example.lean.ui.theme.textMuted

class MainActivity : ComponentActivity() {

    private val viewModel: LeanViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val userSettings by viewModel.userSettings.collectAsState()

            LeanTheme(appThemeMode = userSettings.themeMode) {
                var showSplash by remember { mutableStateOf(true) }

                if (showSplash) {
                    SplashScreen(
                        onSplashFinished = { showSplash = false }
                    )
                } else {
                    val uiState by viewModel.uiState.collectAsState()
                    val sessionState by viewModel.activeRideSession.collectAsState()
                    val locationData by viewModel.locationData.collectAsState()

                    val allRides by viewModel.allRides.collectAsState()
                    val totalRideCount by viewModel.totalRideCount.collectAsState()
                    val totalRideTimeMs by viewModel.totalRideTimeMs.collectAsState()
                    val totalDistanceKm by viewModel.totalDistanceKm.collectAsState()
                    val bestLeanAngle by viewModel.bestLeanAngle.collectAsState()
                    val mostRecentRide by viewModel.mostRecentRide.collectAsState()
                    val selectedHistoricalRide by viewModel.selectedHistoricalRide.collectAsState()

                    // Keep screen awake based on user preference
                    DisposableEffect(userSettings.keepScreenAwake) {
                        if (userSettings.keepScreenAwake) {
                            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                        onDispose {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                    }

                    val navController = rememberNavController()
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route

                    val isMainTabScreen = currentRoute in listOf("home", "history", "settings")

                    Scaffold(
                        bottomBar = {
                            if (isMainTabScreen && sessionState.rideState == RideState.IDLE) {
                                NavigationBar(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 8.dp
                                ) {
                                    NavigationBarItem(
                                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                                        label = { Text("HOME") },
                                        selected = currentRoute == "home",
                                        onClick = {
                                            navController.navigate("home") {
                                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = MaterialTheme.colorScheme.primaryCyan,
                                            selectedTextColor = MaterialTheme.colorScheme.primaryCyan,
                                            unselectedIconColor = MaterialTheme.colorScheme.textMuted,
                                            unselectedTextColor = MaterialTheme.colorScheme.textMuted
                                        )
                                    )

                                    NavigationBarItem(
                                        icon = { Icon(Icons.Default.History, contentDescription = "History") },
                                        label = { Text("RIDES") },
                                        selected = currentRoute == "history",
                                        onClick = {
                                            navController.navigate("history") {
                                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = MaterialTheme.colorScheme.primaryCyan,
                                            selectedTextColor = MaterialTheme.colorScheme.primaryCyan,
                                            unselectedIconColor = MaterialTheme.colorScheme.textMuted,
                                            unselectedTextColor = MaterialTheme.colorScheme.textMuted
                                        )
                                    )

                                    NavigationBarItem(
                                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                                        label = { Text("SETTINGS") },
                                        selected = currentRoute == "settings",
                                        onClick = {
                                            navController.navigate("settings") {
                                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = MaterialTheme.colorScheme.primaryCyan,
                                            selectedTextColor = MaterialTheme.colorScheme.primaryCyan,
                                            unselectedIconColor = MaterialTheme.colorScheme.textMuted,
                                            unselectedTextColor = MaterialTheme.colorScheme.textMuted
                                        )
                                    )
                                }
                            }
                        }
                    ) { innerPadding ->
                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            NavHost(
                                navController = navController,
                                startDestination = "home"
                            ) {
                                // 1. HOME SCREEN
                                composable("home") {
                                    HomeScreen(
                                        totalRides = totalRideCount,
                                        totalTimeMs = totalRideTimeMs ?: 0L,
                                        totalDistanceKm = totalDistanceKm ?: 0f,
                                        bestLeanAngle = bestLeanAngle ?: 0f,
                                        recentRide = mostRecentRide,
                                        isUnfinishedRideFound = sessionState.isUnfinishedRideFound,
                                        onStartRideClick = {
                                            viewModel.prepareRide()
                                            navController.navigate("prepare")
                                        },
                                        onRideCardClick = { ride ->
                                            viewModel.selectHistoricalRide(ride)
                                            navController.navigate("ride_detail")
                                        },
                                        onResumeUnfinishedRide = {
                                            viewModel.resumeUnfinishedRide()
                                            navController.navigate("live_ride")
                                        },
                                        onDiscardUnfinishedRide = {
                                            viewModel.discardUnfinishedRide()
                                        }
                                    )
                                }

                                // 2. RIDE PREPARATION SCREEN
                                composable("prepare") {
                                    PreparationScreen(
                                        leanState = uiState,
                                        locationData = locationData,
                                        isGpsSettingEnabled = userSettings.isGpsEnabled,
                                        onCenterAndStartClick = {
                                            viewModel.centerAndStartRide()
                                            navController.navigate("live_ride") {
                                                popUpTo("home") { inclusive = false }
                                            }
                                        },
                                        onBackClick = { navController.popBackStack() }
                                    )
                                }

                                // 3. LIVE RIDE SCREEN
                                composable("live_ride") {
                                    LiveRideScreen(
                                        leanState = uiState,
                                        sessionState = sessionState,
                                        locationData = locationData,
                                        isGpsSettingEnabled = userSettings.isGpsEnabled,
                                        onReCenterClick = { viewModel.calibrateZero() },
                                        onEndRideConfirmed = {
                                            viewModel.endRide()
                                            navController.navigate("ride_summary") {
                                                popUpTo("home") { inclusive = false }
                                            }
                                        }
                                    )
                                }

                                // 4. POST-RIDE SUMMARY SCREEN
                                composable("ride_summary") {
                                    val completedRide = viewModel.activeRideSession.value.let { session ->
                                        viewModel.selectedHistoricalRide.value ?: viewModel.mostRecentRide.collectAsState().value
                                    }

                                    if (completedRide != null) {
                                        RideSummaryScreen(
                                            ride = completedRide,
                                            isHistoricalView = false,
                                            onDoneClick = {
                                                viewModel.returnToHomeFromRide()
                                                navController.navigate("home") {
                                                    popUpTo("home") { inclusive = true }
                                                }
                                            }
                                        )
                                    } else {
                                        navController.popBackStack()
                                    }
                                }

                                // 5. RIDE HISTORY SCREEN
                                composable("history") {
                                    RideHistoryScreen(
                                        rides = allRides,
                                        onRideClick = { ride ->
                                            viewModel.selectHistoricalRide(ride)
                                            navController.navigate("ride_detail")
                                        },
                                        onDeleteRide = { rideId ->
                                            viewModel.deleteRide(rideId)
                                        }
                                    )
                                }

                                // 6. HISTORICAL RIDE DETAIL VIEW
                                composable("ride_detail") {
                                    val selectedRide = selectedHistoricalRide
                                    if (selectedRide != null) {
                                        RideSummaryScreen(
                                            ride = selectedRide,
                                            isHistoricalView = true,
                                            onDoneClick = { navController.popBackStack() },
                                            onBackClick = { navController.popBackStack() }
                                        )
                                    } else {
                                        navController.popBackStack()
                                    }
                                }

                                // 7. SETTINGS SCREEN
                                composable("settings") {
                                    SettingsScreen(
                                        settings = userSettings,
                                        onThemeModeChange = { viewModel.updateThemeMode(it) },
                                        onSensorModeChange = { viewModel.updateSensorMode(it) },
                                        onSmoothingLevelChange = { viewModel.updateSmoothingLevel(it) },
                                        onKeepScreenAwakeChange = { viewModel.updateKeepScreenAwake(it) },
                                        onLockOrientationChange = { viewModel.updateLockOrientation(it) },
                                        onStraightThresholdChange = { viewModel.updateStraightThreshold(it) },
                                        onWarningThresholdChange = { viewModel.updateWarningThreshold(it) },
                                        onCriticalThresholdChange = { viewModel.updateCriticalThreshold(it) },
                                        onGpsEnabledChange = { viewModel.updateGpsEnabled(it) },
                                        onResetCalibration = { viewModel.resetCalibration() },
                                        onResetPeak = { viewModel.resetPeaks() },
                                        onBackClick = { navController.popBackStack() }
                                    )
                                }

                                // 8. DIAGNOSTIC DEBUG SCREEN
                                composable("debug") {
                                    DebugSensorScreen(
                                        state = uiState,
                                        onBackClick = { navController.popBackStack() }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }

    override fun onPause() {
        super.onPause()
        viewModel.onPause()
    }
}

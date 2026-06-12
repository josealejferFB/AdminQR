package com.example.escanqradmin.presentation.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.toRoute
import com.example.escanqradmin.domain.model.QrContent
import com.example.escanqradmin.presentation.common.sharedcomponents.AppBottomBar
import com.example.escanqradmin.presentation.ui.config.ConfigScreen
import com.example.escanqradmin.presentation.ui.home.HomeScreen
import com.example.escanqradmin.presentation.ui.result.ResultScreen
import com.example.escanqradmin.presentation.ui.result.ResultViewModel
import com.example.escanqradmin.presentation.ui.scanner.ScannerScreen
import com.example.escanqradmin.presentation.ui.splash.SplashScreen

private val routesWithBottomBar = setOf(
    Home::class,
    Scanner::class,
    Config::class
)

@Composable
fun AppNavigation(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showBottomBar = currentDestination?.let { dest ->
        routesWithBottomBar.any { route -> dest.hasRoute(route) }
    } ?: false

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                AppBottomBar(navController = navController)
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Splash,
            modifier = Modifier.padding(innerPadding),
            enterTransition = {
                slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(400, easing = FastOutSlowInEasing)) + fadeIn(tween(400))
            },
            exitTransition = {
                slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(400, easing = FastOutSlowInEasing)) + fadeOut(tween(400))
            },
            popEnterTransition = {
                slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(400, easing = FastOutSlowInEasing)) + fadeIn(tween(400))
            },
            popExitTransition = {
                slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(400, easing = FastOutSlowInEasing)) + fadeOut(tween(400))
            }
        ) {
            composable<Splash> {
                SplashScreen(
                    onNavigateToHome = {
                        navController.navigate(Home) {
                            popUpTo(Splash) { inclusive = true }
                        }
                    }
                )
            }
            composable<Home> {
                HomeScreen(navController = navController)
            }
            composable<Scanner> {
                ScannerScreen(navController = navController, onQrScanned = { qrContent ->
                    navController.navigate(
                        Result(
                            androidId = qrContent.androidId,
                            userName = qrContent.userName,
                            cedula = qrContent.cedula,
                            plate = qrContent.plate
                        )
                    )
                })
            }
            composable<Result> { backStackEntry ->
                val result: Result = backStackEntry.toRoute()
                val qrContent = QrContent(
                    androidId = result.androidId,
                    userName = result.userName,
                    cedula = result.cedula,
                    plate = result.plate
                )
                val resultViewModel: ResultViewModel = hiltViewModel()
                resultViewModel.setQrData(qrContent)
                ResultScreen(
                    navController = navController,
                    onScanAgain = { navController.popBackStack(Home, inclusive = false) }
                )
            }
            composable<Config> {
                ConfigScreen(navController = navController)
            }
        }
    }
}

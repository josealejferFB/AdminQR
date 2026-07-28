package com.example.escanqradmin.presentation.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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

val LocalSnackbarHostState = staticCompositionLocalOf { SnackbarHostState() }

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
    val snackbarHostState = remember { SnackbarHostState() }

    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
        Scaffold(
            // bottomBar removida: ahora es overlay flotante
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Splash,
                modifier = Modifier.padding(innerPadding),
                enterTransition = {
                    fadeIn(tween(350, easing = FastOutSlowInEasing)) +
                    scaleIn(
                        initialScale = 0.92f,
                        animationSpec = tween(350, easing = FastOutSlowInEasing)
                    )
                },
                exitTransition = {
                    fadeOut(tween(200, easing = FastOutSlowInEasing)) +
                    scaleOut(
                        targetScale = 0.95f,
                        animationSpec = tween(200, easing = FastOutSlowInEasing)
                    )
                },
                popEnterTransition = {
                    fadeIn(tween(350, easing = FastOutSlowInEasing)) +
                    scaleIn(
                        initialScale = 0.92f,
                        animationSpec = tween(350, easing = FastOutSlowInEasing)
                    )
                },
                popExitTransition = {
                    fadeOut(tween(200, easing = FastOutSlowInEasing)) +
                    scaleOut(
                        targetScale = 0.95f,
                        animationSpec = tween(200, easing = FastOutSlowInEasing)
                    )
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

        AnimatedVisibility(
            visible = showBottomBar,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(300)) +
                    slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f)
                    ),
            exit = fadeOut(tween(200)) +
                   slideOutVertically(
                       targetOffsetY = { it },
                       animationSpec = tween(200, easing = FastOutSlowInEasing)
                   )
        ) {
            FloatingBottomBar(navController = navController)
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp)
                .padding(horizontal = 16.dp)
        )
    }
}

@Composable
private fun FloatingBottomBar(navController: NavHostController) {
    Surface(
        modifier = Modifier.padding(start = 48.dp, end = 48.dp, bottom = 24.dp),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        AppBottomBar(navController = navController)
    }
}

package com.example.escanqradmin.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.example.escanqradmin.domain.model.QrContent
import com.example.escanqradmin.presentation.ui.home.HomeScreen
import com.example.escanqradmin.presentation.ui.result.ResultScreen
import com.example.escanqradmin.presentation.ui.result.ResultViewModel
import com.example.escanqradmin.presentation.ui.scanner.ScannerScreen
import com.example.escanqradmin.presentation.ui.espconfig.ESPConfigScreen

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing

@Composable
fun AppNavigation(navController: NavHostController) {
    NavHost(
        navController = navController, 
        startDestination = Home,
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(400, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(400))
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { -it },
                animationSpec = tween(400, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(400))
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { -it },
                animationSpec = tween(400, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(400))
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(400, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(400))
        }
    ) {
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
                        phone = qrContent.phone,
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
                phone = result.phone,
                plate = result.plate
            )
            
            val resultViewModel: ResultViewModel = hiltViewModel()
            resultViewModel.setQrData(qrContent)

            ResultScreen(onScanAgain = {
                navController.popBackStack(Home, inclusive = false)
            })
        }
        composable<ESPConfig> {
            ESPConfigScreen(navController = navController)
        }
    }
}

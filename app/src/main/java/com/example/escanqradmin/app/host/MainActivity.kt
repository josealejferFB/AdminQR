package com.example.escanqradmin.app.host

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.escanqradmin.domain.model.QrContent
import com.example.escanqradmin.presentation.ui.home.HomeScreen
import com.example.escanqradmin.presentation.ui.result.ResultScreen
import com.example.escanqradmin.presentation.ui.result.ResultViewModel
import com.example.escanqradmin.presentation.ui.scanner.ScannerScreen
import com.example.escanqradmin.presentation.theme.theme.EscanQRAdminTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EscanQRAdminTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            HomeScreen(onNavigateToScanner = {
                                navController.navigate("scanner")
                            })
                        }
                        composable("scanner") {
                            ScannerScreen(onQrScanned = { qrContent ->
                                val androidId = Uri.encode(qrContent.androidId)
                                val userName = Uri.encode(qrContent.userName)
                                val cedula = Uri.encode(qrContent.cedula)
                                val phone = Uri.encode(qrContent.phone)
                                val plate = Uri.encode(qrContent.plate)
                                navController.navigate("result?androidId=$androidId&userName=$userName&cedula=$cedula&phone=$phone&plate=$plate")
                            })
                        }
                        composable(
                            route = "result?androidId={androidId}&userName={userName}&cedula={cedula}&phone={phone}&plate={plate}",
                            arguments = listOf(
                                navArgument("androidId") { type = NavType.StringType; defaultValue = "" },
                                navArgument("userName") { type = NavType.StringType; defaultValue = "" },
                                navArgument("cedula") { type = NavType.StringType; defaultValue = "" },
                                navArgument("phone") { type = NavType.StringType; defaultValue = "" },
                                navArgument("plate") { type = NavType.StringType; defaultValue = "" }
                            )
                        ) { backStackEntry ->
                            val androidId = backStackEntry.arguments?.getString("androidId") ?: ""
                            val userName = backStackEntry.arguments?.getString("userName") ?: ""
                            val cedula = backStackEntry.arguments?.getString("cedula") ?: ""
                            val phone = backStackEntry.arguments?.getString("phone") ?: ""
                            val plate = backStackEntry.arguments?.getString("plate") ?: ""

                            val qrContent = QrContent(
                                androidId = androidId,
                                userName = userName,
                                cedula = cedula,
                                phone = phone,
                                plate = plate
                            )
                            
                            val resultViewModel: ResultViewModel = hiltViewModel()
                            resultViewModel.setQrData(qrContent)

                            ResultScreen(onScanAgain = {
                                navController.popBackStack("home", inclusive = false)
                            })
                        }
                    }
                }
            }
        }
    }
}
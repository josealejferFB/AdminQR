package com.example.escanqradmin.presentation.ui.scanner

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size as GeometrySize
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import com.example.escanqradmin.domain.model.QrContent
import com.example.escanqradmin.presentation.common.sharedcomponents.CustomBottomBar
import com.example.escanqradmin.presentation.common.sharedcomponents.CustomTopBar
import com.example.escanqradmin.presentation.common.util.SetSystemBarsVisibility
import java.util.concurrent.Executors
import com.example.escanqradmin.presentation.theme.color.*

@Composable
fun ScannerScreen(
    navController: NavHostController,
    onQrScanned: (QrContent) -> Unit,
    viewModel: ScannerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
        }
    )

    LaunchedEffect(key1 = true) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    val scannedData by viewModel.scannedData.collectAsState()
    var isNavigating by remember { mutableStateOf(false) }

    LaunchedEffect(scannedData) {
        if (scannedData != null && !isNavigating) {
            isNavigating = true
            onQrScanned(scannedData!!)
            viewModel.clearData()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            CameraPreview(
                lifecycleOwner = lifecycleOwner,
                onBarcodeDetected = { viewModel.processBarcode(it) }
            )
            ScannerOverlay()
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black), 
                contentAlignment = Alignment.Center
            ) {
                Text(text = "Se requiere acceso a la cámara", color = Color.White)
            }
        }

        // Floating App TopBar (Semi-transparent White)
        CustomTopBar(
            containerColor = Color.White.copy(alpha = 0.85f),
            isFloating = true,
            applyPrivacyPadding = true,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(1f)
        )

        // Floating App BottomBar (Semi-transparent White)
        CustomBottomBar(
            navController = navController,
            containerColor = Color.White.copy(alpha = 0.85f),
            isFloating = true,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(1f)
        )
    }
}

@Composable
fun CameraPreview(
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    onBarcodeDetected: (String) -> Unit
) {
    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val resolutionSelector = ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            android.util.Size(1280, 720),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                        )
                    )
                    .build()

                val imageAnalysis = ImageAnalysis.Builder()
                    .setResolutionSelector(resolutionSelector)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(
                    Executors.newSingleThreadExecutor(),
                    QrCodeAnalyzer { barcode -> onBarcodeDetected(barcode) }
                )

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun ScannerOverlay() {
    val infiniteTransition = rememberInfiniteTransition(label = "scanLine")
    val lineOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "lineOffset"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            
            val boxSize = canvasWidth * 0.7f
            val boxTop = (canvasHeight - boxSize) / 2f
            val boxLeft = (canvasWidth - boxSize) / 2f
            
            val scanRect = Rect(
                offset = Offset(boxLeft, boxTop),
                size = GeometrySize(boxSize, boxSize)
            )
            
            val backgroundPath = Path().apply {
                addRect(Rect(0f, 0f, canvasWidth, canvasHeight))
            }
            val cutoutPath = Path().apply {
                addRoundRect(RoundRect(scanRect, CornerRadius(24.dp.toPx())))
            }
            
            val overlayPath = Path.combine(PathOperation.Difference, backgroundPath, cutoutPath)
            
            drawPath(
                path = overlayPath,
                color = Color.Black.copy(alpha = 0.6f)
            )

            // Draw corners
            val cornerLength = 40.dp.toPx()
            val cornerStroke = 4.dp.toPx()
            val cornerColor = Color.White
            val radius = 24.dp.toPx()
            
            // Top Left
            drawPath(
                path = Path().apply {
                    moveTo(boxLeft, boxTop + cornerLength)
                    lineTo(boxLeft, boxTop + radius)
                    quadraticTo(boxLeft, boxTop, boxLeft + radius, boxTop)
                    lineTo(boxLeft + cornerLength, boxTop)
                },
                color = cornerColor,
                style = Stroke(width = cornerStroke)
            )
            
            // Top Right
            drawPath(
                path = Path().apply {
                    moveTo(boxLeft + boxSize - cornerLength, boxTop)
                    lineTo(boxLeft + boxSize - radius, boxTop)
                    quadraticTo(boxLeft + boxSize, boxTop, boxLeft + boxSize, boxTop + radius)
                    lineTo(boxLeft + boxSize, boxTop + cornerLength)
                },
                color = cornerColor,
                style = Stroke(width = cornerStroke)
            )
            
            // Bottom Left
            drawPath(
                path = Path().apply {
                    moveTo(boxLeft, boxTop + boxSize - cornerLength)
                    lineTo(boxLeft, boxTop + boxSize - radius)
                    quadraticTo(boxLeft, boxTop + boxSize, boxLeft + radius, boxTop + boxSize)
                    lineTo(boxLeft + cornerLength, boxTop + boxSize)
                },
                color = cornerColor,
                style = Stroke(width = cornerStroke)
            )
            
            // Bottom Right
            drawPath(
                path = Path().apply {
                    moveTo(boxLeft + boxSize - cornerLength, boxTop + boxSize)
                    lineTo(boxLeft + boxSize - radius, boxTop + boxSize)
                    quadraticTo(boxLeft + boxSize, boxTop + boxSize, boxLeft + boxSize, boxTop + boxSize - radius)
                    lineTo(boxLeft + boxSize, boxTop + boxSize - cornerLength)
                },
                color = cornerColor,
                style = Stroke(width = cornerStroke)
            )
            
            // Animated Scan Line
            val lineY = boxTop + (boxSize * lineOffset)
            drawLine(
                color = PrimaryBlue.copy(alpha = 0.8f),
                start = Offset(boxLeft + 10.dp.toPx(), lineY),
                end = Offset(boxLeft + boxSize - 10.dp.toPx(), lineY),
                strokeWidth = 3.dp.toPx()
            )
        }

        // Floating UI Elements below the scanning box
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (200).dp), 
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(PrimaryBlue, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ESCANEANDO...",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Posicione el QR en el recuadro.",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Flashlight Button
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color.White.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FlashlightOn,
                        contentDescription = "Flashlight",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                // Manual Entry Button
                Row(
                    modifier = Modifier
                        .height(56.dp)
                        .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(28.dp))
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Keyboard,
                        contentDescription = "Keyboard",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "INGRESAR DATOS\nMANUALMENTE",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 12.sp
                    )
                }
            }
        }
    }
}

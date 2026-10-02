package com.antigravity.assetmanager.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.mlkit.common.MlKitException
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.antigravity.assetmanager.model.Asset
import com.antigravity.assetmanager.ui.components.GlassCard
import com.antigravity.assetmanager.ui.theme.AppColors
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

import com.antigravity.assetmanager.ui.components.AssetItemCard

@Composable
fun ScanScreen(
    scannedAssets: List<Asset>,
    userStats: Map<String, Pair<Int, Int>> = emptyMap(),
    onBarcodeScanned: (String) -> Unit,
    onTrackClick: (String) -> Unit = {},
    onSearchUserClick: (String) -> Unit = {},
    onSearchDeptClick: (String) -> Unit = {},
    onCancelCheckClick: (String) -> Unit = {},
    onSaveNote: (String, String) -> Unit = { _, _ -> },
    onClearRecords: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var manualInput by remember { mutableStateOf("") }
    var isCameraActive by remember { mutableStateOf(false) }
    var isTorchOn by remember { mutableStateOf(false) }
    var showClearRecordsDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // 자산 스캔/등록 완료(또는 재스캔으로 시간 갱신) 시 최상단 0번 항목으로 자동 스크롤
    LaunchedEffect(scannedAssets.firstOrNull()?.assetNumber, scannedAssets.firstOrNull()?.inspectionTime) {
        if (scannedAssets.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    // 카메라 스캐너 활성화 시 뒤로가기 버튼으로 카메라 닫기
    BackHandler(enabled = isCameraActive) {
        isTorchOn = false
        isCameraActive = false
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 카메라 스캐너 영역 (활성화 시에만 공간 차지)
        if (isCameraActive) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppColors.BgCardSolid)
                    .border(1.dp, AppColors.BorderGlass, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (hasCameraPermission) {
                    CameraPreviewView(
                        isTorchOn = isTorchOn,
                        onBarcodeDetected = { barcode ->
                            vibratePhone(context)
                            isTorchOn = false
                            isCameraActive = false
                            onBarcodeScanned(barcode)
                        }
                    )

                    // 조준선 오버레이
                    Box(
                        modifier = Modifier
                            .width(200.dp)
                            .height(100.dp)
                            .border(2.dp, AppColors.Secondary, RoundedCornerShape(8.dp))
                    )

                    // 상단 컨트롤 바 (플래시 토글 & 스캔 닫기)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 플래시 토글 버튼
                        Button(
                            onClick = { isTorchOn = !isTorchOn },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isTorchOn) Color(0xFFFFB300) else Color(0x991E1E34),
                                contentColor = if (isTorchOn) Color.Black else Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(
                                imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                contentDescription = "플래시 토글",
                                modifier = Modifier.size(16.dp),
                                tint = if (isTorchOn) Color.Black else Color.White
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isTorchOn) "플래시 ON" else "플래시 OFF",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isTorchOn) Color.Black else Color.White
                            )
                        }

                        // 스캔 닫기 버튼
                        Button(
                            onClick = {
                                isTorchOn = false
                                isCameraActive = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Danger.copy(alpha = 0.85f)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("스캔 닫기", color = AppColors.TextMain, fontSize = 12.sp)
                        }
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("카메라 권한이 필요합니다.", color = AppColors.TextMuted, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                        ) {
                            Text("권한 요청", color = AppColors.TextMain)
                        }
                    }
                }
            }
        } else {
            // 비활성화 상태: [직접 입력...] [로그 삭제] [등록] [스캔] 1행으로 컴팩트하게 배치
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 슬림 수동 바코드 입력창
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .background(AppColors.BgCardSolid, RoundedCornerShape(8.dp))
                        .border(1.dp, AppColors.BorderGlass, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (manualInput.isEmpty()) {
                        Text("직접 입력...", color = AppColors.TextMuted, fontSize = 13.sp)
                    }
                    BasicTextField(
                        value = manualInput,
                        onValueChange = { manualInput = it },
                        singleLine = true,
                        textStyle = TextStyle(color = AppColors.TextMain, fontSize = 13.sp),
                        cursorBrush = SolidColor(AppColors.Primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                if (manualInput.isNotBlank()) {
                                    onBarcodeScanned(manualInput.trim())
                                    manualInput = ""
                                }
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 로그 삭제 버튼 (아이콘 너비에 맞춘 컴팩트 버튼)
                if (scannedAssets.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(AppColors.Danger.copy(alpha = 0.15f))
                            .clickable { showClearRecordsDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "로그 삭제",
                            modifier = Modifier.size(18.dp),
                            tint = AppColors.Danger
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // 슬림 스캔 버튼
                Button(
                    onClick = {
                        startGoogleCodeScanner(
                            context = context,
                            onBarcodeScanned = { barcode ->
                                vibratePhone(context)
                                onBarcodeScanned(barcode)
                            },
                            onFallbackToCamera = {
                                if (hasCameraPermission) {
                                    isCameraActive = true
                                } else {
                                    permissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier.height(40.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = AppColors.TextMain,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "스캔",
                            color = AppColors.TextMain,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (scannedAssets.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "화면에 표시된 스캔 기록이 없습니다.\n바코드를 스캔하거나 수동 입력하세요.\n(실사 완료된 조사 결과는 안전하게 보존됩니다)",
                    color = AppColors.TextMuted,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(scannedAssets, key = { it.assetNumber }) { asset ->
                    AssetItemCard(
                        asset = asset,
                        userStats = userStats[asset.inUser.trim().ifBlank { asset.userName.trim() }],
                        onTrackClick = onTrackClick,
                        onSearchUserClick = onSearchUserClick,
                        onSearchDeptClick = onSearchDeptClick,
                        onCancelCheckClick = onCancelCheckClick,
                        onSaveNote = onSaveNote
                    )
                }
            }
        }
    }

    if (showClearRecordsDialog) {
        AlertDialog(
            onDismissRequest = { showClearRecordsDialog = false },
            containerColor = AppColors.BgCardSolid,
            shape = RoundedCornerShape(16.dp),
            title = {
                Text(
                    text = "스캔 기록 삭제",
                    color = AppColors.TextMain,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = "화면에 표시된 실사 스캔 목록을 정리하시겠습니까?\n\n(이미 완료된 실사 조사 데이터는 안전하게 보존되며 화면 목록만 초기화됩니다)",
                    color = AppColors.TextMuted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearRecords()
                        showClearRecordsDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Danger),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("기록 삭제", color = AppColors.TextMain, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearRecordsDialog = false }) {
                    Text("취소", color = AppColors.TextMuted)
                }
            }
        )
    }
}

@Composable
fun CameraPreviewView(
    isTorchOn: Boolean,
    onBarcodeDetected: (String) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var lastScannedBarcode by remember { mutableStateOf("") }
    var lastScannedTime by remember { mutableStateOf(0L) }
    var activeCamera by remember { mutableStateOf<Camera?>(null) }

    LaunchedEffect(isTorchOn, activeCamera) {
        try {
            activeCamera?.cameraControl?.enableTorch(isTorchOn)
        } catch (_: Exception) {}
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                activeCamera?.cameraControl?.enableTorch(false)
            } catch (_: Exception) {}
        }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraExecutor = Executors.newSingleThreadExecutor()
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val barcodeScanner = BarcodeScanning.getClient()
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    processImageProxy(barcodeScanner, imageProxy) { barcode ->
                        val currentTime = System.currentTimeMillis()
                        // 1.5초 쿨다운으로 중복 연속인식 방지
                        if (barcode != lastScannedBarcode || (currentTime - lastScannedTime > 1500)) {
                            lastScannedBarcode = barcode
                            lastScannedTime = currentTime
                            onBarcodeDetected(barcode)
                        }
                    }
                }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                try {
                    cameraProvider.unbindAll()
                    val cam = cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis)
                    activeCamera = cam
                    cam.cameraControl.enableTorch(isTorchOn)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

@SuppressLint("UnsafeOptInUsageError")
private fun processImageProxy(
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    imageProxy: ImageProxy,
    onSuccess: (String) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    barcode.rawValue?.let { rawValue ->
                        onSuccess(rawValue)
                        return@addOnSuccessListener
                    }
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    } else {
        imageProxy.close()
    }
}

private fun startGoogleCodeScanner(
    context: Context,
    onBarcodeScanned: (String) -> Unit,
    onFallbackToCamera: () -> Unit
) {
    try {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .enableAutoZoom()
            .build()

        val scanner = GmsBarcodeScanning.getClient(context, options)
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val rawValue = barcode.rawValue ?: barcode.displayValue
                if (!rawValue.isNullOrBlank()) {
                    onBarcodeScanned(rawValue)
                }
            }
            .addOnCanceledListener {
                // 사용자가 X 버튼 등으로 취소 시 아무 작업도 하지 않음
            }
            .addOnFailureListener { e ->
                // 구글 코드 스캐너에서 시스템 뒤로가기 버튼을 누르면 내부적으로 MlKitException(errorCode=13, "Failed to scan code.")
                // 또는 CODE_SCANNER_CANCELLED / CANCELED 가 발생하므로, 취소(X버튼과 동일)로 처리하여 이전 카메라 화면 폴백 방지
                if (e is MlKitException && (e.errorCode == MlKitException.CODE_SCANNER_CANCELLED || e.errorCode == 13 || e.message?.contains("Failed to scan code", ignoreCase = true) == true)) {
                    return@addOnFailureListener
                }
                if (e is ApiException && e.statusCode == CommonStatusCodes.CANCELED) {
                    return@addOnFailureListener
                }
                if (e.message?.contains("cancel", ignoreCase = true) == true) {
                    return@addOnFailureListener
                }
                onFallbackToCamera()
            }
    } catch (e: Exception) {
        if (e is MlKitException && (e.errorCode == MlKitException.CODE_SCANNER_CANCELLED || e.errorCode == 13)) {
            return
        }
        if (e.message?.contains("cancel", ignoreCase = true) == true || e.message?.contains("Failed to scan code", ignoreCase = true) == true) {
            return
        }
        onFallbackToCamera()
    }
}

private fun vibratePhone(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            val vibrator = vibratorManager?.defaultVibrator
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator != null && vibrator.hasVibrator()) {
                @Suppress("DEPRECATION")
                vibrator.vibrate(150)
            }
        }
    } catch (_: Exception) {}
}

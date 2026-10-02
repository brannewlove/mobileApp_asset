package com.antigravity.assetmanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.assetmanager.ui.components.TopHeader
import com.antigravity.assetmanager.ui.theme.AppColors
import com.antigravity.assetmanager.ui.viewmodel.AssetViewModel

@Composable
fun MainScreen(
    viewModel: AssetViewModel,
    onGoogleSignInClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedTab by viewModel.selectedTab.collectAsState()
    val totalCount by viewModel.totalCount.collectAsState()
    val checkedCount by viewModel.checkedCount.collectAsState()

    val assets by viewModel.assets.collectAsState()
    val displayedScannedAssets by viewModel.displayedScannedAssets.collectAsState()
    val departments by viewModel.departments.collectAsState()
    val selectedDept by viewModel.selectedDepartment.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    val tradeLogs by viewModel.tradeLogs.collectAsState()
    val tradeQuery by viewModel.tradeSearchQuery.collectAsState()
    val trackedAsset by viewModel.trackedAsset.collectAsState()

    val isSyncing by viewModel.isSyncing.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val activeSession by viewModel.activeSession.collectAsState()
    val userEmail by viewModel.userEmail.collectAsState()
    val userStats by viewModel.userStats.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.notifications.collect { notification ->
            snackbarHostState.showSnackbar(notification.message)
        }
    }

    LaunchedEffect(selectedTab) {
        snackbarHostState.currentSnackbarData?.dismiss()
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = AppColors.BgDarkEnd,
                contentColor = AppColors.TextMain
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = "실사 스캔") },
                    label = { Text("실사 스캔", fontSize = 12.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AppColors.TextMain,
                        selectedTextColor = AppColors.Secondary,
                        indicatorColor = AppColors.Primary,
                        unselectedIconColor = AppColors.TextMuted,
                        unselectedTextColor = AppColors.TextMuted
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    icon = { Icon(Icons.Default.ListAlt, contentDescription = "자산 조회") },
                    label = { Text("자산 조회", fontSize = 12.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AppColors.TextMain,
                        selectedTextColor = AppColors.Secondary,
                        indicatorColor = AppColors.Primary,
                        unselectedIconColor = AppColors.TextMuted,
                        unselectedTextColor = AppColors.TextMuted
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { viewModel.selectTab(2) },
                    icon = { Icon(Icons.Default.History, contentDescription = "이력 추적") },
                    label = { Text("이력 추적", fontSize = 12.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AppColors.TextMain,
                        selectedTextColor = AppColors.Secondary,
                        indicatorColor = AppColors.Primary,
                        unselectedIconColor = AppColors.TextMuted,
                        unselectedTextColor = AppColors.TextMuted
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { viewModel.selectTab(3) },
                    icon = { Icon(Icons.Default.CloudSync, contentDescription = "시트 동기화") },
                    label = { Text("동기화", fontSize = 12.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AppColors.TextMain,
                        selectedTextColor = AppColors.Secondary,
                        indicatorColor = AppColors.Primary,
                        unselectedIconColor = AppColors.TextMuted,
                        unselectedTextColor = AppColors.TextMuted
                    )
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(AppColors.BgDark, AppColors.BgDarkEnd)
                    )
                )
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopHeader(
                    totalCount = totalCount,
                    checkedCount = checkedCount,
                    activeSession = activeSession,
                    sessions = sessions,
                    onCreateSession = { viewModel.createSession(it) },
                    onSwitchSession = { viewModel.switchSession(it) }
                )

                when (selectedTab) {
                    0 -> ScanScreen(
                        scannedAssets = displayedScannedAssets,
                        userStats = userStats,
                        onBarcodeScanned = { viewModel.scanBarcode(it) },
                        onTrackClick = { viewModel.trackAsset(it) },
                        onSearchUserClick = { viewModel.searchByUser(it) },
                        onSearchDeptClick = { viewModel.searchByDepartment(it) },
                        onCancelCheckClick = { viewModel.cancelScan(it) },
                        onSaveNote = { assetNo, note -> viewModel.updateAssetNote(assetNo, note) },
                        onClearRecords = { viewModel.clearScanDisplayList() }
                    )
                    1 -> AssetListScreen(
                        assets = assets,
                        departments = departments,
                        selectedDepartment = selectedDept,
                        searchQuery = searchQuery,
                        userStats = userStats,
                        onSearchChange = { viewModel.updateSearchQuery(it) },
                        onDepartmentSelect = { viewModel.updateDepartment(it) },
                        onTrackClick = { viewModel.trackAsset(it) },
                        onSearchUserClick = { viewModel.searchByUser(it) },
                        onSearchDeptClick = { viewModel.searchByDepartment(it) },
                        onCancelCheckClick = { viewModel.cancelScan(it) },
                        onSaveNote = { assetNo, note -> viewModel.updateAssetNote(assetNo, note) }
                    )
                    2 -> TradeLogScreen(
                        tradeLogs = tradeLogs,
                        trackedAsset = trackedAsset,
                        searchQuery = tradeQuery,
                        onSearchChange = { viewModel.updateTradeSearchQuery(it) }
                    )
                    3 -> SyncScreen(
                        isSyncing = isSyncing,
                        totalAssets = totalCount,
                        checkedAssets = checkedCount,
                        userEmail = userEmail,
                        onGoogleSignInClick = onGoogleSignInClick,
                        onGoogleSignOutClick = { viewModel.signOutGoogle() },
                        onSyncClick = { viewModel.syncFromGoogle() }
                    )
                }
            }
        }
    }
}

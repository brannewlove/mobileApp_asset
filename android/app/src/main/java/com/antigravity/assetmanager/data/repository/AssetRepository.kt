package com.antigravity.assetmanager.data.repository

import com.antigravity.assetmanager.model.Asset
import com.antigravity.assetmanager.model.TradeLog
import kotlinx.coroutines.flow.Flow

sealed class ScanResult {
    data class Success(val asset: Asset) : ScanResult()
    data class AlreadyChecked(val asset: Asset) : ScanResult()
    data class NotFound(val barcode: String) : ScanResult()
    data class Error(val message: String) : ScanResult()
}

/**
 * 자산 데이터 관리 레포지토리 인터페이스 (OOP Dependency Inversion)
 */
interface AssetRepository {
    fun getAssetsFlow(query: String, department: String): Flow<List<Asset>>
    fun getAssetsInSessionFlow(sessionId: Long, query: String, department: String): Flow<List<Asset>>
    fun getScannedAssetsFlow(): Flow<List<Asset>>
    fun getScannedAssetsInSessionFlow(sessionId: Long): Flow<List<Asset>>
    fun getDepartmentsFlow(): Flow<List<String>>
    fun getTradeLogsFlow(query: String): Flow<List<TradeLog>>
    fun getTotalAssetCountFlow(): Flow<Int>
    fun getCheckedAssetCountFlow(): Flow<Int>
    fun getSessionCheckedCountFlow(sessionId: Long): Flow<Int>

    // 회차(Session) 관리
    fun getAllSessionsFlow(): Flow<List<com.antigravity.assetmanager.model.InspectionSession>>
    fun getActiveSessionFlow(): Flow<com.antigravity.assetmanager.model.InspectionSession?>
    suspend fun createSession(sessionName: String): Long
    suspend fun activateSession(sessionId: Long)
    suspend fun deleteSession(sessionId: Long)

    suspend fun scanAsset(barcode: String): ScanResult
    suspend fun scanAssetInSession(sessionId: Long, barcode: String): ScanResult
    suspend fun cancelScan(sessionId: Long?, barcode: String)
    suspend fun updateAssetNote(sessionId: Long?, barcode: String, note: String)
    suspend fun syncFromGoogleDrive(): Result<String>
    suspend fun loadSampleData(): Result<String>
    suspend fun getAssetByNumber(assetNumber: String): Asset?
}

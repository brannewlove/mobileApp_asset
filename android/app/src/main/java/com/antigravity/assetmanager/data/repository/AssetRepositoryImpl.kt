package com.antigravity.assetmanager.data.repository

import com.antigravity.assetmanager.data.local.AssetDao
import com.antigravity.assetmanager.data.remote.GoogleApiClient
import com.antigravity.assetmanager.model.Asset
import com.antigravity.assetmanager.model.TradeLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AssetRepository 구현체
 * 로컬 Room DB 캐시와 원격 Google API 동기화를 총괄합니다.
 */
class AssetRepositoryImpl(
    private val assetDao: AssetDao,
    private val googleApiClient: GoogleApiClient
) : AssetRepository {

    override fun getAssetsFlow(query: String, department: String): Flow<List<Asset>> {
        return assetDao.searchAssetsFlow(query.trim(), department)
    }

    override fun getAssetsInSessionFlow(sessionId: Long, query: String, department: String): Flow<List<Asset>> {
        return assetDao.searchAssetsInSessionFlow(sessionId, query.trim(), department)
    }

    override fun getScannedAssetsFlow(): Flow<List<Asset>> {
        return assetDao.getScannedAssetsFlow()
    }

    override fun getScannedAssetsInSessionFlow(sessionId: Long): Flow<List<Asset>> {
        return assetDao.getScannedAssetsInSessionFlow(sessionId)
    }

    override fun getDepartmentsFlow(): Flow<List<String>> {
        return assetDao.getDepartmentsFlow()
    }

    override fun getTradeLogsFlow(query: String): Flow<List<TradeLog>> {
        return assetDao.searchTradeLogsFlow(query.trim())
    }

    override fun getTotalAssetCountFlow(): Flow<Int> {
        return assetDao.getTotalAssetCountFlow()
    }

    override fun getCheckedAssetCountFlow(): Flow<Int> {
        return assetDao.getCheckedAssetCountFlow()
    }

    override fun getSessionCheckedCountFlow(sessionId: Long): Flow<Int> {
        return assetDao.getSessionCheckedCountFlow(sessionId)
    }

    // === 회차 관리 ===

    override fun getAllSessionsFlow(): Flow<List<com.antigravity.assetmanager.model.InspectionSession>> {
        return assetDao.getAllSessionsFlow()
    }

    override fun getActiveSessionFlow(): Flow<com.antigravity.assetmanager.model.InspectionSession?> {
        return assetDao.getActiveSessionFlow()
    }

    override suspend fun createSession(sessionName: String): Long = withContext(Dispatchers.IO) {
        val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA).format(Date())
        val session = com.antigravity.assetmanager.model.InspectionSession(
            sessionName = sessionName.trim(),
            createdAt = currentTime,
            isActive = true
        )
        val id = assetDao.insertSession(session)
        assetDao.activateSession(id)
        id
    }

    override suspend fun activateSession(sessionId: Long) = withContext(Dispatchers.IO) {
        assetDao.activateSession(sessionId)
    }

    override suspend fun deleteSession(sessionId: Long) = withContext(Dispatchers.IO) {
        assetDao.clearSessionScans(sessionId)
        assetDao.deleteSession(sessionId)
    }

    override suspend fun getAssetByNumber(assetNumber: String): Asset? {
        return assetDao.getAssetByNumber(assetNumber)
    }

    override suspend fun scanAssetInSession(sessionId: Long, barcode: String): ScanResult = withContext(Dispatchers.IO) {
        val trimmed = barcode.trim()
        if (trimmed.isBlank()) return@withContext ScanResult.Error("바코드가 비어있습니다.")

        val asset = assetDao.getAssetByNumber(trimmed) ?: return@withContext ScanResult.NotFound(trimmed)

        val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA).format(Date())
        val alreadyScanned = assetDao.countAssetScanInSession(sessionId, trimmed) > 0
        if (alreadyScanned) {
            // 이미 실사된 자산인 경우 실사 시간을 현재 시각으로 갱신하여 최상단에 표시되도록 함
            assetDao.updateSessionScanTime(sessionId, trimmed, currentTime)
            assetDao.markAssetAsChecked(asset.assetNumber, currentTime)
            return@withContext ScanResult.AlreadyChecked(asset.copy(status = "checked", inspectionTime = currentTime))
        }

        val scan = com.antigravity.assetmanager.model.SessionScan(
            sessionId = sessionId,
            assetNumber = asset.assetNumber,
            inspectionTime = currentTime
        )
        assetDao.insertSessionScan(scan)

        // 전체 기준 자산 상태도 동기화 갱신
        assetDao.markAssetAsChecked(asset.assetNumber, currentTime)

        ScanResult.Success(asset.copy(status = "checked", inspectionTime = currentTime))
    }

    /**
     * 바코드 스캔 처리 로직:
     * 1. DB에서 자산 번호 일치 확인 (O(1) 인덱스)
     * 2. 이미 실사 완료되었는지 확인
     * 3. 미완료 상태면 현재 시간과 함께 checked 상태로 즉시 갱신
     */
    override suspend fun scanAsset(barcode: String): ScanResult = withContext(Dispatchers.IO) {
        val trimmed = barcode.trim()
        if (trimmed.isBlank()) return@withContext ScanResult.Error("바코드가 비어있습니다.")

        val asset = assetDao.getAssetByNumber(trimmed) ?: return@withContext ScanResult.NotFound(trimmed)

        val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA).format(Date())
        if (asset.isChecked) {
            assetDao.markAssetAsChecked(asset.assetNumber, currentTime)
            return@withContext ScanResult.AlreadyChecked(asset.copy(status = "checked", inspectionTime = currentTime))
        }

        assetDao.markAssetAsChecked(asset.assetNumber, currentTime)
        val updated = asset.copy(status = "checked", inspectionTime = currentTime)

        ScanResult.Success(updated)
    }

    override suspend fun cancelScan(sessionId: Long?, barcode: String) = withContext(Dispatchers.IO) {
        val trimmed = barcode.trim()
        if (sessionId != null) {
            assetDao.deleteSessionScan(sessionId, trimmed)
        }
        assetDao.resetAssetStatus(trimmed)
    }

    override suspend fun updateAssetNote(sessionId: Long?, barcode: String, note: String) = withContext(Dispatchers.IO) {
        val trimmed = barcode.trim()
        if (sessionId != null) {
            assetDao.updateSessionScanNote(sessionId, trimmed, note)
        }
        assetDao.updateAssetNote(trimmed, note)
    }

    /**
     * Google Drive 최신 마스터 시트에 현재 활성 회차의 실사(조사) 데이터를 업로드하고,
     * 최신 마스터 시트 데이터를 로컬 Room DB에 동기화합니다.
     */
    override suspend fun syncFromGoogleDrive(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val masterFile = googleApiClient.getLatestMasterFile()
                ?: return@withContext Result.failure(Exception("마스터 폴더에서 최신 스프레드시트 파일을 찾을 수 없습니다."))

            // 1. 최신 마스터 시트(users, assets, trade) 읽기 (순수 읽기 전용)
            val syncResult = googleApiClient.syncMasterSheet(masterFile.id, masterFile.name)

            if (syncResult.assets.isEmpty()) {
                return@withContext Result.failure(Exception("마스터 파일에 자산 데이터가 존재하지 않습니다."))
            }

            // Room 원자적 트랜잭션으로 로컬 DB 최신화
            assetDao.syncAllData(
                assets = syncResult.assets,
                users = syncResult.users,
                tradeLogs = syncResult.tradeLogs
            )

            // 2. 현재 활성 회차 확인 (없을 경우 오늘 날짜 기준 1차 실사 기본 회차 자동 생성)
            var session = assetDao.getActiveSession()
            if (session == null) {
                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Date())
                createSession("${todayStr} 1차 실사")
                session = assetDao.getActiveSession()
            }

            var uploadMessage = ""
            if (session != null) {
                val scannedList = assetDao.getScannedAssetsInSessionList(session.id)
                val scannedMap = scannedList.associateBy { it.assetNumber }
                var fileCreated = false

                // 구글 드라이브 백업 폴더(BACKUP_FOLDER_ID)에 회차 이름의 독립 구글 스프레드시트 파일 생성 & 업로드
                // (마스터 전체 데이터를 불러오고 실사 완료된 자산만 실사상태/점검시간을 표시하며 미조사는 공백으로 구분)
                try {
                    googleApiClient.createOrUpdateSessionSpreadsheetFile(
                        sessionTitle = session.sessionName,
                        allMasterAssets = syncResult.assets,
                        scannedMap = scannedMap
                    )
                    fileCreated = true
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                uploadMessage = if (fileCreated) {
                    "['${session.sessionName}' 조사시트 생성 (전체 ${syncResult.assets.size}건 중 실사 ${scannedList.size}건)] "
                } else {
                    "['${session.sessionName}'] "
                }
            }

            Result.success("동기화 완료: ${uploadMessage}${syncResult.assets.size}건 자산, ${syncResult.users.size}명 사원 (${masterFile.name})")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun loadSampleData(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val sampleUsers = listOf(
                com.antigravity.assetmanager.model.User("CJ001", "홍길동", "개발팀", "팀장"),
                com.antigravity.assetmanager.model.User("CJ002", "김철수", "개발팀", "선임"),
                com.antigravity.assetmanager.model.User("CJ003", "이영희", "디자인팀", "책임"),
                com.antigravity.assetmanager.model.User("CJ004", "박민수", "기획팀", "수석"),
                com.antigravity.assetmanager.model.User("CJ005", "정수진", "경영지원팀", "선임"),
                com.antigravity.assetmanager.model.User("CJ006", "최동현", "마케팅팀", "팀원")
            )

            val departments = listOf("개발팀", "디자인팀", "기획팀", "경영지원팀", "마케팅팀")
            val models = listOf(
                "MacBook Pro 16 M3 Max" to "노트북",
                "Dell XPS 15 9530" to "노트북",
                "LG Gram 17 2024" to "노트북",
                "Dell UltraSharp 27 4K" to "모니터",
                "LG 32인치 4K 모니터" to "모니터",
                "Apple iPad Pro 12.9" to "태블릿",
                "HP LaserJet Pro M404dn" to "프린터"
            )

            val sampleAssets = mutableListOf<com.antigravity.assetmanager.model.Asset>()
            for (i in 1..60) {
                val numStr = String.format("%03d", i)
                val user = sampleUsers[i % sampleUsers.size]
                val (model, category) = models[i % models.size]

                sampleAssets.add(
                    com.antigravity.assetmanager.model.Asset(
                        assetNumber = "AST-$numStr",
                        modelName = model,
                        serialNumber = "SN-2026-$numStr",
                        category = category,
                        inUser = user.cjId,
                        userName = user.userName,
                        department = user.department,
                        status = "pending",
                        state = "normal"
                    )
                )
            }

            val sampleLogs = listOf(
                com.antigravity.assetmanager.model.TradeLog(
                    assetNo = "AST-001",
                    cjId = "CJ001",
                    dateStr = "2026-09-15",
                    exUserId = "CJ002",
                    note = "팀 이동으로 인한 자산 인계",
                    exUserName = "김철수",
                    exUserPart = "개발팀",
                    joinedName = "홍길동",
                    joinedPart = "개발팀"
                ),
                com.antigravity.assetmanager.model.TradeLog(
                    assetNo = "AST-003",
                    cjId = "CJ003",
                    dateStr = "2026-09-20",
                    exUserId = "",
                    note = "신규 입사자 노트북 지급",
                    joinedName = "이영희",
                    joinedPart = "디자인팀"
                )
            )

            assetDao.syncAllData(sampleAssets, sampleUsers, sampleLogs)
            Result.success("샘플 데이터 로드 완료: ${sampleAssets.size}건 자산, ${sampleUsers.size}명 사원")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

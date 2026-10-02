package com.antigravity.assetmanager.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.antigravity.assetmanager.model.Asset
import com.antigravity.assetmanager.model.InspectionSession
import com.antigravity.assetmanager.model.SessionScan
import com.antigravity.assetmanager.model.TradeLog
import com.antigravity.assetmanager.model.User
import kotlinx.coroutines.flow.Flow

/**
 * 고속 데이터 조회를 위한 Room DAO 인터페이스
 * SQLite 인덱스를 직접 활용하여 수천 건 이상의 데이터도 1ms 내외로 즉시 필터링/검색합니다.
 */
@Dao
interface AssetDao {

    // === 자산(Asset) 쿼리 ===

    @Query("SELECT * FROM assets ORDER BY assetNumber ASC")
    fun getAllAssetsFlow(): Flow<List<Asset>>

    @Query("SELECT * FROM assets ORDER BY assetNumber ASC")
    suspend fun getAllAssetsList(): List<Asset>

    @Query("""
        SELECT * FROM assets 
        WHERE (:department = '전체' OR department = :department)
          AND (:query = '' OR 
               assetNumber LIKE '%' || :query || '%' OR 
               userName LIKE '%' || :query || '%' OR 
               inUser LIKE '%' || :query || '%' OR 
               modelName LIKE '%' || :query || '%' OR 
               serialNumber LIKE '%' || :query || '%')
        ORDER BY CASE WHEN status = 'checked' THEN 1 ELSE 0 END ASC, assetNumber ASC
    """)
    fun searchAssetsFlow(query: String, department: String): Flow<List<Asset>>

    @Query("SELECT * FROM assets WHERE assetNumber = :assetNumber LIMIT 1")
    suspend fun getAssetByNumber(assetNumber: String): Asset?

    @Query("SELECT * FROM assets WHERE status = 'checked' ORDER BY inspectionTime DESC")
    fun getScannedAssetsFlow(): Flow<List<Asset>>

    @Query("SELECT DISTINCT department FROM assets WHERE department != '' ORDER BY department ASC")
    fun getDepartmentsFlow(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM assets")
    fun getTotalAssetCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM assets WHERE status = 'checked'")
    fun getCheckedAssetCountFlow(): Flow<Int>

    @Query("UPDATE assets SET status = 'checked', inspectionTime = :time WHERE assetNumber = :assetNumber")
    suspend fun markAssetAsChecked(assetNumber: String, time: String): Int

    // === 회차(Session) 기반 쿼리 및 분리 저장 ===

    @Query("SELECT * FROM inspection_sessions ORDER BY id DESC")
    fun getAllSessionsFlow(): Flow<List<InspectionSession>>

    @Query("SELECT * FROM inspection_sessions WHERE isActive = 1 LIMIT 1")
    fun getActiveSessionFlow(): Flow<InspectionSession?>

    @Query("SELECT * FROM inspection_sessions WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveSession(): InspectionSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: InspectionSession): Long

    @Query("UPDATE inspection_sessions SET isActive = CASE WHEN id = :sessionId THEN 1 ELSE 0 END")
    suspend fun activateSession(sessionId: Long)

    @Query("DELETE FROM inspection_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)

    @Query("DELETE FROM session_scans WHERE sessionId = :sessionId")
    suspend fun clearSessionScans(sessionId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessionScan(scan: SessionScan)

    @Query("UPDATE session_scans SET inspectionTime = :time WHERE sessionId = :sessionId AND assetNumber = :assetNumber")
    suspend fun updateSessionScanTime(sessionId: Long, assetNumber: String, time: String)

    @Query("UPDATE session_scans SET note = :note WHERE sessionId = :sessionId AND assetNumber = :assetNumber")
    suspend fun updateSessionScanNote(sessionId: Long, assetNumber: String, note: String)

    @Query("UPDATE assets SET note = :note WHERE assetNumber = :assetNumber")
    suspend fun updateAssetNote(assetNumber: String, note: String)

    @Query("SELECT COUNT(*) FROM session_scans WHERE sessionId = :sessionId AND assetNumber = :assetNumber")
    suspend fun countAssetScanInSession(sessionId: Long, assetNumber: String): Int

    @Query("DELETE FROM session_scans WHERE sessionId = :sessionId AND assetNumber = :assetNumber")
    suspend fun deleteSessionScan(sessionId: Long, assetNumber: String)

    @Query("UPDATE assets SET status = 'pending', inspectionTime = '' WHERE assetNumber = :assetNumber")
    suspend fun resetAssetStatus(assetNumber: String)

    @Query("""
        SELECT COUNT(*) 
        FROM session_scans s
        INNER JOIN assets a ON s.assetNumber = a.assetNumber
        WHERE s.sessionId = :sessionId
    """)
    fun getSessionCheckedCountFlow(sessionId: Long): Flow<Int>

    // 마스터 데이터에서 삭제된 자산의 과거 실사 스캔 데이터 자동 정리
    @Query("DELETE FROM session_scans WHERE assetNumber NOT IN (SELECT assetNumber FROM assets)")
    suspend fun cleanOrphanSessionScans(): Int

    // 특정 회차의 실사 완료된 자산 목록 Flow
    @Query("""
        SELECT a.assetNumber, a.modelName, a.serialNumber, a.category, a.inUser, a.userName, a.department, 
               'checked' AS status, s.inspectionTime AS inspectionTime, s.note AS note, a.state
        FROM assets a
        INNER JOIN session_scans s ON a.assetNumber = s.assetNumber
        WHERE s.sessionId = :sessionId
        ORDER BY s.inspectionTime DESC
    """)
    fun getScannedAssetsInSessionFlow(sessionId: Long): Flow<List<Asset>>

    // 특정 회차의 실사 완료된 자산 목록 List (동기화 업로드용)
    @Query("""
        SELECT a.assetNumber, a.modelName, a.serialNumber, a.category, a.inUser, a.userName, a.department, 
               'checked' AS status, s.inspectionTime AS inspectionTime, s.note AS note, a.state
        FROM assets a
        INNER JOIN session_scans s ON a.assetNumber = s.assetNumber
        WHERE s.sessionId = :sessionId
        ORDER BY s.inspectionTime DESC
    """)
    suspend fun getScannedAssetsInSessionList(sessionId: Long): List<Asset>

    // 특정 회차의 상태가 조인된 전체 자산 목록
    @Query("""
        SELECT a.assetNumber, a.modelName, a.serialNumber, a.category, a.inUser, a.userName, a.department,
               CASE WHEN s.assetNumber IS NOT NULL THEN 'checked' ELSE 'pending' END AS status,
               COALESCE(s.inspectionTime, '') AS inspectionTime,
               COALESCE(s.note, '') AS note,
               a.state
        FROM assets a
        LEFT JOIN session_scans s ON a.assetNumber = s.assetNumber AND s.sessionId = :sessionId
        WHERE (:department = '전체' OR a.department = :department)
          AND (:query = '' OR 
               a.assetNumber LIKE '%' || :query || '%' OR 
               a.userName LIKE '%' || :query || '%' OR 
               a.inUser LIKE '%' || :query || '%' OR 
               a.modelName LIKE '%' || :query || '%' OR 
               a.serialNumber LIKE '%' || :query || '%')
        ORDER BY CASE WHEN s.assetNumber IS NOT NULL THEN 1 ELSE 0 END ASC, a.assetNumber ASC
    """)
    fun searchAssetsInSessionFlow(sessionId: Long, query: String, department: String): Flow<List<Asset>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAssets(assets: List<Asset>)

    @Query("DELETE FROM assets")
    suspend fun clearAssets()

    // === 사용자(User) 쿼리 ===

    @Query("SELECT * FROM users")
    suspend fun getAllUsers(): List<User>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<User>)

    @Query("DELETE FROM users")
    suspend fun clearUsers()

    // === 이력 로그(TradeLog) 쿼리 ===

    @Query("SELECT * FROM trade_logs ORDER BY dateStr DESC")
    fun getAllTradeLogsFlow(): Flow<List<TradeLog>>

    @Query("""
        SELECT * FROM trade_logs 
        WHERE (:query = '' OR 
               assetNo LIKE '%' || :query || '%' OR 
               cjId LIKE '%' || :query || '%' OR 
               dateStr LIKE '%' || :query || '%' OR 
               note LIKE '%' || :query || '%' OR 
               exUserId LIKE '%' || :query || '%')
        ORDER BY dateStr DESC
    """)
    fun searchTradeLogsFlow(query: String): Flow<List<TradeLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTradeLogs(logs: List<TradeLog>)

    @Query("DELETE FROM trade_logs")
    suspend fun clearTradeLogs()

    // === 전체 동기화 원자적(Atomic) 트랜잭션 ===
    @Transaction
    suspend fun syncAllData(assets: List<Asset>, users: List<User>, tradeLogs: List<TradeLog>) {
        if (users.isNotEmpty()) {
            clearUsers()
            insertUsers(users)
        }
        if (assets.isNotEmpty()) {
            clearAssets()
            insertAssets(assets)
            cleanOrphanSessionScans()
        }
        if (tradeLogs.isNotEmpty()) {
            clearTradeLogs()
            insertTradeLogs(tradeLogs)
        }
    }
}

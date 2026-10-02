package com.antigravity.assetmanager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.antigravity.assetmanager.data.remote.GoogleAuthHelper
import com.antigravity.assetmanager.data.repository.AssetRepository
import com.antigravity.assetmanager.data.repository.ScanResult
import com.antigravity.assetmanager.model.Asset
import com.antigravity.assetmanager.model.InspectionSession
import com.antigravity.assetmanager.model.TradeLog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UiNotification(
    val message: String,
    val isError: Boolean = false
)

class AssetViewModel(
    private val repository: AssetRepository,
    private val authHelper: GoogleAuthHelper
) : ViewModel() {

    // 현재 선택된 네비게이션 탭 (0: 스캔, 1: 자산목록, 2: 이력, 3: 설정/동기화)
    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    // 검색어 & 부서 필터
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedDepartment = MutableStateFlow("전체")
    val selectedDepartment: StateFlow<String> = _selectedDepartment.asStateFlow()

    // 이력 검색어
    private val _tradeSearchQuery = MutableStateFlow("")
    val tradeSearchQuery: StateFlow<String> = _tradeSearchQuery.asStateFlow()

    // 동기화 진행 상태
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    // UI 알림 메시지 이벤트
    private val _notifications = MutableSharedFlow<UiNotification>()
    val notifications = _notifications.asSharedFlow()

    // 최근 스캔 결과
    private val _lastScanResult = MutableStateFlow<ScanResult?>(null)
    val lastScanResult: StateFlow<ScanResult?> = _lastScanResult.asStateFlow()

    // 1. 회차(Session) 관리 목록 및 활성 회차
    val sessions: StateFlow<List<InspectionSession>> = repository.getAllSessionsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeSession: StateFlow<InspectionSession?> = repository.getActiveSessionFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        // 최초 실행 시 기본 회차가 없으면 자동 생성
        viewModelScope.launch {
            val currentSessions = repository.getAllSessionsFlow().firstOrNull()
            if (currentSessions.isNullOrEmpty()) {
                repository.createSession("1차 정기실사")
            }
        }
    }

    // 2. 부서 목록 Flow
    val departments: StateFlow<List<String>> = repository.getDepartmentsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 3. 자산 목록 Flow (활성 회차 기준 분리된 상태 반영)
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val assets: StateFlow<List<Asset>> = combine(
        _searchQuery.debounce(150),
        _selectedDepartment,
        activeSession
    ) { query, dept, session -> Triple(query, dept, session) }
        .flatMapLatest { (query, dept, session) ->
            if (session != null) {
                repository.getAssetsInSessionFlow(session.id, query, dept)
            } else {
                repository.getAssetsFlow(query, dept)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 4. 실사(스캔) 완료된 자산 목록 Flow (활성 회차 기준)
    @OptIn(ExperimentalCoroutinesApi::class)
    val scannedAssets: StateFlow<List<Asset>> = activeSession
        .flatMapLatest { session ->
            if (session != null) {
                repository.getScannedAssetsInSessionFlow(session.id)
            } else {
                repository.getScannedAssetsFlow()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 실사스캔 탭 화면 표시에서 정리(숨김)된 자산 번호 목록 (실제 조사 결과/DB는 안전하게 유지됨)
    private val _clearedScannedAssetNumbers = MutableStateFlow<Set<String>>(emptySet())

    val displayedScannedAssets: StateFlow<List<Asset>> = combine(
        scannedAssets,
        _clearedScannedAssetNumbers
    ) { list, cleared ->
        list.filter { it.assetNumber !in cleared }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun clearScanDisplayList() {
        val currentNos = scannedAssets.value.map { it.assetNumber }.toSet()
        _clearedScannedAssetNumbers.value = _clearedScannedAssetNumbers.value + currentNos
    }

    // 5. 이력 로그 Flow (즉시 반응 및 트리밍)
    @OptIn(ExperimentalCoroutinesApi::class)
    val tradeLogs: StateFlow<List<TradeLog>> = _tradeSearchQuery
        .flatMapLatest { query ->
            repository.getTradeLogsFlow(query.trim())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 추적 중인 특정 자산 엔티티
    private val _trackedAsset = MutableStateFlow<Asset?>(null)
    val trackedAsset: StateFlow<Asset?> = _trackedAsset.asStateFlow()

    // 6. 진행률 계산 (전체 자산 수 & 현재 회차 실사 완료 수)
    val totalCount: StateFlow<Int> = repository.getTotalAssetCountFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val checkedCount: StateFlow<Int> = activeSession
        .flatMapLatest { session ->
            if (session != null) {
                repository.getSessionCheckedCountFlow(session.id)
            } else {
                repository.getCheckedAssetCountFlow()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // 7. 사용자별 자산 수 및 실사완료 수 통계 (예: 1/3)
    // 검색창이나 부서 필터링에 영향받지 않도록 전체 자산(query='', department='전체')을 기준으로 항상 계산 및 Eagerly 유지
    @OptIn(ExperimentalCoroutinesApi::class)
    val userStats: StateFlow<Map<String, Pair<Int, Int>>> = activeSession
        .flatMapLatest { session ->
            if (session != null) {
                repository.getAssetsInSessionFlow(session.id, "", "전체")
            } else {
                repository.getAssetsFlow("", "전체")
            }
        }
        .map { allAssetsList ->
            val stats = mutableMapOf<String, Pair<Int, Int>>()
            allAssetsList.forEach { a ->
                val uid = a.inUser.trim().ifBlank { a.userName.trim() }
                if (uid.isNotBlank()) {
                    val current = stats[uid] ?: (0 to 0)
                    val isDone = a.isChecked
                    val done = current.first + if (isDone) 1 else 0
                    val total = current.second + 1
                    stats[uid] = done to total
                }
            }
            stats
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun selectTab(index: Int) {
        _selectedTab.value = index
    }

    // 자산 카드 바로가기 버튼 액션들
    fun trackAsset(assetNumber: String) {
        val trimmed = assetNumber.trim()
        _tradeSearchQuery.value = trimmed
        viewModelScope.launch {
            _trackedAsset.value = repository.getAssetByNumber(trimmed)
        }
        _selectedTab.value = 2 // 이력 추적 탭으로 이동
    }

    fun searchByUser(inUser: String) {
        _searchQuery.value = inUser.trim()
        _selectedDepartment.value = "전체"
        _selectedTab.value = 1 // 자산 조회 탭으로 이동
    }

    fun searchByDepartment(department: String) {
        _selectedDepartment.value = department
        _searchQuery.value = ""
        _selectedTab.value = 1 // 자산 조회 탭으로 이동
    }

    fun cancelScan(assetNumber: String) {
        viewModelScope.launch {
            repository.cancelScan(activeSession.value?.id, assetNumber)
            _notifications.emit(UiNotification("[$assetNumber] 실사가 취소되었습니다."))
        }
    }

    fun updateAssetNote(assetNumber: String, note: String) {
        viewModelScope.launch {
            repository.updateAssetNote(activeSession.value?.id, assetNumber, note)
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateDepartment(department: String) {
        _selectedDepartment.value = department
        _searchQuery.value = ""
    }

    fun updateTradeSearchQuery(query: String) {
        val trimmed = query.trim()
        _tradeSearchQuery.value = query
        if (trimmed.isBlank()) {
            _trackedAsset.value = null
        } else {
            viewModelScope.launch {
                _trackedAsset.value = repository.getAssetByNumber(trimmed)
            }
        }
    }

    // 회차 조작 함수
    fun createSession(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.createSession(name.trim())
            _notifications.emit(UiNotification("새로운 실사 회차 [$name]이 생성되었습니다."))
        }
    }

    fun switchSession(sessionId: Long) {
        viewModelScope.launch {
            _clearedScannedAssetNumbers.value = emptySet()
            repository.activateSession(sessionId)
            _notifications.emit(UiNotification("실사 회차가 전환되었습니다."))
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            _notifications.emit(UiNotification("회차가 삭제되었습니다."))
        }
    }

    fun scanBarcode(barcode: String) {
        val trimmed = barcode.trim()
        _clearedScannedAssetNumbers.value = _clearedScannedAssetNumbers.value - trimmed
        viewModelScope.launch {
            val session = activeSession.value
            val result = if (session != null) {
                repository.scanAssetInSession(session.id, trimmed)
            } else {
                repository.scanAsset(trimmed)
            }

            _lastScanResult.value = result
            when (result) {
                is ScanResult.Success -> {
                    _notifications.emit(UiNotification("✅ [${result.asset.assetNumber}] 실사 완료 (${result.asset.userName})"))
                }
                is ScanResult.AlreadyChecked -> {
                    _notifications.emit(UiNotification("⚠️ [${result.asset.assetNumber}] 해당 회차에 이미 실사 완료되었습니다."))
                }
                is ScanResult.NotFound -> {
                    _notifications.emit(UiNotification("❌ 미등록 자산: [${result.barcode}]", isError = true))
                }
                is ScanResult.Error -> {
                    _notifications.emit(UiNotification(result.message, isError = true))
                }
            }
        }
    }

    fun syncFromGoogle() {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            val result = repository.syncFromGoogleDrive()
            _isSyncing.value = false

            result.onSuccess { msg ->
                _notifications.emit(UiNotification(msg))
            }.onFailure { err ->
                _notifications.emit(UiNotification("동기화 실패: ${err.message}", isError = true))
            }
        }
    }

    private val _userEmail = MutableStateFlow(authHelper.getLastSignedInAccount()?.email)
    val userEmail: StateFlow<String?> = _userEmail.asStateFlow()

    fun updateAuthStatus() {
        _userEmail.value = authHelper.getLastSignedInAccount()?.email
    }

    fun signOutGoogle() {
        authHelper.clearAuth()
        _userEmail.value = null
        viewModelScope.launch {
            _notifications.emit(UiNotification("구글 계정에서 로그아웃되었습니다."))
        }
    }

    fun loadSampleData() {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            val result = repository.loadSampleData()
            _isSyncing.value = false

            result.onSuccess { msg ->
                _notifications.emit(UiNotification(msg))
            }.onFailure { err ->
                _notifications.emit(UiNotification("샘플 데이터 로드 실패: ${err.message}", isError = true))
            }
        }
    }

    class Factory(
        private val repository: AssetRepository,
        private val authHelper: GoogleAuthHelper
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AssetViewModel(repository, authHelper) as T
        }
    }
}

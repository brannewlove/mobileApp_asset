<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useAssetStore } from './store/assetStore'
import { 
  Search, 
  Barcode, 
  History, 
  CheckCircle, 
  User, 
  LayoutDashboard,
  LogOut,
  Fingerprint,
  Tag,
  Hash,
  Filter,
  ChevronRight,
  X,
  FileSpreadsheet,
  ArrowLeft,
  RefreshCw,
  Package,
  Save,
  Database,
  Trash2,
  Scan,
  Info,
  AlertCircle,
  ClipboardList,
  Crosshair,
  Settings,
  Wifi,
  WifiOff,
  CloudOff,
  CloudUpload
} from 'lucide-vue-next'
import { Network } from '@capacitor/network'
import AssetCard from './components/AssetCard.vue'

const store = useAssetStore()
const activeTab = ref('inspection')
const showDeptModal = ref(false)
const showProjectSelector = ref(false)
const sessionName = ref('')
const deptSearchQuery = ref('')

// --- 커스텀 확인 모달 상태 ---
const confirmModal = ref({
  show: false,
  title: '확인',
  message: '',
  onConfirm: null
})

const openConfirm = (message, onConfirm, title = '확인') => {
  confirmModal.value = {
    show: true,
    title,
    message,
    onConfirm
  }
}

const triggerModalConfirm = () => {
  if (confirmModal.value.onConfirm) confirmModal.value.onConfirm()
  confirmModal.value.show = false
}

// Filtered department list for modal
const filteredDeptList = computed(() => {
  const q = deptSearchQuery.value.toLowerCase()
  return store.departments.filter(d => d.toLowerCase().includes(q))
})

// Performance Optimization: Pagination
const displayLimit = ref(20)
const pageSize = 20



const loadMore = () => {
  if (activeTab.value === 'inspection' || activeTab.value === 'search') {
    if (store.filteredAssets && displayLimit.value < store.filteredAssets.length) {
      displayLimit.value += pageSize
    }
  } else if (activeTab.value === 'reference') {
    store.referenceLimit += pageSize
  }
}

// Search Debouncing
const internalSearchQuery = ref('')
let debounceTimer = null

const handleSearchInput = (e) => {
  internalSearchQuery.value = e.target.value
  clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    store.searchQuery = internalSearchQuery.value
    displayLimit.value = pageSize // Reset pagination on search
  }, 300)
}

const clearSearch = () => {
  internalSearchQuery.value = ''
  store.searchQuery = ''
  displayLimit.value = pageSize
  clearTimeout(debounceTimer)
}

const clearInspectionSearch = () => {
  store.inspectionSearchQuery = ''
  barcodeValue.value = ''
}

const selectedMaster = ref(null)

// Ensure master is selected when files become available
watch(() => store.masterFiles, (newFiles) => {
  if (newFiles && newFiles.length > 0 && !selectedMaster.value) {
    selectedMaster.value = newFiles[0]
  }
}, { immediate: true })

// Auto-sync master metadata every 5 minutes
let syncInterval = null
// Auto-login on mount
onMounted(async () => {
  const savedToken = localStorage.getItem('google_access_token')
    if (savedToken) {
    accessTokenInput.value = savedToken
    await store.initializeData(savedToken)
    if (store.isAuthenticated) {
      showLogin.value = false
      
      // 만약 로컬에 작업 중인 파일 정보가 있다면 선택 화면을 건너뜁니다.
      if (store.currentFile) {
        showProjectSelector.value = false
        activeTab.value = 'inspection'
      } else {
        showProjectSelector.value = true
      }

      if (!selectedMaster.value && store.masterFiles.length > 0) {
        selectedMaster.value = store.masterFiles[0]
      }
      
      // Check if master sync is needed (Once a day after 06:00)
      store.checkAndSyncMaster()
    }
  }

  // Network Status Monitoring
  const status = await Network.getStatus()
  store.isOnline = status.connected
  
  Network.addListener('networkStatusChange', status => {
    store.isOnline = status.connected
    if (status.connected && store.hasPendingSync) {
      console.log('[Network] Back online, triggering auto-sync...')
      store.triggerDebouncedSave()
    }
  })
})

// 로그인이 완료되면 자동으로 로그인 화면 닫기
watch(() => store.isAuthenticated, (val) => {
  if (val) {
    showLogin.value = false
    showProjectSelector.value = true
  }
})

import { onUnmounted } from 'vue'
onUnmounted(() => {
  if (syncInterval) clearInterval(syncInterval)
})

const barcodeValue = ref('')
const accessTokenInput = ref('')
const showLogin = ref(true)

const handleLogin = async () => {
  if (!accessTokenInput.value) {
    store.showToast('토큰을 입력해주세요.', 'error')
    return
  }
  await store.initializeData(accessTokenInput.value)
  if (store.isAuthenticated) {
    showLogin.value = false
    showProjectSelector.value = true
    if (store.masterFiles.length > 0) {
      selectedMaster.value = store.masterFiles[0]
    }
    await store.checkAndSyncMaster()
  }
}

const handleGoogleLogin = async () => {
  await store.loginWithGoogle()
  if (store.isAuthenticated) {
    showLogin.value = false
    showProjectSelector.value = true
    if (!selectedMaster.value && store.masterFiles.length > 0) {
      selectedMaster.value = store.masterFiles[0]
    }
  }
}

// Search & Stats Computed Properties
const sortedFilteredAssets = computed(() => {
  if (!store.filteredAssets) return []
  // Sort: Unchecked first, then Checked
  return [...store.filteredAssets].sort((a, b) => {
    if (a.status !== 'checked' && b.status === 'checked') return -1
    if (a.status === 'checked' && b.status !== 'checked') return 1
    return 0
  })
})

const visibleAssets = computed(() => {
  return sortedFilteredAssets.value.slice(0, displayLimit.value)
})

const searchStats = computed(() => {
  const assets = store.filteredAssets
  if (assets.length === 0) return null
  
  const unchecked = assets.filter(a => a.status !== 'checked')
  const modelCounts = {}
  
  unchecked.forEach(a => {
    const cat = a.category || '기타'
    modelCounts[cat] = (modelCounts[cat] || 0) + 1
  })

  // Sort by count desc
  const sortedModels = Object.entries(modelCounts)
    .sort(([, a], [, b]) => b - a)
    
  return {
    totalUnchecked: unchecked.length,
    modelCounts: sortedModels
  }
})

const handleSyncMaster = async () => {
  await store.refreshMasterMetadata()
  store.showToast('마스터 데이터가 최신 상태로 동기화되었습니다.', 'success')
}

const handleResumeSession = async (file) => {
  if (store.loading) return
  await store.loadProject(file)
  showProjectSelector.value = false
  activeTab.value = 'inspection'
}

const handleStartSurvey = async () => {
  if (!sessionName.value) {
    store.showToast('회차명을 입력해주세요.', 'error')
    return;
  }
  if (!selectedMaster.value) {
    store.showToast('마스터 데이터를 선택해주세요.', 'error')
    return;
  }
  await store.startSession(selectedMaster.value, sessionName.value)
  showProjectSelector.value = false
  activeTab.value = 'inspection'
}

const handleLogout = () => {
  store.signOutAccount()
  showLogin.value = true
  showProjectSelector.value = false
}

const handleSwitchSession = async () => {
  showProjectSelector.value = true
  await store.refreshSessions()
}

const handleQuickSearchUser = (userId) => {
  if (!userId) return
  store.searchQuery = userId
  internalSearchQuery.value = userId
  store.selectedDepartment = '전체'
  activeTab.value = 'search'
  displayLimit.value = pageSize
}

const handleQuickSearchDept = (dept) => {
  if (!dept) return
  store.selectedDepartment = dept
  store.searchQuery = ''
  internalSearchQuery.value = ''
  activeTab.value = 'search'
  displayLimit.value = pageSize
}

const handleBarcodeEnter = async () => {
  if (!barcodeValue.value) return
  const asset = store.assets.find(a => a.assetNumber === barcodeValue.value)
  if (asset) {
    if (asset.status === 'checked') {
      // 실사 탭 전용 검색어만 설정 (검색 탭에 영향 없음)
      store.inspectionSearchQuery = barcodeValue.value
      store.showToast(`이미 실사 완료된 자산입니다. (${barcodeValue.value})`, 'info')
    } else {
      store.inspectionSearchQuery = '' 
      store.updateAsset(barcodeValue.value, { status: 'checked' })
      store.triggerDebouncedSave()
      store.showToast('실사 확인되었습니다.', 'success')
    }
  } else {
    store.showToast('등록되지 않은 자산입니다.', 'error')
  }
  barcodeValue.value = ''
}

const handleCancelCheck = (assetNumber) => {
  openConfirm(
    `자산(${assetNumber})의 실사를 취소하시겠습니까?`,
    () => {
      store.cancelAssetCheck(assetNumber)
    },
    '실사 취소'
  )
}

const handleConfirm = () => {
  if (!store.scannedAssets || store.scannedAssets.length === 0) return
  openConfirm(
    '현재 실사한 리스트를 확인 완료 처리하고 목록에서 비우시겠습니까?',
    () => {
      store.clearScannedList()
      store.showToast('목록이 비워졌습니다.', 'success')
    }
  )
}

const handleBackupSave = async () => {
  openConfirm(
    '오늘 작업 내용을 저장하고 백업 파일을 생성하시겠습니까?',
    async () => {
      await store.backupAndSave()
      store.showToast('백업이 완료되었습니다.', 'success')
    }
  )
}
const handleTrackAsset = (assetNumber) => {
  store.referenceSearchQuery = assetNumber
  activeTab.value = 'reference'
  // 스크롤을 상단으로 올려주기 위해 (선택사항)
  window.scrollTo(0, 0)
}
</script>

<template>
  <div class="gradient-bg"></div>
  
  <div v-if="store.loading" class="global-loading-overlay">
    <div class="loader"></div>
    <p>데이터를 불러오는 중...</p>
  </div>

  <header class="glass header">
    <div class="header-top">
      <h1>Asset Manager</h1>
      <div class="sync-status" :class="{ syncing: store.isSyncing, offline: !store.isOnline }">
        <template v-if="!store.isOnline">
          <WifiOff size="14" class="status-icon" />
          <span>오프라인 환경</span>
        </template>
        <template v-else-if="store.isSyncing">
          <RefreshCw size="14" class="spin-icon" />
          <span>저장 중...</span>
        </template>
        <template v-else-if="store.hasPendingSync">
          <CloudUpload size="14" class="blink-icon" />
          <span>동기화 대기 중</span>
        </template>
        <template v-else-if="store.lastSavedAt">
          <CheckCircle size="14" />
          <span>{{ store.lastSavedAt }} 저장됨</span>
        </template>
      </div>
    </div>
    <div class="progress-container">
      <div class="progress-bar" :style="{ width: store.progress.percent + '%' }"></div>
      <span class="progress-text">{{ store.progress.done }} / {{ store.progress.total }} ({{ store.progress.percent }}%)</span>
    </div>
  </header>

  <main class="content">
    <!-- Login / Token Input -->
    <div v-if="showLogin" class="tab-content login-screen">
      <div class="glass auth-card">
        <h3>Google API 인증</h3>
        <p class="desc">구글 API를 사용하기 위해 아래 버튼으로 로그인하거나 Access Token을 직접 입력해주세요.</p>
        
        <!-- Native Google Login Button -->
        <button @click="handleGoogleLogin" :disabled="store.loading" class="primary-btn google-login-btn">
          <Database size="18" class="btn-icon" />
          {{ store.loading ? '로그인 중...' : '구글 계정으로 로그인' }}
        </button>

        <div class="divider-text">또는</div>

        <div class="input-group">
          <input 
            v-model="accessTokenInput" 
            type="password" 
            placeholder="Google Access Token 직접 입력" 
          />
          <button @click="handleLogin" :disabled="store.loading" class="secondary-btn">
            토큰으로 인증
          </button>
        </div>
        <p class="help text-muted">
          테스트용 토큰은 <a href="https://developers.google.com/oauthplayground/" target="_blank">OAuth 2.0 Playground</a>에서 발급받을 수 있습니다.
        </p>
        <div v-if="store.error" class="error-msg">{{ store.error }}</div>
      </div>
    </div>

    <!-- Session Selection -->
    <div v-if="!showLogin && showProjectSelector" class="tab-content">
      <div class="session-selector">
        
        <!-- Section 1: Resume Existing -->
        <div v-if="store.sessionFiles.length > 0" class="selector-section">
          <h3>기존 회차 이어하기</h3>
          <p class="desc">진행 중인 실사 파일을 선택하여 작업을 이어가세요.</p>
          <div class="file-list">
            <div 
              v-for="file in store.sessionFiles" 
              :key="file.id" 
              class="glass file-card resume-card"
              :class="{ 'loading-opacity': store.loading }"
              @click="handleResumeSession(file)"
            >
              <div class="file-icon resume">
                <History size="24" />
              </div>
              <div class="file-info">
                <span class="file-name">{{ file.name }}</span>
                <span class="file-date">수정: {{ new Date(file.modifiedTime).toLocaleDateString() }}</span>
              </div>
              <ChevronRight size="18" class="arrow" />
            </div>
          </div>
        </div>

        <!-- Section 2: Start New -->
        <div class="selector-section">
          <h3>새로운 실사 회차 시작</h3>
          <p class="desc">마스터 데이터를 기준으로 새로운 회차를 생성합니다.</p>
          
          <div class="glass master-info-box">
            <div class="box-label">기준 마스터 데이터</div>
            <div class="master-name" v-if="selectedMaster">
              <FileSpreadsheet size="20" class="icon" />
              <span>{{ selectedMaster.name }}</span>
            </div>
            <div v-else class="master-not-found">
              <span class="text-muted">마스터 파일을 불러오는 중...</span>
              <button @click="handleRetryList" class="retry-btn">
                <RefreshCw size="14" /> 다시 시도
              </button>
            </div>
          </div>

          <div class="glass session-input-box focus-box">
            <label>실사 회차명 입력</label>
            <input 
              v-model="sessionName" 
              placeholder="예: 2026년 상반기 정기조사" 
              class="session-name-input"
            />
            <p class="input-desc">입력하신 이름으로 새로운 파일이 생성됩니다.</p>
          </div>

          <button @click="handleStartSurvey" :disabled="!selectedMaster || store.loading" class="glass start-survey-btn">
            {{ store.loading ? '세션 생성 중...' : '새로운 실사 시작하기' }}
          </button>
        </div>

        <button @click="handleLogout" class="back-link">
          <ArrowLeft size="16" /> 다른 계정으로 로그인
        </button>
      </div>
    </div>

    <!-- Inspection Tab -->
    <div v-if="!showLogin && !showProjectSelector && activeTab === 'inspection'" class="tab-content">

      
      <div class="glass input-section">
        <Barcode class="icon" />
        <input 
          v-model="barcodeValue" 
          placeholder="바코드를 스캔하세요" 
          @keyup.enter="handleBarcodeEnter"
          autofocus
        />
        <button v-if="store.inspectionSearchQuery || barcodeValue" @click="clearInspectionSearch" class="clear-filter-btn">
          <X size="18" />
        </button>
        <button v-if="!store.inspectionSearchQuery && !barcodeValue && store.scannedAssets && store.scannedAssets.length > 0" @click="handleConfirm" class="confirm-btn">
          비우기
        </button>
      </div>

      <div class="asset-list">
        <AssetCard 
          v-for="asset in store.scannedAssets" 
          :key="asset.assetNumber" 
          :asset="asset"
          :user-stats="store.userStats"
          @track="handleTrackAsset"
          @search-user="handleQuickSearchUser"
          @search-dept="handleQuickSearchDept"
          @cancel-check="handleCancelCheck"
          @update-note="store.updateAssetNote"
        />
      </div>
    </div>

    <!-- Search Tab -->
    <div v-if="!showLogin && !showProjectSelector && activeTab === 'search'" class="tab-content">
      <div class="glass search-section">
        <Search class="icon" />
        <input 
          :value="internalSearchQuery" 
          @input="handleSearchInput" 
          placeholder="사용자, ID, 자산번호 검색" 
        />
        <button v-if="internalSearchQuery" @click="clearSearch" class="clear-filter-btn">
          <X size="18" />
        </button>
      </div>
      
      <div class="dept-selector-trigger" @click="showDeptModal = true">
        <div class="selector-info">
          <Filter size="18" class="icon" />
          <div class="text-group">
            <span class="label">부서 필터</span>
            <span class="value">{{ store.selectedDepartment }}</span>
          </div>
        </div>
        <ChevronRight size="20" class="arrow" />
      </div>

      <!-- Department Selection Modal -->
      <Transition name="fade">
        <div v-if="showDeptModal" class="modal-overlay" @click.self="showDeptModal = false">
          <div class="glass modal-content full-modal">
            <div class="modal-header">
              <h3>부서 선택</h3>
              <button @click="showDeptModal = false" class="close-btn">
                <X size="24" />
              </button>
            </div>
            
            <div class="glass search-section modal-search">
              <Search class="icon" />
              <input v-model="deptSearchQuery" placeholder="부서명 검색" focus />
            </div>

            <div class="dept-list scroll-container">
              <div 
                v-for="dept in filteredDeptList" 
                :key="dept"
                class="dept-item"
                :class="{ active: store.selectedDepartment === dept }"
                @click="store.selectedDepartment = dept; showDeptModal = false"
              >
                <span>{{ dept }}</span>
                <CheckCircle v-if="store.selectedDepartment === dept" size="18" class="check" />
              </div>
            </div>
          </div>
        </div>
      </Transition>

      <!-- Stats Summary -->
      <div v-if="searchStats && (store.searchQuery || store.selectedDepartment !== '전체') && searchStats.modelCounts.length > 0" class="glass stats-summary fade-in">
        <div class="stats-models">
          <span class="summary-label">미체크 자산:</span>
          <div v-for="[model, count] in searchStats.modelCounts" :key="model" class="model-tag">
            <span class="model-name">{{ model }}</span>
            <span class="model-count">{{ count }}</span>
          </div>
        </div>
      </div>

      <div class="asset-list">
        <AssetCard 
          v-for="asset in visibleAssets" 
          :key="asset.assetNumber" 
          :asset="asset"
          :user-stats="store.userStats"
          @track="handleTrackAsset"
          @search-user="handleQuickSearchUser"
          @search-dept="handleQuickSearchDept"
          @cancel-check="handleCancelCheck"
          @update-note="store.updateAssetNote"
        />

        <!-- Load More Button -->
        <div v-if="displayLimit < store.filteredAssets.length" class="load-more-container">
          <button @click="loadMore" class="glass load-more-btn">
            더 보기 ({{ store.filteredAssets.length - displayLimit }}개 남음)
          </button>
        </div>
      </div>
    </div>
    <!-- Records (Trade Logs) Tab -->
    <div v-if="!showLogin && !showProjectSelector && activeTab === 'reference'" class="tab-content">
      <div class="glass search-section log-search">
        <Search class="icon" />
        <input 
          v-model="store.referenceSearchQuery" 
          placeholder="추적(Trade) 통합 검색" 
        />
        <button v-if="store.referenceSearchQuery" @click="store.referenceSearchQuery = ''" class="clear-filter-btn">
          <X size="18" />
        </button>
      </div>

      <div class="trade-log-container">
        <div v-if="store.filteredTradeLogs.length === 0" class="empty-state">
          <Crosshair size="48" class="icon dimmed" />
          <p>조회된 추적 데이터가 없습니다.</p>
        </div>
        <div v-else class="trade-list">
          <!-- Group Card per Asset -->
          <div v-for="group in store.filteredTradeLogs" :key="group.assetNo" class="glass asset-history-card">
            <div class="history-card-header">
              <div class="asset-badge">
                <Package size="16" />
                <span class="asset-no">{{ group.assetNo }}</span>
              </div>
              <span class="history-count">변경 {{ group.logs.length }}건</span>
            </div>

            <div class="history-timeline">
              <div v-for="(log, lIdx) in group.logs" :key="lIdx" class="timeline-item">
                <div class="timeline-meta">
                  <span class="timeline-date">{{ store._getVal(log, 'timestamp') || store._getVal(log, 'date') || store._getVal(log, '업무일자') }}</span>
                </div>
                
                <div class="timeline-content">
                  <div class="movement">
                    <div class="move-item from">
                      <span class="move-label">이전</span>
                      <div class="move-val-group left-align">
                        <span class="move-val highlight">{{ log._exUserName || '없음' }}</span>
                        <span class="move-sub">{{ log._exUserPart }}</span>
                      </div>
                    </div>
                    <div class="move-arrow">
                      <ChevronRight size="14" />
                    </div>
                    <div class="move-item to">
                      <span class="move-label">신규</span>
                      <div class="move-val-group">
                        <span class="move-val highlight">{{ log._joinedName || '알수없음' }}</span>
                        <span class="move-sub">{{ log._joinedPart }}</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Load More for Reference -->
        <div v-if="activeTab === 'reference' && store.referenceLimit < store.tradeLogs.length" class="load-more-container">
          <button @click="loadMore" class="glass load-more-btn">
             데이터 더 보기
          </button>
        </div>
      </div>
    </div>

    <!-- System Settings Tab -->
    <div v-if="!showLogin && !showProjectSelector && activeTab === 'history'" class="tab-content">
      <div class="glass session-info-bar" @click="handleSwitchSession">
        <div class="session-meta">
          <span class="current-session">{{ store.currentFile?.name }}</span>
          <span class="last-sync-time" v-if="store.lastMasterSync">
            마스터 동기화: {{ new Date(store.lastMasterSync).toLocaleString([], {month:'numeric', day:'numeric', hour:'2-digit', minute:'2-digit'}) }}
          </span>
        </div>
        <button class="switch-session-btn">회차 전환</button>
      </div>



      <!-- Settings / System Actions Section -->
      <div class="history-actions-grid">
        <button class="glass action-card save-backup" @click="handleBackupSave" :disabled="store.loading">
          <div class="action-icon">
            <Save size="20" />
          </div>
          <div class="action-info">
            <span class="action-title">내역 저장 및 백업</span>
            <span class="action-desc">진행 사항을 구글 시트에 업데이트하고 백업 파일을 생성합니다.</span>
          </div>
        </button>

        <button class="glass action-card sync-master" @click="handleSyncMaster" :disabled="store.loading">
          <div class="action-icon">
            <RefreshCw size="20" />
          </div>
          <div class="action-info">
            <span class="action-title">마스터 데이터 동기화</span>
            <span class="action-desc">최신 인사정보 및 거래이력을 동기화합니다.</span>
          </div>
        </button>
        
        <button class="glass action-card sync-now" @click="store.saveDataInBackground" :disabled="store.loading || !store.isOnline || !store.hasPendingSync">
          <div class="action-icon">
            <CloudUpload size="20" />
          </div>
          <div class="action-info">
            <span class="action-title">즉시 동기화</span>
            <span class="action-desc">대기 중인 변경사항을 지금 업로드합니다.</span>
          </div>
          <div v-if="store.hasPendingSync" class="pending-badge">!</div>
        </button>

        <button class="glass action-card logout" @click="handleLogout">
          <div class="action-icon">
            <LogOut size="20" />
          </div>
          <div class="action-info">
            <span class="action-title">로그아웃</span>
            <span class="action-desc">인증 토큰을 삭제하고 계정을 전환합니다.</span>
          </div>
        </button>
      </div>


    </div>
  </main>

    <!-- Bottom Navigation -->
    <nav class="glass bottom-nav">
      <button @click="activeTab = 'inspection'" :class="{ active: activeTab === 'inspection' }">
        <Barcode />
        <span>실사</span>
      </button>
      <button @click="activeTab = 'search'" :class="{ active: activeTab === 'search' }">
        <Search />
        <span>검색</span>
      </button>
      <button @click="activeTab = 'reference'" :class="{ active: activeTab === 'reference' }">
        <Crosshair />
        <span>추적</span>
      </button>
      <button @click="activeTab = 'history'" :class="{ active: activeTab === 'history' }">
        <Settings />
        <span>설정</span>
      </button>
    </nav>

    <!-- Global Confirm Modal -->
    <Transition name="fade">
      <div v-if="confirmModal.show" class="modal-overlay">
        <div class="glass confirm-modal">
          <div class="modal-body">
            <h3>{{ confirmModal.title }}</h3>
            <p>{{ confirmModal.message }}</p>
          </div>
          <div class="modal-actions">
            <button @click="confirmModal.show = false" class="cancel-btn">취소</button>
            <button @click="triggerModalConfirm" class="confirm-btn-final">확인</button>
          </div>
        </div>
      </div>
    </Transition>

    <!-- Global Toast Notification -->
    <Transition name="toast">
      <div v-if="store.toast.show" class="toast-overlay">
        <div class="glass toast" :class="store.toast.type">
          <Info v-if="store.toast.type === 'info'" size="18" />
          <CheckCircle v-else-if="store.toast.type === 'success'" size="18" />
          <AlertCircle v-else size="18" />
          <span>{{ store.toast.message }}</span>
        </div>
      </div>
    </Transition>
</template>

<style scoped>
.header {
  margin: 0;
  padding: calc(10px + env(safe-area-inset-top)) 16px 10px 16px;
  border-radius: 0 0 20px 20px;
}

.header-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.logout-btn {
  background: transparent;
  color: var(--text-muted);
  padding: 4px;
}

.logout-btn:hover {
  color: var(--danger);
}

.global-loading-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(4px);
  z-index: 9999;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  color: white;
}

.loader {
  border: 4px solid rgba(255, 255, 255, 0.3);
  border-radius: 50%;
  border-top: 4px solid white;
  width: 40px;
  height: 40px;
  animation: spin 1s linear infinite;
  margin-bottom: 16px;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.loading-opacity {
  opacity: 0.5;
  pointer-events: none;
}

h1 {
  margin: 0;
  font-size: 1.1rem;
  letter-spacing: -0.3px;
  font-weight: 800;
  background: linear-gradient(135deg, #fff 0%, #a78bfa 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.progress-container {
  height: 18px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 10px;
  position: relative;
  overflow: hidden;
}

.progress-bar {
  height: 100%;
  background: linear-gradient(90deg, var(--primary), var(--secondary));
  transition: width 0.5s ease;
}

.progress-text {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  font-size: 0.65rem;
  font-weight: 700;
}

.sync-status {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.75rem;
  color: var(--text-muted);
  background: rgba(255, 255, 255, 0.05);
  padding: 4px 10px;
  border-radius: 20px;
}

.sync-status.syncing {
  color: var(--secondary);
}

.spin-icon {
  animation: spin 2s linear infinite;
}

.content {
  flex: 1;
  padding: 12px 16px calc(110px + env(safe-area-inset-bottom)) 16px;
  overflow-y: auto;
}

.input-section, .search-section {
  display: flex;
  align-items: center;
  padding: 4px 12px;
  margin-bottom: 12px;
  min-height: 44px;
}

.input-section input, .search-section input {
  flex: 1;
  background: transparent;
  border: none;
  font-size: 0.9rem;
  outline: none;
  padding: 8px 0;
}

.icon {
  margin-right: 12px;
  color: var(--text-muted);
}

.asset-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.status-icon.checked {
  color: var(--secondary);
}

.search-section.modal-search {
  margin: 0 0 16px 0;
}

.dept-selector-trigger {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 16px;
  padding: 12px 16px;
  margin-bottom: 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  cursor: pointer;
  transition: all 0.2s ease;
}

.dept-selector-trigger:active {
  transform: scale(0.98);
  background: rgba(255, 255, 255, 0.1);
}

.dept-selector-trigger .selector-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.dept-selector-trigger .icon {
  color: var(--primary);
}

.dept-selector-trigger .text-group {
  display: flex;
  flex-direction: column;
}

.dept-selector-trigger .label {
  font-size: 0.75rem;
  color: var(--text-muted);
}

.dept-selector-trigger .value {
  font-size: 0.95rem;
  font-weight: 600;
}

.dept-selector-trigger .arrow {
  color: var(--text-muted);
  opacity: 0.5;
}

/* Modal Styles */
.full-modal {
  width: 90% !important;
  max-width: 400px;
  height: 80vh;
  display: flex;
  flex-direction: column;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.modal-header h3 {
  margin: 0;
  font-size: 1.2rem;
}

.close-btn {
  background: transparent;
  color: var(--text-muted);
}

.dept-list {
  flex: 1;
  overflow-y: auto;
  padding-right: 4px;
}

.dept-item {
  padding: 14px 16px;
  border-radius: 12px;
  margin-bottom: 8px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  transition: all 0.2s ease;
  background: rgba(255, 255, 255, 0.03);
}

.dept-item.active {
  background: rgba(79, 70, 229, 0.15);
  border: 1px solid rgba(79, 70, 229, 0.3);
}

.dept-item.active span {
  color: var(--primary);
  font-weight: 600;
}

.dept-item .check {
  color: var(--primary);
}

.scroll-container::-webkit-scrollbar {
  width: 4px;
}

.scroll-container::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.1);
  border-radius: 4px;
}
.filter-chips {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding-bottom: 12px;
  margin-bottom: 8px;
}

.chip {
  white-space: nowrap;
  padding: 6px 16px;
  background: var(--bg-card);
  color: var(--text-muted);
  border: 1px solid var(--border-glass);
}

.chip.active {
  background: var(--primary);
  color: white;
  border-color: var(--primary);
}

.bottom-nav {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  height: calc(42px + env(safe-area-inset-bottom));
  padding-bottom: env(safe-area-inset-bottom);
  display: flex;
  justify-content: space-around;
  align-items: center;
  border-radius: 16px 16px 0 0;
  border-bottom: none;
  z-index: 1000;
  box-shadow: 0 -8px 24px rgba(0, 0, 0, 0.5);
}

.bottom-nav button {
  background: transparent;
  color: var(--text-muted);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1px;
}

.bottom-nav button :deep(svg), 
.bottom-nav button svg {
  width: 16px;
  height: 16px;
}

.bottom-nav button span {
  font-size: 0.65rem;
  font-weight: 500;
}

.bottom-nav button.active {
  color: var(--primary);
}

.save-btn {
  background: var(--secondary) !important;
  color: white !important;
  padding: 8px 16px;
  border-radius: 12px;
}

.auth-card {
  padding: 24px;
  margin-top: 40px;
  text-align: center;
}

.auth-card h3 {
  margin-top: 0;
}

.desc {
  font-size: 0.9rem;
  color: var(--text-muted);
  margin-bottom: 24px;
}

.input-group {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.primary-btn {
  background: var(--primary);
  color: white;
  padding: 12px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  border-radius: 12px;
}

.secondary-btn {
  background: rgba(255, 255, 255, 0.1);
  color: white;
  padding: 12px;
  font-weight: 600;
  border-radius: 12px;
  border: 1px solid var(--border-glass);
}

.google-login-btn {
  background: #ffffff;
  color: #1f2937;
  width: 100%;
  margin-bottom: 8px;
}

.divider-text {
  margin: 16px 0;
  font-size: 0.8rem;
  color: var(--text-muted);
  display: flex;
  align-items: center;
  gap: 12px;
}

.divider-text::before, .divider-text::after {
  content: "";
  flex: 1;
  height: 1px;
  background: rgba(255, 255, 255, 0.1);
}

.help {
  font-size: 0.8rem;
  margin-top: 20px;
}

.help a {
  color: var(--primary);
  text-decoration: none;
}

.error-msg {
  color: var(--danger);
  margin-top: 16px;
  font-size: 0.9rem;
}

.sheet-info {
  margin-top: 8px;
  font-size: 0.75rem !important;
  color: var(--primary) !important;
  opacity: 0.8;
  font-weight: 500;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-progress-tag {
  background: rgba(255, 255, 255, 0.1);
  padding: 2px 8px;
  border-radius: 8px;
  font-size: 0.7rem;
  font-weight: 600;
  color: var(--text-muted);
}

.user-stats-stack {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
}

.track-btn {
  background: rgba(79, 70, 229, 0.1);
  color: var(--primary);
  border: 1px solid rgba(79, 70, 229, 0.2);
  padding: 2px 8px;
  font-size: 0.65rem;
  font-weight: 700;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.track-btn:active {
  background: var(--primary);
  color: white;
}

.divider {
  margin: 0 4px;
  opacity: 0.3;
}

.confirm-btn {
  background: var(--primary);
  color: white;
  padding: 4px 10px;
  font-size: 0.75rem;
  margin-left: 6px;
  border-radius: 6px;
  font-weight: 600;
  white-space: nowrap;
}

.clear-filter-btn {
  background: rgba(255, 255, 255, 0.1);
  color: white;
  padding: 6px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-left: 8px;
}

.load-more-container {
  padding: 20px 0 40px 0;
  display: flex;
  justify-content: center;
}

.load-more-btn {
  padding: 12px 24px;
  border-radius: 12px;
  color: var(--primary);
  font-weight: 600;
  font-size: 0.9rem;
  border: 1px solid rgba(79, 70, 229, 0.3);
}

.load-more-btn:active {
  transform: scale(0.95);
}

/* History Tab Styles */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 60vh;
  text-align: center;
  color: var(--text-muted);
}

.empty-state .icon {
  margin-right: 0;
  margin-bottom: 16px;
  opacity: 0.3;
}

.empty-state .sub {
  font-size: 0.8rem;
  margin-top: 8px;
  opacity: 0.6;
}

.log-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-top: 8px;
}

.log-card {
  padding: 16px;
  border-radius: 20px;
}

.log-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
  padding-bottom: 8px;
}

.log-asset {
  font-weight: bold;
  color: #a78bfa; /* Light purple for better visibility */
}

.log-time {
  font-size: 0.75rem;
  color: var(--text-muted);
}

.movement {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.move-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.move-label {
  font-size: 0.7rem;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.move-val {
  font-size: 0.85rem;
  font-weight: 500;
  color: #ffffff;
}

.move-arrow {
  color: var(--primary);
  opacity: 0.5;
}

/* Trade Log Styles */
.trade-log-container {
  padding-bottom: 20px;
}

.trade-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.trade-card {
  padding: 16px;
  border-radius: 20px;
}

.asset-history-card {
  padding: 16px;
  border-radius: 20px;
  margin-bottom: 8px;
  border: 1px solid rgba(255, 255, 255, 0.05);
}

.history-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.asset-badge {
  display: flex;
  align-items: center;
  gap: 8px;
  background: rgba(79, 70, 229, 0.1);
  padding: 4px 12px;
  border-radius: 12px;
  color: var(--primary);
  font-weight: 700;
  font-size: 0.9rem;
}

.history-count {
  font-size: 0.75rem;
  color: var(--text-muted);
}

.history-timeline {
  display: flex;
  flex-direction: column;
  gap: 16px;
  position: relative;
  padding-left: 8px;
}

.timeline-item {
  position: relative;
  padding-left: 16px;
  border-left: 2px solid rgba(255, 255, 255, 0.05);
}

.timeline-item::before {
  content: "";
  position: absolute;
  left: -5px;
  top: 4px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--primary);
  opacity: 0.6;
}

.timeline-meta {
  margin-bottom: 6px;
}

.timeline-date {
  font-size: 0.7rem;
  font-weight: 600;
  color: var(--text-muted);
}

.move-val-group {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  text-align: right;
}

.move-val-group.left-align {
  align-items: flex-start;
  text-align: left;
}

.move-val.highlight {
  font-weight: 700;
  color: #a78bfa; /* Light purple to match theme */
  font-size: 0.95rem;
}

.move-sub {
  font-size: 0.75rem;
  color: var(--text-muted);
}

.log-search {
  margin-bottom: 16px !important;
}

.log-asset-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.log-reason-tag {
  background: rgba(167, 139, 250, 0.2);
  color: #a78bfa;
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 0.65rem;
  font-weight: 600;
}

.memo-input {
  background: transparent;
  border: none;
  color: white;
  font-size: 0.9rem;
  width: 100%;
  outline: none;
}

.memo-input::placeholder {
  color: rgba(255, 255, 255, 0.3);
}

.memo-input:focus {
  color: #a78bfa;
}
/* Session Selection Styles */
.session-selector {
  padding-top: 10px;
  display: flex;
  flex-direction: column;
  gap: 32px;
}

.selector-section h3 {
  margin: 0 0 4px 0;
  font-size: 1.2rem;
}

.selector-section .desc {
  margin-bottom: 20px;
}

.resume-card {
  border-left: 4px solid var(--primary);
  margin-bottom: 12px;
}

.file-icon.resume {
  background: rgba(79, 70, 229, 0.1);
  color: var(--primary);
}

.file-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 24px;
}

.file-card {
  display: flex;
  align-items: center;
  padding: 16px;
  border-radius: 20px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.file-card:active {
  transform: scale(0.98);
}

.file-icon {
  width: 48px;
  height: 48px;
  background: rgba(79, 70, 229, 0.1);
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--primary);
  margin-right: 16px;
}

.file-info {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.file-name {
  font-weight: 600;
  font-size: 1rem;
}

.file-date {
  font-size: 0.75rem;
  color: var(--text-muted);
}

.file-card .arrow {
  color: var(--text-muted);
  opacity: 0.5;
}

.back-link {
  background: transparent;
  color: var(--text-muted);
  font-size: 0.9rem;
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0 auto;
}

/* Session Info Bar */
.session-info-bar {
  margin-bottom: 16px;
  padding: 8px 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-radius: 12px;
  background: rgba(79, 70, 229, 0.05);
  border: 1px solid rgba(79, 70, 229, 0.1);
  cursor: pointer;
}

.current-session {
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 200px;
}

.session-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.last-sync-time {
  font-size: 0.65rem;
  color: var(--text-muted);
}

.switch-session-btn {
  background: transparent;
  color: var(--text-muted);
  font-size: 0.75rem;
  font-weight: 500;
  padding: 4px 8px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 6px;
}

/* Sync Status & Offline Styles */
.sync-status.offline {
  background: rgba(239, 68, 68, 0.1);
  color: #f87171;
  border: 1px solid rgba(239, 68, 68, 0.2);
}

.sync-status.offline span {
  color: #f87171;
}

.blink-icon {
  animation: blink 2s infinite;
  color: var(--warning);
}

@keyframes blink {
  0% { opacity: 1; }
  50% { opacity: 0.4; }
  100% { opacity: 1; }
}

.pending-badge {
  background: var(--warning);
  color: white;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 800;
  margin-left: auto;
  box-shadow: 0 0 10px rgba(245, 158, 11, 0.4);
}

.sync-now:disabled {
  opacity: 0.3;
}

.sync-now .action-icon {
  background: rgba(217, 119, 6, 0.1);
  color: #d97706;
}

/* History Actions Grid */
.history-actions-grid {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 24px;
}

.action-card {
  display: flex;
  align-items: center;
  padding: 16px;
  border-radius: 20px;
  text-align: left;
  border: 1px solid rgba(255, 255, 255, 0.05);
  transition: all 0.2s ease;
}

.action-card:active {
  transform: scale(0.98);
}

.action-icon {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 12px;
  flex-shrink: 0;
}

.action-icon svg {
  width: 20px;
  height: 20px;
}

.save-backup .action-icon {
  background: rgba(16, 185, 129, 0.1);
  color: #10b981;
}

.logout .action-icon {
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
}

.sync-master .action-icon {
  background: rgba(59, 130, 246, 0.1);
  color: #3b82f6;
}

.action-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.action-title {
  font-weight: 600;
  font-size: 0.95rem;
  color: #ffffff;
}

.action-desc {
  font-size: 0.75rem;
  color: var(--text-muted);
}

/* Stats Summary */
.stats-summary {
  padding: 12px;
  border-radius: 12px;
  margin-bottom: 20px;
  border: 1px solid rgba(255, 255, 255, 0.05);
  background: rgba(255, 255, 255, 0.03);
}

.stats-models {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.summary-label {
  font-size: 0.85rem;
  color: var(--text-muted);
  margin-right: 4px;
}

.model-tag {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 8px;
  font-size: 0.8rem;
}

.model-name {
  color: var(--text-muted);
}

.model-count {
  color: white;
  font-weight: 600;
}

.session-input-box {
  padding: 16px;
  margin-bottom: 24px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.focus-box {
  border: 1px solid var(--primary);
  background: rgba(79, 70, 229, 0.05);
}

.input-desc {
  font-size: 0.75rem;
  color: var(--text-muted);
  margin-top: 4px;
}

.master-info-box {
  padding: 16px;
  margin-bottom: 16px;
  border-left: 4px solid var(--secondary);
}

.box-label {
  font-size: 0.75rem;
  color: var(--text-muted);
  margin-bottom: 8px;
}

.master-name {
  display: flex;
  align-items: center;
  gap: 10px;
  font-weight: 600;
  color: var(--secondary);
}

.master-name .icon {
  opacity: 0.8;
}

.master-not-found {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.retry-btn {
  background: rgba(255, 255, 255, 0.1);
  color: var(--primary);
  border: 1px solid rgba(79, 70, 229, 0.2);
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 0.75rem;
  display: flex;
  align-items: center;
  gap: 4px;
}

.top-util-bar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 8px;
}

.sync-badge {
  background: rgba(79, 70, 229, 0.1);
  color: var(--primary);
  padding: 4px 10px;
  border-radius: 20px;
  font-size: 0.75rem;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
}

.start-survey-btn {
  width: 100%;
  padding: 18px;
  border-radius: 20px;
  background: var(--primary) !important;
  color: white;
  font-weight: bold;
  font-size: 1.1rem;
  margin-bottom: 24px;
  border: none;
  box-shadow: 0 4px 15px rgba(79, 70, 229, 0.4);
}

.start-survey-btn:disabled {
  opacity: 0.5;
  box-shadow: none;
}

.start-survey-btn:active {
  transform: scale(0.98);
}

.session-input-box label, .list-label {
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--primary);
  opacity: 0.8;
}

.session-name-input {
  background: transparent;
  border: none;
  border-bottom: 1px solid rgba(255, 255, 255, 0.2);
  padding: 8px 0;
  color: white;
  font-size: 1.1rem;
  outline: none;
}

.start-badge {
  background: var(--primary);
  color: white;
  font-size: 0.75rem;
  padding: 4px 10px;
  border-radius: 8px;
  font-weight: 600;
}

.card-actions {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid rgba(255, 255, 255, 0.05);
  display: flex;
  justify-content: flex-end;
}

.terminate-btn {
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
  border: 1px solid rgba(239, 68, 68, 0.2);
  padding: 6px 16px;
  border-radius: 8px;
  font-size: 0.8rem;
  font-weight: 600;
}

.termination-log {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
}

.term-badge {
  background: #ef4444;
  color: white;
  font-size: 0.7rem;
  padding: 2px 6px;
  border-radius: 4px;
  font-weight: bold;
}
/* Global Modal Styles */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(8px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  z-index: 10000;
}

.confirm-modal {
  width: 100%;
  max-width: 320px;
  padding: 24px;
  border-radius: 24px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  text-align: center;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.4);
}

.modal-body h3 {
  margin: 0 0 12px 0;
  font-size: 1.25rem;
  color: white;
}

.modal-body p {
  margin: 0 0 24px 0;
  font-size: 0.95rem;
  color: var(--text-muted);
  line-height: 1.5;
}

.modal-actions {
  display: flex;
  gap: 12px;
}

.modal-actions button {
  flex: 1;
  padding: 14px;
  border-radius: 14px;
  font-weight: 600;
  font-size: 1rem;
}

.cancel-btn {
  background: rgba(255, 255, 255, 0.05);
  color: white;
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.confirm-btn-final {
  background: var(--primary);
  color: white;
  border: none;
}

/* Transiton: Fade */
.fade-enter-active, .fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from, .fade-leave-to {
  opacity: 0;
}

.fade-enter-active .confirm-modal {
  animation: modalIn 0.3s ease-out;
}

@keyframes modalIn {
  from { transform: scale(0.9) translateY(20px); opacity: 0; }
  to { transform: scale(1) translateY(0); opacity: 1; }
}

/* Toast Animation & Styles */
.toast-overlay {
  position: fixed;
  top: 80px;
  left: 0;
  right: 0;
  display: flex;
  justify-content: center;
  z-index: 9999;
  pointer-events: none;
}

.toast {
  padding: 12px 20px;
  border-radius: 30px;
  display: flex;
  align-items: center;
  gap: 10px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
  font-weight: 500;
  animation: slideUp 0.3s ease-out;
}

.toast.success { border-left: 4px solid var(--secondary); }
.toast.error { border-left: 4px solid #f87171; }
.toast.info { border-left: 4px solid var(--primary); }

.toast-enter-active, .toast-leave-active {
  transition: all 0.3s ease;
}

.toast-enter-from, .toast-leave-to {
  opacity: 0;
  transform: translateY(20px);
}

@keyframes slideUp {
  from { transform: translateY(20px); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>

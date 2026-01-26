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
  Trash2
} from 'lucide-vue-next'

const store = useAssetStore()
const activeTab = ref('inspection')
const showDeptModal = ref(false)
const showProjectSelector = ref(false)
const sessionName = ref('')
const deptSearchQuery = ref('')

// Filtered department list for modal
const filteredDeptList = computed(() => {
  const q = deptSearchQuery.value.toLowerCase()
  return store.departments.filter(d => d.toLowerCase().includes(q))
})

// Performance Optimization: Pagination
const displayLimit = ref(20)
const pageSize = 20



const loadMore = () => {
  if (store.filteredAssets && displayLimit.value < store.filteredAssets.length) {
    displayLimit.value += pageSize
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
      showProjectSelector.value = true
      if (!selectedMaster.value && store.masterFiles.length > 0) {
        selectedMaster.value = store.masterFiles[0]
      }
      
      // Check if master sync is needed (Once a day after 06:00)
      store.checkAndSyncMaster()
    }
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
  if (!accessTokenInput.value) return
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

const handleRetryList = async () => {
  if (accessTokenInput.value) {
    await store.initializeData(accessTokenInput.value)
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
  alert('마스터 데이터가 최신 상태로 동기화되었습니다.')
}

const handleResumeSession = async (file) => {
  if (store.loading) return
  await store.loadProject(file)
  showProjectSelector.value = false
  activeTab.value = 'inspection'
}

const handleStartSurvey = async () => {
  if (!sessionName.value) {
    alert('회차명을 입력해주세요 (예: 2026년 정기조사)');
    return;
  }
  if (!selectedMaster.value) {
    alert('마스터 데이터를 찾을 수 없습니다.');
    return;
  }
  await store.startSession(selectedMaster.value, sessionName.value)
  showProjectSelector.value = false
  activeTab.value = 'inspection'
}

const handleLogout = () => {
  localStorage.removeItem('google_access_token')
  store.isAuthenticated = false
  showLogin.value = true
  showProjectSelector.value = false
  store.currentFile = null
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
    store.updateAsset(barcodeValue.value, { status: 'checked' })
    try {
      await store.saveData()
    } catch (saveError) {
      console.warn('Auto-save failed:', saveError)
    }
  } else {
    alert('등록되지 않은 자산입니다.')
  }
  barcodeValue.value = ''
}

const handleConfirm = () => {
  if (!store.scannedAssets || store.scannedAssets.length === 0) return
  if (confirm('현재 실사한 리스트를 확인 완료 처리하고 목록에서 비우시겠습니까?')) {
    store.clearScannedList()
  }
}

const handleBackupSave = async () => {
  if (confirm('오늘 작업 내용을 저장하고 백업 파일을 생성하시겠습니까?')) {
    await store.backupAndSave()
  }
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
        <p class="desc">구글 API를 사용하기 위해 Access Token을 입력해주세요.</p>
        <div class="input-group">
          <input 
            v-model="accessTokenInput" 
            type="password" 
            placeholder="Google Access Token 입력" 
          />
          <button @click="handleLogin" :disabled="store.loading" class="primary-btn">
            {{ store.loading ? '연결 중...' : '인증 및 조회' }}
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
        <button v-if="store.scannedAssets && store.scannedAssets.length > 0" @click="handleConfirm" class="confirm-btn">
          비우기
        </button>
      </div>

      <div class="asset-list">
        <div v-for="asset in store.scannedAssets" :key="asset.assetNumber" class="glass asset-card" :class="asset.status">
          <div class="card-header">
            <span class="asset-id">{{ asset.assetNumber }}</span>
            <div class="header-right">
              <span class="user-progress-tag" v-if="store.userStats[asset.in_user]">
                {{ store.userStats[asset.in_user].done }}/{{ store.userStats[asset.in_user].total }}
              </span>
              <CheckCircle v-if="asset.status === 'checked'" class="status-icon checked" />
            </div>
          </div>
          <div class="card-body">
            <div class="info-row">
              <User size="16" />
              <span>{{ asset.userName }} ({{ asset.department }})</span>
            </div>
            <div class="info-row">
              <Fingerprint size="16" />
              <span class="text-muted">ID: {{ asset.in_user }}</span>
            </div>
            <div class="info-row" v-if="asset.category || (asset.model_name || asset.model)">
              <Tag size="16" />
              <span class="text-muted">{{ asset.category }}</span>
              <span class="divider">|</span>
              <Package size="16" />
              <span class="text-muted">{{ asset.model_name || asset.model }}</span>
            </div>
            <div class="info-row" v-if="asset.serial_number">
              <Hash size="16" />
              <span class="text-muted">SN: {{ asset.serial_number }}</span>
            </div>
            <div class="info-row memo-row">
              <Database size="16" />
              <input 
                class="memo-input" 
                v-model="asset.note" 
                placeholder="조사 메모 입력..." 
                @blur="store.updateAssetNote(asset.assetNumber, asset.note)"
              />
            </div>
            <div class="card-shortcuts">
              <button @click.stop="handleQuickSearchUser(asset.in_user)" class="shortcut-btn">
                <Search size="16" /> ID로 검색
              </button>
              <button @click.stop="handleQuickSearchDept(asset.department)" class="shortcut-btn">
                <Filter size="16" /> 부서로 검색
              </button>
            </div>
          </div>
          <!-- card-actions removed -->
        </div>
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
        <div v-for="asset in visibleAssets" :key="asset.assetNumber" class="glass asset-card" :class="asset.status">
          <div class="card-header">
            <span class="asset-id">{{ asset.assetNumber }}</span>
            <div class="header-right">
              <span class="user-progress-tag" v-if="store.userStats[asset.in_user]">
                {{ store.userStats[asset.in_user].done }}/{{ store.userStats[asset.in_user].total }}
              </span>
              <CheckCircle v-if="asset.status === 'checked'" class="status-icon checked" />
            </div>
          </div>
          <div class="card-body">
            <div class="info-row">
              <User size="16" />
              <span>{{ asset.userName }} ({{ asset.department }})</span>
            </div>
            <div class="info-row">
              <Fingerprint size="16" />
              <span class="text-muted">ID: {{ asset.in_user }}</span>
            </div>
            <div class="info-row" v-if="asset.category || (asset.model_name || asset.model)">
              <Tag size="16" />
              <span class="text-muted">{{ asset.category }}</span>
              <span class="divider">|</span>
              <Package size="16" />
              <span class="text-muted">{{ asset.model_name || asset.model }}</span>
            </div>
            <div class="info-row" v-if="asset.serial_number">
              <Hash size="16" />
              <span class="text-muted">SN: {{ asset.serial_number }}</span>
            </div>
            <div class="info-row memo-row">
              <Database size="16" />
              <input 
                class="memo-input" 
                v-model="asset.note" 
                placeholder="조사 메모 입력..." 
                @blur="store.updateAssetNote(asset.assetNumber, asset.note)"
              />
            </div>
            <div class="card-shortcuts">
              <button @click.stop="handleQuickSearchUser(asset.in_user)" class="shortcut-btn">
                <Search size="16" /> ID로 검색
              </button>
              <button @click.stop="handleQuickSearchDept(asset.department)" class="shortcut-btn">
                <Filter size="16" /> 부서로 검색
              </button>
            </div>
          </div>
        </div>

        <!-- Load More Button -->
        <div v-if="displayLimit < store.filteredAssets.length" class="load-more-container">
          <button @click="loadMore" class="glass load-more-btn">
            더 보기 ({{ store.filteredAssets.length - displayLimit }}개 남음)
          </button>
        </div>
      </div>
    </div>
    <!-- History Tab -->
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
            <Save size="24" />
          </div>
          <div class="action-info">
            <span class="action-title">내역 저장 및 백업</span>
            <span class="action-desc">진행 사항을 구글 시트에 업데이트하고 백업 파일을 생성합니다.</span>
          </div>
        </button>

        <button class="glass action-card sync-master" @click="handleSyncMaster" :disabled="store.loading">
          <div class="action-icon">
            <RefreshCw size="24" />
          </div>
          <div class="action-info">
            <span class="action-title">마스터 데이터 동기화</span>
            <span class="action-desc">최신 자산 목록을 불러와 현재 회차에 합칩니다.</span>
          </div>
        </button>
        
        <button class="glass action-card logout" @click="handleLogout">
          <div class="action-icon">
            <LogOut size="24" />
          </div>
          <div class="action-info">
            <span class="action-title">로그아웃</span>
            <span class="action-desc">인증 토큰을 삭제하고 계정을 전환합니다.</span>
          </div>
        </button>
      </div>

      <div v-if="store.inspectionLogs.length === 0" class="empty-state">
        <History size="48" class="icon dimmed" />
        <p>현재 세션에서 발생한 변동 내역이 없습니다.</p>
        <span class="sub">사용자나 부서가 변경된 자산이 여기에 표시됩니다.</span>
      </div>

      <div v-else class="log-list">
        <div v-for="(log, idx) in [...store.inspectionLogs].reverse()" :key="idx" class="glass log-card">
          <div class="log-header">
            <span class="log-asset">{{ log.assetNumber }}</span>
            <div class="log-time">{{ new Date(log.timestamp).toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'}) }}</div>
          </div>
          <div class="log-body">
            <template v-if="log.type === 'movement'">
              <div class="movement">
                <div class="move-item from">
                  <span class="move-label">이전 소속</span>
                  <span class="move-val">{{ log.from.user || '없음' }} ({{ log.from.dept || '없음' }})</span>
                </div>
                <div class="move-arrow">
                  <ChevronRight size="18" />
                </div>
                <div class="move-item to">
                  <span class="move-label">현재 소속</span>
                  <span class="move-val">{{ log.to.user }} ({{ log.to.dept }})</span>
                </div>
              </div>
            </template>
          </div>
        </div>
      </div>
    </div>
  </main>

  <nav class="glass bottom-nav">
    <button :class="{ active: activeTab === 'inspection' }" @click="activeTab = 'inspection'">
      <Barcode />
      <span>실사</span>
    </button>
    <button :class="{ active: activeTab === 'search' }" @click="activeTab = 'search'">
      <Search />
      <span>검색</span>
    </button>
    <button :class="{ active: activeTab === 'history' }" @click="activeTab = 'history'">
      <History />
      <span>기록</span>
    </button>
  </nav>
</template>

<style scoped>
.header {
  margin: 16px;
  padding: 16px;
}

.header-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
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
  font-size: 1.5rem;
  letter-spacing: -0.5px;
}

.progress-container {
  height: 24px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 12px;
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
  font-size: 0.75rem;
  font-weight: 600;
}

.content {
  flex: 1;
  padding: 0 16px 80px 16px;
  overflow-y: auto;
}

.input-section, .search-section {
  display: flex;
  align-items: center;
  padding: 8px 16px;
  margin-bottom: 20px;
}

.input-section input, .search-section input {
  flex: 1;
  background: transparent;
  border: none;
  font-size: 1rem;
  outline: none;
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

.asset-card {
  padding: 16px;
  transition: transform 0.2s;
}

.asset-card.checked {
  border-left: 4px solid var(--secondary);
}

.asset-card.missing {
  border-left: 4px solid var(--danger);
  opacity: 0.6;
}

.card-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
}

.asset-id {
  font-weight: bold;
  color: var(--primary);
}

.info-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.9rem;
  color: var(--text-muted);
  margin-top: 4px;
}

.info-val {
  flex: 1;
}

.card-shortcuts {
  display: flex;
  gap: 10px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid rgba(255, 255, 255, 0.05);
}

.shortcut-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 10px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 10px;
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--text-muted);
  transition: all 0.2s;
}

.shortcut-btn:hover {
  background: rgba(79, 70, 229, 0.1);
  color: var(--primary);
  border-color: var(--primary);
  transform: translateY(-1px);
}

.shortcut-btn:active {
  transform: scale(0.98);
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
  height: 70px;
  display: flex;
  justify-content: space-around;
  align-items: center;
  border-radius: 20px 20px 0 0;
  border-bottom: none;
}

.bottom-nav button {
  background: transparent;
  color: var(--text-muted);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
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
}

.primary-btn:disabled {
  opacity: 0.5;
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

.divider {
  margin: 0 4px;
  opacity: 0.3;
}

.confirm-btn {
  background: var(--primary);
  color: white;
  padding: 4px 12px;
  font-size: 0.8rem;
  margin-left: 8px;
  border-radius: 8px;
  font-weight: 600;
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

.move-item.to .move-val {
  color: var(--secondary);
}

.memo-row {
  margin-top: 12px !important;
  padding: 8px 12px;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.1);
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
  width: 48px;
  height: 48px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 16px;
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
</style>

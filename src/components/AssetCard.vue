<script setup>
import { 
  User, 
  Fingerprint, 
  Tag, 
  Package, 
  Hash, 
  Database, 
  Search, 
  Filter, 
  X, 
  CheckCircle,
  Crosshair
} from 'lucide-vue-next'

const props = defineProps({
  asset: {
    type: Object,
    required: true
  },
  userStats: {
    type: Object,
    default: () => ({})
  }
})

const emit = defineEmits(['track', 'search-user', 'search-dept', 'cancel-check', 'update-note'])

const handleUpdateNote = (e) => {
  emit('update-note', props.asset.assetNumber, e.target.value)
}
</script>

<template>
  <div class="glass asset-card" :class="asset.status">
    <div class="card-header">
      <span class="asset-id">{{ asset.assetNumber }}</span>
      <div class="header-right">
        <span class="user-progress-tag" v-if="userStats[asset.in_user]">
          {{ userStats[asset.in_user].done }}/{{ userStats[asset.in_user].total }}
        </span>
        <CheckCircle v-if="asset.status === 'checked'" class="status-icon checked" />
      </div>
    </div>
    <div class="card-body">
      <div class="info-row user-row">
        <div class="user-info">
          <User size="16" />
          <span>{{ asset.userName }} ({{ asset.department }})</span>
        </div>
        <button class="track-btn" @click.stop="emit('track', asset.assetNumber)">추적</button>
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
          @blur="handleUpdateNote"
        />
      </div>
      <div class="card-shortcuts">
        <button @click.stop="emit('search-user', asset.in_user)" class="shortcut-btn">
          <Search size="16" /> ID로 검색
        </button>
        <button @click.stop="emit('search-dept', asset.department)" class="shortcut-btn">
          <Filter size="16" /> 부서로 검색
        </button>
        <button v-if="asset.status.toLowerCase() === 'checked'" @click.stop="emit('cancel-check', asset.assetNumber)" class="shortcut-btn cancel-undo-btn">
          <X size="16" /> 취소
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.asset-card {
  padding: 16px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 20px;
  backdrop-filter: blur(10px);
  transition: transform 0.2s;
}

.asset-card.checked {
  border-left: 4px solid var(--secondary);
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

.track-btn {
  background: rgba(79, 70, 229, 0.15);
  color: var(--primary);
  border: 1px solid rgba(79, 70, 229, 0.3);
  padding: 6px 14px;
  font-size: 0.85rem;
  font-weight: 700;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;
}

.track-btn:active {
  background: var(--primary);
  color: white;
}

.status-icon.checked {
  color: var(--secondary);
}

.info-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.9rem;
  color: var(--text-muted);
  margin-top: 4px;
}

.user-row {
  justify-content: space-between;
  margin-top: 8px;
  margin-bottom: 4px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
}

.text-muted {
  opacity: 0.6;
}

.divider {
  margin: 0 4px;
  opacity: 0.3;
}

.memo-row {
  margin-top: 12px;
  padding-top: 8px;
  border-top: 1px solid rgba(255, 255, 255, 0.05);
}

.memo-input {
  background: transparent;
  border: none;
  color: white;
  font-size: 0.9rem;
  width: 100%;
  outline: none;
}

.card-shortcuts {
  display: flex;
  gap: 8px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid rgba(255, 255, 255, 0.05);
  width: 100%;
}

.shortcut-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px 4px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  font-size: 0.75rem;
  color: var(--text-muted);
  transition: all 0.2s ease;
  white-space: nowrap;
}

.cancel-undo-btn {
  flex: 0.5;
  color: #f87171 !important;
  border-color: rgba(248, 113, 113, 0.2) !important;
  background: rgba(248, 113, 113, 0.05) !important;
}

.shortcut-btn:hover {
  background: rgba(79, 70, 229, 0.1);
  color: var(--primary);
  border-color: var(--primary);
}
</style>

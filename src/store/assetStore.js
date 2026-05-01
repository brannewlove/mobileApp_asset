import { defineStore } from 'pinia'
import { googleApi } from '../api/google';

export const useAssetStore = defineStore('asset', {
    state: () => ({
        assets: JSON.parse(localStorage.getItem('cached_assets') || '[]'),
        users: JSON.parse(localStorage.getItem('cached_users') || '[]'),
        tradeLogs: JSON.parse(localStorage.getItem('cached_trade_logs') || '[]'),
        currentFile: JSON.parse(localStorage.getItem('current_session_file') || 'null'),
        loading: false,
        error: null,
        searchQuery: '',
        inspectionSearchQuery: '',
        referenceSearchQuery: '',
        selectedDepartment: '전체',
        isAuthenticated: !!localStorage.getItem('google_access_token'),
        masterFiles: [],
        sessionFiles: [],
        scannedAssetIds: JSON.parse(localStorage.getItem('cached_scanned_ids') || '[]'),
        lastMasterSync: localStorage.getItem('last_master_sync') || null,
        globalTradeLogs: JSON.parse(localStorage.getItem('cached_global_trade_logs') || '[]'),
        toast: { show: false, message: '', type: 'info' },
        isSyncing: false,
        lastSavedAt: null,
        saveTimeout: null,
        referenceLimit: 20,
        isOnline: true,
        hasPendingSync: JSON.parse(localStorage.getItem('has_pending_sync') || 'false'),
        tokenRefreshTimer: null,
        isSyncingMaster: false,
        pendingTradeLogs: JSON.parse(localStorage.getItem('pending_trade_logs') || '[]'),
        needsDailySync: false
    }),

    getters: {
        scannedAssets: (state) => {
            let result = state.scannedAssetIds
                .map(id => state.assets.find(a => a.assetNumber === id))
                .filter(Boolean)
                .reverse();

            if (state.inspectionSearchQuery) {
                const q = state.inspectionSearchQuery.toLowerCase();
                result = result.filter(a => a.assetNumber.toLowerCase().includes(q));
            }
            return result;
        },

        filteredAssets: (state) => {
            let result = state.assets;
            if (state.selectedDepartment !== '전체') {
                result = result.filter(a => a.department === state.selectedDepartment);
            }
            if (state.searchQuery) {
                const q = state.searchQuery.toLowerCase();
                result = result.filter(a =>
                    [a.assetNumber, a.userName, a.in_user, a.modelName, a.serial_number]
                        .some(v => (v || '').toLowerCase().includes(q))
                );
            }
            return result;
        },



        usersMap: (state) => {
            const map = new Map();
            state.users.forEach(u => {
                const cid = state._getVal(u, 'cj_id')?.toString();
                if (cid) map.set(cid, u);
            });
            return map;
        },

        filteredTradeLogs: (state) => {
            if (!state.globalTradeLogs || state.globalTradeLogs.length === 0) return [];

            const usersMap = state.usersMap;
            const q = state.referenceSearchQuery?.toLowerCase();

            // 1. Group and deduplicate in one pass
            const groups = {};
            state.globalTradeLogs.forEach(log => {
                const assetNo = log._assetNo || 'Unknown';
                const cjId = log._cjId || '';
                const date = log._dateStr || '0000-00-00';

                // Fast search check if query exists
                if (q) {
                    const match = [assetNo, cjId, date, log._note, log._exUserId]
                        .some(v => (v || '').toString().toLowerCase().includes(q));
                    if (!match) return;
                }

                if (!groups[assetNo]) groups[assetNo] = { assetNo, itemsMap: new Map(), lastUpdate: '0000-00-00' };

                const uniqueKey = `${date}_${cjId}`;
                if (!groups[assetNo].itemsMap.has(uniqueKey)) {
                    const exUserId = log._exUserId;
                    const user = usersMap.get(cjId);
                    const exUser = exUserId ? usersMap.get(exUserId) : null;

                    const processedLog = {
                        ...log,
                        _exUserName: exUser ? state._getVal(exUser, 'user_name') : (exUserId || ''),
                        _exUserPart: exUser ? state._getVal(exUser, 'department') : '',
                        _joinedName: user ? state._getVal(user, 'user_name') : (cjId || ''),
                        _joinedPart: user ? state._getVal(user, 'department') : ''
                    };
                    groups[assetNo].itemsMap.set(uniqueKey, processedLog);
                    if (date > groups[assetNo].lastUpdate) {
                        groups[assetNo].lastUpdate = date;
                    }
                }
            });

            // 2. Convert groups to array, sort by last update, slice, then sort inner logs
            return Object.values(groups)
                .sort((a, b) => b.lastUpdate.localeCompare(a.lastUpdate))
                .slice(0, state.referenceLimit)
                .map(group => ({
                    assetNo: group.assetNo,
                    lastUpdate: group.lastUpdate,
                    logs: Array.from(group.itemsMap.values()).sort((a, b) => a._dateStr.localeCompare(b._dateStr))
                }));
        },

        departments: (state) => ['전체', ...Array.from(new Set(state.assets.map(a => a.department).filter(Boolean))).sort()],

        userStats: (state) => {
            const stats = {};
            state.assets.forEach(a => {
                const uid = a.in_user || 'unknown';
                if (!stats[uid]) stats[uid] = { done: 0, total: 0 };
                stats[uid].total++;
                if (a.status === 'checked') stats[uid].done++;
            });
            return stats;
        },

        tradeLogGroupCount: (state) => {
            const q = state.referenceSearchQuery.toLowerCase();
            const groups = new Set();
            state.globalTradeLogs.forEach(log => {
                const assetNo = log._assetNo || 'Unknown';
                const cjId = log._cjId || '';
                const date = log._dateStr || '0000-00-00';

                // Fast search check if query exists
                if (q) {
                    const match = [assetNo, cjId, date, log._note, log._exUserId]
                        .some(v => (v || '').toString().toLowerCase().includes(q));
                    if (!match) return;
                }
                groups.add(assetNo);
            });
            return groups.size;
        },

        progress: (state) => {
            const total = state.assets.length;
            const done = state.assets.filter(a => a.status === 'checked').length;
            return { total, done, percent: total > 0 ? Math.round((done / total) * 100) : 0 };
        }
    },

    actions: {
        _getVal(obj, key) {
            if (!obj || typeof obj !== 'object') return null;
            if (!this._aliasCache) this._aliasCache = {};
            if (!this._aliasCache[key]) {
                const aliases = {
                    asset_number: ['assetnumber', '자산번호', '관리번호', 'assetno', 'no', '관리no'],
                    in_user: ['inuser', '사용자id', '사번', 'id', 'cjid', 'user_id', '인사번호', 'in_user'],
                    user_name: ['username', '사용자', '성함', '성명', '이름', 'name', 'user'],
                    department: ['department', '부서', '소속', 'part', '팀', '팀명', '부서명'],
                    model_name: ['modelname', '모델명', '모델', '품명', '자산명', '기종', '모델코드', 'model'],
                    serial_number: ['serialnumber', 'sn', 's/n', '시리얼', '제조번호', 'serial_number'],
                    category: ['category', '카테고리', '분류', '자산분류'],
                    state: ['state', '상태', '구분', '자산구분'],
                    status: ['status', '실사상태', '진행상태'],
                    inspection_time: ['inspectiontime', '실사시간', '점검시간', '시간'],
                    ex_user: ['ex_user', '이전에사용하던사람', '이전사용자', 'asset_in_user', 'prev_user'],
                    cj_id: ['cjid', '사번', 'id', 'cj_id', '사용자id', '사용자사번'],
                    date: ['date', '업무일자', '일자', '날짜', 'timestamp', '수정일', '변경일', '작업일자', '시간', '수정시간', '생성일'],
                    note: ['note', '메모', '비고', '사항']
                };
                this._aliasCache[key] = (aliases[key] || [key.toLowerCase()]).map(t => t.toLowerCase().replace(/[\s_]/g, ''));
            }

            const targetAliases = this._aliasCache[key];
            const keys = Object.keys(obj);
            const actualKey = keys.find(k => targetAliases.includes(k.toLowerCase().replace(/[\s_]/g, '')));
            return actualKey ? obj[actualKey] : null;
        },

        _normalizeLog(log) {
            return {
                ...log,
                _assetNo: this._getVal(log, 'asset_number') || 'Unknown',
                _cjId: this._getVal(log, 'cj_id')?.toString() || '',
                _dateStr: this._getVal(log, 'date') || '0000-00-00',
                _exUserId: this._getVal(log, 'ex_user')?.toString() || '',
                _note: this._getVal(log, 'note') || ''
            };
        },

        setAssets(data) {
            console.log(`[Store] setAssets called with ${data.length} records`);

            // 1. Parse raw data groups by normalized type
            const rawAssets = data.filter(item => {
                return (item._type === 'assets' || item._sheetName?.toLowerCase() === 'assets');
            });

            const rawUsers = data.filter(item => item._type === 'users' || item._sheetName?.toLowerCase() === 'users');
            const rawTrade = data.filter(item => item._type === 'trade' || item._sheetName?.toLowerCase() === 'trade');

            console.log(`[Store] Raw mapping: Assets=${rawAssets.length}, Users=${rawUsers.length}, Trade=${rawTrade.length}`);

            if (rawUsers.length > 0) this.users = rawUsers;
            if (rawTrade.length > 0) this.tradeLogs = rawTrade;

            // Create a user map for O(1) lookup during asset processing
            const usersByCjId = new Map();
            this.users.forEach(u => {
                const cid = this._getVal(u, 'cj_id')?.toString()?.trim();
                if (cid) usersByCjId.set(cid, u);
            });

            // 2. Join Assets with Users and DEDUPLICATE by assetNumber
            const seen = new Set();
            const processedAssets = [];

            rawAssets.forEach(asset => {
                const assetNo = this._getVal(asset, 'asset_number');
                const state = this._getVal(asset, 'state');

                if (!assetNo || seen.has(assetNo) || (state && state.toLowerCase() === 'termination')) return;
                seen.add(assetNo);

                const inUser = this._getVal(asset, 'in_user')?.toString()?.trim();
                const user = inUser ? usersByCjId.get(inUser) : null;

                processedAssets.push({
                    ...asset,
                    category: this._getVal(asset, 'category') || '',
                    modelName: this._getVal(asset, 'model_name') || this._getVal(asset, 'model') || '',
                    serial_number: this._getVal(asset, 'serial_number') || '',
                    asset_number: assetNo,
                    assetNumber: assetNo,
                    in_user: inUser,
                    userName: user ? this._getVal(user, 'user_name') : (this._getVal(asset, 'user_name') || inUser),
                    department: user ? this._getVal(user, 'department') : (this._getVal(asset, 'department') || ''),

                    status: (this._getVal(asset, 'status') || 'pending').toLowerCase(),
                    inspection_time: this._getVal(asset, 'inspection_time') || '',
                    note: this._getVal(asset, 'note') || '',
                    originalData: { ...asset }
                });
            });

            this.assets = processedAssets;
            console.log(`[Store] Final assets joined and deduped: ${this.assets.length}`);
        },

        async startSession(masterFile, sessionName) {
            this.loading = true;
            this.error = null;
            try {
                // 1. Fetch data from master first to get the asset list
                const masterData = await googleApi.fetchSheetData(masterFile.id);
                const assetsOnly = masterData.filter(item => {
                    if (item._type !== 'assets') return false;
                    const state = this._getVal(item, 'state');
                    return !(state && state.toLowerCase() === 'termination');
                });

                console.log(`[Store] startSession: Master data fetched. Assets found: ${assetsOnly.length}`);

                if (assetsOnly.length === 0) {
                    console.warn('[Store] Warning: No assets found in the selected master file.');
                    throw new Error('마스터 파일에서 자산 정보를 찾을 수 없습니다.');
                }

                // 2. Map assets to include user info (make session file self-contained)
                const processedAssets = assetsOnly.map(asset => {
                    const inUser = this._getVal(asset, 'in_user');
                    const user = this.users.find(u => {
                        const cid = this._getVal(u, 'cj_id');
                        return cid && inUser && cid.toString().trim() === inUser.toString().trim();
                    });

                    // _headers가 공유 참조일 수 있으므로 복사하여 수정
                    const newHeaders = asset._headers ? [...asset._headers] : [];
                    if (!newHeaders.includes('user_name')) newHeaders.push('user_name');
                    if (!newHeaders.includes('department')) newHeaders.push('department');

                    return {
                        ...asset,
                        _headers: newHeaders,
                        user_name: user ? this._getVal(user, 'user_name') : (this._getVal(asset, 'user_name') || ''),
                        department: user ? this._getVal(user, 'department') : (this._getVal(asset, 'department') || '')
                    };
                });

                // 3. Create the flat result file with processed assets
                const sessionFile = await googleApi.createSessionFile(masterFile.id, sessionName, processedAssets);

                // 4. Refresh session list and load the new session
                await this.refreshSessions();
                await this.loadProject(sessionFile);
            } catch (err) {
                this.handleAuthError(err);
            } finally {
                this.loading = false;
            }
        },

        _persistSession() {
            localStorage.setItem('cached_assets', JSON.stringify(this.assets));
            localStorage.setItem('cached_scanned_ids', JSON.stringify(this.scannedAssetIds));
            if (this.currentFile) {
                localStorage.setItem('current_session_file', JSON.stringify(this.currentFile));
            }
        },

        updateAsset(assetNumber, newData) {
            const index = this.assets.findIndex(a => a.assetNumber === assetNumber);
            if (index !== -1) {
                const oldData = { ...this.assets[index] };
                const now = new Date().toLocaleString(); // 표시용은 그대로 유지하되

                this.assets[index] = {
                    ...this.assets[index],
                    ...newData,
                    status: 'checked',
                    inspection_time: now,
                    _sort_time: Date.now() // 정렬용 타임스탬프 추가 (백업용)
                };
                this.hasPendingSync = true;
                localStorage.setItem('has_pending_sync', 'true');

                // ID 목록 처리: 이미 있다면 제거하고 맨 뒤에 추가 (최신화)
                this.scannedAssetIds = this.scannedAssetIds.filter(id => id !== assetNumber);
                this.scannedAssetIds.push(assetNumber);

                this._persistSession();
            }
        },

        cancelAssetCheck(assetNumber) {
            const index = this.assets.findIndex(a => a.assetNumber === assetNumber);
            if (index !== -1) {
                this.assets[index].status = 'pending';
                this.assets[index].inspection_time = null;

                // Remove from scanned IDs
                this.scannedAssetIds = this.scannedAssetIds.filter(id => id !== assetNumber);



                this.hasPendingSync = true;
                localStorage.setItem('has_pending_sync', 'true');
                this._persistSession();
                this.triggerDebouncedSave();
                this.showToast('실사 취소가 완료되었습니다.', 'success');
            }
        },

        updateAssetNote(assetNumber, note) {
            const index = this.assets.findIndex(a => a.assetNumber === assetNumber);
            if (index !== -1) {
                this.assets[index].note = note;
                this.hasPendingSync = true;
                localStorage.setItem('has_pending_sync', 'true');
                this._persistSession();
                this.triggerDebouncedSave();
            }
        },

        signOutAccount() {
            localStorage.clear();
            this.assets = [];
            this.users = [];
            this.tradeLogs = [];
            this.isAuthenticated = false;
            this.currentFile = null;
            this.scannedAssetIds = [];
            this.globalTradeLogs = [];
            this.pendingTradeLogs = [];
            this.needsDailySync = false;
        },

        clearScannedList() {
            this.scannedAssetIds = [];
            this._persistSession();
        },

        async loginWithGoogle() {
            this.loading = true;
            this.error = null;
            try {
                const user = await googleApi.signInWithGoogle();
                const token = user.authentication.accessToken;

                if (token) {
                    await this.initializeData(token);
                    this.isAuthenticated = true;
                    console.log('[Store] Login success, UI should update.');
                } else {
                    throw new Error('토큰을 발급받지 못했습니다.');
                }
            } catch (err) {
                console.error('Login Failed:', err);
                this.error = '구글 로그인에 실패했습니다.';
                this.showToast('로그인 실패: ' + err.message, 'error');
            } finally {
                this.loading = false;
            }
        },

        async initializeData(token) {
            this.loading = true;
            this.error = null;
            try {
                if (!token) throw new Error('토큰을 입력해주세요.');
                googleApi.setToken(token);

                this.isAuthenticated = true;
                // 토큰 갱신 타이머 불필요 (동기화 시에만 토큰 사용)

                // 0. Normalize existing cached logs if any
                this.normalizeLocalLogs();

                // 로컬 캐시에서만 데이터 로드 (API 호출 없음)
                this._loadFromLocalCache();

                // 하루 1회 동기화 필요 여부 체크 (자동 실행하지 않음)
                this._checkDailySyncNeeded();

                // 2. 앱 최초 설치 / 데이터가 전혀 없는 경우 초기 동기화 자동 실행
                if (this.masterFiles.length === 0) {
                    console.log('[Store] No cached data found. Starting initial sync...');
                    await this.performDailySync();
                }

                console.log(`[Store] Initialized from local cache. Masters=${this.masterFiles.length}, Sessions=${this.sessionFiles.length}, Assets=${this.assets.length}`);
            } catch (err) {
                this.handleAuthError(err);
                this.isAuthenticated = false;
            } finally {
                this.loading = false;
            }
        },
        async refreshMasters() {
            this.masterFiles = await googleApi.listMasterSheets();
        },
        async refreshSessions() {
            const files = await googleApi.listSessionSheets();
            // Filter out the global trade log file from the selection list
            this.sessionFiles = files.filter(f => f.name !== googleApi.TRADE_LOG_FILE_NAME);
        },
        async loadProject(file) {
            this.error = null;
            this.loading = true;
            try {
                this.currentFile = file;
                localStorage.setItem('current_session_file', JSON.stringify(file));

                this.showToast('회차 데이터를 가져오는 중입니다...', 'info');
                try {
                    // 구글 드라이브에서 회차 파일 데이터 다운로드
                    const sessionData = await googleApi.fetchSheetData(file.id);
                    const sessionAssets = sessionData.filter(item => item._type === 'assets' || item._sheetName === 'Sheet1' || item._sheetName === 'assets');
                    
                    if (sessionAssets.length > 0) {
                        if (this.assets.length === 0) {
                            // 로컬 캐시가 완전히 비어있다면 세션 데이터를 기반으로 초기화
                            this.setAssets(sessionData);
                        } else {
                            // 기존 로컬 캐시(마스터 기준)에 세션의 실사 데이터 병합
                            const sessionMap = new Map();
                            sessionAssets.forEach(a => {
                                const assetNo = this._getVal(a, 'asset_number');
                                if (assetNo) sessionMap.set(assetNo, a);
                            });

                            this.assets = this.assets.map(asset => {
                                const assetNo = asset.assetNumber || this._getVal(asset, 'asset_number');
                                const saved = sessionMap.get(assetNo);
                                if (saved) {
                                    return {
                                        ...asset,
                                        status: (this._getVal(saved, 'status') || 'pending').toLowerCase(),
                                        inspection_time: this._getVal(saved, 'inspection_time') || '',
                                        note: this._getVal(saved, 'note') || ''
                                    };
                                }
                                return asset;
                            });
                        }

                        // 스캔된 ID 목록 복구
                        this.scannedAssetIds = this.assets
                            .filter(a => a.status === 'checked' && a.assetNumber)
                            .map(a => a.assetNumber);

                        this._persistSession();
                        this.showToast('회차 데이터를 성공적으로 불러왔습니다.', 'success');
                    } else {
                        this.showToast('선택한 회차에 데이터가 없습니다.', 'warning');
                    }
                } catch (fetchErr) {
                    console.error('[Store] Failed to fetch session data:', fetchErr);
                    this.showToast('회차 데이터를 가져오지 못했습니다. 로컬 캐시를 사용합니다.', 'warning');
                }

                this.needsDailySync = false;
            } catch (err) {
                this.handleAuthError(err);
            } finally {
                this.loading = false;
            }
        },

        async refreshMasterMetadata() {
            try {
                // 0. 현재 회차의 변경사항을 먼저 저장
                if (this.currentFile && this.hasPendingSync) {
                    await this.saveDataInBackground();
                }

                const latestMaster = await googleApi.getLatestSheet();
                if (!latestMaster) return;

                const masterData = await googleApi.fetchSheetData(latestMaster.id);
                const rawUsers = masterData.filter(item => item._type === 'users');
                const rawTrade = masterData.filter(item => item._type === 'trade');
                const rawAssets = masterData.filter(item => item._type === 'assets');

                this.showToast(`마스터 읽기 성공: 사용자 ${rawUsers.length}건, 자산 ${rawAssets.length}건, 이력 ${rawTrade.length}건`, 'info');

                if (rawUsers.length > 0) this.users = rawUsers;
                // 마스터의 tradeLogs는 전역 로그 파일 에 반영하는 용도로만 사용
                if (rawTrade.length > 0) {
                    console.log(`[Store] Syncing ${rawTrade.length} master trade logs to global file...`);
                    this.showToast('이력 동기화 시작...', 'info');
                    await googleApi.syncMasterTradeToGlobal(rawTrade);
                }

                // 로컬 스토리지에 캐시 저장
                localStorage.setItem('cached_users', JSON.stringify(this.users));
                this.tradeLogs = [];

                // 2. 전역 저장 파일만 읽어서 UI에 표시
                await this.refreshGlobalTradeLogs();

                // 3. 현재 회차(assets)에 마스터의 최신 정보 반영 및 신규 자산 추가
                if (rawAssets.length > 0) {
                    const currentAssetsMap = new Map();
                    this.assets.forEach(a => {
                        const assetNo = a.assetNumber || this._getVal(a, 'asset_number');
                        if (assetNo) currentAssetsMap.set(assetNo, a);
                    });

                    rawAssets.forEach(masterAsset => {
                        const assetNo = this._getVal(masterAsset, 'asset_number');
                        const state = this._getVal(masterAsset, 'state');
                        if (!assetNo) return;

                        // 마스터에서 폐기(termination) 상태인 경우 현재 목록에서 제거
                        if (state && state.toLowerCase() === 'termination') {
                            if (currentAssetsMap.has(assetNo)) {
                                console.log(`[Sync] Removing terminated asset from session: ${assetNo}`);
                                currentAssetsMap.delete(assetNo);
                            }
                            return;
                        }

                        const inUser = this._getVal(masterAsset, 'in_user');
                        const user = this.users.find(u => {
                            const cid = this._getVal(u, 'cj_id');
                            return cid && inUser && cid.toString().trim() === inUser.toString().trim();
                        });

                        const assetUpdates = {
                            category: this._getVal(masterAsset, 'category') || '',
                            modelName: this._getVal(masterAsset, 'model_name') || this._getVal(masterAsset, 'model') || '',
                            serial_number: this._getVal(masterAsset, 'serial_number') || '',
                            in_user: inUser,
                            userName: user ? this._getVal(user, 'user_name') : (this._getVal(masterAsset, 'user_name') || inUser),
                            department: user ? this._getVal(user, 'department') : (this._getVal(masterAsset, 'department') || ''),
                        };

                        if (currentAssetsMap.has(assetNo)) {
                            // 기존 자산 업데이트 (실사 상태/메모는 유지하고 메타데이터만 갱신)
                            const existing = currentAssetsMap.get(assetNo);
                            currentAssetsMap.set(assetNo, {
                                ...existing,
                                ...assetUpdates,
                                originalData: { ...existing.originalData, ...masterAsset }
                            });
                        } else {
                            // 신규 자산 추가
                            currentAssetsMap.set(assetNo, {
                                ...masterAsset,
                                ...assetUpdates,
                                assetNumber: assetNo,
                                status: 'pending',
                                inspection_time: '',
                                note: '',
                                originalData: { ...masterAsset }
                            });
                        }
                    });

                    // 업데이트된 맵 정보를 다시 배열로 변환
                    this.assets = Array.from(currentAssetsMap.values());

                    // 변경된 내용을 회차 파일에도 즉시 저장
                    this.hasPendingSync = true;
                    await this.saveDataInBackground();
                }

                this.showToast('전체 데이터 동기화가 완료되었습니다.', 'success');

                // Update sync timestamp
                const now = new Date().toISOString();
                this.lastMasterSync = now;
                localStorage.setItem('last_master_sync', now);

                this._persistSession();
                console.log('Master and Global metadata refreshed successfully');
            } catch (err) {
                console.warn('Failed to refresh master metadata:', err);
                this.showToast('동기화 중 오류 발생: ' + err.message, 'error');
            }
        },

        async refreshGlobalTradeLogs() {
            try {
                console.log('[Store] refreshGlobalTradeLogs called');
                const logs = await googleApi.fetchGlobalTradeLogs();
                console.log(`[Store] Logs received: ${logs.length}`);
                if (logs && logs.length > 0) {
                    // Normalize all logs once upon receipt
                    this.globalTradeLogs = logs.map(log => this._normalizeLog(log));
                    localStorage.setItem('cached_global_trade_logs', JSON.stringify(this.globalTradeLogs));
                    console.log('[Store] Global trade logs updated and normalized');
                } else {
                    console.warn('[Store] No logs returned from API');
                }
            } catch (err) {
                console.error('[Store] Failed to fetch global trade logs:', err);
            }
        },

        normalizeLocalLogs() {
            if (this.globalTradeLogs && this.globalTradeLogs.length > 0 && !this.globalTradeLogs[0]._assetNo) {
                console.log('[Store] Normalizing legacy cached logs...');
                this.globalTradeLogs = this.globalTradeLogs.map(log => this._normalizeLog(log));
            }
        },

        async logAssetChange(assetNumber, newCjId, exUserCjId, note = '') {
            try {
                const logData = {
                    date: new Date().toISOString().split('T')[0],
                    asset_number: assetNumber,
                    cj_id: newCjId,
                    ex_user: exUserCjId,
                    note: note
                };

                // 로컬 큐에만 저장 (구글 API 전송은 performDailySync에서 일괄 수행)
                this.pendingTradeLogs.push(logData);
                localStorage.setItem('pending_trade_logs', JSON.stringify(this.pendingTradeLogs));

                // 로컬 상태에도 추가 (UI 즉시 반영)
                const normalized = this._normalizeLog({
                    ...logData,
                    _type: 'trade',
                    _sheetName: 'Global_Trade'
                });
                this.globalTradeLogs.push(normalized);
                localStorage.setItem('cached_global_trade_logs', JSON.stringify(this.globalTradeLogs));

                console.log(`[Store] Trade log queued locally for ${assetNumber} (pending: ${this.pendingTradeLogs.length})`);
            } catch (err) {
                console.error('[Store] Failed to queue asset change:', err);
                this.showToast('변경 이력 저장 실패', 'error');
            }
        },


        /**
         * 지연 저장 트리거: 3초 동안 추가 입력이 없으면 로컬에 저장합니다.
         */
        triggerDebouncedSave() {
            if (this.saveTimeout) {
                clearTimeout(this.saveTimeout);
            }
            this.saveTimeout = setTimeout(() => {
                this.saveDataInBackground();
            }, 3000); // 3초 대기
        },

        /**
         * 백그라운드 저장: 로컬 스토리지에만 저장합니다. (구글 API 호출 없음)
         * 구글 시트 업로드는 performDailySync()에서 일괄 수행합니다.
         */
        async saveDataInBackground() {
            if (!this.currentFile) return;

            this.hasPendingSync = true;
            localStorage.setItem('has_pending_sync', 'true');
            this._persistSession();
            this.lastSavedAt = new Date().toLocaleTimeString();
            console.log('[Sync] Saved locally at', this.lastSavedAt);
        },

        async saveData() {
            // 수동 저장이나 즉시 저장이 필요한 경우 사용
            await this.saveDataInBackground();
        },

        async backupAndSave() {
            if (!this.currentFile) return;
            this.loading = true;
            try {
                // 1. Update current sheet
                const sessionAssets = this.assets.filter(a => a._type === 'assets');
                await googleApi.updateSheet(this.currentFile.id, sessionAssets);
                // 2. Create backup
                await googleApi.createBackup(this.currentFile.id, this.currentFile.name);
                console.log('Backup created and sheet updated');
            } catch (err) {
                this.handleAuthError(err);
            } finally {
                this.loading = false;
            }
        },

        updateTradeLog(tradeLog, newMemo) {
            // 마스터 시트 수정 대신 로컬 메모 테이블(객체)에 저장
            const memoKey = `${tradeLog._sheetName}_${tradeLog._rowIndex}`;
            this.tradeMemos[memoKey] = newMemo;

            localStorage.setItem('cached_trade_memos', JSON.stringify(this.tradeMemos));
            console.log(`[Store] Trade memo saved locally for ${memoKey}`);

            // UI 업데이트를 위해 tradeLogs 상태를 갱신하지 않아도 getter에서 tradeMemos를 참조하므로 즉시 반영됨
        },

        showToast(message, type = 'info') {
            this.toast = { show: true, message, type };
            if (this.toastTimeout) clearTimeout(this.toastTimeout);
            this.toastTimeout = setTimeout(() => {
                this.toast.show = false;
            }, 3000);
        },

        async handleAuthError(err) {
            this.error = err.message;
            if (err.message.includes('[AUTH_EXPIRED]') || err.message.includes('[AUTH_REQUIRED]')) {
                console.warn('[Store] Authentication failed or expired.');
                this.isAuthenticated = false;
                localStorage.removeItem('google_access_token');
                this.showToast('인증이 만료되어 다시 로그인이 필요합니다.', 'error');
            } else {
                console.error('API Error:', err);
                this.showToast('오류 발생: ' + err.message, 'error');
            }
        },

        /**
         * 로컬 캐시에서 파일 목록 및 데이터 로드 (API 호출 없음)
         */
        _loadFromLocalCache() {
            // 마스터/세션 파일 목록은 localStorage에서 복원
            const cachedMasters = localStorage.getItem('cached_master_files');
            const cachedSessions = localStorage.getItem('cached_session_files');
            if (cachedMasters) {
                try { this.masterFiles = JSON.parse(cachedMasters); } catch(e) { this.masterFiles = []; }
            }
            if (cachedSessions) {
                try { this.sessionFiles = JSON.parse(cachedSessions); } catch(e) { this.sessionFiles = []; }
            }
            // assets, users, tradeLogs, globalTradeLogs는 이미 state 초기화에서 localStorage로부터 로드됨
            console.log('[Store] Loaded from local cache.');
        },

        /**
         * 하루 1회 동기화 필요 여부 체크 (자동 실행하지 않음, UI 알림만)
         */
        _checkDailySyncNeeded() {
            const now = new Date();
            const lastSync = this.lastMasterSync ? new Date(this.lastMasterSync) : null;
            const isDifferentDay = !lastSync || now.toDateString() !== lastSync.toDateString();
            const ONE_DAY_MS = 24 * 60 * 60 * 1000;

            if (!lastSync || isDifferentDay || (now - lastSync > ONE_DAY_MS)) {
                this.needsDailySync = true;
                const reason = !lastSync ? '동기화 기록 없음' : isDifferentDay ? '날짜 변경' : '24시간 경과';
                console.log(`[Store] Daily sync needed: ${reason}`);
                this.showToast(`동기화 권장: ${reason}. 설정에서 동기화를 실행하세요.`, 'info');
            } else {
                this.needsDailySync = false;
                console.log(`[Store] Daily sync not needed. Last: ${lastSync.toLocaleString()}`);
            }
        },

        /**
         * 하루 1회 전체 동기화 (이 함수에서만 구글 API 접속)
         * 1. 토큰 갱신
         * 2. 로컬 pending 이력 업로드
         * 3. 현재 세션 저장
         * 4. 마스터 데이터 다운로드
         * 5. 전역 이력 다운로드
         * 6. 파일 목록 갱신
         */
        async performDailySync() {
            if (this.isSyncingMaster) {
                this.showToast('이미 동기화가 진행 중입니다.', 'info');
                return;
            }

            this.isSyncingMaster = true;
            this.loading = true;
            this.error = null;

            try {
                // 1. 토큰 갱신 (동기화 시에만 인증 시도)
                this.showToast('토큰 갱신 중...', 'info');
                const newToken = await googleApi.refreshAccessToken();
                if (newToken) {
                    localStorage.setItem('google_access_token', newToken);
                }

                // 2. 로컬 pending 이력 업로드
                if (this.pendingTradeLogs.length > 0) {
                    this.showToast(`대기 중인 이력 ${this.pendingTradeLogs.length}건 업로드 중...`, 'info');
                    await this._uploadPendingTradeLogs();
                }

                // 3. 현재 세션 변경사항 업로드
                if (this.currentFile && this.hasPendingSync) {
                    this.showToast('세션 데이터 업로드 중...', 'info');
                    const sessionAssets = this.assets.filter(a => a._type === 'assets');
                    await googleApi.updateSheet(this.currentFile.id, sessionAssets);
                    this.hasPendingSync = false;
                    localStorage.setItem('has_pending_sync', 'false');
                    console.log('[DailySync] Session data uploaded.');
                }

                // 4. 마스터 데이터 동기화 (refreshMasterMetadata 재사용)
                this.showToast('마스터 데이터 동기화 중...', 'info');
                await this.refreshMasterMetadata();

                // 5. 파일 목록 갱신 및 캐시 저장
                await this.refreshMasters();
                await this.refreshSessions();
                localStorage.setItem('cached_master_files', JSON.stringify(this.masterFiles));
                localStorage.setItem('cached_session_files', JSON.stringify(this.sessionFiles));

                // 6. 최신 토큰 저장
                const latestToken = googleApi.accessToken;
                if (latestToken) {
                    localStorage.setItem('google_access_token', latestToken);
                }

                // 7. 동기화 완료
                this.needsDailySync = false;
                this.showToast('전체 동기화가 완료되었습니다!', 'success');
                console.log('[DailySync] Full sync completed successfully.');

            } catch (err) {
                console.error('[DailySync] Failed:', err);
                this.handleAuthError(err);
            } finally {
                this.isSyncingMaster = false;
                this.loading = false;
            }
        },

        /**
         * 로컬에 쌓인 대기 중인 이력 로그를 구글 시트에 일괄 업로드
         */
        async _uploadPendingTradeLogs() {
            if (this.pendingTradeLogs.length === 0) return;

            try {
                for (const log of this.pendingTradeLogs) {
                    await googleApi.appendGlobalTradeLog(log);
                }
                console.log(`[DailySync] Uploaded ${this.pendingTradeLogs.length} pending trade logs.`);
                // 업로드 성공 시 큐 비우기
                this.pendingTradeLogs = [];
                localStorage.setItem('pending_trade_logs', '[]');
            } catch (err) {
                console.error('[DailySync] Failed to upload pending trade logs:', err);
                this.showToast('이력 업로드 중 오류 발생', 'error');
                throw err; // 상위에서 처리
            }
        },

        /**
         * 토큰 갱신 타이머 중지 (더 이상 사용하지 않지만 호환성 유지)
         */
        stopTokenRefreshTimer() {
            if (this.tokenRefreshTimer) {
                clearInterval(this.tokenRefreshTimer);
                this.tokenRefreshTimer = null;
                console.log('[Store] Token refresh timer stopped.');
            }
        }
    }
})

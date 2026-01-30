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
        hasPendingSync: JSON.parse(localStorage.getItem('has_pending_sync') || 'false')
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



        filteredTradeLogs: (state) => {
            const getVal = (obj, key) => {
                const aliases = {
                    cj_id: ['cjid', '사번', 'id', 'cj_id'],
                    asset_number: ['assetnumber', '자산번호', '관리번호', 'assetno', 'no', '관리no'],
                    date: ['date', '업무일자', '일자', '날짜', 'timestamp'],
                    ex_user: ['ex_user', '이전사용자', 'asset_in_user', 'prev_user'],
                    user_name: ['username', '사용자', '성함', '성명', '이름', 'name', 'user'],
                    department: ['department', '부서', '소속', 'part', '팀', '팀명', '부서명']
                };
                const targets = (aliases[key] || [key]).map(t => t.toLowerCase().replace(/[\s_]/g, ''));
                const foundKey = Object.keys(obj).find(k => targets.includes(k.toLowerCase().replace(/[\s_]/g, '')));
                return foundKey ? obj[foundKey] : null;
            };

            let rawLogs = [...state.globalTradeLogs];

            // Deduplicate logs: key = date + assetNo + cjId
            const uniqueLogsMap = new Map();
            rawLogs.forEach(log => {
                const assetNo = getVal(log, 'asset_number') || 'Unknown';
                const cjId = getVal(log, 'cj_id') || '';
                const date = getVal(log, 'date') || '0000-00-00';
                const key = `${date}_${assetNo}_${cjId}`;
                if (!uniqueLogsMap.has(key)) {
                    uniqueLogsMap.set(key, log);
                }
            });
            let logs = Array.from(uniqueLogsMap.values());

            if (state.referenceSearchQuery) {
                const q = state.referenceSearchQuery.toLowerCase();
                logs = logs.filter(log => Object.values(log).some(v => (v || '').toString().toLowerCase().includes(q)));
            }

            const groups = {};
            logs.forEach(log => {
                const assetNo = getVal(log, 'asset_number') || 'Unknown';
                const cjId = getVal(log, 'cj_id');
                const exUserId = getVal(log, 'ex_user');
                const user = state.users.find(u => getVal(u, 'cj_id')?.toString() === cjId?.toString());
                const exUser = state.users.find(u => getVal(u, 'cj_id')?.toString() === exUserId?.toString());

                if (!groups[assetNo]) groups[assetNo] = [];
                groups[assetNo].push({
                    ...log,
                    _assetNo: assetNo,
                    _dateStr: getVal(log, 'date') || '0000-00-00',
                    _exUserName: exUser ? getVal(exUser, 'user_name') : (exUserId || ''),
                    _exUserPart: exUser ? getVal(exUser, 'department') : '',
                    _joinedName: user ? getVal(user, 'user_name') : (cjId || ''),
                    _joinedPart: user ? getVal(user, 'department') : ''
                });
            });

            return Object.entries(groups).map(([assetNo, items]) => {
                const sorted = items.sort((a, b) => a._dateStr.localeCompare(b._dateStr));
                return { assetNo, logs: sorted, lastUpdate: sorted[sorted.length - 1]._dateStr };
            }).sort((a, b) => b.lastUpdate.localeCompare(a.lastUpdate)).slice(0, state.referenceLimit);
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

        progress: (state) => {
            const total = state.assets.length;
            const done = state.assets.filter(a => a.status === 'checked').length;
            return { total, done, percent: total > 0 ? Math.round((done / total) * 100) : 0 };
        }
    },

    actions: {
        _getVal(obj, key) {
            if (!obj || typeof obj !== 'object') return null;
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
                cj_id: ['cjid', '사번', 'id', 'cj_id'],
                date: ['date', '업무일자', '일자', '날짜', 'timestamp'],
                note: ['note', '메모', '비고', '사항']
            };
            const targetAliases = (aliases[key] || [key.toLowerCase()]).map(t => t.toLowerCase().replace(/[\s_]/g, ''));
            const keys = Object.keys(obj);
            const actualKey = keys.find(k => {
                const normalized = k.toLowerCase().replace(/[\s_]/g, '');
                return targetAliases.includes(normalized);
            });
            return actualKey ? obj[actualKey] : null;
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

            // 2. Join Assets with Users and DEDUPLICATE by assetNumber
            const seen = new Set();
            this.assets = [];

            rawAssets.forEach(asset => {
                const assetNo = this._getVal(asset, 'asset_number');
                const state = this._getVal(asset, 'state');

                if (!assetNo || seen.has(assetNo) || (state && state.toLowerCase() === 'termination')) return;
                seen.add(assetNo);

                const inUser = this._getVal(asset, 'in_user');
                const user = this.users.find(u => {
                    const cid = this._getVal(u, 'cj_id');
                    return cid && inUser && cid.toString().trim() === inUser.toString().trim();
                });

                this.assets.push({
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

                // 2. Create the flat result file
                const sessionFile = await googleApi.createSessionFile(masterFile.id, sessionName, assetsOnly);

                // 3. Refresh session list and load the new session
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

                // Fetch both lists and global logs in parallel
                await Promise.all([
                    this.refreshMasters(),
                    this.refreshSessions(),
                    this.refreshGlobalTradeLogs()
                ]);
                this.isAuthenticated = true;

                // Save token for persistence
                localStorage.setItem('google_access_token', token);
                console.log(`Found ${this.masterFiles.length} masters and ${this.sessionFiles.length} existing sessions`);
            } catch (err) {
                this.handleAuthError(err);
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
            try {
                this.currentFile = file;
                localStorage.setItem('current_session_file', JSON.stringify(file));

                // 1. 만약 현재 선택한 파일이 로컬 캐시와 같다면, 통신 없이 즉시 UI 보여주기
                if (this.assets.length > 0) {
                    this.loading = false; // 화면 프리징 방지
                } else {
                    this.loading = true; // 캐시가 아예 없는 경우만 로딩 표시
                }

                // 2. 배경에서 최신 데이터 가져오기 (Background Sync)
                googleApi.fetchSheetData(file.id).then(sessionData => {
                    this.setAssets(sessionData);
                    // 더 이상 모든 'checked' 자산을 scannedAssetIds에 자동으로 넣지 않습니다.
                    // (작업 중인 '실사 목록'에만 집중하기 위함)
                    this._persistSession();
                    console.log(`[Sync] Session data updated from Google for: ${file.name}`);
                }).catch(err => {
                    console.error('[Sync] Failed to background update session:', err);
                });

                // 3. 마스터 데이터(인사정보) 동기화 체크 (배경 작업)
                this.checkAndSyncMaster();

                // 4. 전역 이력 데이터 즉시 로드 시도
                this.refreshGlobalTradeLogs();

            } catch (err) {
                this.handleAuthError(err);
            } finally {
                // Background fetch이므로 finally에서 loading을 끄지 않고 즉시 끕니다.
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
                        if (!assetNo || (state && state.toLowerCase() === 'termination')) return;

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
                    this.globalTradeLogs = logs;
                    localStorage.setItem('cached_global_trade_logs', JSON.stringify(this.globalTradeLogs));
                    console.log('[Store] Global trade logs updated in state and storage');
                } else {
                    console.warn('[Store] No logs returned from API');
                }
            } catch (err) {
                console.error('[Store] Failed to fetch global trade logs:', err);
            }
        },

        async logAssetChange(assetNumber, newCjId, exUserCjId, note = '') {
            try {
                const log = {
                    date: new Date().toISOString().split('T')[0],
                    asset_number: assetNumber,
                    cj_id: newCjId,
                    ex_user: exUserCjId,
                    note: note
                };

                // 1. 서버에 즉시 전송 시도
                await googleApi.appendGlobalTradeLog(log);

                // 2. 성공하면 로컬 상태에도 추가 (새로고침 전에도 보이게)
                this.globalTradeLogs.push({
                    ...log,
                    _type: 'trade',
                    _sheetName: 'Global_Trade'
                });
                localStorage.setItem('cached_global_trade_logs', JSON.stringify(this.globalTradeLogs));

                console.log(`[Store] Global trade log appended for ${assetNumber}`);
            } catch (err) {
                console.error('[Store] Failed to log asset change globally:', err);
                this.showToast('변경 이력 기록 실패', 'error');
            }
        },

        async checkAndSyncMaster() {
            if (!this.isAuthenticated) return;

            const now = new Date();
            const lastSync = this.lastMasterSync ? new Date(this.lastMasterSync) : new Date(0);
            const needsInitial = this.users.length === 0;

            // Create a threshold for today's 06:00 AM
            const threshold = new Date();
            threshold.setHours(6, 0, 0, 0);

            // If current time is before 06:00 AM today, the 'today's sync window' hasn't opened yet.
            // We should check against YESTERDAY'S 06:00 AM.
            if (now < threshold) {
                threshold.setDate(threshold.getDate() - 1);
            }

            // If last sync was before the relevant 06:00 AM threshold, or we have no user data, refresh.
            if (needsInitial || lastSync < threshold) {
                console.log(`Master sync ${needsInitial ? 'initial' : 'required'} (Last: ${lastSync.toLocaleString()}, Threshold: ${threshold.toLocaleString()})`);
                await this.refreshMasterMetadata();
            } else {
                console.log(`Master sync not needed (Updated at ${lastSync.toLocaleString()})`);
            }
        },

        /**
         * 지연 저장 트리거: 3초 동안 추가 입력이 없으면 구글 시트에 저장합니다.
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
         * 백그라운드 저장: UI 로딩(this.loading)을 건드리지 않고 조용히 저장합니다.
         */
        async saveDataInBackground() {
            if (!this.currentFile || this.isSyncing) return;

            if (!this.isOnline) {
                console.log('[Sync] Offline: Change queued for later sync');
                return;
            }

            this.isSyncing = true;
            try {
                const sessionAssets = this.assets.filter(a => a._type === 'assets');
                await googleApi.updateSheet(this.currentFile.id, sessionAssets);
                this.lastSavedAt = new Date().toLocaleTimeString();
                this.hasPendingSync = false;
                localStorage.setItem('has_pending_sync', 'false');
                console.log('[Sync] Background save completed at', this.lastSavedAt);
            } catch (err) {
                console.error('[Sync] Background save failed:', err);
                if (err.message.includes('fetch') || err.message.includes('network')) {
                    this.isOnline = false;
                }
            } finally {
                this.isSyncing = false;
                this.saveTimeout = null;
            }
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
            if (err.message.includes('[AUTH_EXPIRED]')) {
                console.warn('[Store] Auth expired, attempting silent recovery...');
                try {
                    // 1. Silent refresh 시도
                    await googleApi.refreshAccessToken();
                    // 2. 성공 시 에러 초기화 (UI는 중단 없이 유지됨)
                    this.error = null;
                    this.showToast('연결이 재설정되었습니다. (자동 갱신)', 'info');
                    console.log('[Store] Silent recovery success.');
                    return; // 성공했으므로 로그아웃 로직 건너뜀
                } catch (retryErr) {
                    console.error('[Store] Silent recovery failed:', retryErr);
                    // 갱신 실패 시에만 실제 로그아웃 처리
                    this.isAuthenticated = false;
                    localStorage.removeItem('google_access_token');
                    this.showToast('인증이 만료되어 다시 로그인이 필요합니다.', 'error');
                }
            } else {
                console.error('API Error:', err);
                this.showToast('오류 발생: ' + err.message, 'error');
            }
        }
    }
})

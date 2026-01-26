import { defineStore } from 'pinia'
import { googleApi } from '../api/google';

export const useAssetStore = defineStore('asset', {
    state: () => ({
        assets: [], // Records from 'assets' sheet
        users: [], // Records from 'users' sheet
        tradeLogs: [], // Records from 'trade' sheet
        currentFile: null,
        inspectionLogs: [],
        loading: false,
        error: null,
        searchQuery: '',
        selectedDepartment: '전체',
        isAuthenticated: false,
        masterFiles: [], // Sources from Master folder
        sessionFiles: [], // Existing surveys from Backup folder
        scannedAssetIds: [],
        lastMasterSync: localStorage.getItem('last_master_sync') || null
    }),

    getters: {
        scannedAssets: (state) => {
            // Return only assets scanned in this session
            return state.assets.filter(a => state.scannedAssetIds.includes(a.asset_number));
        },

        filteredAssets: (state) => {
            let result = state.assets;

            if (state.selectedDepartment !== '전체') {
                result = result.filter(a => a.department === state.selectedDepartment);
            }

            if (state.searchQuery) {
                const q = state.searchQuery.toLowerCase();
                result = result.filter(a =>
                    (a.asset_number || '').toLowerCase().includes(q) ||
                    (a.userName || '').toLowerCase().includes(q) ||
                    (a.in_user || '').toLowerCase().includes(q) ||
                    (a.model_name || '').toLowerCase().includes(q) ||
                    (a.serial_number || '').toLowerCase().includes(q)
                );
            }
            return result;
        },

        departments: (state) => {
            const deps = new Set(state.assets.map(a => a.department).filter(Boolean));
            return ['전체', ...Array.from(deps).sort()];
        },

        userStats: (state) => {
            const stats = {};
            state.assets.forEach(a => {
                const userId = a.in_user || 'unknown';
                if (!stats[userId]) {
                    stats[userId] = { done: 0, total: 0 };
                }
                stats[userId].total++;
                if (a.status === 'checked') {
                    stats[userId].done++;
                }
            });
            return stats;
        },

        progress: (state) => {
            const total = state.assets.length;
            const done = state.assets.filter(a => a.status === 'checked').length;
            return {
                total,
                done,
                percent: total > 0 ? Math.round((done / total) * 100) : 0
            };
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
                cj_id: ['cjid', '사번', 'id', 'cj_id'],
                note: ['note', '메모', '비고', '사항']
            };
            const targetAliases = aliases[key] || [key.toLowerCase()];
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

                    status: this._getVal(asset, 'status') || 'pending',
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

        updateAsset(assetNumber, newData) {
            const index = this.assets.findIndex(a => a.assetNumber === assetNumber);
            if (index !== -1) {
                const oldData = { ...this.assets[index] };
                const now = new Date().toLocaleString();

                this.assets[index] = {
                    ...this.assets[index],
                    ...newData,
                    status: 'checked',
                    inspection_time: now
                };

                if (!this.scannedAssetIds.includes(assetNumber)) {
                    this.scannedAssetIds.push(assetNumber);
                }

                if (oldData.userName !== this.assets[index].userName || oldData.department !== this.assets[index].department) {
                    this.inspectionLogs.push({
                        assetNumber,
                        type: 'movement',
                        timestamp: new Date().toISOString(),
                        from: { user: oldData.userName, dept: oldData.department },
                        to: { user: this.assets[index].userName, dept: this.assets[index].department }
                    });
                }
            }
        },

        updateAssetNote(assetNumber, note) {
            const index = this.assets.findIndex(a => a.assetNumber === assetNumber);
            if (index !== -1) {
                this.assets[index].note = note;
                // If the asset was checked, update the timestamp or just keep it.
                // Usually memo update doesn't trigger "checked" status if not scanned, 
                // but since it's on a "scanned card", it's fine.
            }
        },



        clearScannedList() {
            this.scannedAssetIds = [];
        },

        async initializeData(token) {
            this.loading = true;
            this.error = null;
            try {
                if (!token) throw new Error('토큰을 입력해주세요.');
                googleApi.setToken(token);

                // Fetch both lists in parallel
                await Promise.all([
                    this.refreshMasters(),
                    this.refreshSessions()
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
            this.sessionFiles = await googleApi.listSessionSheets();
        },
        async loadProject(file) {
            this.loading = true;
            this.error = null;
            try {
                this.currentFile = file;

                // 1. Fetch Session Assets
                const sessionData = await googleApi.fetchSheetData(file.id);

                this.setAssets(sessionData);

                // 2. Refresh Master Metadata & Merge
                await this.checkAndSyncMaster();

                this.scannedAssetIds = this.assets.filter(a => a.status === 'checked').map(a => a.asset_number);
                this.inspectionLogs = [];
            } catch (err) {
                this.handleAuthError(err);
            } finally {
                this.loading = false;
            }
        },

        async refreshMasterMetadata() {
            try {
                const latestMaster = await googleApi.getLatestSheet();
                if (!latestMaster) return;

                const masterData = await googleApi.fetchSheetData(latestMaster.id);
                const rawUsers = masterData.filter(item => item._type === 'users');
                const rawTrade = masterData.filter(item => item._type === 'trade');
                const rawMasterAssets = masterData.filter(item => {
                    if (item._type !== 'assets') return false;
                    const state = this._getVal(item, 'state');
                    return !(state && state.toLowerCase() === 'termination');
                });

                if (rawUsers.length > 0) this.users = rawUsers;
                if (rawTrade.length > 0) this.tradeLogs = rawTrade;

                // --- MERGE ASSETS INTO CURRENT SESSION ---
                if (this.currentFile && rawMasterAssets.length > 0) {
                    console.log(`[Sync] Merging ${rawMasterAssets.length} master assets into current session...`);

                    // 1. Get the current session's sheet name. 
                    let sessionSheetName = this.assets[0]?._sheetName;
                    if (!sessionSheetName && this.currentFile) {
                        // If we can't find it in assets, it might be a localized default
                        sessionSheetName = 'Sheet1';
                    }

                    const existingMap = new Map();
                    this.assets.forEach(a => existingMap.set(a.assetNumber, a));

                    const mergedAssets = [];
                    const masterSeen = new Set();

                    // 1. Process Master Assets (Update or Add)
                    rawMasterAssets.forEach(ma => {
                        const assetNo = this._getVal(ma, 'asset_number');
                        if (!assetNo) return;
                        masterSeen.add(assetNo);

                        const existing = existingMap.get(assetNo);
                        const inUser = this._getVal(ma, 'in_user');
                        const user = this.users.find(u => {
                            const cid = this._getVal(u, 'cj_id');
                            return cid && inUser && cid.toString().trim() === inUser.toString().trim();
                        });

                        // Standard metadata from master
                        const meta = {
                            assetNumber: assetNo,
                            asset_number: assetNo,
                            in_user: inUser,
                            userName: user ? this._getVal(user, 'user_name') : (this._getVal(ma, 'user_name') || inUser),
                            department: user ? this._getVal(user, 'department') : (this._getVal(ma, 'department') || ''),
                            category: this._getVal(ma, 'category') || '',
                            model_name: this._getVal(ma, 'model_name') || this._getVal(ma, 'model') || '',
                            serial_number: this._getVal(ma, 'serial_number') || '',
                            _type: 'assets',
                            _headers: ma._headers,
                            _sheetName: sessionSheetName
                        };

                        if (existing) {
                            // Update existing: Keep status, time, note
                            mergedAssets.push({
                                ...existing,
                                ...meta,
                                status: existing.status,
                                inspection_time: existing.inspection_time,
                                note: existing.note
                            });
                        } else {
                            // New asset: Start as missing
                            mergedAssets.push({
                                ...meta,
                                status: 'missing',
                                inspection_time: '',
                                note: ''
                            });
                            console.log(`[Sync] New asset added: ${assetNo}`);
                        }
                    });

                    // 2. Keep assets that are in session but NOT in master (Optional safety)
                    this.assets.forEach(a => {
                        if (!masterSeen.has(a.assetNumber)) {
                            mergedAssets.push(a);
                        }
                    });

                    this.assets = mergedAssets;

                    // 3. Persist merged data back to the session file
                    await this.saveData();
                    console.log(`[Sync] Merged asset list saved to ${this.currentFile.name}`);
                }

                // Update sync timestamp
                const now = new Date().toISOString();
                this.lastMasterSync = now;
                localStorage.setItem('last_master_sync', now);

                // Re-apply joins to existing assets
                if (this.assets.length > 0) {
                    this.assets = this.assets.map(asset => {
                        const inUser = this._getVal(asset, 'in_user');
                        const user = this.users.find(u => {
                            const cid = this._getVal(u, 'cj_id');
                            return cid && inUser && cid.toString().trim() === inUser.toString().trim();
                        });
                        return {
                            ...asset,
                            userName: user ? this._getVal(user, 'user_name') : (this._getVal(asset, 'user_name') || inUser),
                            department: user ? this._getVal(user, 'department') : (this._getVal(asset, 'department') || ''),
                        };
                    });
                }
                console.log('Master metadata refreshed successfully');
            } catch (err) {
                console.warn('Failed to refresh master metadata:', err);
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

        async saveData() {
            if (!this.currentFile) return;
            this.loading = true;
            try {
                // IMPORTANT: Only save data of type 'assets' (the actual survey records)
                // This prevents trying to write 'users' or 'trade' data into the session file
                const sessionAssets = this.assets.filter(a => a._type === 'assets');
                await googleApi.updateSheet(this.currentFile.id, sessionAssets);
                console.log('Changes saved to Google Sheets');
            } catch (err) {
                this.handleAuthError(err);
            } finally {
                this.loading = false;
            }
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

        handleAuthError(err) {
            this.error = err.message;
            if (err.message.includes('[AUTH_EXPIRED]')) {
                console.error('Session expired. Logging out.');
                this.isAuthenticated = false;
                localStorage.removeItem('google_access_token');
                alert('인증 세션이 만료되었습니다. 다시 로그인해주세요.');
            } else {
                console.error('API Error:', err);
                alert('오류 발생: ' + err.message);
            }
        }
    }
})

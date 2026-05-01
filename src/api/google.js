import { SocialLogin } from '@capgo/capacitor-social-login';

/**
 * Google API Bridge for Asset Management (REST API Version)
 */

export const googleApi = {
    // Folder ID where source asset spreadsheets are stored
    FOLDER_ID: '1FK2Opt907pBIsETpULcOWedY_X52pCy-',
    accessToken: null,
    // Folder ID for backups (User needs to fill this)
    BACKUP_FOLDER_ID: '11hRf6h8ciyd7uXOZeeorR4XaXKKgqSfv',
    TRADE_LOG_FILE_NAME: 'APP_GLOBAL_TRADE_LOGS_V2',
    CLIENT_ID: '876684580795-l4nj5d5k5uh111j7oc1a1seb7877mtg6.apps.googleusercontent.com',
    SCOPES: [
        'profile',
        'email',
        'https://www.googleapis.com/auth/drive.readonly',
        'https://www.googleapis.com/auth/drive.file',
        'https://www.googleapis.com/auth/spreadsheets'
    ],

    async initialize() {
        if (this._initialized) return;
        await SocialLogin.initialize({
            google: { webClientId: this.CLIENT_ID },
        });
        this._initialized = true;
    },

    /**
     * 공통 API 요청 핸들러: 인증 토큰을 자동으로 삽입하고, 
     * 401(인증 만료) 발생 시 1회에 한해 자동으로 토큰을 갱신하고 재시도합니다.
     */
    /**
     * Google tokeninfo API를 통해 현재 토큰이 아직 유효한지 확인합니다.
     * 계정 선택창을 띄우지 않고 순수 HTTP 요청만 합니다.
     * @returns {number} 남은 유효시간(초). 만료됐으면 0.
     */
    async _validateToken() {
        if (!this.accessToken) return 0;
        try {
            const res = await fetch(
                `https://www.googleapis.com/oauth2/v3/tokeninfo?access_token=${encodeURIComponent(this.accessToken)}`
            );
            if (!res.ok) return 0;
            const data = await res.json();
            const expiresIn = parseInt(data.expires_in, 10);
            return isNaN(expiresIn) ? 0 : expiresIn;
        } catch {
            return 0;
        }
    },

    async _request(url, options = {}, retry = true) {
        if (!this.accessToken) {
            // 로컬 스토리지에서 토큰 복구 시도
            const savedToken = localStorage.getItem('google_access_token');
            if (savedToken) {
                this.accessToken = savedToken;
            } else {
                throw new Error('[AUTH_REQUIRED] 인증이 필요합니다.');
            }
        }

        const headers = {
            ...options.headers,
            'Authorization': `Bearer ${this.accessToken}`
        };

        try {
            const response = await fetch(url, { ...options, headers });

            if (response.status === 401 && retry) {
                console.warn('[GoogleAPI] 401 Unauthorized detected. Attempting silent recovery...');
                try {
                    // 1단계: tokeninfo API로 토큰이 진짜 만료됐는지 확인
                    //        (서버 측 일시적 거부일 수도 있으므로)
                    const remaining = await this._validateToken();
                    if (remaining > 30) {
                        // 토큰은 아직 유효한데 401이 온 경우 → 단순 재시도
                        console.log(`[GoogleAPI] Token still valid (${remaining}s). Retrying request...`);
                        return await this._request(url, options, false);
                    }

                    // 2단계: 토큰이 진짜 만료 → 재인증 (계정 선택창 불가피)
                    console.log('[GoogleAPI] Token truly expired. Re-authenticating...');
                    await this.refreshAccessToken();
                    return await this._request(url, options, false);
                } catch (refreshError) {
                    console.error('[GoogleAPI] Recovery failed:', refreshError);
                    throw new Error('[AUTH_EXPIRED] 인증 세션이 만료되었습니다.');
                }
            }

            return response;
        } catch (fetchError) {
            if (fetchError.message.includes('[AUTH_EXPIRED]') || fetchError.message.includes('[AUTH_REQUIRED]')) {
                throw fetchError;
            }
            console.error('[GoogleAPI] Network or Fetch error:', fetchError);
            throw new Error(`네트워크 오류가 발생했습니다: ${fetchError.message}`);
        }
    },

    async signInWithGoogle() {
        try {
            await this.initialize();
            // 안드로이드에서는 이미 로그인된 정보가 있다면 계정 선택 없이 즉시 토큰을 가져옵니다.
            const response = await SocialLogin.login({
                provider: 'google',
                options: {
                    scopes: this.SCOPES,
                }
            });

            let token = '';
            if (response.result && response.result.accessToken) {
                token = typeof response.result.accessToken === 'string'
                    ? response.result.accessToken
                    : response.result.accessToken.token;
            }
            this.accessToken = token;
            localStorage.setItem('google_access_token', token);
            localStorage.setItem('google_token_time', Date.now().toString());

            // 기존 코드 호환성을 위해 authentication 객체를 포함하여 반환합니다.
            return {
                ...response.result,
                authentication: { accessToken: token }
            };
        } catch (error) {
            console.error('Google Auth Error:', error);
            throw error;
        }
    },

    /**
     * 토큰 갱신 (3단계 전략):
     *   1단계: tokeninfo API로 아직 유효하면 → 그대로 사용 (팝업 없음)
     *   2단계: SocialLogin.refresh()로 무음 갱신 시도 (팝업 없음)
     *   3단계: SocialLogin.login() 최후 수단 (계정선택창 불가피)
     */
    async refreshAccessToken() {
        // ── 1단계: 현재 토큰이 아직 유효한지 확인 (계정선택 없이) ──
        const remaining = await this._validateToken();
        if (remaining > 60) {
            console.log(`[GoogleAPI] Token still valid (${remaining}s). Skipping re-auth.`);
            localStorage.setItem('google_token_time', Date.now().toString());
            return this.accessToken;
        }

        // ── 2단계: SocialLogin.refresh()로 무음 갱신 시도 ──
        // Android 네이티브에서는 Google Play Services의 silentSignIn을 사용하여
        // 계정선택창 없이 백그라운드에서 새 토큰을 받아올 수 있습니다.
        try {
            await this.initialize();
            console.log('[GoogleAPI] Attempting silent refresh via SocialLogin.refresh()...');
            await SocialLogin.refresh({
                provider: 'google',
                options: { scopes: this.SCOPES }
            });

            // refresh 후 login으로 갱신된 토큰을 가져옴
            // (refresh는 void를 반환하므로 login으로 토큰 재취득)
            const response = await SocialLogin.login({
                provider: 'google',
                options: { scopes: this.SCOPES }
            });

            let token = '';
            if (response.result && response.result.accessToken) {
                token = typeof response.result.accessToken === 'string'
                    ? response.result.accessToken
                    : response.result.accessToken.token;
            }
            if (token) {
                this.accessToken = token;
                localStorage.setItem('google_access_token', token);
                localStorage.setItem('google_token_time', Date.now().toString());
                console.log('[GoogleAPI] Silent refresh succeeded (no account picker).');
                return this.accessToken;
            }
        } catch (silentError) {
            console.warn('[GoogleAPI] Silent refresh not available:', silentError.message || silentError);
            // 2단계 실패 → 3단계로 진행
        }

        // ── 3단계: 최후 수단 — SocialLogin.login() (계정선택창 뜰 수 있음) ──
        try {
            console.log('[GoogleAPI] Falling back to SocialLogin.login() (may show picker)...');
            const response = await SocialLogin.login({
                provider: 'google',
                options: { scopes: this.SCOPES }
            });

            let token = '';
            if (response.result && response.result.accessToken) {
                token = typeof response.result.accessToken === 'string'
                    ? response.result.accessToken
                    : response.result.accessToken.token;
            }
            this.accessToken = token;
            localStorage.setItem('google_access_token', this.accessToken);
            localStorage.setItem('google_token_time', Date.now().toString());
            console.log('[GoogleAPI] Token refreshed via login fallback.');
            return this.accessToken;
        } catch (error) {
            console.error('[GoogleAPI] All refresh methods failed:', error);
            throw error;
        }
    },

    async signOutGoogle() {
        try {
            await this.initialize();
            await SocialLogin.logout({ provider: 'google' });
            this.accessToken = null;
        } catch (error) {
            console.error('Logout Error:', error);
        }
    },

    setToken(token) {
        this.accessToken = token;
    },

    async listFilesFromFolder(folderId) {
        if (!folderId) throw new Error('폴더 ID가 설정되지 않았습니다.');

        const q = encodeURIComponent(`'${folderId}' in parents and mimeType='application/vnd.google-apps.spreadsheet' and trashed=false`);
        const url = `https://www.googleapis.com/drive/v3/files?q=${q}&orderBy=modifiedTime desc&fields=files(id,name,modifiedTime)`;

        const response = await this._request(url);

        if (!response.ok) {
            if (response.status === 401) throw new Error('[AUTH_EXPIRED] 토큰이 만료되었습니다.');
            throw new Error(`파일 목록 조회 실패: ${response.status}`);
        }

        const data = await response.json();
        return data.files || [];
    },

    async listMasterSheets() {
        return this.listFilesFromFolder(this.FOLDER_ID);
    },

    async listSessionSheets() {
        return this.listFilesFromFolder(this.BACKUP_FOLDER_ID);
    },

    async getLatestSheet() {
        const files = await this.listMasterSheets();
        return files[0];
    },

    async fetchSheetData(sheetId) {
        console.log(`[GoogleAPI] Fetching data for file: ${sheetId}`);
        const metaUrl = `https://sheets.googleapis.com/v4/spreadsheets/${sheetId}?fields=sheets(properties(title))`;
        const metaResponse = await this._request(metaUrl);

        if (!metaResponse.ok) {
            if (metaResponse.status === 401) throw new Error('[AUTH_EXPIRED] 토큰이 만료되었습니다.');
            throw new Error(`스프레드시트 정보를 가져오지 못했습니다. (Status: ${metaResponse.status})`);
        }

        const metadata = await metaResponse.json();
        const allSheetTitles = metadata.sheets.map(s => s.properties.title);
        console.log('[GoogleAPI] Found sheet titles:', allSheetTitles.join(', '));

        let allAssets = [];
        for (const title of allSheetTitles) {
            const lowerTitle = title.toLowerCase().replace(/\s/g, '');
            let type = '';

            // 1. Exact matches for requested names
            if (lowerTitle === 'users') {
                type = 'users';
            } else if (lowerTitle === 'trade') {
                type = 'trade';
            } else if (lowerTitle === 'assets') {
                type = 'assets';
            }
            // 2. Lenient Korean mappings (fallback)
            else if (lowerTitle.includes('인사') || lowerTitle.includes('사용자') || lowerTitle.includes('사원')) {
                type = 'users';
            } else if (lowerTitle.includes('거래') || lowerTitle.includes('변동') || lowerTitle.includes('이력') || lowerTitle.includes('변경') || lowerTitle.includes('추적')) {
                type = 'trade';
            } else if (lowerTitle.includes('자산') || lowerTitle.includes('현황') || lowerTitle.includes('대장') || lowerTitle.includes('목록') || lowerTitle === 'sheet1' || lowerTitle === '시트1') {
                type = 'assets';
            }

            if (!type) {
                console.log(`[GoogleAPI] Skipping sheet: "${title}" (No direct match)`);
                continue;
            }

            console.log(`[GoogleAPI] Loading sheet: "${title}" as type: [${type}]`);
            const url = `https://sheets.googleapis.com/v4/spreadsheets/${sheetId}/values/${encodeURIComponent(title)}!A:ZZ`;
            const response = await this._request(url);

            if (response.ok) {
                const data = await response.json();
                const sheetAssets = this.parseRangeToJson(data.values || [], title, type);
                console.log(`[GoogleAPI] Parsed ${sheetAssets.length} records from "${title}"`);
                allAssets = allAssets.concat(sheetAssets);
            } else {
                console.error(`[GoogleAPI] Failed to fetch values for "${title}": ${response.status}`);
            }
        }

        if (allAssets.length === 0) {
            console.warn('[GoogleAPI] Warning: No records found in any recognized sheets.');
        }

        return allAssets;
    },

    async updateSheet(sheetId, assets) {
        // 1. 현재 스프레드시트의 실제 시트 목록 가져오기 (시트명 불일치 방지)
        const metaUrl = `https://sheets.googleapis.com/v4/spreadsheets/${sheetId}?fields=sheets(properties(title))`;
        const metaRes = await this._request(metaUrl);
        const metaData = await metaRes.json();
        const existingSheets = metaData.sheets.map(s => s.properties.title);
        const firstSheet = existingSheets[0] || 'Sheet1';

        const sheetsToUpdate = {};
        assets.forEach(a => {
            // 자산에 기록된 시트명이 실제 존재하면 사용, 없으면 첫 번째 시트 사용
            let name = a._sheetName;
            if (!name || !existingSheets.includes(name)) {
                name = firstSheet;
            }
            if (!sheetsToUpdate[name]) sheetsToUpdate[name] = [];
            sheetsToUpdate[name].push(a);
        });

        for (const [sheetName, sheetAssets] of Object.entries(sheetsToUpdate)) {
            console.log(`[GoogleAPI] Updating sheet: ${sheetName}`);
            const values = this.jsonToRange(sheetAssets);
            const url = `https://sheets.googleapis.com/v4/spreadsheets/${sheetId}/values/${encodeURIComponent(sheetName)}!A1?valueInputOption=USER_ENTERED`;

            const response = await this._request(url, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ values })
            });

            if (!response.ok) {
                if (response.status === 401) throw new Error('[AUTH_EXPIRED] 토큰이 만료되었습니다.');
                const errDetail = await response.text();
                console.error(`Update failed detail: ${errDetail}`);
                throw new Error(`${sheetName} 시트 업데이트 실패: ${response.status}`);
            }
        }
    },

    async createSessionFile(masterId, sessionName, assets) {
        if (!this.BACKUP_FOLDER_ID) throw new Error('저장 폴더(BACKUP_FOLDER_ID)가 설정되지 않았습니다.');

        console.log(`Creating new flat session spreadsheet: ${sessionName}`);

        // 1. Create a fresh spreadsheet
        const createUrl = `https://sheets.googleapis.com/v4/spreadsheets`;
        const createRes = await this._request(createUrl, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                properties: { title: sessionName }
            })
        });

        if (!createRes.ok) {
            if (createRes.status === 401) throw new Error('[AUTH_EXPIRED] 토큰이 만료되었습니다.');
            throw new Error(`스프레드시트 생성 실패: ${createRes.status}`);
        }

        const newSheet = await createRes.json();
        const sheetId = newSheet.spreadsheetId;
        const firstSheetTitle = newSheet.sheets[0].properties.title; // Handle localized names like '시트1'
        console.log(`[GoogleAPI] New sheet created. ID: ${sheetId}, Default sheet: "${firstSheetTitle}"`);

        // 2. Move to Backup Folder
        const moveUrl = `https://www.googleapis.com/drive/v3/files/${sheetId}?addParents=${this.BACKUP_FOLDER_ID}&removeParents=root`;
        await this._request(moveUrl, { method: 'PATCH' });

        // 3. Prepare data for the single "results" sheet
        if (assets.length > 0) {
            // Consolidate unique headers from all assets to avoid data loss
            const headerSet = new Set();
            assets.forEach(a => {
                const head = a._headers || Object.keys(a).filter(k => !k.startsWith('_'));
                head.forEach(h => headerSet.add(h));
            });
            const newHeaders = Array.from(headerSet);

            // Add status, inspection_time and note if they don't exist
            const lowerHeaders = newHeaders.map(h => h.toLowerCase());
            if (!lowerHeaders.includes('status')) newHeaders.push('status');
            if (!lowerHeaders.includes('inspection_time')) newHeaders.push('inspection_time');
            if (!lowerHeaders.includes('note')) newHeaders.push('note');

            console.log(`[GoogleAPI] Writing ${assets.length} assets with consolidated headers: ${newHeaders.join(', ')}`);

            const values = [
                newHeaders,
                ...assets.map(a => newHeaders.map(h => {
                    const lh = h.toLowerCase();
                    if (lh === 'inspection_time') return a[h] || a.inspection_time || '';
                    if (lh === 'note') return a[h] || a.note || '';
                    return a[h] !== undefined ? a[h] : '';
                }))
            ];

            const updateUrl = `https://sheets.googleapis.com/v4/spreadsheets/${sheetId}/values/${encodeURIComponent(firstSheetTitle)}!A1?valueInputOption=USER_ENTERED`;
            const updateRes = await this._request(updateUrl, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ values })
            });

            if (!updateRes.ok) {
                console.error(`[GoogleAPI] Failed to update session file content: ${updateRes.status}`);
            } else {
                console.log(`[GoogleAPI] Session data written successfully to "${firstSheetTitle}"`);
            }
        }

        return { id: sheetId, name: sessionName };
    },

    async createBackup(sheetId, originalName) {
        // Renaming current session file as a timestamped backup
        const timestamp = new Date().toISOString().replace(/[:.]/g, '').slice(0, 15);
        const newName = `${originalName}_BK_${timestamp}`;
        return this.createSessionFile(sheetId, newName);
    },

    async getOrCreateGlobalTradeLog() {
        // 1. Search for existing file anywhere in the user's drive
        const q = encodeURIComponent(`name='${this.TRADE_LOG_FILE_NAME}' and mimeType='application/vnd.google-apps.spreadsheet' and trashed=false`);
        const searchUrl = `https://www.googleapis.com/drive/v3/files?q=${q}&orderBy=createdTime desc&fields=files(id,name)`;
        const searchRes = await this._request(searchUrl);
        const searchData = await searchRes.json();

        if (searchData.files && searchData.files.length > 0) {
            const fileId = searchData.files[0].id;
            // Get the first sheet name
            const metaUrl = `https://sheets.googleapis.com/v4/spreadsheets/${fileId}?fields=sheets(properties(title))`;
            const metaRes = await this._request(metaUrl);
            const metaData = await metaRes.json();
            const sheetTitle = metaData.sheets[0].properties.title;

            // Check if headers exist
            const getUrl = `https://sheets.googleapis.com/v4/spreadsheets/${fileId}/values/${encodeURIComponent(sheetTitle)}!A1:Z1`;
            const getRes = await this._request(getUrl);
            const getData = await getRes.json();

            if (!getData.values || getData.values.length === 0 || getData.values[0].length === 0) {
                console.log('[GoogleAPI] Global Trade Log headers missing, initializing...');
                const headers = ['unique_key', 'date', 'asset_number', 'cj_id', 'ex_user', 'note', 'timestamp'];
                const updateUrl = `https://sheets.googleapis.com/v4/spreadsheets/${fileId}/values/${encodeURIComponent(sheetTitle)}!A1?valueInputOption=USER_ENTERED`;
                await this._request(updateUrl, {
                    method: 'PUT',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ values: [headers] })
                });
            }
            return { id: fileId, name: this.TRADE_LOG_FILE_NAME, sheetTitle };
        }

        // 2. If not found, create it in the main Data folder (FOLDER_ID)
        console.log('[GoogleAPI] Global trade log file not found. Creating a new one...');
        const createRes = await this._request('https://www.googleapis.com/drive/v3/files', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                name: this.TRADE_LOG_FILE_NAME,
                mimeType: 'application/vnd.google-apps.spreadsheet',
                parents: [this.FOLDER_ID]
            })
        });
        const newSheetFile = await createRes.json();
        const sheetId = newSheetFile.id;

        // Fetch sheet properties to get the first sheet title
        const metaUrl = `https://sheets.googleapis.com/v4/spreadsheets/${sheetId}?fields=sheets(properties(title))`;
        const metaRes = await this._request(metaUrl);
        const metaData = await metaRes.json();
        const sheetTitle = metaData.sheets[0].properties.title;

        // Initialize header
        const headers = ['unique_key', 'date', 'asset_number', 'cj_id', 'ex_user', 'note', 'timestamp'];
        const updateUrl = `https://sheets.googleapis.com/v4/spreadsheets/${sheetId}/values/${encodeURIComponent(sheetTitle)}!A1?valueInputOption=USER_ENTERED`;
        await this._request(updateUrl, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ values: [headers] })
        });

        return { id: sheetId, name: this.TRADE_LOG_FILE_NAME, sheetTitle };
    },

    async fetchGlobalTradeLogs() {
        try {
            console.log('[GoogleAPI] Fetching global trade logs started...');
            const file = await this.getOrCreateGlobalTradeLog();
            console.log(`[GoogleAPI] Target file: ${file.name}, ID: ${file.id}, Sheet: ${file.sheetTitle}`);

            const url = `https://sheets.googleapis.com/v4/spreadsheets/${file.id}/values/${encodeURIComponent(file.sheetTitle)}!A:ZZ`;
            const response = await this._request(url);

            if (response.ok) {
                const data = await response.json();
                console.log(`[GoogleAPI] Raw rows fetched: ${data.values ? data.values.length : 0}`);
                const parsed = this.parseRangeToJson(data.values || [], file.sheetTitle || 'Global_Trade', 'trade');
                console.log(`[GoogleAPI] Parsed objects: ${parsed.length}`);
                return parsed;
            } else {
                const errText = await response.text();
                console.error(`[GoogleAPI] Fetch failed: ${response.status} - ${errText}`);
                return [];
            }
        } catch (err) {
            console.error('[GoogleAPI] Failed to fetch global trade logs:', err);
            return [];
        }
    },

    async appendGlobalTradeLog(log) {
        const file = await this.getOrCreateGlobalTradeLog();

        const dateVal = log.date || new Date().toISOString().split('T')[0];
        const assetVal = log.asset_number || '';
        const cjIdVal = log.cj_id || '';
        const exUserVal = log.ex_user || '';
        const noteVal = log.note || '';
        
        // Generate a unique key for deduplication
        const uniqueKey = `${dateVal.replace(/[^0-9]/g, '')}_${assetVal.replace(/\s/g, '').toLowerCase()}_${cjIdVal.replace(/\s/g, '').toLowerCase()}_${exUserVal.replace(/\s/g, '').toLowerCase()}_${noteVal.replace(/\s/g, '').toLowerCase()}`;

        // Headers: ['unique_key', 'date', 'asset_number', 'cj_id', 'ex_user', 'note', 'timestamp']
        const row = [
            uniqueKey,
            dateVal,
            assetVal,
            cjIdVal,
            log.ex_user || '',
            log.note || '',
            new Date().toISOString()
        ];

        const url = `https://sheets.googleapis.com/v4/spreadsheets/${file.id}/values/${encodeURIComponent(file.sheetTitle)}!A1:append?valueInputOption=RAW`;
        const response = await this._request(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ values: [row] })
        });

        if (!response.ok) {
            throw new Error(`글로벌 로그 업데이트 실패: ${response.status}`);
        }
    },

    async syncMasterTradeToGlobal(masterTradeLogs) {
        const globalFile = await this.getOrCreateGlobalTradeLog();

        // 1. Fetch current global logs
        const globalResponse = await this._request(`https://sheets.googleapis.com/v4/spreadsheets/${globalFile.id}/values/${encodeURIComponent(globalFile.sheetTitle)}!A:ZZ`);
        const globalData = await globalResponse.json();
        const globalRows = globalData.values || [[]];

        // 2. Parse existing global logs for deduplication using unique_key
        const existingKeys = new Set();
        if (globalRows.length > 1) {
            globalRows.slice(1).forEach(row => {
                const uniqueKey = (row[0] || '').toString().trim();
                if (uniqueKey) {
                    existingKeys.add(uniqueKey);
                } else {
                    // Fallback for any malformed rows
                    const date = (row[1] || '').toString().replace(/[^0-9]/g, '');
                    const assetNo = (row[2] || '').toString().replace(/\s/g, '').toLowerCase();
                    const cjId = (row[3] || '').toString().replace(/\s/g, '').toLowerCase();
                    const exUser = (row[4] || '').toString().replace(/\s/g, '').toLowerCase();
                    const note = (row[5] || '').toString().replace(/\s/g, '').toLowerCase();
                    existingKeys.add(`${date}_${assetNo}_${cjId}_${exUser}_${note}`);
                }
            });
        }
        console.log(`[GoogleAPI] Existing global log keys: ${existingKeys.size}`);

        // 3. Filter only NEW logs from master
        const newRows = [];
        masterTradeLogs.forEach(log => {
            const rawDate = this._getVal(log, 'date') || '';
            const rawAssetNo = this._getVal(log, 'asset_number') || '';
            const rawCjId = this._getVal(log, 'cj_id') || '';
            const rawExUser = this._getVal(log, 'ex_user') || '';
            const rawNote = this._getVal(log, 'note') || 'Master Sync';

            if (!rawDate || !rawAssetNo) return; // Skip invalid entries

            const date = rawDate.toString().replace(/[^0-9]/g, '');
            const assetNo = rawAssetNo.toString().replace(/\s/g, '').toLowerCase();
            const cjId = rawCjId.toString().replace(/\s/g, '').toLowerCase();
            const exUser = rawExUser.toString().replace(/\s/g, '').toLowerCase();
            const note = rawNote.toString().replace(/\s/g, '').toLowerCase();

            const uniqueKey = `${date}_${assetNo}_${cjId}_${exUser}_${note}`;
            if (!existingKeys.has(uniqueKey)) {
                newRows.push([
                    uniqueKey,
                    rawDate,
                    rawAssetNo,
                    rawCjId,
                    rawExUser,
                    rawNote,
                    new Date().toISOString()
                ]);
                existingKeys.add(uniqueKey);
            }
        });

        console.log(`[GoogleAPI] New rows to sync: ${newRows.length}`);

        if (newRows.length === 0) {
            console.log('[GoogleAPI] No new master trade logs to sync.');
            return;
        }

        // 4. Append new logs to global file
        const appendUrl = `https://sheets.googleapis.com/v4/spreadsheets/${globalFile.id}/values/${encodeURIComponent(globalFile.sheetTitle)}!A1:append?valueInputOption=RAW`;
        const response = await this._request(appendUrl, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ values: newRows })
        });

        if (!response.ok) {
            const err = await response.text();
            console.error(`[GoogleAPI] Sync failed: ${err}`);
            throw new Error('글로벌 로그 동기화 실패');
        }

        console.log(`[GoogleAPI] Synced ${newRows.length} master logs to global file.`);
    },

    _getVal(obj, key) {
        if (!obj) return null;
        const aliases = {
            asset_number: ['assetnumber', '자산번호', '관리번호', 'assetno', 'no', '관리no'],
            cj_id: ['cjid', '사번', 'id', 'cj_id', '사용자id', '사용자사번'],
            date: ['date', '업무일자', '일자', '날짜', 'timestamp', '수정일', '변경일', '작업일자', '시간', '수정시간', '생성일'],
            ex_user: ['ex_user', '이전에사용하던사람', '이전사용자', 'asset_in_user', 'prev_user'],
            note: ['note', '메모', '비고', '사항']
        };
        const targets = (aliases[key] || [key]).map(t => t.toLowerCase().replace(/[\s_]/g, ''));
        const foundKey = Object.keys(obj).find(k => targets.includes(k.toLowerCase().replace(/[\s_]/g, '')));
        return foundKey ? obj[foundKey] : null;
    },

    parseRangeToJson(values, sheetName = '', type = '') {
        if (!values || values.length === 0) return [];
        const headers = values[0];
        return values.slice(1).map((row, index) => {
            const obj = {
                _sheetName: sheetName,
                _type: type, // Normalized type (assets, users, trade)
                _rowIndex: index + 2,
                _headers: headers // Store headers for writing back
            };
            headers.forEach((h, i) => {
                obj[h] = row[i] || '';
            });
            return obj;
        });
    },

    jsonToRange(assets) {
        if (!assets || !assets.length) return [];

        let headers = [...(assets[0]._headers || [])];
        if (headers.length === 0) {
            headers = Object.keys(assets[0]).filter(k => !k.startsWith('_'));
        }

        // Ensure critical survey columns exist in the headers for saving
        const critical = ['status', 'inspection_time', 'note'];
        critical.forEach(c => {
            const exists = headers.some(h => h.toLowerCase() === c);
            if (!exists) {
                headers.push(c);
            }
        });

        const rows = assets.map(a => headers.map(h => {
            // Find exact match or case-insensitive match in the object
            if (a[h] !== undefined) return a[h];
            const lcKey = Object.keys(a).find(k => k.toLowerCase() === h.toLowerCase());
            return lcKey ? a[lcKey] : '';
        }));
        return [headers, ...rows];
    }
}

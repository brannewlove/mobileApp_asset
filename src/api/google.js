/**
 * Google API Bridge for Asset Management (REST API Version)
 */

export const googleApi = {
    // Folder ID where source asset spreadsheets are stored
    FOLDER_ID: '1FK2Opt907pBIsETpULcOWedY_X52pCy-',
    // Folder ID for backups (User needs to fill this)
    BACKUP_FOLDER_ID: '11hRf6h8ciyd7uXOZeeorR4XaXKKgqSfv',
    accessToken: null,

    setToken(token) {
        this.accessToken = token;
    },

    async listFilesFromFolder(folderId) {
        if (!this.accessToken) throw new Error('인증 토큰이 없습니다.');
        if (!folderId) throw new Error('폴더 ID가 설정되지 않았습니다.');

        const q = encodeURIComponent(`'${folderId}' in parents and mimeType='application/vnd.google-apps.spreadsheet' and trashed=false`);
        const url = `https://www.googleapis.com/drive/v3/files?q=${q}&orderBy=modifiedTime desc&fields=files(id,name,modifiedTime)`;

        const response = await fetch(url, {
            headers: { 'Authorization': `Bearer ${this.accessToken}` }
        });

        if (response.status === 401) throw new Error('[AUTH_EXPIRED] 토큰이 만료되었습니다.');
        if (!response.ok) throw new Error(`파일 목록 조회 실패: ${response.status}`);

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
        if (!this.accessToken) throw new Error('인증 토큰이 없습니다.');

        console.log(`[GoogleAPI] Fetching data for file: ${sheetId}`);
        const metaUrl = `https://sheets.googleapis.com/v4/spreadsheets/${sheetId}?fields=sheets(properties(title))`;
        const metaResponse = await fetch(metaUrl, {
            headers: { 'Authorization': `Bearer ${this.accessToken}` }
        });

        if (metaResponse.status === 401) throw new Error('[AUTH_EXPIRED] 토큰이 만료되었습니다.');
        if (!metaResponse.ok) throw new Error(`스프레드시트 정보를 가져오지 못했습니다. (Status: ${metaResponse.status})`);

        const metadata = await metaResponse.json();
        const allSheetTitles = metadata.sheets.map(s => s.properties.title);
        console.log('[GoogleAPI] Found sheet titles:', allSheetTitles.join(', '));

        let allAssets = [];
        for (const title of allSheetTitles) {
            const lowerTitle = title.toLowerCase().replace(/\s/g, '');
            let type = '';

            // Inclusive mapping for "Assets"
            // Strict mapping for primary "Assets" sheets
            if (lowerTitle === 'assets' || lowerTitle === '자산현황' || lowerTitle === '자산' || lowerTitle === '현황' || lowerTitle === 'sheet1' || lowerTitle === '시트1') {
                type = 'assets';
            }
            // Inclusive mapping for "Users"
            else if (lowerTitle.includes('user') || lowerTitle.includes('인사') || lowerTitle.includes('사용자') || lowerTitle.includes('사원')) {
                type = 'users';
            }
            // Inclusive mapping for "Trade"
            else if (lowerTitle.includes('trade') || lowerTitle.includes('거래') || lowerTitle.includes('변동') || lowerTitle.includes('이력')) {
                type = 'trade';
            }

            if (!type) {
                console.log(`[GoogleAPI] Skipping sheet: "${title}" (No direct match)`);
                continue;
            }

            console.log(`[GoogleAPI] Loading sheet: "${title}" as type: [${type}]`);
            const url = `https://sheets.googleapis.com/v4/spreadsheets/${sheetId}/values/${encodeURIComponent(title)}!A:ZZ`;
            const response = await fetch(url, {
                headers: { 'Authorization': `Bearer ${this.accessToken}` }
            });

            if (response.status === 401) throw new Error('[AUTH_EXPIRED] 토큰이 만료되었습니다.');
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
        if (!this.accessToken) throw new Error('인증 토큰이 없습니다.');

        const sheetsToUpdate = {};
        assets.forEach(a => {
            const name = a._sheetName || 'Sheet1';
            if (!sheetsToUpdate[name]) sheetsToUpdate[name] = [];
            sheetsToUpdate[name].push(a);
        });

        for (const [sheetName, sheetAssets] of Object.entries(sheetsToUpdate)) {
            console.log(`[GoogleAPI] Updating sheet: ${sheetName}`);
            const values = this.jsonToRange(sheetAssets);
            const url = `https://sheets.googleapis.com/v4/spreadsheets/${sheetId}/values/${encodeURIComponent(sheetName)}!A1?valueInputOption=USER_ENTERED`;

            const response = await fetch(url, {
                method: 'PUT',
                headers: {
                    'Authorization': `Bearer ${this.accessToken}`,
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ values })
            });

            if (response.status === 401) throw new Error('[AUTH_EXPIRED] 토큰이 만료되었습니다.');
            if (!response.ok) {
                throw new Error(`${sheetName} 시트 업데이트 실패: ${response.status}`);
            }
        }
    },

    async createSessionFile(masterId, sessionName, assets) {
        if (!this.accessToken) throw new Error('인증 토큰이 없습니다.');
        if (!this.BACKUP_FOLDER_ID) throw new Error('저장 폴더(BACKUP_FOLDER_ID)가 설정되지 않았습니다.');

        console.log(`Creating new flat session spreadsheet: ${sessionName}`);

        // 1. Create a fresh spreadsheet
        const createUrl = `https://sheets.googleapis.com/v4/spreadsheets`;
        const createRes = await fetch(createUrl, {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${this.accessToken}`,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                properties: { title: sessionName }
            })
        });

        if (createRes.status === 401) throw new Error('[AUTH_EXPIRED] 토큰이 만료되었습니다.');
        if (!createRes.ok) throw new Error(`스프레드시트 생성 실패: ${createRes.status}`);

        const newSheet = await createRes.json();
        const sheetId = newSheet.spreadsheetId;
        const firstSheetTitle = newSheet.sheets[0].properties.title; // Handle localized names like '시트1'
        console.log(`[GoogleAPI] New sheet created. ID: ${sheetId}, Default sheet: "${firstSheetTitle}"`);

        // 2. Move to Backup Folder
        const moveUrl = `https://www.googleapis.com/drive/v3/files/${sheetId}?addParents=${this.BACKUP_FOLDER_ID}&removeParents=root`;
        await fetch(moveUrl, {
            method: 'PATCH',
            headers: { 'Authorization': `Bearer ${this.accessToken}` }
        });

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
            const updateRes = await fetch(updateUrl, {
                method: 'PUT',
                headers: {
                    'Authorization': `Bearer ${this.accessToken}`,
                    'Content-Type': 'application/json'
                },
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

package com.antigravity.assetmanager.data.remote

import com.antigravity.assetmanager.model.AliasMapper
import com.antigravity.assetmanager.model.Asset
import com.antigravity.assetmanager.model.TradeLog
import com.antigravity.assetmanager.model.User
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class DriveFileItem(
    val id: String,
    val name: String,
    val modifiedTime: String? = null
)

data class SyncResult(
    val assets: List<Asset>,
    val users: List<User>,
    val tradeLogs: List<TradeLog>,
    val masterFileName: String
)

/**
 * Google Drive v3 & Google Sheets v4 API 클라이언트
 * 비동기 코루틴(Dispatchers.IO)을 통해 UI 스레드 블로킹 없이 수천 건의 시트 데이터를 고속 스트리밍/파싱합니다.
 */
class GoogleApiClient(private val authHelper: GoogleAuthHelper) {

    companion object {
        const val MASTER_FOLDER_ID = "1FK2Opt907pBIsETpULcOWedY_X52pCy-"
        const val BACKUP_FOLDER_ID = "11hRf6h8ciyd7uXOZeeorR4XaXKKgqSfv"
        const val TRADE_LOG_FILE_NAME = "APP_GLOBAL_TRADE_LOGS_V2"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val aliasMapper = AliasMapper()

    suspend fun getValidAuthToken(): String = withContext(Dispatchers.IO) {
        authHelper.fetchOAuthAccessToken()
            ?: authHelper.getAccessToken()
            ?: throw Exception("구글 계정 로그인이 필요합니다. [구글 계정 로그인]을 먼저 진행해주세요.")
    }

    /**
     * 지정된 마스터 폴더에서 가장 최근에 수정된 스프레드시트 파일을 검색합니다.
     */
    suspend fun getLatestMasterFile(): DriveFileItem? = withContext(Dispatchers.IO) {
        val token = getValidAuthToken()

        val query = "'$MASTER_FOLDER_ID' in parents and mimeType='application/vnd.google-apps.spreadsheet' and trashed=false"
        val url = "https://www.googleapis.com/drive/v3/files?q=${java.net.URLEncoder.encode(query, "UTF-8")}&orderBy=modifiedTime desc&pageSize=10&fields=files(id,name,modifiedTime)"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (response.code == 401) {
                throw Exception("구글 인증 세션이 만료되었습니다. 다시 로그인해주세요.")
            }
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: ""
                throw Exception("드라이브 폴더 접근 실패 (HTTP ${response.code}): $errBody")
            }
            val body = response.body?.string() ?: throw Exception("응답 본문이 비어있습니다.")
            val json = gson.fromJson(body, JsonObject::class.java)
            val filesArray = json.getAsJsonArray("files")
            if (filesArray == null || filesArray.size() == 0) {
                throw Exception("마스터 폴더($MASTER_FOLDER_ID)에 접근 가능한 스프레드시트가 없습니다.")
            }

            val first = filesArray.get(0).asJsonObject
            DriveFileItem(
                id = first.get("id").asString,
                name = first.get("name").asString,
                modifiedTime = first.get("modifiedTime")?.asString
            )
        }
    }

    /**
     * 시트 내의 특정 탭 데이터를 행/열 2차원 리스트로 읽어옵니다.
     */
    private suspend fun fetchSheetRange(spreadsheetId: String, range: String): List<List<String>> = withContext(Dispatchers.IO) {
        val token = getValidAuthToken()
        val url = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId/values/${java.net.URLEncoder.encode(range, "UTF-8")}"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList()
            val body = response.body?.string() ?: return@withContext emptyList()
            val json = gson.fromJson(body, JsonObject::class.java)
            val valuesArray = json.getAsJsonArray("values") ?: return@withContext emptyList()

            val rows = mutableListOf<List<String>>()
            for (rowElem in valuesArray) {
                if (rowElem.isJsonArray) {
                    val row = rowElem.asJsonArray.map { if (it.isJsonNull) "" else it.asString }
                    rows.add(row)
                }
            }
            rows
        }
    }

    /**
     * 스프레드시트의 시트(탭) 이름 목록을 가져옵니다.
     */
    suspend fun getSheetTitles(spreadsheetId: String): List<String> = withContext(Dispatchers.IO) {
        val token = getValidAuthToken()
        val url = "https://sheets.googleapis.com/v4/spreadsheets/$spreadsheetId?fields=sheets.properties.title"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList()
            val body = response.body?.string() ?: return@withContext emptyList()
            val json = gson.fromJson(body, JsonObject::class.java)
            val sheets = json.getAsJsonArray("sheets") ?: return@withContext emptyList()
            val titles = mutableListOf<String>()
            for (sheet in sheets) {
                val prop = sheet.asJsonObject.getAsJsonObject("properties")
                prop?.get("title")?.asString?.let { titles.add(it) }
            }
            titles
        }
    }

    // (참고: 마스터 시트는 시스템 원본 백업이므로 절대 쓰기/수정/탭추가를 수행하지 않으며, 순수 읽기 전용으로만 사용됩니다.)

    /**
     * 구글 드라이브(백업 폴더 BACKUP_FOLDER_ID)에 회차 이름으로
     * 독립 구글 스프레드시트 파일을 생성하거나 기존 파일에 실사 데이터를 동기화합니다.
     */
    suspend fun createOrUpdateSessionSpreadsheetFile(
        sessionTitle: String,
        allMasterAssets: List<Asset>,
        scannedMap: Map<String, Asset>
    ): String = withContext(Dispatchers.IO) {
        val token = getValidAuthToken()

        // 1. 기존에 해당 이름의 스프레드시트 파일이 백업 폴더에 존재하는지 확인
        val query = "'$BACKUP_FOLDER_ID' in parents and name=${gson.toJson(sessionTitle)} and mimeType='application/vnd.google-apps.spreadsheet' and trashed=false"
        val searchUrl = "https://www.googleapis.com/drive/v3/files?q=${java.net.URLEncoder.encode(query, "UTF-8")}&fields=files(id,name)"

        var spreadsheetId: String? = null

        val searchReq = Request.Builder()
            .url(searchUrl)
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        httpClient.newCall(searchReq).execute().use { response ->
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val json = gson.fromJson(body, JsonObject::class.java)
                val files = json.getAsJsonArray("files")
                if (files != null && files.size() > 0) {
                    spreadsheetId = files.get(0).asJsonObject.get("id").asString
                }
            }
        }

        // 2. 파일이 없으면 새 스프레드시트 생성 후 백업 폴더로 이동
        if (spreadsheetId == null) {
            val createUrl = "https://sheets.googleapis.com/v4/spreadsheets"
            val createJson = """
                {
                  "properties": {
                    "title": ${gson.toJson(sessionTitle)}
                  }
                }
            """.trimIndent()

            val createReq = Request.Builder()
                .url(createUrl)
                .addHeader("Authorization", "Bearer $token")
                .post(createJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            httpClient.newCall(createReq).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string() ?: ""
                    throw Exception("스프레드시트 파일 생성 실패: $err")
                }
                val body = response.body?.string() ?: ""
                val json = gson.fromJson(body, JsonObject::class.java)
                spreadsheetId = json.get("spreadsheetId")?.asString
            }

            // 폴더 이동 (addParents=BACKUP_FOLDER_ID)
            if (spreadsheetId != null) {
                val moveUrl = "https://www.googleapis.com/drive/v3/files/$spreadsheetId?addParents=$BACKUP_FOLDER_ID&fields=id,parents"
                val moveReq = Request.Builder()
                    .url(moveUrl)
                    .addHeader("Authorization", "Bearer $token")
                    .patch("{}".toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()

                httpClient.newCall(moveReq).execute().close()
            }
        }

        val targetId = spreadsheetId ?: throw Exception("회차 구글 시트 ID를 획득할 수 없습니다.")

        // 3. 스프레드시트의 첫 번째 시트에 실사 데이터 기록
        val titles = getSheetTitles(targetId)
        val firstSheet = titles.firstOrNull() ?: "Sheet1"
        val range = "'$firstSheet'!A1"
        val putUrl = "https://sheets.googleapis.com/v4/spreadsheets/$targetId/values/${java.net.URLEncoder.encode(range, "UTF-8")}?valueInputOption=USER_ENTERED"

        val rows = mutableListOf<List<String>>()
        // 요청된 헤더 순서 및 컬럼명:
        // 분류 | 자산번호 | 모델 | 시리얼번호 | CJ_ID | 사용자명 | 부서 | 실사상태 | 점검시간 | 조사메모
        rows.add(
            listOf("분류", "자산번호", "모델", "시리얼번호", "CJ_ID", "사용자명", "부서", "실사상태", "점검시간", "조사메모")
        )

        for (asset in allMasterAssets) {
            val scanned = scannedMap[asset.assetNumber]
            val isSurveyed = scanned != null

            rows.add(
                listOf(
                    asset.category,
                    asset.assetNumber,
                    asset.modelName,
                    asset.serialNumber,
                    asset.inUser,
                    asset.userName,
                    asset.department,
                    if (isSurveyed) "실사완료" else "",
                    if (isSurveyed) scanned.inspectionTime else "",
                    if (isSurveyed) scanned.note else asset.note.ifBlank { "" }
                )
            )
        }

        val bodyMap = mapOf(
            "range" to range,
            "majorDimension" to "ROWS",
            "values" to rows
        )
        val bodyJson = gson.toJson(bodyMap)

        val writeReq = Request.Builder()
            .url(putUrl)
            .addHeader("Authorization", "Bearer $token")
            .put(bodyJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        httpClient.newCall(writeReq).execute().use { response ->
            if (!response.isSuccessful) {
                val err = response.body?.string() ?: ""
                throw Exception("회차 시트 데이터 쓰기 실패: $err")
            }
        }

        targetId
    }

    /**
     * 마스터 파일로부터 users, assets, trade 3개 시트를 파싱하고
     * 사원 정보와 매핑하여 최적화된 엔티티 리스트로 변환합니다.
     */
    suspend fun syncMasterSheet(spreadsheetId: String, fileName: String): SyncResult = withContext(Dispatchers.IO) {
        // 1. users 시트 읽기
        val usersRows = fetchSheetRange(spreadsheetId, "users")
        val users = parseUsers(usersRows)

        // 2. 사원 Map 사전 구축 (O(1) 빠른 매핑)
        val userMapByCjId = users.associateBy { it.cjId }

        // 3. assets 시트 읽기
        val assetsRows = fetchSheetRange(spreadsheetId, "assets")
        val assets = parseAssets(assetsRows, userMapByCjId)

        // 4. trade 시트 읽기
        val tradeRows = fetchSheetRange(spreadsheetId, "trade")
        val tradeLogs = parseTradeLogs(tradeRows, userMapByCjId)

        SyncResult(
            assets = assets,
            users = users,
            tradeLogs = tradeLogs,
            masterFileName = fileName
        )
    }

    private fun parseUsers(rows: List<List<String>>): List<User> {
        if (rows.size < 2) return emptyList()
        val headers = rows[0]
        val users = mutableListOf<User>()

        for (i in 1 until rows.size) {
            val row = rows[i]
            val rowMap = headers.indices.associate { idx ->
                val key = headers[idx]
                val value = if (idx < row.size) row[idx] else ""
                key to value
            }

            val cjId = aliasMapper.getValue(rowMap, "cj_id") ?: continue
            if (cjId.isBlank()) continue

            users.add(
                User(
                    cjId = cjId,
                    userName = aliasMapper.getValue(rowMap, "user_name") ?: "",
                    department = aliasMapper.getValue(rowMap, "department") ?: "",
                    position = rowMap["직급"] ?: rowMap["직책"] ?: ""
                )
            )
        }
        return users
    }

    private fun parseAssets(rows: List<List<String>>, userMap: Map<String, User>): List<Asset> {
        if (rows.size < 2) return emptyList()
        val headers = rows[0]
        val assets = mutableListOf<Asset>()
        val seen = mutableSetOf<String>()

        for (i in 1 until rows.size) {
            val row = rows[i]
            val rowMap = headers.indices.associate { idx ->
                val key = headers[idx]
                val value = if (idx < row.size) row[idx] else ""
                key to value
            }

            val assetNo = aliasMapper.getValue(rowMap, "asset_number") ?: continue
            val state = aliasMapper.getValue(rowMap, "state")
            if (assetNo.isBlank() || seen.contains(assetNo) || state.equals("termination", ignoreCase = true)) {
                continue
            }
            seen.add(assetNo)

            val inUser = aliasMapper.getValue(rowMap, "in_user") ?: ""
            val matchedUser = if (inUser.isNotBlank()) userMap[inUser] else null

            val userName = matchedUser?.userName
                ?: aliasMapper.getValue(rowMap, "user_name")
                ?: inUser
            val department = matchedUser?.department
                ?: aliasMapper.getValue(rowMap, "department")
                ?: ""

            assets.add(
                Asset(
                    assetNumber = assetNo,
                    modelName = aliasMapper.getValue(rowMap, "model_name") ?: "",
                    serialNumber = aliasMapper.getValue(rowMap, "serial_number") ?: "",
                    category = aliasMapper.getValue(rowMap, "category") ?: "",
                    inUser = inUser,
                    userName = userName,
                    department = department,
                    status = (aliasMapper.getValue(rowMap, "status") ?: "pending").lowercase(),
                    inspectionTime = aliasMapper.getValue(rowMap, "inspection_time") ?: "",
                    note = aliasMapper.getValue(rowMap, "note") ?: "",
                    state = state ?: "normal"
                )
            )
        }
        return assets
    }

    private fun parseTradeLogs(rows: List<List<String>>, userMap: Map<String, User>): List<TradeLog> {
        if (rows.size < 2) return emptyList()
        val headers = rows[0]
        val tradeLogs = mutableListOf<TradeLog>()

        for (i in 1 until rows.size) {
            val row = rows[i]
            val rowMap = headers.indices.associate { idx ->
                val key = headers[idx]
                val value = if (idx < row.size) row[idx] else ""
                key to value
            }

            val assetNo = aliasMapper.getValue(rowMap, "asset_number") ?: continue
            if (assetNo.isBlank()) continue

            val cjId = aliasMapper.getValue(rowMap, "cj_id") ?: ""
            val exUserId = aliasMapper.getValue(rowMap, "ex_user") ?: ""
            val dateStr = aliasMapper.getValue(rowMap, "date") ?: "0000-00-00"
            val note = aliasMapper.getValue(rowMap, "note") ?: ""

            val user = userMap[cjId]
            val exUser = if (exUserId.isNotBlank()) userMap[exUserId] else null

            tradeLogs.add(
                TradeLog(
                    assetNo = assetNo,
                    cjId = cjId,
                    dateStr = dateStr,
                    exUserId = exUserId,
                    note = note,
                    exUserName = exUser?.userName ?: exUserId,
                    exUserPart = exUser?.department ?: "",
                    joinedName = user?.userName ?: cjId,
                    joinedPart = user?.department ?: ""
                )
            )
        }
        return tradeLogs
    }
}

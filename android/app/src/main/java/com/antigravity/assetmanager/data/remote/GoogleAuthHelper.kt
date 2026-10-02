package com.antigravity.assetmanager.data.remote

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 구글 계정 인증 및 OAuth2 Bearer Access Token 획득 관리 객체
 */
class GoogleAuthHelper(private val context: Context) {

    companion object {
        const val CLIENT_ID = "876684580795-l4nj5d5k5uh111j7oc1a1seb7877mtg6.apps.googleusercontent.com"
        val SCOPES = arrayOf(
            Scope("profile"),
            Scope("email"),
            Scope("https://www.googleapis.com/auth/drive.readonly"),
            Scope("https://www.googleapis.com/auth/drive.file"),
            Scope("https://www.googleapis.com/auth/spreadsheets")
        )
    }

    private val prefs = context.getSharedPreferences("asset_auth_prefs", Context.MODE_PRIVATE)

    val googleSignInClient: GoogleSignInClient by lazy {
        val gsoBuilder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestIdToken(CLIENT_ID)
            .requestServerAuthCode(CLIENT_ID, false)

        for (scope in SCOPES) {
            gsoBuilder.requestScopes(scope)
        }

        GoogleSignIn.getClient(context, gsoBuilder.build())
    }

    fun getSignInIntent(): Intent {
        return googleSignInClient.signInIntent
    }

    fun getLastSignedInAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    fun saveAccessToken(token: String) {
        prefs.edit().putString("access_token", token).apply()
    }

    fun getAccessToken(): String? {
        return prefs.getString("access_token", null)
    }

    /**
     * Google Play Services의 GoogleAuthUtil을 사용하여
     * Google Drive 및 Sheets REST API 호출에 필요한 OAuth2 Access Token을 비동기로 획득합니다.
     */
    suspend fun fetchOAuthAccessToken(): String? = withContext(Dispatchers.IO) {
        val account = getLastSignedInAccount() ?: return@withContext null
        try {
            val androidAccount = account.account ?: return@withContext null
            val scopeString = "oauth2:email profile https://www.googleapis.com/auth/drive.readonly https://www.googleapis.com/auth/drive.file https://www.googleapis.com/auth/spreadsheets"
            val token = GoogleAuthUtil.getToken(context, androidAccount, scopeString)
            saveAccessToken(token)
            token
        } catch (e: Exception) {
            e.printStackTrace()
            // 토큰 직접 획득 실패 시 저장된 토큰 반환
            getAccessToken()
        }
    }

    fun clearAuth() {
        prefs.edit().clear().apply()
        googleSignInClient.signOut()
    }

    val isAuthenticated: Boolean
        get() = getLastSignedInAccount() != null && getAccessToken() != null
}

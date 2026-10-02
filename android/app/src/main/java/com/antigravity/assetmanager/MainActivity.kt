package com.antigravity.assetmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.antigravity.assetmanager.data.local.AppDatabase
import com.antigravity.assetmanager.data.remote.GoogleAuthHelper
import com.antigravity.assetmanager.data.remote.GoogleApiClient
import com.antigravity.assetmanager.data.repository.AssetRepositoryImpl
import com.antigravity.assetmanager.ui.screens.MainScreen
import com.antigravity.assetmanager.ui.theme.AssetManagerTheme
import com.antigravity.assetmanager.ui.viewmodel.AssetViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var authHelper: GoogleAuthHelper
    private lateinit var mainViewModel: AssetViewModel

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            account?.let {
                lifecycleScope.launch {
                    val token = authHelper.fetchOAuthAccessToken()
                    mainViewModel.updateAuthStatus()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        authHelper = GoogleAuthHelper(this)
        val database = AppDatabase.getInstance(this)
        val apiClient = GoogleApiClient(authHelper)
        val repository = AssetRepositoryImpl(database.assetDao(), apiClient)

        val viewModel: AssetViewModel by viewModels {
            AssetViewModel.Factory(repository, authHelper)
        }
        mainViewModel = viewModel

        setContent {
            AssetManagerTheme {
                MainScreen(
                    viewModel = viewModel,
                    onGoogleSignInClick = {
                        signInLauncher.launch(authHelper.getSignInIntent())
                    }
                )
            }
        }
    }
}

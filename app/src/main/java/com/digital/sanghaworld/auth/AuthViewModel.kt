package com.digital.sanghaworld.auth

import android.app.Application
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.digital.sanghaworld.BuildConfig
import com.digital.sanghaworld.hall.HallApi
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AuthUiState(
    val ready: Boolean = false,
    val signedIn: Boolean = false,
    val displayName: String? = null,
    val loading: Boolean = false,
    val error: String? = null
)

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val tokens = TokenStore(application)
    val api = HallApi(tokens)
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val signedIn = withContext(Dispatchers.IO) { restore() }
            _state.value = AuthUiState(ready = true, signedIn = signedIn, displayName = tokens.displayName)
        }
    }

    fun signInWithGoogle(activityContext: Context) {
        if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) {
            _state.value = _state.value.copy(
                error = "Add GOOGLE_WEB_CLIENT_ID to local.properties (Google Cloud Web client ID)."
            )
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val result = runCatching { requestGoogleIdToken(activityContext) }
            val idToken = result.getOrNull()
            if (idToken == null) {
                val message = result.exceptionOrNull()?.message ?: "Google sign-in was cancelled."
                _state.value = _state.value.copy(loading = false, error = message)
                return@launch
            }
            val ok = withContext(Dispatchers.IO) { api.loginWithGoogle(idToken) }
            _state.value = AuthUiState(
                ready = true,
                signedIn = ok,
                displayName = tokens.displayName,
                loading = false,
                error = if (ok) null else "Could not reach the server or Google token was rejected."
            )
        }
    }

    fun signOut() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { api.logout() }
            _state.value = AuthUiState(ready = true, signedIn = false)
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    private fun restore(): Boolean {
        if (!tokens.isSignedIn()) return false
        return runCatching { api.me() }.isSuccess || runCatching { api.refreshSession() }.isSuccess
    }

    private suspend fun requestGoogleIdToken(activityContext: Context): String {
        val nonce = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val nonceStr = android.util.Base64.encodeToString(
            nonce,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
        )
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setAutoSelectEnabled(true)
            .setNonce(nonceStr)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()
        val response = try {
            CredentialManager.create(activityContext).getCredential(activityContext, request)
        } catch (e: GetCredentialException) {
            throw IllegalStateException(e.errorMessage?.toString() ?: e.message ?: "Google sign-in failed")
        }
        val credential = response.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        }
        throw IllegalStateException("Unexpected credential type.")
    }
}

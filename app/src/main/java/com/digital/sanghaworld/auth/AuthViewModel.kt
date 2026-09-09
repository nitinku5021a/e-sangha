package com.digital.sanghaworld.auth

import android.app.Application
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
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
            val login = withContext(Dispatchers.IO) {
                runCatching { api.loginWithGoogle(idToken) }
            }
            val ok = login.getOrDefault(false)
            val loginError = login.exceptionOrNull()?.message
            _state.value = AuthUiState(
                ready = true,
                signedIn = ok,
                displayName = tokens.displayName,
                loading = false,
                error = when {
                    ok -> null
                    !loginError.isNullOrBlank() -> loginError
                    else -> "Could not reach the server or Google token was rejected."
                }
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
        val clientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
        val manager = CredentialManager.create(activityContext)
        val oneTap = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(false)
            .setNonce(nonceStr)
            .build()
        val response = try {
            manager.getCredential(
                activityContext,
                GetCredentialRequest.Builder().addCredentialOption(oneTap).build()
            )
        } catch (e: GetCredentialException) {
            // One Tap often returns "No credentials available" on a fresh install / new
            // package / missing Android OAuth SHA-1. Sign in with Google still shows the picker.
            val siwg = GetSignInWithGoogleOption.Builder(clientId)
                .setNonce(nonceStr)
                .build()
            try {
                manager.getCredential(
                    activityContext,
                    GetCredentialRequest.Builder().addCredentialOption(siwg).build()
                )
            } catch (e2: GetCredentialException) {
                val detail = e2.errorMessage?.toString() ?: e2.message
                    ?: e.errorMessage?.toString() ?: e.message
                    ?: "Google sign-in failed"
                val hint = if (e is NoCredentialException || e2 is NoCredentialException) {
                    " No Google account on this phone, or Google Cloud is missing an Android OAuth client for package com.digital.meditationsangha with this APK's SHA-1."
                } else {
                    ""
                }
                throw IllegalStateException(detail + hint)
            }
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

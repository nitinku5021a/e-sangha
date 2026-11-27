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
import org.json.JSONObject
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
    val email: String? = null,
    val hasPassword: Boolean = true,
    val passwordCleared: Boolean = false,
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
            val profile = if (signedIn) withContext(Dispatchers.IO) { accountProfile() } else null
            if (signedIn) {
                withContext(Dispatchers.IO) { runCatching { api.syncTimezone() } }
            }
            _state.value = AuthUiState(
                ready = true,
                signedIn = signedIn,
                displayName = tokens.displayName,
                email = profile?.email,
                hasPassword = profile?.hasPassword ?: true
            )
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
            val cleared = login.getOrNull() == true
            val ok = login.isSuccess
            val profile = if (ok) withContext(Dispatchers.IO) {
                runCatching { api.syncTimezone() }
                accountProfile()
            } else {
                null
            }
            _state.value = AuthUiState(
                ready = true,
                signedIn = ok,
                displayName = tokens.displayName,
                email = profile?.email,
                hasPassword = profile?.hasPassword ?: !cleared,
                passwordCleared = cleared,
                loading = false,
                error = when {
                    ok -> null
                    else -> readableError(login.exceptionOrNull())
                        ?: "Could not reach the server or Google token was rejected."
                }
            )
        }
    }

    fun signInWithName(displayName: String, email: String, password: String) {
        val name = displayName.trim()
        val address = email.trim()
        if (name.length < 2) {
            _state.value = _state.value.copy(error = "Enter the name you want others to see.")
            return
        }
        if (!address.matches(Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))) {
            _state.value = _state.value.copy(error = "Enter a valid email address.")
            return
        }
        if (password.length < 8) {
            _state.value = _state.value.copy(error = "Use a password of at least 8 characters.")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val login = withContext(Dispatchers.IO) {
                runCatching { api.loginWithName(name, address, password) }
            }
            val ok = login.isSuccess
            val profile = if (ok) withContext(Dispatchers.IO) {
                runCatching { api.syncTimezone() }
                accountProfile()
            } else {
                null
            }
            _state.value = AuthUiState(
                ready = true,
                signedIn = ok,
                displayName = tokens.displayName,
                email = profile?.email,
                hasPassword = profile?.hasPassword ?: true,
                loading = false,
                error = when {
                    ok -> null
                    else -> readableError(login.exceptionOrNull()) ?: "Could not reach the server."
                }
            )
        }
    }

    fun setPassword(password: String, onDone: (Boolean) -> Unit = {}) {
        if (password.length < 8) {
            _state.value = _state.value.copy(error = "Use a password of at least 8 characters.")
            onDone(false)
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val result = withContext(Dispatchers.IO) { runCatching { api.setPassword(password) } }
            val ok = result.isSuccess
            _state.value = _state.value.copy(
                loading = false,
                hasPassword = if (ok) true else _state.value.hasPassword,
                passwordCleared = if (ok) false else _state.value.passwordCleared,
                error = if (ok) null else readableError(result.exceptionOrNull()) ?: "Could not save the password."
            )
            onDone(ok)
        }
    }

    fun dismissPasswordPrompt() {
        _state.value = _state.value.copy(passwordCleared = false)
    }

    fun signOut() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { api.logout() }
            _state.value = AuthUiState(ready = true, signedIn = false)
        }
    }

    fun deleteAccount(onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val result = withContext(Dispatchers.IO) { runCatching { api.deleteAccount() } }
            val ok = result.isSuccess
            _state.value = AuthUiState(
                ready = true,
                signedIn = !ok,
                displayName = if (ok) null else tokens.displayName,
                error = if (ok) null else result.exceptionOrNull()?.message ?: "Could not delete the account."
            )
            onDone(ok)
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun updateDisplayName(name: String) {
        val trimmed = name.trim().take(120)
        if (trimmed.isBlank()) return
        tokens.displayName = trimmed
        _state.value = _state.value.copy(displayName = trimmed)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { api.updateDisplayName(trimmed) }
        }
    }

    private data class AccountProfile(val email: String?, val hasPassword: Boolean)

    private fun accountProfile(): AccountProfile? {
        val me = runCatching { api.me() }.getOrNull() ?: return null
        val email = me.optString("email").takeIf { it.isNotBlank() }
        return AccountProfile(email, me.optBoolean("hasPassword", false))
    }

    private fun readableError(error: Throwable?): String? {
        val raw = error?.message ?: return null
        val jsonStart = raw.indexOf('{')
        if (jsonStart >= 0) {
            val message = runCatching {
                JSONObject(raw.substring(jsonStart)).optJSONObject("error")?.optString("message")
            }.getOrNull()
            if (!message.isNullOrBlank()) return message
        }
        return raw
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

package dev.colabdeck

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.colabdeck.data.Accelerator
import dev.colabdeck.data.AuthState
import dev.colabdeck.data.ColabSession
import dev.colabdeck.data.SshProfile
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as ColabDeckApp).container

    val authState = container.auth.state
    val sessionState = container.sessions.state
    val profiles = container.profiles.profiles

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    private var bootstrapped = false

    fun bootstrap() {
        if (bootstrapped) return
        bootstrapped = true
        viewModelScope.launch {
            container.auth.bootstrap()
            if (container.auth.state.value is AuthState.SignedIn) {
                container.sessions.refresh()
            }
        }
    }

    fun refreshSessions() {
        viewModelScope.launch { container.sessions.refresh() }
    }

    fun signInWithToken(token: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            runCatching { container.auth.signInWithToken(token) }
                .onSuccess {
                    onSuccess()
                    container.sessions.refresh()
                    emit("Signed in to Google")
                }
                .onFailure { emit(it.message ?: "Could not sign in.") }
        }
    }

    fun signInWithOAuth(
        context: Context,
        clientId: String,
        clientSecret: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            runCatching { container.auth.signInWithOAuth(context, clientId, clientSecret) }
                .onSuccess {
                    onSuccess()
                    container.sessions.refresh()
                    emit("Signed in to Google")
                }
                .onFailure { emit(it.message ?: "Google sign-in failed.") }
        }
    }

    fun signOut() {
        container.auth.signOut()
        emit("Signed out")
    }

    fun createSession(
        accelerator: Accelerator,
        highMemory: Boolean,
        name: String,
        onSuccess: (ColabSession) -> Unit
    ) {
        viewModelScope.launch {
            container.sessions.create(accelerator, highMemory, name)
                .onSuccess {
                    emit("${it.displayName} is ready")
                    onSuccess(it)
                }
                .onFailure { emit(it.message ?: "Could not create a session.") }
        }
    }

    fun stopSession(endpoint: String) {
        viewModelScope.launch {
            container.sessions.stop(endpoint)
                .onSuccess { emit("Session terminated") }
                .onFailure { emit(it.message ?: "Could not stop the session.") }
        }
    }

    fun renameSession(endpoint: String, name: String) {
        container.sessions.rename(endpoint, name)
    }

    fun markConnected(endpoint: String) {
        container.sessions.markConnected(endpoint)
    }

    suspend fun freshSession(endpoint: String): Result<ColabSession> =
        container.sessions.fresh(endpoint)

    fun filesApi() = container.files

    fun session(endpoint: String): ColabSession? = container.sessions.session(endpoint)

    fun saveProfile(profile: SshProfile) {
        container.profiles.save(profile)
        emit("SSH profile saved")
    }

    fun deleteProfile(id: String) {
        container.profiles.delete(id)
        emit("SSH profile deleted")
    }

    fun importProfile(text: String): SshProfile? {
        val profile = container.profiles.importFromText(text)
        if (profile != null) {
            emit("Imported ${profile.host}")
        } else {
            emit("Could not find an SSH address in that text.")
        }
        return profile
    }

    fun profile(id: String): SshProfile? = container.profiles.get(id)

    fun updateClientConfig(clientId: String, clientSecret: String) {
        container.auth.updateClientConfig(clientId, clientSecret)
        emit("OAuth client settings saved")
    }

    fun clientId(): String = container.auth.clientId

    fun clientSecret(): String = container.auth.clientSecret

    fun prefs() = container.prefs

    fun sshManager() = container.ssh

    fun appPublicKey(): String = container.keys.material().publicKey

    private fun emit(message: String) {
        _events.tryEmit(message)
    }
}

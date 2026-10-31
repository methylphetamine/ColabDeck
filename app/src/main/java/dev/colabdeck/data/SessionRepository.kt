package dev.colabdeck.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SessionsUiState(
    val sessions: List<ColabSession> = emptyList(),
    val loading: Boolean = false,
    val creating: Boolean = false,
    val error: String? = null,
    val lastUpdated: Long = 0L
)

class SessionRepository(
    private val api: ColabApi,
    private val prefs: Prefs
) {
    private val _state = MutableStateFlow(SessionsUiState())
    val state: StateFlow<SessionsUiState> = _state.asStateFlow()

    private val mutex = Mutex()
    private var cache: Map<String, ColabSession> = emptyMap()

    suspend fun refresh(showLoading: Boolean = true): Result<Unit> = mutex.withLock {
        if (showLoading) {
            _state.update { it.copy(loading = true, error = null) }
        }
        try {
            val assignments = api.listAssignments()
            val meta = prefs.sessionMeta()
            val merged = assignments.map { merge(it, meta[it.endpoint]) }
                .sortedWith(compareByDescending<ColabSession> { it.isGpu || it.isTpu }.thenBy { it.hardwareLabel })
            cache = merged.associateBy { it.endpoint }
            _state.value = SessionsUiState(
                sessions = merged,
                loading = false,
                creating = _state.value.creating,
                error = null,
                lastUpdated = System.currentTimeMillis()
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    loading = false,
                    error = e.message ?: "Could not load Colab sessions."
                )
            }
            Result.failure(e)
        }
    }

    suspend fun create(
        accelerator: Accelerator,
        highMemory: Boolean,
        name: String
    ): Result<ColabSession> {
        _state.update { it.copy(creating = true, error = null) }
        return try {
            val created = api.createSession(accelerator, highMemory)
            val meta = prefs.sessionMeta()
            val finalName = name.trim().ifBlank {
                "${accelerator.shortLabel.lowercase()}-${created.endpoint.takeLast(4)}"
            }
            meta[created.endpoint] = SessionMeta(
                endpoint = created.endpoint,
                name = finalName,
                createdAt = System.currentTimeMillis()
            )
            prefs.saveSessionMeta(meta)

            refresh(showLoading = false)
            val session = cache[created.endpoint] ?: created.copy(name = finalName)
            _state.update { it.copy(creating = false, error = null) }
            Result.success(session)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update {
                it.copy(creating = false, error = e.message ?: "Could not create a session.")
            }
            Result.failure(e)
        }
    }

    suspend fun stop(endpoint: String): Result<Unit> = try {
        api.stopSession(endpoint)
        val meta = prefs.sessionMeta()
        meta.remove(endpoint)
        prefs.saveSessionMeta(meta)
        cache = cache - endpoint
        _state.update { it.copy(sessions = it.sessions.filterNot { s -> s.endpoint == endpoint }) }
        refresh(showLoading = false)
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        _state.update { it.copy(error = e.message ?: "Could not stop the session.") }
        Result.failure(e)
    }

    /** Returns a session with a freshly minted runtime proxy token. */
    suspend fun fresh(endpoint: String): Result<ColabSession> = try {
        val assignments = api.listAssignments()
        val meta = prefs.sessionMeta()
        val found = assignments.firstOrNull { it.endpoint == endpoint }
        if (found == null) {
            Result.failure(ApiException(404, "That runtime is no longer assigned. Create a new session."))
        } else {
            val merged = merge(found, meta[endpoint])
            cache = cache + (endpoint to merged)
            _state.update { state ->
                state.copy(sessions = state.sessions.map { if (it.endpoint == endpoint) merged else it })
            }
            Result.success(merged)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    fun session(endpoint: String): ColabSession? = cache[endpoint]

    fun rename(endpoint: String, name: String) {
        val meta = prefs.sessionMeta()
        val existing = meta[endpoint]
        meta[endpoint] = (existing ?: SessionMeta(endpoint)).copy(name = name.trim())
        prefs.saveSessionMeta(meta)
        val updated = cache[endpoint]?.copy(name = name.trim())
        if (updated != null) {
            cache = cache + (endpoint to updated)
            _state.update { state ->
                state.copy(sessions = state.sessions.map { if (it.endpoint == endpoint) updated else it })
            }
        }
    }

    fun markConnected(endpoint: String) {
        val meta = prefs.sessionMeta()
        meta[endpoint] = (meta[endpoint] ?: SessionMeta(endpoint)).copy(
            lastConnectedAt = System.currentTimeMillis()
        )
        prefs.saveSessionMeta(meta)
    }

    private fun merge(session: ColabSession, meta: SessionMeta?): ColabSession {
        val fallback = "${session.hardwareLabel.lowercase().replace(" ", "-")}-${session.endpoint.takeLast(4)}"
        return session.copy(
            name = meta?.name?.takeIf { it.isNotBlank() } ?: fallback,
            lastConnectedAt = meta?.lastConnectedAt ?: 0L
        )
    }
}

package dev.colabdeck.data

import android.content.Context
import dev.colabdeck.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.security.MessageDigest
import java.security.SecureRandom
class AuthRepository(
    private val prefs: Prefs,
    private val secure: SecureStore
) {
    private val _state = MutableStateFlow<AuthState>(AuthState.SignedOut)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val mutex = Mutex()

    val clientId: String
        get() = prefs.clientId.ifBlank { BuildConfig.DEFAULT_CLIENT_ID }

    val clientSecret: String
        get() = (secure.get(KEY_CLIENT_SECRET).orEmpty()).ifBlank { BuildConfig.DEFAULT_CLIENT_SECRET }

    val canRefresh: Boolean
        get() = !secure.get(KEY_REFRESH).isNullOrBlank() && clientId.isNotBlank()

    init {
        restore()
    }

    private fun restore() {
        val token = secure.get(KEY_ACCESS)
        if (token.isNullOrBlank()) {
            _state.value = AuthState.SignedOut
            return
        }
        _state.value = AuthState.SignedIn(
            email = prefs.email.ifBlank { null },
            name = prefs.displayName.ifBlank { null },
            expiresAt = prefs.tokenExpiresAt,
            canRefresh = canRefresh
        )
    }

    suspend fun bootstrap() {
        if (_state.value !is AuthState.SignedIn) return
        runCatching { accessToken() }
    }

    suspend fun accessToken(): String = mutex.withLock {
        val token = secure.get(KEY_ACCESS)
        val expiresAt = prefs.tokenExpiresAt
        val now = System.currentTimeMillis()

        if (!token.isNullOrBlank() && expiresAt > now + 60_000L) {
            return@withLock token
        }

        val refreshToken = secure.get(KEY_REFRESH)
        if (!refreshToken.isNullOrBlank() && clientId.isNotBlank()) {
            try {
                val refreshed = withContext(Dispatchers.IO) { refresh(refreshToken) }
                storeTokens(refreshed)
                return@withLock refreshed.accessToken
            } catch (e: Exception) {
                markExpired()
                throw AuthException(e.message ?: "Google sign-in expired. Sign in again.")
            }
        }

        if (!token.isNullOrBlank() && expiresAt > now) {
            return@withLock token
        }

        markExpired()
        throw AuthException("Google sign-in expired. Sign in again.")
    }

    suspend fun signInWithToken(raw: String) {
        _state.value = AuthState.Loading
        try {
            val pasted = parsePastedToken(raw)
            if (pasted.clientId.isNotBlank()) prefs.clientId = pasted.clientId
            if (pasted.clientSecret.isNotBlank()) secure.put(KEY_CLIENT_SECRET, pasted.clientSecret)

            // Validate against the Colab control plane, not userinfo: a token
            // authorized only for the Colab scope cannot call userinfo.
            withContext(Dispatchers.IO) { validateColabToken(pasted.accessToken) }

            prefs.email = ""
            prefs.displayName = ""
            storeTokens(
                OAuthTokens(
                    accessToken = pasted.accessToken,
                    refreshToken = pasted.refreshToken,
                    idToken = pasted.idToken,
                    expiresInSeconds = pasted.expiresInSeconds
                ),
                info = null
            )
        } catch (e: Exception) {
            _state.value = AuthState.Error(e.message ?: "Could not use that access token.")
            throw e
        }
    }

    suspend fun signInWithOAuth(context: Context, clientId: String, clientSecret: String) {
        _state.value = AuthState.Loading
        try {
            val tokens = OAuthLoopback.authorize(context, clientId, clientSecret)
            val info = withContext(Dispatchers.IO) { fetchUserInfo(tokens.accessToken) }
            prefs.clientId = clientId
            if (clientSecret.isNotBlank()) {
                secure.put(KEY_CLIENT_SECRET, clientSecret)
            } else {
                secure.remove(KEY_CLIENT_SECRET)
            }
            storeTokens(tokens, info)
        } catch (e: Exception) {
            _state.value = AuthState.Error(e.message ?: "Google sign-in failed.")
            throw e
        }
    }

    /**
     * Builds the authorization URL for Google's official Colab CLI OAuth client
     * (the same client `colab auth login` uses). No Google Cloud project or
     * client ID is required from the user: the flow is the remote copy-paste
     * one (PKCE + https://sdk.cloud.google.com/applicationdefaultauthcode.html),
     * which works on a phone because the landing page prints the code.
     *
     * The PKCE verifier is stored (encrypted) so it survives the trip to the
     * browser and back; call [completeColabCliSignIn] with the pasted code.
     */
    fun colabCliAuthUrl(): String {
        val verifier = randomPkceVerifier()
        val challenge = pkceChallenge(verifier)
        val state = randomState()
        secure.put(KEY_OAUTH_VERIFIER, verifier)
        secure.put(KEY_OAUTH_STATE, state)

        return buildString {
            append(COLAB_CLI_AUTH_ENDPOINT)
            append("?response_type=code")
            append("&client_id=").append(urlEncode(COLAB_CLI_CLIENT_ID))
            append("&redirect_uri=").append(urlEncode(COLAB_CLI_REDIRECT_URI))
            append("&scope=").append(urlEncode(COLAB_CLI_SCOPES.joinToString(" ")))
            append("&state=").append(urlEncode(state))
            append("&code_challenge=").append(urlEncode(challenge))
            append("&code_challenge_method=S256")
            append("&prompt=consent")
            append("&token_usage=remote")
            append("&access_type=offline")
        }
    }

    suspend fun completeColabCliSignIn(code: String) {
        _state.value = AuthState.Loading
        try {
            val verifier = secure.get(KEY_OAUTH_VERIFIER)
                ?: throw AuthException("Sign-in was not started. Tap \"Open Google sign-in\" first.")
            val tokens = withContext(Dispatchers.IO) {
                exchangeColabCliCode(code.trim(), verifier)
            }

            // Use Colab's own client so refresh_token grants can be renewed.
            prefs.clientId = COLAB_CLI_CLIENT_ID
            secure.put(KEY_CLIENT_SECRET, COLAB_CLI_CLIENT_SECRET)

            withContext(Dispatchers.IO) { validateColabToken(tokens.accessToken) }
            val info = withContext(Dispatchers.IO) { fetchUserInfo(tokens.accessToken) }
            storeTokens(tokens, info)

            secure.remove(KEY_OAUTH_VERIFIER)
            secure.remove(KEY_OAUTH_STATE)
        } catch (e: Exception) {
            _state.value = AuthState.Error(e.message ?: "Google sign-in failed.")
            throw e
        }
    }

    private fun exchangeColabCliCode(code: String, verifier: String): OAuthTokens {
        val form = FormBody.Builder()
            .add("code", code)
            .add("client_id", COLAB_CLI_CLIENT_ID)
            .add("client_secret", COLAB_CLI_CLIENT_SECRET)
            .add("redirect_uri", COLAB_CLI_REDIRECT_URI)
            .add("grant_type", "authorization_code")
            .add("code_verifier", verifier)

        val request = Request.Builder()
            .url(TOKEN_ENDPOINT)
            .post(form.build())
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw AuthException(oauthError(body, response.code))
            }
            val obj = json.parseToJsonElement(body).jsonObject
            val access = obj["access_token"]?.jsonPrimitive?.content
                ?: throw AuthException("Google did not return an access token.")
            return OAuthTokens(
                accessToken = access,
                refreshToken = obj["refresh_token"]?.jsonPrimitive?.content,
                idToken = obj["id_token"]?.jsonPrimitive?.content,
                expiresInSeconds = obj["expires_in"]?.jsonPrimitive?.longOrNull ?: 3300L
            )
        }
    }

    /**
     * Drops only the access token (keeps the refresh token) so the next
     * [accessToken] call forces a refresh. Used on a 401 to recover without
     * signing the user out.
     */
    fun invalidateAccessToken() {
        secure.remove(KEY_ACCESS)
        prefs.tokenExpiresAt = 0L
        val current = _state.value
        if (current is AuthState.SignedIn) {
            _state.value = current.copy(expiresAt = 0L, canRefresh = canRefresh)
        }
    }

    fun updateClientConfig(clientId: String, clientSecret: String) {        prefs.clientId = clientId.trim()
        if (clientSecret.isBlank()) {
            secure.remove(KEY_CLIENT_SECRET)
        } else {
            secure.put(KEY_CLIENT_SECRET, clientSecret.trim())
        }
        if (_state.value is AuthState.SignedIn) {
            restore()
        }
    }

    fun signOut() {
        secure.remove(KEY_ACCESS)
        secure.remove(KEY_REFRESH)
        secure.remove(KEY_ID)
        secure.remove(KEY_OAUTH_VERIFIER)
        secure.remove(KEY_OAUTH_STATE)
        prefs.tokenExpiresAt = 0L
        prefs.email = ""
        prefs.displayName = ""
        _state.value = AuthState.SignedOut
    }

    fun markExpired() {
        secure.remove(KEY_ACCESS)
        prefs.tokenExpiresAt = 0L
        _state.value = AuthState.Error("Google sign-in expired. Sign in again.")
    }

    private fun randomPkceVerifier(): String {
        val bytes = ByteArray(64)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun pkceChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    private fun randomState(): String {
        val bytes = ByteArray(24)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun storeTokens(tokens: OAuthTokens, info: UserInfo? = null) {        secure.put(KEY_ACCESS, tokens.accessToken)
        tokens.refreshToken?.takeIf { it.isNotBlank() }?.let { secure.put(KEY_REFRESH, it) }
        tokens.idToken?.takeIf { it.isNotBlank() }?.let { secure.put(KEY_ID, it) }
        prefs.tokenExpiresAt = System.currentTimeMillis() + tokens.expiresInSeconds * 1000L
        if (info != null) {
            prefs.email = info.email
            prefs.displayName = info.name
        }
        _state.value = AuthState.SignedIn(
            email = prefs.email.ifBlank { null },
            name = prefs.displayName.ifBlank { null },
            expiresAt = prefs.tokenExpiresAt,
            canRefresh = canRefresh
        )
    }

    private fun refresh(refreshToken: String): OAuthTokens {
        val form = FormBody.Builder()
            .add("client_id", clientId)
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)

        if (clientSecret.isNotBlank()) {
            form.add("client_secret", clientSecret)
        }

        val request = Request.Builder()
            .url(TOKEN_ENDPOINT)
            .post(form.build())
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw AuthException(oauthError(body, response.code))
            }
            val obj = json.parseToJsonElement(body).jsonObject
            val access = obj["access_token"]?.jsonPrimitive?.content
                ?: throw AuthException("Google did not return an access token.")
            return OAuthTokens(
                accessToken = access,
                refreshToken = obj["refresh_token"]?.jsonPrimitive?.content ?: refreshToken,
                idToken = obj["id_token"]?.jsonPrimitive?.content,
                expiresInSeconds = obj["expires_in"]?.jsonPrimitive?.longOrNull ?: 3300L
            )
        }
    }

    /**
     * Checks a pasted token against Colab's control plane. This is the only
     * scope the app needs, and unlike userinfo it accepts a token that carries
     * just https://www.googleapis.com/auth/colaboratory.
     */
    private fun validateColabToken(accessToken: String) {
        val request = Request.Builder()
            .url("https://colab.research.google.com/tun/m/assignments?authuser=0")
            .header("Authorization", "Bearer $accessToken")
            .header("Accept", "application/json")
            .header("X-Colab-Client-Agent", "colabdeck")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            when (response.code) {
                401 -> throw AuthException(
                    "Google rejected this token. It was revoked or has expired — generate a new one."
                )
                403 -> throw AuthException(
                    "This Google account is not allowed to use Colab (HTTP 403)."
                )
            }
            // Other non-2xx responses are transient backend/quota issues and
            // should not block storing a token that Google just issued.
        }
    }

    /**
     * Best-effort profile lookup for the interactive OAuth flow. A token
     * authorized only for the Colab scope is valid for the Colab API but is
     * not allowed to call userinfo, so a 403 there must not block sign-in.
     * Only a 401 means the token itself is bad.
     */
    private fun fetchUserInfo(accessToken: String): UserInfo? {
        val request = Request.Builder()
            .url("https://www.googleapis.com/oauth2/v3/userinfo")
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                if (response.code == 401) {
                    throw AuthException(
                        "Google rejected this access token. It may be expired or missing the Colab scope."
                    )
                }
                return null
            }
            val body = response.body?.string().orEmpty()
            return runCatching {
                val obj = json.parseToJsonElement(body).jsonObject
                UserInfo(
                    email = obj["email"]?.jsonPrimitive?.content.orEmpty(),
                    name = obj["name"]?.jsonPrimitive?.content.orEmpty()
                )
            }.getOrNull()
        }
    }

    private fun parsePastedToken(raw: String): PastedToken {
        val trimmed = raw.trim()
        if (trimmed.startsWith("{")) {
            val obj = json.parseToJsonElement(trimmed).jsonObject
            val access = obj["access_token"]?.jsonPrimitive?.content
                ?: obj["token"]?.jsonPrimitive?.content
                ?: throw AuthException("That JSON does not contain an access_token.")
            return PastedToken(
                accessToken = access,
                refreshToken = obj["refresh_token"]?.jsonPrimitive?.content,
                idToken = obj["id_token"]?.jsonPrimitive?.content,
                clientId = obj["client_id"]?.jsonPrimitive?.content.orEmpty(),
                clientSecret = obj["client_secret"]?.jsonPrimitive?.content.orEmpty(),
                expiresInSeconds = obj["expires_in"]?.jsonPrimitive?.longOrNull ?: 3300L
            )
        }
        if (trimmed.isBlank()) throw AuthException("Paste a Google access token first.")
        return PastedToken(accessToken = trimmed)
    }

    private fun oauthError(body: String, status: Int): String {
        val parsed = runCatching {
            val obj = json.parseToJsonElement(body).jsonObject
            obj["error_description"]?.jsonPrimitive?.content
                ?: obj["error"]?.jsonPrimitive?.content
        }.getOrNull()
        return when {
            !parsed.isNullOrBlank() -> "Google sign-in failed: $parsed"
            status == 400 -> "Google rejected the token request. Check the OAuth client ID and secret."
            else -> "Google sign-in failed (HTTP $status)."
        }
    }

    private data class UserInfo(val email: String, val name: String)

    private data class PastedToken(
        val accessToken: String,
        val refreshToken: String? = null,
        val idToken: String? = null,
        val clientId: String = "",
        val clientSecret: String = "",
        val expiresInSeconds: Long = 3300L
    )

    private companion object {
        const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
        const val KEY_ACCESS = "google_access_token"
        const val KEY_REFRESH = "google_refresh_token"
        const val KEY_ID = "google_id_token"
        const val KEY_CLIENT_SECRET = "google_client_secret"
        const val KEY_OAUTH_VERIFIER = "colab_cli_pkce_verifier"
        const val KEY_OAUTH_STATE = "colab_cli_oauth_state"

        const val COLAB_CLI_CLIENT_ID =
            "764086051850-6qr4p6gpi6hn506pt8ejuq83di341hur.apps.googleusercontent.com"
        const val COLAB_CLI_CLIENT_SECRET = "d-FL95Q19q7MQmFpd7hHD0Ty"
        const val COLAB_CLI_REDIRECT_URI =
            "https://sdk.cloud.google.com/applicationdefaultauthcode.html"
        const val COLAB_CLI_AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/auth"
        val COLAB_CLI_SCOPES = listOf(
            "openid",
            "https://www.googleapis.com/auth/userinfo.profile",
            "https://www.googleapis.com/auth/userinfo.email",
            "https://www.googleapis.com/auth/cloud-platform",
            "https://www.googleapis.com/auth/colaboratory",
            "https://www.googleapis.com/auth/drive.file"
        )
    }
}

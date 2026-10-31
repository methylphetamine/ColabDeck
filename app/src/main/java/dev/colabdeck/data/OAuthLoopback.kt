package dev.colabdeck.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Google OAuth 2.0 for native apps using PKCE and a loopback redirect
 * (RFC 8252). The user creates a "Desktop app" OAuth client in Google Cloud,
 * which permits http://127.0.0.1:<random port> without registering each port.
 *
 * No client secret is required for the authorization-code + PKCE flow; if the
 * user supplies one it is forwarded for compatibility with desktop clients.
 */
object OAuthLoopback {

    private const val AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
    private const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
    private val SCOPES = listOf(
        "openid",
        "email",
        "profile",
        "https://www.googleapis.com/auth/colaboratory"
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val random = SecureRandom()

    suspend fun authorize(
        context: Context,
        clientId: String,
        clientSecret: String
    ): OAuthTokens {
        require(clientId.isNotBlank()) { "OAuth client ID is required." }

        val verifier = randomString(64)
        val challenge = base64Url(sha256(verifier.toByteArray(Charsets.US_ASCII)))
        val state = randomString(24)

        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val redirectUri = "http://127.0.0.1:${server.localPort}"

        val authUri = Uri.parse(AUTH_ENDPOINT).buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", SCOPES.joinToString(" "))
            .appendQueryParameter("access_type", "offline")
            .appendQueryParameter("prompt", "consent")
            .appendQueryParameter("include_granted_scopes", "true")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", state)
            .build()

        try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                launchBrowser(context, authUri)
            }
            val code = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                awaitAuthorizationCode(server, state)
            }
            return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                exchangeCode(code, redirectUri, verifier, clientId, clientSecret)
            }
        } finally {
            runCatching { server.close() }
        }
    }

    private fun launchBrowser(context: Context, uri: Uri) {
        try {
            val intent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setUrlBarHidingEnabled(true)
                .build()
                .intent
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.data = uri
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            try {
                val fallback = Intent(Intent.ACTION_VIEW, uri)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fallback)
            } catch (e: Exception) {
                throw AuthException("No browser is available to complete Google sign-in.")
            }
        }
    }

    private fun awaitAuthorizationCode(server: ServerSocket, expectedState: String): String {
        server.soTimeout = 300_000
        val socket = try {
            server.accept()
        } catch (e: Exception) {
            throw AuthException("Timed out waiting for Google sign-in. Try again.")
        }

        socket.use {
            val reader = BufferedReader(InputStreamReader(it.getInputStream(), Charsets.US_ASCII))
            val requestLine = reader.readLine() ?: throw AuthException("Empty OAuth callback.")
            val target = requestLine.split(' ').getOrNull(1)
                ?: throw AuthException("Malformed OAuth callback.")

            val callback = Uri.parse("http://127.0.0.1$target")
            val error = callback.getQueryParameter("error")
            val code = callback.getQueryParameter("code")
            val returnedState = callback.getQueryParameter("state")

            val success = error == null && code != null
            respond(it, success, error)

            if (error != null) {
                val description = callback.getQueryParameter("error_description")
                throw AuthException(description ?: "Google returned: $error")
            }
            if (returnedState != expectedState) {
                throw AuthException("OAuth state mismatch. Sign-in was not completed.")
            }
            return code ?: throw AuthException("Google did not return an authorization code.")
        }
    }

    private fun respond(
        socket: java.net.Socket,
        success: Boolean,
        error: String?
    ) {
        val title = if (success) "ColabDeck is connected" else "Sign-in failed"
        val message = if (success) {
            "You can close this tab and return to the ColabDeck app."
        } else {
            "Google reported: ${error ?: "unknown error"}. Return to ColabDeck and try again."
        }
        val html = """
            <!doctype html>
            <html><head><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>$title</title>
            <style>
              body{margin:0;min-height:100vh;display:grid;place-items:center;background:#070B10;color:#D7E2EE;font-family:-apple-system,Roboto,sans-serif}
              .card{max-width:420px;margin:24px;padding:28px;border:1px solid #243244;border-radius:20px;background:#0E141C}
              h1{font-size:20px;margin:0 0 10px;color:#22D3EE}
              p{line-height:1.5;color:#94A3B8;margin:0}
              .dot{width:10px;height:10px;border-radius:50%;background:${if (success) "#34D399" else "#FB7185"};display:inline-block;margin-right:8px}
            </style></head>
            <body><div class="card"><h1><span class="dot"></span>$title</h1><p>$message</p></div></body></html>
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val header = buildString {
            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: text/html; charset=utf-8\r\n")
            append("Content-Length: ${html.size}\r\n")
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n\r\n")
        }.toByteArray(Charsets.US_ASCII)

        runCatching {
            socket.getOutputStream().write(header)
            socket.getOutputStream().write(html)
            socket.getOutputStream().flush()
        }
    }

    private fun exchangeCode(
        code: String,
        redirectUri: String,
        verifier: String,
        clientId: String,
        clientSecret: String
    ): OAuthTokens {
        val form = FormBody.Builder()
            .add("code", code)
            .add("client_id", clientId)
            .add("redirect_uri", redirectUri)
            .add("grant_type", "authorization_code")
            .add("code_verifier", verifier)

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
            val accessToken = obj["access_token"]?.jsonPrimitive?.content
                ?: throw AuthException("Google did not return an access token.")
            return OAuthTokens(
                accessToken = accessToken,
                refreshToken = obj["refresh_token"]?.jsonPrimitive?.content,
                idToken = obj["id_token"]?.jsonPrimitive?.content,
                expiresInSeconds = obj["expires_in"]?.jsonPrimitive?.content?.toLongOrNull() ?: 3300L
            )
        }
    }

    private fun oauthError(body: String, status: Int): String {
        val parsed = runCatching {
            val obj = json.parseToJsonElement(body).jsonObject
            obj["error_description"]?.jsonPrimitive?.content
                ?: obj["error"]?.jsonPrimitive?.content
        }.getOrNull()
        return when {
            !parsed.isNullOrBlank() -> "Google sign-in failed: $parsed"
            status == 400 -> "Google rejected the sign-in request. Check the OAuth client type (Desktop app) and client ID."
            else -> "Google sign-in failed (HTTP $status)."
        }
    }

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun base64Url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun randomString(length: Int): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
        val out = StringBuilder(length)
        repeat(length) { out.append(alphabet[random.nextInt(alphabet.length)]) }
        return out.toString()
    }
}

class AuthException(message: String) : Exception(message)

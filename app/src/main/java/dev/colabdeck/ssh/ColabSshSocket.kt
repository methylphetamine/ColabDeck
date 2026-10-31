package dev.colabdeck.ssh

import dev.colabdeck.data.ColabSession
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Opens Colab's official SSH-over-WebSocket endpoint:
 *
 *   wss://<runtime-proxy-host>/colab/ssh?colab-runtime-proxy-token=...
 *
 * The public key is sent verbatim in X-Colab-Ssh-Pubkey. Only Ed25519 and
 * ECDSA keys are accepted by the server; RSA is rejected.
 */
object ColabSshSocket {

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    suspend fun connect(session: ColabSession, publicKey: String): WebSocketSocket {
        val base = session.proxyUrl.toHttpUrlOrNull()
            ?: throw SshTransportException("Colab returned an invalid runtime URL.")

        // OkHttp's HttpUrl only knows http/https, so build the query with an
        // https URL and flip the scheme to wss/ws afterwards. Calling
        // .scheme("wss") on the builder throws "unexpected scheme: wss".
        val httpsUrl = base.newBuilder()
            .encodedPath("/colab/ssh")
            .encodedQuery(null)
            .addQueryParameter("colab-runtime-proxy-token", session.proxyToken)
            .addQueryParameter("colab-client-agent", "colabdeck")
            .addQueryParameter("colab-client-agent", "ssh-mode-interactive")
            .build()
        val url = httpsUrl.toString().replaceFirst(
            Regex("^https?://"),
            if (base.isHttps) "wss://" else "ws://"
        )

        val request = Request.Builder()
            .url(url)
            .header("X-Colab-Ssh-Pubkey", publicKey)
            .header("X-Colab-Client-Agent", "colabdeck")
            .build()

        return withTimeout(25_000) {
            suspendCancellableCoroutine { continuation ->
                var socket: WebSocketSocket? = null

                val listener = object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        val transport = WebSocketSocket(webSocket)
                        socket = transport
                        if (continuation.isActive) continuation.resume(transport)
                    }

                    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                        socket?.onBytes(bytes.toByteArray())
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        socket?.onClosed()
                        webSocket.close(1000, null)
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        socket?.onClosed()
                    }

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?
                    ) {
                        val status = response?.code
                        val message = handshakeMessage(status, t.message)
                        val error = SshTransportException(message, status)
                        if (socket != null) {
                            socket?.onClosed(error)
                        } else if (continuation.isActive) {
                            continuation.resumeWithException(error)
                        }
                    }
                }

                val webSocket = client.newWebSocket(request, listener)
                continuation.invokeOnCancellation {
                    runCatching { webSocket.cancel() }
                }
            }
        }
    }

    private fun handshakeMessage(status: Int?, detail: String?): String = when (status) {
        400 -> "Colab rejected the SSH public key. Create a new session and retry."
        401 -> "The runtime proxy token is invalid or expired. Refresh sessions and retry."
        403 -> "This account is not allowed to open an SSH session on this runtime."
        404 -> "SSH is not exposed on this runtime. Create a new Colab session from ColabDeck."
        429 -> "SSH session limit reached for this runtime. Disconnect another client and retry."
        502 -> "The runtime's SSH daemon is unreachable. The runtime may be unhealthy."
        null -> "Could not reach Colab's SSH endpoint${if (!detail.isNullOrBlank()) ": $detail" else "."}"
        else -> "Colab SSH handshake failed (HTTP $status)${if (!detail.isNullOrBlank()) ": $detail" else "."}"
    }
}

class SshTransportException(
    message: String,
    val statusCode: Int? = null
) : IOException(message)

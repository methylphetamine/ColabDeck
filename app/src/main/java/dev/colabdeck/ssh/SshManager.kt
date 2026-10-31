package dev.colabdeck.ssh

import com.jcraft.jsch.Channel
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import com.jcraft.jsch.SocketFactory
import com.jcraft.jsch.UserInfo
import dev.colabdeck.data.ColabSession
import dev.colabdeck.data.SshKeyManager
import dev.colabdeck.data.SshProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

enum class SshState {
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    FAILED
}

interface SshListener {
    fun onOutput(bytes: ByteArray)
    fun onState(state: SshState)
    fun onError(message: String)
}

interface SshChannel {
    val input: InputStream
    val output: OutputStream
    val isOpen: Boolean
    fun resize(cols: Int, rows: Int)
    fun close()
}

class SshConnection(
    private val connector: suspend () -> SshChannel,
    private val listener: SshListener
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val writeLock = Any()

    @Volatile
    private var channel: SshChannel? = null

    @Volatile
    private var userClosed = false

    @Volatile
    private var pendingCols = 80

    @Volatile
    private var pendingRows = 24

    val isConnected: Boolean
        get() = channel?.isOpen == true

    fun start() {
        if (job != null) return
        job = scope.launch {
            var failure: String? = null
            try {
                listener.onState(SshState.CONNECTING)
                val opened = connector()
                channel = opened
                opened.resize(pendingCols, pendingRows)
                listener.onState(SshState.CONNECTED)

                val buffer = ByteArray(16 * 1024)
                while (isActive) {
                    val read = opened.input.read(buffer)
                    if (read < 0) break
                    if (read > 0) listener.onOutput(buffer.copyOf(read))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = friendlyMessage(e)
            } finally {
                channel?.close()
                channel = null
            }

            if (!userClosed) {
                if (failure != null) {
                    listener.onError(failure)
                    listener.onState(SshState.FAILED)
                } else {
                    listener.onState(SshState.DISCONNECTED)
                }
            }
            scope.cancel()
        }
    }

    fun write(bytes: ByteArray) {
        val open = channel ?: return
        runCatching {
            synchronized(writeLock) {
                open.output.write(bytes)
                open.output.flush()
            }
        }
    }

    fun resize(cols: Int, rows: Int) {
        pendingCols = cols.coerceAtLeast(1)
        pendingRows = rows.coerceAtLeast(1)
        channel?.resize(pendingCols, pendingRows)
    }

    fun disconnect() {
        userClosed = true
        runCatching { channel?.close() }
        channel = null
        job?.cancel()
        scope.cancel()
    }

    private fun friendlyMessage(error: Throwable): String = when {
        error is SshTransportException -> error.message ?: "Could not open the Colab SSH tunnel."
        error is JSchException && error.message?.contains("Auth fail", true) == true ->
            "SSH authentication failed. The runtime may have rejected the app key — create a fresh session."
        error is JSchException && error.message?.contains("timeout", true) == true ->
            "The SSH handshake timed out. Check your connection and retry."
        error is JSchException -> "SSH error: ${error.message ?: "unknown JSch error"}"
        error is IOException -> error.message ?: "Network error while opening SSH."
        else -> error.message ?: "SSH connection failed."
    }
}

class SshManager(private val keys: SshKeyManager) {

    fun connectColab(
        session: ColabSession,
        listener: SshListener
    ): SshConnection = SshConnection(
        connector = { openColab(session) },
        listener = listener
    )

    fun connectProfile(
        profile: SshProfile,
        listener: SshListener
    ): SshConnection = SshConnection(
        connector = { openProfile(profile) },
        listener = listener
    )

    private suspend fun openColab(session: ColabSession): SshChannel {
        val key = keys.material()
        val transport = ColabSshSocket.connect(session, key.publicKey)

        try {
            val jsch = JSch()
            jsch.addIdentity(
                "colabdeck",
                key.privateKey.toByteArray(Charsets.UTF_8),
                null,
                null
            )

            val ssh = jsch.getSession("root", "colab-runtime", 22)
            ssh.setSocketFactory(object : SocketFactory {
                override fun createSocket(host: String, port: Int): Socket = transport
                override fun getInputStream(socket: Socket): InputStream = socket.getInputStream()
                override fun getOutputStream(socket: Socket): OutputStream = socket.getOutputStream()
            })
            ssh.setConfig("StrictHostKeyChecking", "no")
            ssh.setConfig("PreferredAuthentications", "publickey")
            ssh.setConfig("HashKnownHosts", "no")
            ssh.connect(25_000)

            val channel = ssh.openChannel("exec") as ChannelExec
            channel.setCommand("cd /content 2>/dev/null; exec \${SHELL:-/bin/bash} -l")
            channel.setPty(true)
            channel.setPtyType("xterm-256color", 80, 24, 640, 480)
            channel.setInputStream(null)
            channel.connect(15_000)

            return JschChannel(ssh, channel, transport)
        } catch (e: Exception) {
            runCatching { transport.close() }
            throw e
        }
    }

    private suspend fun openProfile(profile: SshProfile): SshChannel {
        if (profile.host.isBlank()) throw SshTransportException("SSH profile has no host.")

        val jsch = if (profile.authType == SshProfile.AUTH_KEY) {
            if (profile.privateKey.isBlank()) {
                throw SshTransportException("This profile is set to key auth but has no private key.")
            }
            keys.jschWithPrivateKey(profile.privateKey, profile.passphrase)
        } else {
            JSch()
        }

        val ssh = jsch.getSession(profile.user.ifBlank { "root" }, profile.host, profile.port)
        ssh.setConfig("StrictHostKeyChecking", "no")
        ssh.setConfig("HashKnownHosts", "no")

        if (profile.authType == SshProfile.AUTH_KEY) {
            ssh.setConfig("PreferredAuthentications", "publickey")
        } else {
            ssh.setPassword(profile.password)
            ssh.setConfig("PreferredAuthentications", "password,keyboard-interactive")
        }

        ssh.userInfo = object : UserInfo {
            override fun getPassphrase(): String = profile.passphrase
            override fun getPassword(): String = profile.password
            override fun promptPassword(message: String?): Boolean = true
            override fun promptPassphrase(message: String?): Boolean = true
            override fun promptYesNo(message: String?): Boolean = true
            override fun showMessage(message: String?) = Unit
        }

        try {
            ssh.connect(20_000)
            val channel = ssh.openChannel("shell") as ChannelShell
            channel.setPty(true)
            channel.setPtyType("xterm-256color", 80, 24, 640, 480)
            channel.connect(10_000)
            return JschChannel(ssh, channel, null)
        } catch (e: Exception) {
            runCatching { ssh.disconnect() }
            throw e
        }
    }

    private class JschChannel(
        private val session: Session,
        private val channel: Channel,
        private val transport: WebSocketSocket?
    ) : SshChannel {
        override val input: InputStream = channel.inputStream
        override val output: OutputStream = channel.outputStream
        override val isOpen: Boolean
            get() = channel.isConnected && session.isConnected

        override fun resize(cols: Int, rows: Int) {
            // ChannelSession is package-private in JSch 0.2.x, so call the
            // inherited public setPtySize(int,int,int,int) reflectively.
            runCatching {
                val method = channel.javaClass.getMethod(
                    "setPtySize",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
                method.isAccessible = true
                method.invoke(
                    channel,
                    cols.coerceAtLeast(1),
                    rows.coerceAtLeast(1),
                    cols.coerceAtLeast(1) * 8,
                    rows.coerceAtLeast(1) * 16
                )
            }
        }

        override fun close() {
            runCatching { channel.disconnect() }
            runCatching { session.disconnect() }
            runCatching { transport?.close() }
        }
    }
}

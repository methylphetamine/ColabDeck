package dev.colabdeck.ssh

import okhttp3.WebSocket
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.io.OutputStream
import java.net.InetAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * A java.net.Socket facade over an OkHttp WebSocket. JSch only needs streams,
 * so the entire SSH protocol can travel inside the /colab/ssh WebSocket
 * without a local TCP listener.
 */
class WebSocketSocket(private val webSocket: WebSocket) : Socket() {

    private val incoming = LinkedBlockingQueue<Any>(MAX_CHUNKS)
    private val inputStream = SocketInput()
    private val outputStream = SocketOutput()

    @Volatile
    private var closed = false

    @Volatile
    private var failure: Throwable? = null

    @Volatile
    private var readTimeoutMs = 0

    private var current: ByteArray? = null
    private var position = 0

    fun onBytes(bytes: ByteArray) {
        if (closed) return
        try {
            incoming.put(bytes)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    fun onClosed(cause: Throwable? = null) {
        if (cause != null) failure = cause
        closed = true
        incoming.clear()
        incoming.offer(EOF)
    }

    override fun getInputStream(): InputStream = inputStream

    override fun getOutputStream(): OutputStream = outputStream

    override fun setSoTimeout(timeout: Int) {
        readTimeoutMs = timeout
    }

    override fun getSoTimeout(): Int = readTimeoutMs

    override fun setTcpNoDelay(on: Boolean) = Unit

    override fun setKeepAlive(on: Boolean) = Unit

    override fun setReceiveBufferSize(size: Int) = Unit

    override fun setSendBufferSize(size: Int) = Unit

    override fun getInetAddress(): InetAddress = InetAddress.getLoopbackAddress()

    override fun getPort(): Int = 22

    override fun getLocalPort(): Int = 0

    override fun isConnected(): Boolean = !closed

    override fun isClosed(): Boolean = closed

    override fun shutdownInput() {
        onClosed()
    }

    override fun shutdownOutput() {
        closed = true
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { webSocket.close(1000, "client closed") }
        incoming.clear()
        incoming.offer(EOF)
    }

    private inner class SocketInput : InputStream() {
        override fun read(): Int {
            val one = ByteArray(1)
            val read = read(one, 0, 1)
            return if (read < 0) -1 else one[0].toInt() and 0xff
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0

            var chunk: ByteArray? = current
            if (chunk == null || position >= chunk.size) {
                val item = try {
                    if (readTimeoutMs > 0) {
                        incoming.poll(readTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
                    } else {
                        incoming.take()
                    }
                } catch (e: InterruptedException) {
                    throw InterruptedIOException("SSH read interrupted").apply { initCause(e) }
                }

                if (item == null) throw SocketTimeoutException("SSH read timed out")
                if (item === EOF) {
                    failure?.let { throw IOException("SSH transport closed: ${it.message}", it) }
                    return -1
                }
                chunk = item as ByteArray
                current = chunk
                position = 0
            }

            val active = chunk ?: return -1
            val count = minOf(length, active.size - position)
            System.arraycopy(active, position, buffer, offset, count)
            position += count
            return count
        }

        override fun available(): Int {
            val chunk = current ?: return 0
            return (chunk.size - position).coerceAtLeast(0)
        }
    }

    private inner class SocketOutput : OutputStream() {
        override fun write(b: Int) {
            write(byteArrayOf(b.toByte()), 0, 1)
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            if (closed) throw IOException("SSH transport is closed")
            val bytes = if (offset == 0 && length == buffer.size) {
                buffer
            } else {
                buffer.copyOfRange(offset, offset + length)
            }
            if (!webSocket.send(bytes.toByteString())) {
                throw IOException("Could not send data over the Colab SSH tunnel")
            }
        }

        override fun flush() = Unit
    }

    private companion object {
        const val MAX_CHUNKS = 2048
        val EOF = Any()
    }
}

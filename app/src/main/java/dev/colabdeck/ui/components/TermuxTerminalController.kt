package dev.colabdeck.ui.components

import android.content.Context
import com.termux.terminal.TerminalEmulator
import com.termux.view.TerminalView

/**
 * Bridges Termux TerminalView to raw SSH bytes.
 */
class TermuxTerminalController {

    private var terminalView: TerminalView? = null
    private var sshSession: com.termux.view.SshTerminalSession? = null
    private var ready = false

    var onInput: ((ByteArray) -> Unit)? = null
    var onResize: ((Int, Int) -> Unit)? = null
    var onReady: (() -> Unit)? = null

    private val sessionClient = com.termux.view.SshTerminalSessionClient()

    fun attach(context: Context, view: TerminalView) {
        terminalView = view
        val session = com.termux.view.SshTerminalSession(sessionClient, 80, 24)
        sshSession = session
        session.setSshCallback { bytes ->
            onInput?.invoke(bytes)
        }
        view.attachSession(session)
        view.setTextSize(14)
        view.requestFocus()

        ready = true
        onReady?.invoke()
    }

    fun write(bytes: ByteArray) {
        sshSession?.receiveData(bytes)
    }

    fun sendKey(text: String) {
        onInput?.invoke(text.toByteArray(Charsets.UTF_8))
    }

    fun sendRaw(text: String) {
        onInput?.invoke(text.toByteArray(Charsets.UTF_8))
    }

    fun clear() {
        sshSession?.mEmulator?.reset()
    }

    fun refit() {
        terminalView?.let { view ->
            val cols = view.mEmulator?.mColumns ?: 80
            val rows = view.mEmulator?.mRows ?: 24
            onResize?.invoke(cols, rows)
        }
    }

    fun focus() {
        terminalView?.requestFocus()
    }

    fun setFontSize(px: Int) {
        terminalView?.setTextSize(px)
    }
}

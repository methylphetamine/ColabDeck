package dev.colabdeck.ui.components

import android.annotation.SuppressLint
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject
import java.util.ArrayDeque

/**
 * Bridges xterm.js in a WebView to raw SSH bytes.
 *
 * Output is base64-encoded so partial UTF-8 sequences survive the JS boundary;
 * xterm.js writes the decoded Uint8Array directly.
 */
class TerminalController {

    private var webView: WebView? = null
    private var ready = false
    private var pendingBytes = 0
    private val pending = ArrayDeque<ByteArray>()

    var onInput: ((ByteArray) -> Unit)? = null
    var onResize: ((Int, Int) -> Unit)? = null
    var onReady: (() -> Unit)? = null

    private val bridge = object {
        @JavascriptInterface
        fun onData(data: String) {
            onInput?.invoke(data.toByteArray(Charsets.UTF_8))
        }

        @JavascriptInterface
        fun onResize(cols: Int, rows: Int) {
            if (cols > 0 && rows > 0) onResize?.invoke(cols, rows)
        }

        @JavascriptInterface
        fun onReady() {
            ready = true
            flushPending()
            onReady?.invoke()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun attach(view: WebView) {
        webView = view
        view.settings.javaScriptEnabled = true
        view.settings.domStorageEnabled = true
        view.settings.allowContentAccess = false
        view.settings.allowFileAccess = false
        view.settings.loadWithOverviewMode = true
        view.settings.useWideViewPort = true
        view.setBackgroundColor(android.graphics.Color.parseColor("#070B10"))
        view.addJavascriptInterface(bridge, "Android")
        view.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                view?.evaluateJavascript("window.termRefit && window.termRefit()", null)
            }
        }
        view.loadUrl("file:///android_asset/terminal/index.html")
    }

    fun write(bytes: ByteArray) {
        if (!ready) {
            synchronized(pending) {
                if (pendingBytes + bytes.size > MAX_PENDING) {
                    pending.clear()
                    pendingBytes = 0
                }
                pending.addLast(bytes)
                pendingBytes += bytes.size
            }
            return
        }
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        webView?.post {
            webView?.evaluateJavascript("window.termWrite('$encoded')", null)
        }
    }

    private fun flushPending() {
        val snapshot = synchronized(pending) {
            val copy = pending.toList()
            pending.clear()
            pendingBytes = 0
            copy
        }
        snapshot.forEach { bytes ->
            val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
            webView?.post {
                webView?.evaluateJavascript("window.termWrite('$encoded')", null)
            }
        }
    }

    fun sendKey(text: String) {
        val quoted = JSONObject.quote(text)
        webView?.post {
            webView?.evaluateJavascript("window.termSend($quoted)", null)
        }
    }

    /** Sends raw bytes to the SSH channel without passing through xterm input handling. */
    fun sendRaw(text: String) {
        onInput?.invoke(text.toByteArray(Charsets.UTF_8))
    }

    fun clear() {
        webView?.post {
            webView?.evaluateJavascript("window.termClear && window.termClear()", null)
        }
    }

    fun refit() {
        webView?.post {
            webView?.evaluateJavascript("window.termRefit && window.termRefit()", null)
        }
    }

    fun focus() {
        webView?.post {
            webView?.requestFocus()
            webView?.evaluateJavascript("window.termFocus && window.termFocus()", null)
        }
    }

    fun setFontSize(px: Int) {
        webView?.post {
            webView?.evaluateJavascript("window.termSetFont && window.termSetFont($px)", null)
        }
    }

    private companion object {
        const val MAX_PENDING = 2 * 1024 * 1024
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TerminalWebView(controller: TerminalController, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).also { controller.attach(it) }
        }
    )
}

package dev.colabdeck.ui.screens

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.colabdeck.MainViewModel
import dev.colabdeck.data.ColabSession
import dev.colabdeck.data.SshProfile
import dev.colabdeck.ssh.SshConnection
import dev.colabdeck.ssh.SshListener
import dev.colabdeck.ssh.SshState
import dev.colabdeck.ui.components.StatusDot
import dev.colabdeck.ui.components.TerminalController
import dev.colabdeck.ui.components.TerminalWebView
import dev.colabdeck.ui.theme.Amber
import dev.colabdeck.ui.theme.Cyan
import dev.colabdeck.ui.theme.Ink
import dev.colabdeck.ui.theme.InkBorder
import dev.colabdeck.ui.theme.InkElevated
import dev.colabdeck.ui.theme.InkRaised
import dev.colabdeck.ui.theme.Mint
import dev.colabdeck.ui.theme.Rose
import dev.colabdeck.ui.theme.TextMuted
import dev.colabdeck.ui.theme.TextPrimary
import dev.colabdeck.ui.theme.TextSecondary
import dev.colabdeck.ui.theme.Violet
import java.util.concurrent.atomic.AtomicReference

private enum class TermStatus { LOADING, CONNECTING, CONNECTED, DISCONNECTED, FAILED }

/**
 * Interactive shell. Colab sessions use the official SSH-over-WebSocket endpoint,
 * saved profiles use a plain JSch connection. Above the IME sits a Termux-style
 * key bar (Esc, Tab, sticky Ctrl/Alt, arrows, nav keys and common control keys).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TerminalScreen(
    vm: MainViewModel,
    endpoint: String?,
    profileId: String?,
    onBack: () -> Unit,
    onOpenFiles: (String) -> Unit
) {
    val controller = remember { TerminalController() }
    val connectionRef = remember { AtomicReference<SshConnection?>(null) }
    val main = remember { Handler(Looper.getMainLooper()) }
    val prefs = remember { vm.prefs() }
    val view = LocalView.current

    var session by remember { mutableStateOf<ColabSession?>(null) }
    var profile by remember { mutableStateOf<SshProfile?>(null) }
    var status by remember { mutableStateOf(TermStatus.LOADING) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var ctrlArmed by remember { mutableStateOf(false) }
    var altArmed by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var fontPx by remember { mutableIntStateOf(prefs.fontSize) }

    val imeVisible = WindowInsets.isImeVisible

    LaunchedEffect(controller) {
        controller.onInput = input@{ bytes ->
            val connection = connectionRef.get() ?: return@input
            if (bytes.size == 1 && (ctrlArmed || altArmed)) {
                connection.write(applyKeyModifiers(bytes, ctrlArmed, altArmed))
                main.post {
                    ctrlArmed = false
                    altArmed = false
                }
            } else {
                connection.write(bytes)
            }
        }
        controller.onResize = { cols, rows -> connectionRef.get()?.resize(cols, rows) }
        controller.onReady = { controller.setFontSize(fontPx) }
    }

    DisposableEffect(Unit) {
        val previous = view.keepScreenOn
        view.keepScreenOn = prefs.keepScreenOn
        onDispose { view.keepScreenOn = previous }
    }

    LaunchedEffect(endpoint, profileId, attempt) {
        connectionRef.getAndSet(null)?.disconnect()
        controller.clear()
        error = null
        status = TermStatus.LOADING

        val listener = object : SshListener {
            override fun onOutput(bytes: ByteArray) {
                controller.write(bytes)
            }

            override fun onState(state: SshState) {
                main.post {
                    status = when (state) {
                        SshState.CONNECTING -> TermStatus.CONNECTING
                        SshState.CONNECTED -> TermStatus.CONNECTED
                        SshState.DISCONNECTED -> TermStatus.DISCONNECTED
                        SshState.FAILED -> TermStatus.FAILED
                    }
                }
            }

            override fun onError(message: String) {
                main.post { error = message }
            }
        }

        val connection = if (endpoint != null) {
            val fresh = vm.freshSession(endpoint).getOrElse {
                main.post {
                    error = it.message ?: "Could not refresh the runtime token."
                    status = TermStatus.FAILED
                }
                return@LaunchedEffect
            }
            session = fresh
            vm.markConnected(endpoint)
            vm.sshManager().connectColab(fresh, listener)
        } else if (profileId != null) {
            val saved = vm.profile(profileId)
            if (saved == null) {
                main.post {
                    error = "That SSH profile no longer exists."
                    status = TermStatus.FAILED
                }
                return@LaunchedEffect
            }
            profile = saved
            vm.sshManager().connectProfile(saved, listener)
        } else {
            main.post {
                error = "No terminal target was provided."
                status = TermStatus.FAILED
            }
            return@LaunchedEffect
        }

        connectionRef.set(connection)
        connection.start()
    }

    DisposableEffect(Unit) {
        onDispose {
            connectionRef.getAndSet(null)?.disconnect()
        }
    }

    LaunchedEffect(imeVisible) {
        // Let the WebView resize with the IME before refitting xterm.
        kotlinx.coroutines.delay(120)
        controller.refit()
    }

    Scaffold(
        containerColor = Ink,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(
                            when (status) {
                                TermStatus.CONNECTED -> Mint
                                TermStatus.CONNECTING, TermStatus.LOADING -> Amber
                                TermStatus.FAILED -> Rose
                                TermStatus.DISCONNECTED -> TextMuted
                            }
                        )
                        Spacer(Modifier.width(9.dp))
                        Column {
                            Text(
                                text = session?.displayName ?: profile?.name ?: "Terminal",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = when (status) {
                                    TermStatus.CONNECTED ->
                                        if (endpoint != null) "root@colab-runtime" else "${profile?.user}@${profile?.host}"
                                    TermStatus.CONNECTING, TermStatus.LOADING -> "connecting…"
                                    TermStatus.DISCONNECTED -> "session ended"
                                    TermStatus.FAILED -> "not connected"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { controller.focus() }) {
                        Icon(Icons.Rounded.Keyboard, contentDescription = "Show keyboard", tint = TextSecondary)
                    }
                    if (endpoint != null) {
                        IconButton(onClick = { onOpenFiles(endpoint) }) {
                            Icon(Icons.Rounded.FolderOpen, contentDescription = "Files", tint = TextSecondary)
                        }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = TextSecondary)
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                            containerColor = InkRaised
                        ) {
                            DropdownMenuItem(
                                text = { Text("Reconnect") },
                                leadingIcon = { Icon(Icons.Rounded.Refresh, null, tint = TextSecondary) },
                                onClick = {
                                    menuOpen = false
                                    attempt++
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Clear screen") },
                                leadingIcon = { Icon(Icons.Rounded.DeleteSweep, null, tint = TextSecondary) },
                                onClick = {
                                    menuOpen = false
                                    controller.clear()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Larger text") },
                                onClick = {
                                    menuOpen = false
                                    fontPx = (fontPx + 2).coerceAtMost(26)
                                    prefs.fontSize = fontPx
                                    controller.setFontSize(fontPx)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Smaller text") },
                                onClick = {
                                    menuOpen = false
                                    fontPx = (fontPx - 2).coerceAtLeast(9)
                                    prefs.fontSize = fontPx
                                    controller.setFontSize(fontPx)
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Ink,
                    titleContentColor = TextPrimary,
                    navigationIconContentColor = TextSecondary,
                    actionIconContentColor = TextSecondary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .imePadding()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                TerminalWebView(
                    controller = controller,
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (status != TermStatus.CONNECTED) {
                TerminalBanner(
                    status = status,
                    error = error,
                    onRetry = {
                        attempt++
                        controller.focus()
                    }
                )
            }

            ExtraKeyBar(
                ctrl = ctrlArmed,
                alt = altArmed,
                onCtrl = { ctrlArmed = !ctrlArmed },
                onAlt = { altArmed = !altArmed },
                onRaw = { text ->
                    controller.sendRaw(text)
                    controller.focus()
                }
            )
        }
    }

    // Keep the WebView focused so the soft keyboard stays attached to xterm.
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(300)
        controller.focus()
    }
}

@Composable
private fun TerminalBanner(status: TermStatus, error: String?, onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (status == TermStatus.FAILED) Color(0xFF21121A) else InkElevated)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (status) {
            TermStatus.LOADING, TermStatus.CONNECTING -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = Amber
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    text = if (status == TermStatus.LOADING) {
                        "Refreshing runtime token…"
                    } else {
                        "Opening the SSH tunnel…"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }

            TermStatus.DISCONNECTED -> {
                Text(
                    text = "Session ended.",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onRetry) { Text("Reconnect", color = Cyan) }
            }

            TermStatus.FAILED -> {
                Text(
                    text = error ?: "SSH connection failed.",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFFFC7D1),
                    modifier = Modifier.weight(1f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                TextButton(onClick = onRetry) { Text("Retry", color = Cyan) }
            }

            TermStatus.CONNECTED -> Unit
        }
    }
}

@Composable
private fun ExtraKeyBar(
    ctrl: Boolean,
    alt: Boolean,
    onCtrl: () -> Unit,
    onAlt: () -> Unit,
    onRaw: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().background(InkElevated)) {
        HorizontalDivider(color = InkBorder)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KeyCap("ESC", onClick = { onRaw("\u001b") })
            KeyCap("TAB", onClick = { onRaw("\t") })
            ToggleCap("CTRL", active = ctrl, tint = Cyan, onClick = onCtrl)
            ToggleCap("ALT", active = alt, tint = Violet, onClick = onAlt)

            KeyCap("←", onClick = { onRaw("\u001b[D") })
            KeyCap("↑", onClick = { onRaw("\u001b[A") })
            KeyCap("↓", onClick = { onRaw("\u001b[B") })
            KeyCap("→", onClick = { onRaw("\u001b[C") })

            KeyCap("HOME", onClick = { onRaw("\u001b[H") })
            KeyCap("END", onClick = { onRaw("\u001b[F") })
            KeyCap("PGUP", onClick = { onRaw("\u001b[5~") })
            KeyCap("PGDN", onClick = { onRaw("\u001b[6~") })

            KeyCap("^C", accent = Rose, onClick = { onRaw("\u0003") })
            KeyCap("^D", onClick = { onRaw("\u0004") })
            KeyCap("^Z", onClick = { onRaw("\u001a") })
            KeyCap("^L", onClick = { onRaw("\u000c") })
            KeyCap("^R", onClick = { onRaw("\u0012") })

            KeyCap("|", onClick = { onRaw("|") })
            KeyCap("~", onClick = { onRaw("~") })
            KeyCap("-", onClick = { onRaw("-") })
            KeyCap("/", onClick = { onRaw("/") })
        }
    }
}

@Composable
private fun KeyCap(
    label: String,
    accent: Color = TextSecondary,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(InkRaised)
            .border(1.dp, InkBorder, RoundedCornerShape(7.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp
            ),
            color = accent,
            maxLines = 1
        )
    }
}

@Composable
private fun ToggleCap(
    label: String,
    active: Boolean,
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (active) tint.copy(alpha = 0.18f) else InkRaised)
            .border(
                1.dp,
                if (active) tint.copy(alpha = 0.65f) else InkBorder,
                RoundedCornerShape(7.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            ),
            color = if (active) tint else TextSecondary,
            maxLines = 1
        )
    }
}

/** Applies sticky Ctrl/Alt to a single soft-keyboard character. */
private fun applyKeyModifiers(bytes: ByteArray, ctrl: Boolean, alt: Boolean): ByteArray {
    if (bytes.isEmpty()) return bytes
    val raw = bytes[0].toInt() and 0xFF
    val modified = if (ctrl) {
        when (raw) {
            in 0x61..0x7A -> raw - 0x60        // a-z -> ^A..^Z
            in 0x41..0x5A -> raw - 0x40        // A-Z
            0x20 -> 0x00                        // space -> NUL
            0x40 -> 0x00                        // @ -> NUL
            0x3F -> 0x7F                        // ? -> DEL
            in 0x5B..0x5F -> raw - 0x40        // [ \ ] ^ _
            else -> raw
        }
    } else {
        raw
    }
    return if (alt) byteArrayOf(0x1B, modified.toByte()) else byteArrayOf(modified.toByte())
}

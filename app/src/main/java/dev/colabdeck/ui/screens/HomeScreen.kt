package dev.colabdeck.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.colabdeck.MainViewModel
import dev.colabdeck.data.Accelerator
import dev.colabdeck.data.AuthState
import dev.colabdeck.data.ColabSession
import dev.colabdeck.ui.components.HardwareChip
import dev.colabdeck.ui.components.Pill
import dev.colabdeck.ui.components.SectionLabel
import dev.colabdeck.ui.components.StatusDot
import dev.colabdeck.ui.components.copyToClipboard
import dev.colabdeck.ui.components.formatRelative
import dev.colabdeck.ui.components.formatTokenExpiry
import dev.colabdeck.ui.components.sessionAccent
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: MainViewModel,
    onSignIn: () -> Unit,
    onOpenTerminal: (String) -> Unit,
    onOpenFiles: (String) -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val sessions by vm.sessionState.collectAsStateWithLifecycle()
    val auth by vm.authState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showNewSession by remember { mutableStateOf(false) }
    var stopTarget by remember { mutableStateOf<ColabSession?>(null) }
    var renameTarget by remember { mutableStateOf<ColabSession?>(null) }

    LaunchedEffect(auth) {
        if (auth is AuthState.SignedIn) vm.refreshSessions()
    }

    Scaffold(
        containerColor = Ink,
        topBar = {
            HomeHeader(
                auth = auth,
                onRefresh = vm::refreshSessions,
                onAccount = onOpenSettings
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showNewSession = true },
                containerColor = Cyan,
                contentColor = Ink,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("New runtime", fontWeight = FontWeight.SemiBold) }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = sessions.loading,
            onRefresh = vm::refreshSessions,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    when (val state = auth) {
                        is AuthState.SignedOut -> ConnectCard(onSignIn)
                        is AuthState.Error -> ExpiredCard(state.message, onSignIn)
                        is AuthState.Loading -> LoadingCard()
                        is AuthState.SignedIn -> AccountStrip(state)
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickAction(
                            title = "SSH profiles",
                            subtitle = "tmate · ngrok · LAN",
                            tint = Violet,
                            icon = Icons.Rounded.VpnKey,
                            onClick = onOpenProfiles,
                            modifier = Modifier.weight(1f)
                        )
                        QuickAction(
                            title = "Setup guide",
                            subtitle = "OAuth · SSH · files",
                            tint = Cyan,
                            icon = Icons.Rounded.MenuBook,
                            onClick = onOpenGuide,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (sessions.error != null && auth is AuthState.SignedIn) {
                    item {
                        ErrorBanner(
                            message = sessions.error ?: "",
                            onRetry = vm::refreshSessions
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionLabel("Active sessions")
                        Spacer(Modifier.width(8.dp))
                        if (sessions.sessions.isNotEmpty()) {
                            Pill("${sessions.sessions.size}", Cyan, filled = true)
                        }
                        Spacer(Modifier.weight(1f))
                        if (sessions.lastUpdated > 0) {
                            Text(
                                text = "updated ${formatRelative(sessions.lastUpdated)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                    }
                }

                if (sessions.sessions.isEmpty() && !sessions.loading) {
                    item {
                        EmptySessions(
                            signedIn = auth is AuthState.SignedIn,
                            onCreate = { showNewSession = true },
                            onSignIn = onSignIn
                        )
                    }
                }

                items(sessions.sessions, key = { it.endpoint }) { session ->
                    SessionCard(
                        session = session,
                        onOpen = { onOpenTerminal(session.endpoint) },
                        onFiles = { onOpenFiles(session.endpoint) },
                        onCopy = {
                            copyToClipboard(context, session.endpoint, "Runtime endpoint copied")
                        },
                        onRename = { renameTarget = session },
                        onStop = { stopTarget = session }
                    )
                }

                item {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Sessions are real Colab VMs. Stopping one releases its GPU/TPU immediately.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }
        }
    }

    if (showNewSession) {
        NewSessionSheet(
            vm = vm,
            onDismiss = { showNewSession = false },
            onCreated = {
                showNewSession = false
                onOpenTerminal(it.endpoint)
            }
        )
    }

    stopTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { stopTarget = null },
            title = { Text("Stop ${target.displayName}?") },
            text = {
                Text(
                    "This terminates the Colab runtime and releases its compute. Unsaved work on the VM will be lost.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.stopSession(target.endpoint)
                    stopTarget = null
                }) { Text("Stop runtime", color = Rose) }
            },
            dismissButton = {
                TextButton(onClick = { stopTarget = null }) { Text("Cancel") }
            },
            containerColor = InkElevated
        )
    }

    renameTarget?.let { target ->
        RenameDialog(
            current = target.displayName,
            onDismiss = { renameTarget = null },
            onSave = { name ->
                vm.renameSession(target.endpoint, name)
                renameTarget = null
            }
        )
    }
}

@Composable
private fun HomeHeader(
    auth: AuthState,
    onRefresh: () -> Unit,
    onAccount: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Cyan.copy(alpha = 0.22f), Violet.copy(alpha = 0.18f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Terminal,
                contentDescription = null,
                tint = Cyan,
                modifier = Modifier.size(21.dp)
            )
        }
        Spacer(Modifier.width(11.dp))
        Column {
            Text(
                text = "ColabDeck",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
            Text(
                text = "runtime control deck",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onRefresh) {
            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = TextSecondary)
        }
        IconButton(onClick = onAccount) {
            Icon(
                Icons.Rounded.AccountCircle,
                contentDescription = "Account",
                tint = if (auth is AuthState.SignedIn) Cyan else TextSecondary
            )
        }
    }
}

@Composable
private fun ConnectCard(onSignIn: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF0C2A33),
                        Color(0xFF101A2C),
                        Color(0xFF1A1230)
                    )
                )
            )
            .padding(1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(21.dp))
                .background(InkElevated.copy(alpha = 0.92f))
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill("GOOGLE COLAB", Cyan, filled = true)
                Spacer(Modifier.width(8.dp))
                Pill("SSH OVER WSS", Violet)
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Your Colab, one tap away.",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Create CPU, GPU or TPU runtimes, then open a real interactive shell. No notebook, no cloudflared, no port forwarding.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onSignIn,
                    colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Ink),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Connect Google", fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "OAuth or access token",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
private fun ExpiredCard(message: String, onSignIn: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1018)),
        border = BorderStroke(1.dp, Rose.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(Rose)
                Spacer(Modifier.width(8.dp))
                Text("Google sign-in needed", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            }
            Spacer(Modifier.height(6.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onSignIn,
                colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Ink)
            ) { Text("Sign in again") }
        }
    }
}

@Composable
private fun LoadingCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = InkElevated),
        border = BorderStroke(1.dp, InkBorder),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Cyan)
            Spacer(Modifier.width(12.dp))
            Text("Talking to Google…", color = TextSecondary)
        }
    }
}

@Composable
private fun AccountStrip(state: AuthState.SignedIn) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(InkElevated)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusDot(Mint)
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = state.email ?: "Google account",
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "token ${formatTokenExpiry(state.expiresAt)} · ${if (state.canRefresh) "auto-refresh" else "no refresh token"}",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }
        if (!state.canRefresh) {
            Pill("re-auth soon", Amber)
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF21121A))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusDot(Rose)
        Spacer(Modifier.width(9.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.weight(1f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        TextButton(onClick = onRetry) { Text("Retry", color = Cyan) }
    }
}

@Composable
private fun EmptySessions(
    signedIn: Boolean,
    onCreate: () -> Unit,
    onSignIn: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = InkElevated),
        border = BorderStroke(1.dp, InkBorder),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 30.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(InkRaised),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.CloudQueue, contentDescription = null, tint = Cyan, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = if (signedIn) "No active runtimes" else "Connect your Colab account",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (signedIn) {
                    "Spin up a free CPU or a GPU/TPU from your Colab plan. The runtime appears here in under a minute."
                } else {
                    "Sign in with Google to list, start, stop and SSH into your Colab runtimes."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = if (signedIn) onCreate else onSignIn,
                colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Ink)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (signedIn) "Create runtime" else "Connect Google")
            }
        }
    }
}

@Composable
private fun SessionCard(
    session: ColabSession,
    onOpen: () -> Unit,
    onFiles: () -> Unit,
    onCopy: () -> Unit,
    onRename: () -> Unit,
    onStop: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val accent = sessionAccent(session)
    val tokenMinutes = (session.tokenExpiresAt - System.currentTimeMillis()) / 60_000

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = InkElevated),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.22f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (session.isTpu) Icons.Rounded.Bolt else Icons.Rounded.Memory,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(23.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = session.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = session.endpoint,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        ),
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = TextMuted)
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        containerColor = InkRaised
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Rounded.Edit, null, tint = TextSecondary) },
                            onClick = { menuOpen = false; onRename() }
                        )
                        DropdownMenuItem(
                            text = { Text("Browse files") },
                            leadingIcon = { Icon(Icons.Rounded.FolderOpen, null, tint = TextSecondary) },
                            onClick = { menuOpen = false; onFiles() }
                        )
                        DropdownMenuItem(
                            text = { Text("Copy runtime endpoint") },
                            leadingIcon = { Icon(Icons.Rounded.ContentCopy, null, tint = TextSecondary) },
                            onClick = { menuOpen = false; onCopy() }
                        )
                        DropdownMenuItem(
                            text = { Text("Stop runtime", color = Rose) },
                            leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = Rose) },
                            onClick = { menuOpen = false; onStop() }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HardwareChip(session)
                Pill(session.variantLabel, TextSecondary)
                Pill(session.shapeLabel, TextSecondary)
                Pill(
                    text = "token ${formatTokenExpiry(session.tokenExpiresAt)}",
                    color = if (tokenMinutes < 10) Amber else TextMuted
                )
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = InkBorder)
            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(Mint)
                Spacer(Modifier.width(7.dp))
                Text(
                    text = "Ready",
                    style = MaterialTheme.typography.labelMedium,
                    color = Mint
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onCopy) {
                    Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(15.dp), tint = TextSecondary)
                    Spacer(Modifier.width(4.dp))
                    Text("Copy", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
                }
                IconButton(onClick = onFiles) {
                    Icon(
                        Icons.Rounded.FolderOpen,
                        contentDescription = "Browse files",
                        tint = TextSecondary,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(Modifier.width(2.dp))
                Button(
                    onClick = onOpen,
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Ink),
                    shape = RoundedCornerShape(11.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Rounded.Terminal, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Terminal", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewSessionSheet(
    vm: MainViewModel,
    onDismiss: () -> Unit,
    onCreated: (ColabSession) -> Unit
) {
    val state by vm.sessionState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selected by remember { mutableStateOf(Accelerator.CPU) }
    var highMemory by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = { if (!state.creating) onDismiss() },
        sheetState = sheetState,
        containerColor = InkElevated,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 38.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(InkBorder)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text("New Colab runtime", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "Pick hardware from your Colab plan. Allocation usually takes 20–60 seconds.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Spacer(Modifier.height(18.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.heightIn(max = 370.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                userScrollEnabled = false
            ) {
                items(Accelerator.entries.toList()) { option ->
                    HardwareOption(
                        option = option,
                        selected = option == selected,
                        onClick = {
                            selected = option
                            if (option.highMemoryOnly) highMemory = false
                        }
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(InkRaised)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("High-RAM machine", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(
                        text = if (selected.highMemoryOnly) {
                            "Not available for ${selected.label}"
                        } else {
                            "Requires Colab Pro / Pro+ entitlement"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
                Switch(
                    checked = highMemory && !selected.highMemoryOnly,
                    onCheckedChange = { highMemory = it },
                    enabled = !selected.highMemoryOnly,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Ink,
                        checkedTrackColor = Cyan,
                        uncheckedTrackColor = InkBorder
                    )
                )
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Session name (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Cyan,
                    unfocusedBorderColor = InkBorder,
                    focusedLabelColor = Cyan,
                    cursorColor = Cyan,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            )

            AnimatedVisibility(visible = state.error != null) {
                Text(
                    text = state.error ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = Rose,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    vm.createSession(
                        accelerator = selected,
                        highMemory = highMemory && !selected.highMemoryOnly,
                        name = name
                    ) { onCreated(it) }
                },
                enabled = !state.creating,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Cyan,
                    contentColor = Ink,
                    disabledContainerColor = Cyan.copy(alpha = 0.4f),
                    disabledContentColor = Ink
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (state.creating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(19.dp),
                        strokeWidth = 2.dp,
                        color = Ink
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Allocating ${selected.label}…", fontWeight = FontWeight.SemiBold)
                } else {
                    Icon(Icons.Rounded.Add, null, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("Create ${selected.label}", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun HardwareOption(
    option: Accelerator,
    selected: Boolean,
    onClick: () -> Unit
) {
    val accent = when (option.variant) {
        "GPU" -> Cyan
        "TPU" -> Violet
        else -> Mint
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) accent.copy(alpha = 0.10f) else InkRaised)
            .then(
                if (selected) {
                    Modifier.background(Color.Transparent)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (option.variant) {
                        "TPU" -> Icons.Rounded.Bolt
                        "GPU" -> Icons.Rounded.Memory
                        else -> Icons.Rounded.CloudQueue
                    },
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            if (selected) {
                Icon(Icons.Rounded.Check, null, tint = accent, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(option.label, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Text(
            option.description,
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            maxLines = 2
        )
    }
}

@Composable
private fun QuickAction(
    title: String,
    subtitle: String,
    tint: Color,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(InkElevated)
            .border(1.dp, InkBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(13.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.height(9.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = TextMuted)
    }
}

@Composable
private fun RenameDialog(
    current: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var value by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename session") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                label = { Text("Name") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Cyan,
                    unfocusedBorderColor = InkBorder,
                    cursorColor = Cyan
                )
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(value) }) { Text("Save", color = Cyan) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        containerColor = InkElevated
    )
}

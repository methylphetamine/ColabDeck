package dev.colabdeck.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.colabdeck.MainViewModel
import dev.colabdeck.data.SshProfile
import dev.colabdeck.ui.components.Pill
import dev.colabdeck.ui.components.SectionLabel
import dev.colabdeck.ui.components.readClipboard
import dev.colabdeck.ui.theme.Cyan
import dev.colabdeck.ui.theme.Ink
import dev.colabdeck.ui.theme.InkBorder
import dev.colabdeck.ui.theme.InkElevated
import dev.colabdeck.ui.theme.InkRaised
import dev.colabdeck.ui.theme.Mint
import dev.colabdeck.ui.theme.TextMuted
import dev.colabdeck.ui.theme.TextPrimary
import dev.colabdeck.ui.theme.TextSecondary
import dev.colabdeck.ui.theme.Violet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
    vm: MainViewModel,
    onConnect: (String) -> Unit,
    onEdit: (String) -> Unit,
    onAdd: () -> Unit
) {
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        containerColor = Ink,
        topBar = {
            TopAppBar(
                title = { Text("SSH profiles") },
                actions = {
                    IconButton(onClick = onAdd) {
                        Icon(Icons.Rounded.Add, contentDescription = "Add profile")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Ink,
                    titleContentColor = TextPrimary,
                    actionIconContentColor = TextSecondary
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                containerColor = Violet,
                contentColor = Ink,
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("Add profile", fontWeight = FontWeight.SemiBold) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = InkElevated),
                    border = BorderStroke(1.dp, Violet.copy(alpha = 0.25f)),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Pill("DIRECT SSH", Violet, filled = true)
                            Spacer(Modifier.width(8.dp))
                            Pill("TMATE / NGROK / LAN", TextMuted)
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "SSH into any reachable host",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "For Colab's built-in SSH, use the Sessions tab — no profile needed. Add a profile for tmate, ngrok, a LAN box, or any sshd with a public port.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                val text = readClipboard(context)
                                vm.importProfile(text)
                            },
                            shape = RoundedCornerShape(11.dp)
                        ) {
                            Icon(Icons.Rounded.ContentPaste, null, modifier = Modifier.size(16.dp), tint = Cyan)
                            Spacer(Modifier.width(6.dp))
                            Text("Import from clipboard", color = Cyan)
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionLabel("Saved profiles")
                    Spacer(Modifier.width(8.dp))
                    if (profiles.isNotEmpty()) Pill("${profiles.size}", Violet, filled = true)
                }
            }

            if (profiles.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 30.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .background(InkRaised, RoundedCornerShape(20.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.VpnKey, null, tint = Violet, modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.height(14.dp))
                        Text("No SSH profiles yet", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Paste an address like ssh -p 2222 root@host, or create one manually.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            items(profiles, key = { it.id }) { profile ->
                ProfileCard(
                    profile = profile,
                    onConnect = { onConnect(profile.id) },
                    onEdit = { onEdit(profile.id) }
                )
            }

            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Cloudflare quick tunnels (*.trycloudflare.com) need cloudflared on the client. Use tmate or the built-in Colab terminal instead.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
private fun ProfileCard(
    profile: SshProfile,
    onConnect: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onConnect),
        colors = CardDefaults.cardColors(containerColor = InkElevated),
        border = BorderStroke(1.dp, InkBorder),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Violet.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (profile.authType == SshProfile.AUTH_KEY) Icons.Rounded.Key else Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = Violet,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        profile.name.ifBlank { profile.host },
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${profile.user}@${profile.host}:${profile.port}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Edit", tint = TextMuted, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(if (profile.authType == SshProfile.AUTH_KEY) "KEY" else "PASSWORD", TextSecondary)
                if (profile.note.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        profile.note,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onConnect,
                    colors = ButtonDefaults.buttonColors(containerColor = Violet, contentColor = Ink),
                    shape = RoundedCornerShape(11.dp),
                    contentPadding = PaddingValues(horizontal = 13.dp, vertical = 7.dp)
                ) {
                    Icon(Icons.Rounded.Terminal, null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Connect", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

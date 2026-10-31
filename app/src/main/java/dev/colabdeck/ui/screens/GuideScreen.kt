package dev.colabdeck.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.colabdeck.ui.components.CodeBlock
import dev.colabdeck.ui.components.InfoCard
import dev.colabdeck.ui.components.Pill
import dev.colabdeck.ui.components.SectionLabel
import dev.colabdeck.ui.theme.Cyan
import dev.colabdeck.ui.theme.Ink
import dev.colabdeck.ui.theme.InkRaised
import dev.colabdeck.ui.theme.Mint
import dev.colabdeck.ui.theme.TextMuted
import dev.colabdeck.ui.theme.TextPrimary
import dev.colabdeck.ui.theme.TextSecondary
import dev.colabdeck.ui.theme.Violet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(onBack: () -> Unit) {
    Scaffold(
        containerColor = Ink,
        topBar = {
            TopAppBar(
                title = { Text("Setup guide") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Ink,
                    titleContentColor = TextPrimary,
                    navigationIconContentColor = TextSecondary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill("GOOGLE COLAB", Cyan, filled = true)
                Spacer(Modifier.width(8.dp))
                Pill("NO WEBVIEW APP", Violet)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "How ColabDeck connects",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "ColabDeck talks to the same control plane and runtime endpoints as Google's official colab CLI. " +
                    "There is no relay server: requests go from this device straight to Google.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Spacer(Modifier.height(22.dp))
            SectionLabel("1 · Sign in")
            Spacer(Modifier.height(10.dp))
            InfoCard(
                title = "Desktop OAuth client (recommended)",
                body = "Create a Google Cloud project, enable nothing extra, then create an OAuth client of type " +
                    "\"Desktop app\". Paste its client ID here. The app opens Chrome and captures the loopback redirect.",
                accent = Cyan,
                icon = { Icon(Icons.Rounded.Key, null, tint = Cyan, modifier = Modifier.size(18.dp)) }
            )
            Spacer(Modifier.height(10.dp))
            CodeBlock(
                code = """
                    Google Cloud Console → APIs & Services
                    → OAuth consent screen → External
                    → Credentials → Create credentials
                    → OAuth client ID → Desktop app
                """.trimIndent()
            )
            Spacer(Modifier.height(10.dp))
            InfoCard(
                title = "Access token (fastest)",
                body = "In the OAuth 2.0 Playground, authorize https://www.googleapis.com/auth/colaboratory, " +
                    "exchange the code, and paste the access token. Tokens last about an hour.",
                accent = Violet,
                icon = { Icon(Icons.Rounded.Lock, null, tint = Violet, modifier = Modifier.size(18.dp)) }
            )

            Spacer(Modifier.height(22.dp))
            SectionLabel("2 · Start a runtime")
            Spacer(Modifier.height(10.dp))
            InfoCard(
                title = "Real Colab VMs",
                body = "The Sessions tab lists your assignments. New runtime allocates a CPU, GPU or TPU machine " +
                    "from your Colab plan. Stopping a runtime releases its compute immediately.",
                accent = Mint,
                icon = { Icon(Icons.Rounded.Memory, null, tint = Mint, modifier = Modifier.size(18.dp)) }
            )

            Spacer(Modifier.height(22.dp))
            SectionLabel("3 · SSH terminal")
            Spacer(Modifier.height(10.dp))
            InfoCard(
                title = "Official SSH over WebSocket",
                body = "ColabDeck generates an ECDSA P-256 key on this device and opens " +
                    "wss://<runtime>/colab/ssh with X-Colab-Ssh-Pubkey. JSch then runs an interactive shell " +
                    "over that socket. No port forwarding, no cloudflared, no notebook cell.",
                accent = Cyan,
                icon = { Icon(Icons.Rounded.Terminal, null, tint = Cyan, modifier = Modifier.size(18.dp)) }
            )
            Spacer(Modifier.height(10.dp))
            InfoCard(
                title = "Termux-style key bar",
                body = "Above the keyboard: ESC, TAB, sticky CTRL and ALT, arrow keys, HOME/END, PGDN/PGUP and " +
                    "common control keys. Tap CTRL once, then type a letter to send Ctrl+letter.",
                accent = Violet,
                icon = { Icon(Icons.Rounded.Key, null, tint = Violet, modifier = Modifier.size(18.dp)) }
            )

            Spacer(Modifier.height(22.dp))
            SectionLabel("4 · Files")
            Spacer(Modifier.height(10.dp))
            InfoCard(
                title = "Upload and download /content",
                body = "The folder icon opens the runtime's /content directory through the Jupyter Contents API. " +
                    "Pick any file with Android's document picker to upload it, or tap a file to save it locally. " +
                    "In-app transfers are capped at 100 MB.",
                accent = Mint,
                icon = { Icon(Icons.Rounded.FolderOpen, null, tint = Mint, modifier = Modifier.size(18.dp)) }
            )

            Spacer(Modifier.height(22.dp))
            SectionLabel("5 · Other SSH hosts")
            Spacer(Modifier.height(10.dp))
            InfoCard(
                title = "Saved profiles",
                body = "Add tmate, ngrok, a LAN machine or any reachable sshd with a password or private key. " +
                    "Cloudflare quick tunnels (*.trycloudflare.com) need cloudflared on the client and are not supported.",
                accent = Violet,
                icon = { Icon(Icons.Rounded.Key, null, tint = Violet, modifier = Modifier.size(18.dp)) }
            )

            Spacer(Modifier.height(26.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(InkRaised, RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Column {
                    Text(
                        "Privacy",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Tokens, SSH profiles and the app private key are encrypted with a non-exportable " +
                            "Android Keystore key. ColabDeck has no analytics and no backend.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            Spacer(Modifier.height(30.dp))
            Text(
                "ColabDeck is an independent client. Google Colab's terms still apply to your account.",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = Color(0xFF64748B)
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

package dev.colabdeck.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.colabdeck.BuildConfig
import dev.colabdeck.MainViewModel
import dev.colabdeck.data.AuthState
import dev.colabdeck.ui.components.CodeBlock
import dev.colabdeck.ui.components.Pill
import dev.colabdeck.ui.components.SectionLabel
import dev.colabdeck.ui.components.StatusDot
import dev.colabdeck.ui.components.formatTokenExpiry
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
fun SettingsScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenProfiles: () -> Unit
) {
    val auth by vm.authState.collectAsStateWithLifecycle()
    var clientId by remember { mutableStateOf(vm.clientId()) }
    var clientSecret by remember { mutableStateOf(vm.clientSecret()) }
    var keepScreenOn by remember { mutableStateOf(vm.prefs().keepScreenOn) }
    var fontSize by remember { mutableStateOf(vm.prefs().fontSize) }

    Scaffold(
        containerColor = Ink,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
            SectionLabel("Google account")
            Spacer(Modifier.height(10.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = InkElevated),
                border = BorderStroke(1.dp, InkBorder),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    when (val state = auth) {
                        is AuthState.SignedIn -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusDot(Mint)
                                Spacer(Modifier.width(9.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        state.email ?: "Google account",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextPrimary
                                    )
                                    Text(
                                        state.name.orEmpty().ifBlank { "Colab access granted" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                                Pill(
                                    if (state.canRefresh) "AUTO-REFRESH" else "SESSION TOKEN",
                                    if (state.canRefresh) Mint else Amber,
                                    filled = true
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "token ${formatTokenExpiry(state.expiresAt)}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    color = TextMuted
                                )
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = onSignIn) {
                                    Text("Re-authenticate", color = Cyan)
                                }
                                TextButton(onClick = { vm.signOut() }) {
                                    Icon(Icons.Rounded.Logout, null, modifier = Modifier.size(15.dp), tint = Rose)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Sign out", color = Rose)
                                }
                            }
                        }

                        is AuthState.Error -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusDot(Rose)
                                Spacer(Modifier.width(9.dp))
                                Text(
                                    "Sign-in needs attention",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(state.message, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = onSignIn,
                                colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Ink)
                            ) { Text("Sign in again") }
                        }

                        AuthState.SignedOut, AuthState.Loading -> {
                            Text(
                                "Not connected",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Sign in to list, create and stop Colab runtimes.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = onSignIn,
                                colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Ink)
                            ) { Text("Connect Google") }
                        }
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            SectionLabel("OAuth client")
            Spacer(Modifier.height(10.dp))
            Text(
                "Used by the loopback sign-in flow. Create a Desktop app client in Google Cloud Console. " +
                    "Stored encrypted on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = clientId,
                onValueChange = { clientId = it },
                label = { Text("Client ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                colors = fieldColors(),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = clientSecret,
                onValueChange = { clientSecret = it },
                label = { Text("Client secret (optional)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                colors = fieldColors(),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { vm.updateClientConfig(clientId.trim(), clientSecret.trim()) },
                enabled = clientId != vm.clientId() || clientSecret != vm.clientSecret(),
                colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Ink),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Rounded.Key, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(7.dp))
                Text("Save client settings")
            }

            Spacer(Modifier.height(22.dp))
            SectionLabel("Colab SSH key")
            Spacer(Modifier.height(10.dp))
            Text(
                "This ECDSA P-256 key is generated on first use and offered to each runtime. The private half " +
                    "never leaves the Android Keystore-encrypted store.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
            Spacer(Modifier.height(10.dp))
            CodeBlock(code = vm.appPublicKey())

            Spacer(Modifier.height(22.dp))
            SectionLabel("Terminal")
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(InkElevated)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Keep screen on", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Text(
                        "Prevents sleep while an SSH session is open",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
                Switch(
                    checked = keepScreenOn,
                    onCheckedChange = {
                        keepScreenOn = it
                        vm.prefs().keepScreenOn = it
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Ink,
                        checkedTrackColor = Cyan,
                        uncheckedTrackColor = InkBorder
                    )
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(InkElevated)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Terminal text size", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Text(
                        "$fontSize sp · also adjustable from the terminal menu",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
                TextButton(
                    onClick = {
                        fontSize = (fontSize - 1).coerceAtLeast(9)
                        vm.prefs().fontSize = fontSize
                    }
                ) { Text("A-", color = TextSecondary) }
                TextButton(
                    onClick = {
                        fontSize = (fontSize + 1).coerceAtMost(26)
                        vm.prefs().fontSize = fontSize
                    }
                ) { Text("A+", color = Cyan) }
            }

            Spacer(Modifier.height(22.dp))
            SectionLabel("More")
            Spacer(Modifier.height(8.dp))
            SettingsRow(
                icon = { Icon(Icons.Rounded.MenuBook, null, tint = Cyan, modifier = Modifier.size(19.dp)) },
                title = "Setup guide",
                subtitle = "OAuth, SSH and file transfer explained",
                onClick = onOpenGuide
            )
            Spacer(Modifier.height(8.dp))
            SettingsRow(
                icon = { Icon(Icons.Rounded.VpnKey, null, tint = Violet, modifier = Modifier.size(19.dp)) },
                title = "SSH profiles",
                subtitle = "tmate, ngrok, LAN and other hosts",
                onClick = onOpenProfiles
            )

            Spacer(Modifier.height(22.dp))
            SectionLabel("About")
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, null, tint = TextMuted, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(7.dp))
                Text(
                    "ColabDeck ${BuildConfig.VERSION_NAME} · no backend, no analytics",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Independent client. Google Colab's terms apply to your account.",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF4B5A6B)
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(InkElevated)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(InkRaised, RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center
        ) { icon() }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Cyan,
    unfocusedBorderColor = InkBorder,
    focusedLabelColor = Cyan,
    unfocusedLabelColor = TextMuted,
    cursorColor = Cyan,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedContainerColor = InkElevated,
    unfocusedContainerColor = InkElevated
)

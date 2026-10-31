package dev.colabdeck.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.colabdeck.MainViewModel
import dev.colabdeck.data.AuthState
import dev.colabdeck.ui.components.Pill
import dev.colabdeck.ui.components.SectionLabel
import dev.colabdeck.ui.components.copyToClipboard
import dev.colabdeck.ui.components.readClipboard
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onOpenGuide: () -> Unit
) {
    val auth by vm.authState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var tab by remember { mutableStateOf(0) }
    var clientId by remember { mutableStateOf(vm.clientId()) }
    var clientSecret by remember { mutableStateOf(vm.clientSecret()) }
    var token by remember { mutableStateOf("") }

    val loading = auth is AuthState.Loading

    Scaffold(
        containerColor = Ink,
        topBar = {
            TopAppBar(
                title = { Text("Connect Colab") },
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
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(Cyan.copy(alpha = 0.12f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Key, null, tint = Cyan, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Sign in to Google",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "ColabDeck needs the colaboratory scope to list, create and stop runtimes. Credentials stay encrypted on this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Spacer(Modifier.height(20.dp))
            TabRow(
                selectedTabIndex = tab,
                containerColor = Ink,
                contentColor = Cyan,
                divider = {},
                indicator = {}
            ) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = {
                        Text(
                            "Google OAuth",
                            color = if (tab == 0) Cyan else TextMuted,
                            fontWeight = if (tab == 0) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = {
                        Text(
                            "Access token",
                            color = if (tab == 1) Cyan else TextMuted,
                            fontWeight = if (tab == 1) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                )
            }

            Spacer(Modifier.height(18.dp))

            when (tab) {
                0 -> {
                    Text(
                        "Use a Desktop app OAuth client. The app opens Chrome and captures the loopback redirect — no SHA-1 fingerprint or client secret required.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                    Spacer(Modifier.height(12.dp))
                    AuthField(
                        value = clientId,
                        onValueChange = { clientId = it },
                        label = "OAuth client ID",
                        placeholder = "1234-abc.apps.googleusercontent.com",
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false)
                    )
                    Spacer(Modifier.height(10.dp))
                    AuthField(
                        value = clientSecret,
                        onValueChange = { clientSecret = it },
                        label = "Client secret (optional)",
                        placeholder = "GOCSPX-…",
                        secret = true
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = {
                            vm.signInWithOAuth(context, clientId.trim(), clientSecret.trim()) { onBack() }
                        },
                        enabled = !loading && clientId.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Cyan,
                            contentColor = Ink,
                            disabledContainerColor = Cyan.copy(alpha = 0.35f)
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (loading) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Ink)
                            Spacer(Modifier.width(9.dp))
                        }
                        Text("Continue with Google", fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onOpenGuide) {
                        Text("How to create an OAuth client", color = Cyan)
                    }
                }

                else -> {
                    Text(
                        "Paste a Google OAuth access token with the colaboratory scope. Quick way: OAuth 2.0 Playground → authorize the scope → exchange the code → copy Access token.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        label = { Text("Access token") },
                        placeholder = { Text("ya29.…") },
                        minLines = 4,
                        maxLines = 7,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        colors = fieldColors(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Row {
                        TextButton(onClick = { token = readClipboard(context) }) {
                            Text("Paste", color = Cyan)
                        }
                        TextButton(
                            onClick = {
                                openUrl(context, "https://developers.google.com/oauthplayground/")
                            }
                        ) {
                            Icon(Icons.Rounded.OpenInNew, null, modifier = Modifier.size(15.dp), tint = Cyan)
                            Spacer(Modifier.width(5.dp))
                            Text("OAuth Playground", color = Cyan)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Button(
                        onClick = { vm.signInWithToken(token) { onBack() } },
                        enabled = !loading && token.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Cyan,
                            contentColor = Ink,
                            disabledContainerColor = Cyan.copy(alpha = 0.35f)
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (loading) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Ink)
                            Spacer(Modifier.width(9.dp))
                        }
                        Text("Use access token", fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(10.dp))
                    TextButton(
                        onClick = {
                            copyToClipboard(
                                context,
                                "https://www.googleapis.com/auth/colaboratory",
                                "Scope copied"
                            )
                        }
                    ) {
                        Text("Copy Colab scope", color = TextMuted)
                    }
                }
            }

            AnimatedVisibility(visible = auth is AuthState.Error) {
                val message = (auth as? AuthState.Error)?.message ?: ""
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF21121A)),
                    border = BorderStroke(1.dp, Rose.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFFC7D1),
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            if (auth is AuthState.SignedIn) {
                val signedIn = auth as AuthState.SignedIn
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(InkRaised, RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Pill("CONNECTED", Mint, filled = true)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        signedIn.email ?: "Google account",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onBack) { Text("Done", color = Cyan) }
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionLabel("Security")
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.Lock, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Tokens and SSH keys are encrypted with a non-exportable Android Keystore key. ColabDeck has no backend and talks directly to Google.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
            Spacer(Modifier.height(28.dp))
        }
    }

    LaunchedEffect(auth) {
        if (auth is AuthState.SignedIn) {
            // Keep the screen usable; the caller closes it after a successful flow.
        }
    }
}

@Composable
private fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    secret: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions(autoCorrectEnabled = false)
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder, color = TextMuted) },
        singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = keyboardOptions,
        modifier = Modifier.fillMaxWidth(),
        colors = fieldColors(),
        shape = RoundedCornerShape(12.dp)
    )
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

fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

package dev.colabdeck.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.colabdeck.MainViewModel
import dev.colabdeck.data.SshProfile
import dev.colabdeck.ui.components.SectionLabel
import dev.colabdeck.ui.components.readClipboard
import dev.colabdeck.ui.theme.Cyan
import dev.colabdeck.ui.theme.Ink
import dev.colabdeck.ui.theme.InkBorder
import dev.colabdeck.ui.theme.InkElevated
import dev.colabdeck.ui.theme.Rose
import dev.colabdeck.ui.theme.TextMuted
import dev.colabdeck.ui.theme.TextPrimary
import dev.colabdeck.ui.theme.TextSecondary
import dev.colabdeck.ui.theme.Violet
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreen(
    vm: MainViewModel,
    profileId: String,
    onBack: () -> Unit
) {
    val existing = remember(profileId) { if (profileId == "new") null else vm.profile(profileId) }
    val context = LocalContext.current

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var host by remember { mutableStateOf(existing?.host ?: "") }
    var port by remember { mutableStateOf((existing?.port ?: 22).toString()) }
    var user by remember { mutableStateOf(existing?.user ?: "root") }
    var authType by remember { mutableStateOf(existing?.authType ?: SshProfile.AUTH_PASSWORD) }
    var password by remember { mutableStateOf(existing?.password ?: "") }
    var privateKey by remember { mutableStateOf(existing?.privateKey ?: "") }
    var passphrase by remember { mutableStateOf(existing?.passphrase ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var showDelete by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = Ink,
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "New SSH profile" else "Edit profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (existing != null) {
                        IconButton(onClick = { showDelete = true }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", tint = Rose)
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            SectionLabel("Connection")
            Spacer(Modifier.height(8.dp))
            ProfileField(name, { name = it }, "Display name", "My tmate box")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ProfileField(host, { host = it }, "Host", "nyc1.tmate.io", Modifier.weight(1f))
                ProfileField(
                    port,
                    { port = it.filter(Char::isDigit).take(5) },
                    "Port",
                    "22",
                    Modifier.width(94.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
            Spacer(Modifier.height(10.dp))
            ProfileField(user, { user = it }, "User", "root")

            Spacer(Modifier.height(20.dp))
            SectionLabel("Authentication")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = authType == SshProfile.AUTH_PASSWORD,
                    onClick = { authType = SshProfile.AUTH_PASSWORD },
                    label = { Text("Password") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Cyan.copy(alpha = 0.15f),
                        selectedLabelColor = Cyan,
                        containerColor = InkElevated,
                        labelColor = TextSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = authType == SshProfile.AUTH_PASSWORD,
                        borderColor = InkBorder,
                        selectedBorderColor = Cyan.copy(alpha = 0.4f)
                    )
                )
                FilterChip(
                    selected = authType == SshProfile.AUTH_KEY,
                    onClick = { authType = SshProfile.AUTH_KEY },
                    label = { Text("Private key") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Violet.copy(alpha = 0.15f),
                        selectedLabelColor = Violet,
                        containerColor = InkElevated,
                        labelColor = TextSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = authType == SshProfile.AUTH_KEY,
                        borderColor = InkBorder,
                        selectedBorderColor = Violet.copy(alpha = 0.4f)
                    )
                )
            }

            Spacer(Modifier.height(12.dp))
            if (authType == SshProfile.AUTH_PASSWORD) {
                ProfileField(
                    password,
                    { password = it },
                    "Password",
                    "••••••••",
                    secret = true
                )
            } else {
                OutlinedTextField(
                    value = privateKey,
                    onValueChange = { privateKey = it },
                    label = { Text("Private key (OpenSSH / PEM)") },
                    placeholder = { Text("-----BEGIN OPENSSH PRIVATE KEY-----", color = TextMuted) },
                    minLines = 5,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    colors = profileFieldColors(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { privateKey = readClipboard(context) },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Rounded.ContentPaste, null, modifier = Modifier.size(16.dp), tint = Violet)
                    Spacer(Modifier.width(6.dp))
                    Text("Paste key from clipboard", color = Violet)
                }
                Spacer(Modifier.height(10.dp))
                ProfileField(
                    passphrase,
                    { passphrase = it },
                    "Key passphrase (optional)",
                    "leave blank if unencrypted",
                    secret = true
                )
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel("Notes")
            Spacer(Modifier.height(8.dp))
            ProfileField(note, { note = it }, "Note", "Optional label")

            validationError?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = Rose)
            }

            Spacer(Modifier.height(22.dp))
            Button(
                onClick = {
                    val parsedPort = port.toIntOrNull()
                    validationError = when {
                        host.isBlank() -> "Host is required."
                        parsedPort == null || parsedPort !in 1..65535 -> "Port must be between 1 and 65535."
                        authType == SshProfile.AUTH_KEY && privateKey.isBlank() -> "Paste a private key or switch to password auth."
                        else -> null
                    }
                    if (validationError == null) {
                        vm.saveProfile(
                            SshProfile(
                                id = existing?.id ?: UUID.randomUUID().toString(),
                                name = name.trim().ifBlank { host.trim() },
                                host = host.trim(),
                                port = parsedPort ?: 22,
                                user = user.trim().ifBlank { "root" },
                                authType = authType,
                                password = password,
                                privateKey = privateKey,
                                passphrase = passphrase,
                                note = note.trim()
                            )
                        )
                        onBack()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Violet, contentColor = Ink),
                shape = RoundedCornerShape(13.dp)
            ) {
                Text("Save profile", fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(14.dp))
            Text(
                "JSch connects directly to host:port. Cloudflare quick tunnels need cloudflared on the client, so use tmate or the built-in Colab terminal for those.",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
            Spacer(Modifier.height(28.dp))
        }
    }

    if (showDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete profile?") },
            text = { Text("This removes the saved host and credentials from this device.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteProfile(existing.id)
                    showDelete = false
                    onBack()
                }) { Text("Delete", color = Rose) }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("Cancel") }
            },
            containerColor = InkElevated
        )
    }
}

@Composable
private fun ProfileField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
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
        modifier = modifier,
        colors = profileFieldColors(),
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun profileFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Violet,
    unfocusedBorderColor = InkBorder,
    focusedLabelColor = Violet,
    unfocusedLabelColor = TextMuted,
    cursorColor = Violet,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedContainerColor = InkElevated,
    unfocusedContainerColor = InkElevated
)

package dev.colabdeck.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Upload
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.colabdeck.MainViewModel
import dev.colabdeck.data.ColabFilesApi
import dev.colabdeck.data.ColabSession
import dev.colabdeck.data.RemoteEntry
import dev.colabdeck.ui.components.Pill
import dev.colabdeck.ui.components.SectionLabel
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Browses /content on a live Colab runtime through the Jupyter Contents API.
 * Uploads use the Storage Access Framework and are streamed through the
 * runtime proxy; no file ever touches a third-party server.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    vm: MainViewModel,
    endpoint: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var path by remember { mutableStateOf("/content") }
    var entries by remember { mutableStateOf<List<RemoteEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var busyText by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showNewFolder by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<RemoteEntry?>(null) }
    var pendingDownload by remember { mutableStateOf<RemoteEntry?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }

    suspend fun <T> withSession(block: suspend (ColabSession) -> T): Result<T> {
        val session = vm.freshSession(endpoint).getOrElse { return Result.failure(it) }
        return runCatching { block(session) }
    }

    fun load(target: String = path, spinner: Boolean = true) {
        scope.launch {
            if (spinner) loading = true
            error = null
            withSession { session -> vm.filesApi().list(session, target) }
                .onSuccess {
                    entries = it
                    path = target
                }
                .onFailure { error = it.message ?: "Could not list the runtime directory." }
            loading = false
        }
    }

    LaunchedEffect(endpoint, reloadKey) {
        load(path, spinner = true)
    }

    fun join(parent: String, name: String): String = parent.trimEnd('/') + "/" + name.trimStart('/')

    fun upload(uri: Uri) {
        scope.launch {
            busyText = "Uploading…"
            error = null
            runCatching {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IOException("Could not read the selected file.")
                }
                if (bytes.size > ColabFilesApi.MAX_TRANSFER_BYTES) {
                    throw IOException(
                        "Files are limited to ${ColabFilesApi.MAX_TRANSFER_BYTES / (1024 * 1024)} MB in ColabDeck."
                    )
                }
                val name = displayName(context, uri) ?: "upload-${System.currentTimeMillis()}.bin"
                val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                withSession { session ->
                    vm.filesApi().upload(session, join(path, name), bytes, mime)
                }.getOrThrow()
            }
                .onSuccess {
                    toast(context, "Uploaded")
                    load(path, spinner = false)
                }
                .onFailure { error = it.message ?: "Upload failed." }
            busyText = null
        }
    }

    val uploadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { upload(it) } }

    val downloadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val target = pendingDownload
        pendingDownload = null
        if (uri == null || target == null) return@rememberLauncherForActivityResult
        scope.launch {
            busyText = "Downloading ${target.name}…"
            error = null
            runCatching {
                val file = withSession { session ->
                    vm.filesApi().download(session, target.path)
                }.getOrThrow()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(file.bytes) }
                        ?: throw IOException("Could not open the destination file.")
                }
            }
                .onSuccess { toast(context, "Saved ${target.name}") }
                .onFailure { error = it.message ?: "Download failed." }
            busyText = null
        }
    }

    fun download(entry: RemoteEntry) {
        pendingDownload = entry
        downloadLauncher.launch(entry.name.ifBlank { "download.bin" })
    }

    fun delete(entry: RemoteEntry) {
        scope.launch {
            busyText = "Deleting ${entry.name}…"
            error = null
            withSession { session -> vm.filesApi().delete(session, entry.path) }
                .onSuccess {
                    toast(context, "Deleted ${entry.name}")
                    load(path, spinner = false)
                }
                .onFailure { error = it.message ?: "Delete failed." }
            busyText = null
        }
    }

    fun createFolder(name: String) {
        scope.launch {
            busyText = "Creating folder…"
            error = null
            withSession { session ->
                vm.filesApi().createDirectory(session, join(path, name))
            }
                .onSuccess {
                    toast(context, "Folder created")
                    load(path, spinner = false)
                }
                .onFailure { error = it.message ?: "Could not create the folder." }
            busyText = null
        }
    }

    BackHandler(enabled = path != "/content" && path != "/") {
        load(parentOf(path), spinner = true)
    }

    Scaffold(
        containerColor = Ink,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Files", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text(
                            text = path,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showNewFolder = true }) {
                        Icon(Icons.Rounded.CreateNewFolder, contentDescription = "New folder", tint = TextSecondary)
                    }
                    IconButton(onClick = { load(path, spinner = true) }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Ink,
                    titleContentColor = TextPrimary,
                    navigationIconContentColor = TextSecondary,
                    actionIconContentColor = TextSecondary
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { uploadLauncher.launch(arrayOf("*/*")) },
                containerColor = Cyan,
                contentColor = Ink,
                icon = { Icon(Icons.Rounded.Upload, null) },
                text = { Text("Upload", fontWeight = FontWeight.SemiBold) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Breadcrumbs(
                path = path,
                onNavigate = { target -> load(target, spinner = true) }
            )

            if (loading || busyText != null) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = Cyan,
                    trackColor = InkBorder
                )
            } else {
                HorizontalDivider(color = InkBorder)
            }

            busyText?.let { label ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(InkElevated)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = Cyan
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            error?.let { message ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF21121A))
                        .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFFC7D1),
                        modifier = Modifier.weight(1f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    TextButton(onClick = { load(path, spinner = true) }) {
                        Text("Retry", color = Cyan)
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("Runtime contents")
                        Spacer(Modifier.width(8.dp))
                        if (entries.isNotEmpty()) {
                            Pill("${entries.size}", Cyan, filled = true)
                        }
                    }
                }

                if (entries.isEmpty() && !loading) {
                    item {
                        EmptyFolder()
                    }
                }

                items(entries) { entry ->
                    EntryRow(
                        entry = entry,
                        onOpen = {
                            if (entry.isDirectory) {
                                load(entry.path, spinner = true)
                            } else {
                                download(entry)
                            }
                        },
                        onDownload = { download(entry) },
                        onDelete = { deleteTarget = entry }
                    )
                }

                item {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Files live in the runtime's /content directory and disappear when the runtime stops. " +
                            "In-app transfers are limited to ${ColabFilesApi.MAX_TRANSFER_BYTES / (1024 * 1024)} MB.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }
        }
    }

    if (showNewFolder) {
        NewFolderDialog(
            onDismiss = { showNewFolder = false },
            onCreate = { name ->
                showNewFolder = false
                createFolder(name)
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete ${target.name}?") },
            text = {
                Text(
                    text = if (target.isDirectory) {
                        "The folder and everything inside it will be removed from the runtime."
                    } else {
                        "This file will be removed from the runtime."
                    },
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    delete(target)
                }) { Text("Delete", color = Rose) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
            containerColor = InkElevated
        )
    }
}

@Composable
private fun Breadcrumbs(path: String, onNavigate: (String) -> Unit) {
    val segments = path.trim('/').split('/').filter { it.isNotEmpty() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Crumb(label = "/", onClick = { onNavigate("/") })
        var built = ""
        segments.forEach { segment ->
            built = "$built/$segment"
            val target = built
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(14.dp)
            )
            Crumb(label = segment, onClick = { onNavigate(target) })
        }
    }
}

@Composable
private fun Crumb(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
        color = Cyan,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 3.dp)
    )
}

@Composable
private fun EntryRow(
    entry: RemoteEntry,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = InkElevated),
        border = BorderStroke(1.dp, InkBorder),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 2.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(
                        if (entry.isDirectory) Violet.copy(alpha = 0.12f) else Cyan.copy(alpha = 0.10f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (entry.isDirectory) Icons.Rounded.Folder else Icons.Rounded.InsertDriveFile,
                    contentDescription = null,
                    tint = if (entry.isDirectory) Violet else Cyan,
                    modifier = Modifier.size(19.dp)
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (entry.isDirectory) {
                        "folder"
                    } else {
                        listOfNotNull(
                            formatBytes(entry.size),
                            entry.lastModified.take(16).replace('T', ' ').takeIf { it.isNotBlank() }
                        ).joinToString(" · ")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    maxLines = 1
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Actions", tint = TextMuted)
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    containerColor = InkRaised
                ) {
                    if (!entry.isDirectory) {
                        DropdownMenuItem(
                            text = { Text("Download") },
                            leadingIcon = { Icon(Icons.Rounded.Download, null, tint = TextSecondary) },
                            onClick = {
                                menuOpen = false
                                onDownload()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Delete", color = Rose) },
                        leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = Rose) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyFolder() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .background(InkRaised, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.FolderOpen, null, tint = TextMuted, modifier = Modifier.size(27.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text("Empty folder", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text(
            "Upload a file with the button below.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}

@Composable
private fun NewFolderDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New folder") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Folder name") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Cyan,
                    unfocusedBorderColor = InkBorder,
                    cursorColor = Cyan
                )
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name.trim()) },
                enabled = name.isNotBlank() && !name.contains('/')
            ) { Text("Create", color = Cyan) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        containerColor = InkElevated
    )
}

private fun parentOf(path: String): String {
    val trimmed = path.trimEnd('/')
    val parent = trimmed.substringBeforeLast('/', "")
    return if (parent.isBlank()) "/" else parent
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
    else -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
}

private fun displayName(context: Context, uri: Uri): String? {
    runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    val value = cursor.getString(index)
                    if (!value.isNullOrBlank()) return value
                }
            }
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
}

private fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

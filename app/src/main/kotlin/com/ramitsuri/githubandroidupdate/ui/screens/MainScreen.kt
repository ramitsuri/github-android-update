package com.ramitsuri.githubandroidupdate.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramitsuri.githubandroidupdate.data.DataStoreManager
import com.ramitsuri.githubandroidupdate.data.model.TrackedRepo
import com.ramitsuri.githubandroidupdate.viewmodel.MainViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    var showAddRepoDialog by remember { mutableStateOf(false) }
    var showPatDialog by remember { mutableStateOf(false) }

    if (state.isLoaded && state.pat == null) {
        PatDialog(onConfirm = { viewModel.savePat(it) })
    }

    if (showPatDialog) {
        PatDialog(
            initialPat = state.pat ?: "",
            onConfirm = {
                viewModel.savePat(it)
                showPatDialog = false
            },
            onDismiss = { showPatDialog = false }
        )
    }

    if (showAddRepoDialog) {
        AddRepoDialog(
            onConfirm = { owner, name ->
                viewModel.addRepo(owner, name)
                showAddRepoDialog = false
            },
            onDismiss = { showAddRepoDialog = false }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("GitHub Update") },
                actions = {
                    IconButton(onClick = { showPatDialog = true }) {
                        Icon(Icons.Rounded.Key, contentDescription = "Edit PAT")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddRepoDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Repo")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            InstallPermissionCard()

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
            ) {
                items(state.trackedRepos, key = { it.fullName }) { repo ->
                    val isSelfRepo = repo.owner == DataStoreManager.SELF_OWNER &&
                            repo.name == DataStoreManager.SELF_REPO
                    TrackedRepoCard(
                        repo = repo,
                        isSelfRepo = isSelfRepo,
                        progress = state.downloadProgress[repo.fullName],
                        onRefresh = { viewModel.checkForUpdates(repo.owner, repo.name) },
                        onDownload = { viewModel.downloadAndInstall(repo) },
                        onDelete = { viewModel.removeRepo(repo) }
                    )
                }
            }
        }
    }
}

@Composable
fun InstallPermissionCard() {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(context.packageManager.canRequestPackageInstalls())
    }

    if (!hasPermission) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Install Permission Required",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text("To install updates, you need to allow this app to install other apps.")
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = "package:${context.packageName}".toUri()
                    }
                    context.startActivity(intent)
                }) {
                    Text("Grant Permission")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackedRepoCard(
    repo: TrackedRepo,
    isSelfRepo: Boolean,
    progress: Float?,
    onRefresh: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    if (isSelfRepo) {
        TrackedRepoCardContent(
            repo = repo,
            isSelfRepo = true,
            progress = progress,
            onRefresh = onRefresh,
            onDownload = onDownload
        )
    } else {
        val dismissState = rememberSwipeToDismissBoxState()

        SwipeToDismissBox(
            state = dismissState,
            onDismiss = {
                onDelete()
            },
            enableDismissFromStartToEnd = false,
            backgroundContent = {
                val color = when (dismissState.dismissDirection) {
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                    else -> Color.Transparent
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(color, CardDefaults.shape)
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        ) {
            TrackedRepoCardContent(
                repo = repo,
                isSelfRepo = false,
                progress = progress,
                onRefresh = onRefresh,
                onDownload = onDownload
            )
        }
    }
}

@Composable
fun TrackedRepoCardContent(
    repo: TrackedRepo,
    isSelfRepo: Boolean,
    progress: Float?,
    onRefresh: () -> Unit,
    onDownload: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = if (isSelfRepo) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (isSelfRepo) "This App" else repo.owner,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelfRepo) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.secondary
                        }
                    )
                    Text(
                        repo.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    repo.latestReleaseVersion?.let { version ->
                        val dateText = remember(repo.latestReleaseTimestamp) {
                            repo.latestReleaseTimestamp?.let {
                                DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                                    .withZone(ZoneId.systemDefault())
                                    .format(Instant.ofEpochMilli(it))
                            }
                        }
                        Text(
                            text = version,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        if (dateText != null) {
                            Text(
                                text = dateText,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
                if (repo.hasUpdate) {
                    Text(
                        "Update",
                        color = if (isSelfRepo) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Row {
                    IconButton(onClick = onRefresh, enabled = progress == null) {
                        Icon(Icons.Default.Refresh, contentDescription = "Check for updates")
                    }
                    if (repo.hasUpdate || repo.latestReleaseVersion != null) {
                        IconButton(onClick = onDownload, enabled = progress == null) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = "Download and Install"
                            )
                        }
                    }
                }
            }
            if (progress != null) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
fun PatDialog(
    initialPat: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: (() -> Unit)? = null
) {
    val patState = rememberTextFieldState(initialPat)
    AlertDialog(
        onDismissRequest = { onDismiss?.invoke() },
        title = { Text("GitHub Personal Access Token") },
        text = {
            Column {
                Text("Enter your GitHub PAT to access private repos and avoid rate limiting.")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    state = patState,
                    label = { Text("PAT") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(patState.text.toString()) }) {
                Text("Save")
            }
        },
        dismissButton = onDismiss?.let {
            {
                TextButton(onClick = it) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
fun AddRepoDialog(
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val ownerState = rememberTextFieldState()
    val nameState = rememberTextFieldState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Tracked Repository") },
        text = {
            Column {
                OutlinedTextField(
                    state = ownerState,
                    label = { Text("Owner") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    state = nameState,
                    label = { Text("Repository Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(ownerState.text.toString(), nameState.text.toString())
            }) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

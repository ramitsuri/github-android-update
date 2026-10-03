package com.ramitsuri.githubandroidupdate.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.rounded.Info
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramitsuri.githubandroidupdate.data.model.TrackedRepo
import androidx.compose.ui.tooling.preview.Preview
import com.ramitsuri.githubandroidupdate.ui.theme.GitHubAndroidUpdateTheme
import com.ramitsuri.githubandroidupdate.viewmodel.MainUiState
import com.ramitsuri.githubandroidupdate.viewmodel.MainViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MainScreen(
        state = state,
        onSavePat = viewModel::savePat,
        onAddRepo = viewModel::addRepo,
        onCheckForUpdates = viewModel::checkForUpdates,
        onDownloadAndInstall = viewModel::downloadAndInstall,
        onDownloadAndInstallOnWatch = viewModel::downloadAndInstallOnWatch,
        onRemoveRepo = viewModel::removeRepo,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: MainUiState,
    onSavePat: (String) -> Unit,
    onAddRepo: (String, String) -> Unit,
    onCheckForUpdates: (String, String) -> Unit,
    onDownloadAndInstall: (TrackedRepo) -> Unit,
    onDownloadAndInstallOnWatch: (TrackedRepo) -> Unit,
    onRemoveRepo: (TrackedRepo) -> Unit,
) {
    var showAddRepoDialog by remember { mutableStateOf(false) }
    var showPatDialog by remember { mutableStateOf(false) }
    var selfRepoInfoToShow: TrackedRepo? by remember { mutableStateOf(null) }

    LaunchedEffect(state.isLoaded, state.pat) {
        if (state.isLoaded && state.pat == null) {
            showPatDialog = true
        }
    }

    if (showPatDialog) {
        PatDialog(
            initialPat = state.pat ?: "",
            onConfirm = {
                onSavePat(it)
                showPatDialog = false
            },
            onDismiss = { showPatDialog = false }
        )
    }

    if (showAddRepoDialog) {
        AddRepoDialog(
            onConfirm = { owner, name ->
                onAddRepo(owner, name)
                showAddRepoDialog = false
            },
            onDismiss = { showAddRepoDialog = false }
        )
    }

    selfRepoInfoToShow?.let { selfRepo ->
        SelfRepoInfoDialog(
            selfRepo = selfRepo,
            onDismiss = { selfRepoInfoToShow = null },
            onRefresh = { onCheckForUpdates(selfRepo.owner, selfRepo.name) },
            onDownload = { onDownloadAndInstall(selfRepo) },
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("GitHub Update") },
                actions = {
                    if (state.selfRepo != null) {
                        IconButton(onClick = { selfRepoInfoToShow = state.selfRepo }) {
                            Icon(Icons.Rounded.Info, contentDescription = "Show app info")
                        }
                    }
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
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.trackedRepos, key = { it.fullName }) { repo ->
                    TrackedRepoCard(
                        repo = repo,
                        progress = state.downloadProgress[repo.fullName],
                        onRefresh = { onCheckForUpdates(repo.owner, repo.name) },
                        onDownload = { onDownloadAndInstall(repo) },
                        onDownloadOnWatch = { onDownloadAndInstallOnWatch(repo) },
                        onDelete = { onRemoveRepo(repo) }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(96.dp))
                }
            }
        }
    }
}

@Composable
private fun InstallPermissionCard() {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(context.packageManager.canRequestPackageInstalls())
    }

    LifecycleResumeEffect(context) {
        hasPermission = context.packageManager.canRequestPackageInstalls()
        onPauseOrDispose { }
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
private fun TrackedRepoCard(
    repo: TrackedRepo,
    progress: Float?,
    onRefresh: () -> Unit,
    onDownload: () -> Unit,
    onDownloadOnWatch: () -> Unit,
    onDelete: () -> Unit,
) {
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
            progress = progress,
            onRefresh = onRefresh,
            onDownload = onDownload,
            onDownloadOnWatch = onDownloadOnWatch
        )
    }
}

@Composable
private fun TrackedRepoCardContent(
    repo: TrackedRepo,
    progress: Float?,
    onRefresh: () -> Unit,
    onDownload: () -> Unit,
    onDownloadOnWatch: () -> Unit = {},
) {
    val borderModifier = if (repo.hasUpdate) {
        val borderColor = rememberInfiniteTransition(label = "updateBorder").animateColor(
            initialValue = MaterialTheme.colorScheme.primary,
            targetValue = CardDefaults.cardColors().containerColor,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1000),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "updateBorderColor",
        )
        val shape = CardDefaults.shape
        Modifier.drawWithCache {
            val strokeWidth = 2.dp.toPx()
            val inset = strokeWidth / 2
            val outline = shape.createOutline(
                Size(size.width - strokeWidth, size.height - strokeWidth),
                layoutDirection,
                this,
            )
            val stroke = Stroke(strokeWidth)
            onDrawWithContent {
                drawContent()
                translate(inset, inset) {
                    drawOutline(outline, color = borderColor.value, style = stroke)
                }
            }
        }
    } else {
        Modifier
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors()
    ) {
        Column(
            modifier = Modifier
                .then(borderModifier)
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = repo.owner,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary
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
                Row {
                    IconButton(onClick = onRefresh, enabled = progress == null) {
                        Icon(Icons.Default.Refresh, contentDescription = "Check for updates")
                    }
                    if (repo.hasWearUpdate) {
                        IconButton(onClick = onDownloadOnWatch, enabled = progress == null) {
                            Icon(
                                Icons.Default.Watch,
                                contentDescription = "Install on Watch"
                            )
                        }
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
private fun PatDialog(
    initialPat: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: (() -> Unit)? = null,
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
private fun AddRepoDialog(
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val ownerState = rememberTextFieldState(initialText = "ramitsuri")
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

@Composable
private fun SelfRepoInfoDialog(
    selfRepo: TrackedRepo,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onDownload: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
    ) {
        TrackedRepoCardContent(
            repo = selfRepo,
            progress = null,
            onRefresh = onRefresh,
            onDownload = onDownload,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MainScreenPreview() {
    GitHubAndroidUpdateTheme {
        MainScreen(
            state = MainUiState(
                pat = "ghp_sample",
                trackedRepos = listOf(
                    TrackedRepo(
                        owner = "owner-name1",
                        name = "app-name1",
                        latestReleaseVersion = "v1.2.0",
                        latestReleaseTimestamp = 1_797_200_000_000L,
                        hasUpdate = true,
                    ),
                    TrackedRepo(
                        owner = "owner-name2",
                        name = "app-name2",
                        latestReleaseVersion = "v3.2.0",
                        latestReleaseTimestamp = 1_757_200_000_000L,
                    ),
                    TrackedRepo(
                        owner = "owner-name2",
                        name = "app-name3",
                        latestReleaseVersion = "v1.2.0",
                        latestReleaseTimestamp = 1_717_200_000_000L,
                    ),
                    TrackedRepo(
                        owner = "owner-name1",
                        name = "app-name4",
                        latestReleaseVersion = "v3.2.0",
                        latestReleaseTimestamp = 1_757_200_000_000L,
                    ),
                    TrackedRepo(
                        owner = "owner-name2",
                        name = "app-name5",
                        latestReleaseVersion = "v1.2.0",
                        latestReleaseTimestamp = 1_717_200_000_000L,
                    ),
                ),
                downloadProgress = mapOf("ramitsuri/expense-tracker" to 0.4f),
                isLoaded = true,
            ),
            onSavePat = {},
            onAddRepo = { _, _ -> },
            onCheckForUpdates = { _, _ -> },
            onDownloadAndInstall = {},
            onDownloadAndInstallOnWatch = {},
            onRemoveRepo = {},
        )
    }
}

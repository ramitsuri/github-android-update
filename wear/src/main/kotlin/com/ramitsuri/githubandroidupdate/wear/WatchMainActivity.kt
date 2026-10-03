package com.ramitsuri.githubandroidupdate.wear

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import java.io.File

class WatchMainActivity : ComponentActivity() {

    private var apkPathState = mutableStateOf<String?>(null)
    private var isReadyToInstall = mutableStateOf(false)
    private var isDownloading = mutableStateOf(false)
    private var downloadProgressState = mutableFloatStateOf(0f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleIntent(intent)

        setContent {
            LaunchedEffect(Unit) {
                WearUpdateBus.events.collect { event ->
                    when (event) {
                        is WearUpdateEvent.Progress -> {
                            downloadProgressState.floatValue = event.progress
                            isDownloading.value = true
                            isReadyToInstall.value = false
                        }
                        is WearUpdateEvent.Ready -> {
                            apkPathState.value = event.apkPath
                            isDownloading.value = false
                            isReadyToInstall.value = true
                        }
                    }
                }
            }

            MaterialTheme {
                UpdateScreen(
                    isReady = isReadyToInstall.value,
                    isDownloading = isDownloading.value,
                    downloadProgress = downloadProgressState.floatValue,
                    onInstallClick = {
                        apkPathState.value?.let { path ->
                            promptSystemInstall(File(path))
                        }
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(launchIntent: Intent?) {
        val destinationFile = File(cacheDir, WearApkReceiverService.TARGET_APK_NAME)
        val fileAgeMs = System.currentTimeMillis() - destinationFile.lastModified()
        val isRecentFile = destinationFile.exists() && destinationFile.length() > 0 && fileAgeMs < MAX_APK_AGE_MS

        val hasStatusDownloading = launchIntent?.getStringExtra(WearApkReceiverService.EXTRA_STATUS) == WearApkReceiverService.STATUS_DOWNLOADING

        if (hasStatusDownloading) {
            isDownloading.value = true
            isReadyToInstall.value = false
        } else if (isRecentFile) {
            apkPathState.value = destinationFile.absolutePath
            isDownloading.value = false
            isReadyToInstall.value = true
        } else {
            // Manual launch or stale file: clean up old file
            if (destinationFile.exists()) {
                destinationFile.delete()
            }
            apkPathState.value = null
            isDownloading.value = false
            isReadyToInstall.value = false
        }
    }

    private fun promptSystemInstall(apkFile: File) {
        try {
            if (!packageManager.canRequestPackageInstalls()) {
                Log.i(TAG, "Install unknown apps permission missing on Wear OS. Launching settings...")
                val permissionIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = "package:$packageName".toUri()
                }
                startActivity(permissionIntent)
                return
            }

            Log.i(TAG, "Prompting system install for APK: ${apkFile.absolutePath} (size: ${apkFile.length()})")
            val apkUri = FileProvider.getUriForFile(this, "${packageName}.provider", apkFile)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch system installer: ${e.stackTraceToString()}")
        }
    }

    companion object {
        private const val MAX_APK_AGE_MS = 30 * 60 * 1000L // 30 minutes
        private const val TAG = "WatchMainActivity"
    }
}

@Composable
private fun UpdateScreen(
    isReady: Boolean,
    isDownloading: Boolean,
    downloadProgress: Float,
    onInstallClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            isReady -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Update ready!",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.body1
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Chip(
                        onClick = onInstallClick,
                        label = {
                            Text(
                                text = "Install Now",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        },
                        colors = ChipDefaults.primaryChipColors()
                    )
                }
            }
            isDownloading -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        progress = downloadProgress,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Receiving update...\n${(downloadProgress * 100).toInt()}%",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.body2
                    )
                }
            }
            else -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No active updates.\nCheck for updates\non your phone.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurfaceVariant
                    )
                }
            }
        }
    }
}

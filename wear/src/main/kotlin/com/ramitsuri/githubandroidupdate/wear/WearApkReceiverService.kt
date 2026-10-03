package com.ramitsuri.githubandroidupdate.wear

import android.content.Intent
import android.util.Log
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

class WearApkReceiverService : WearableListenerService() {

    override fun onChannelOpened(channel: ChannelClient.Channel) {
        if (channel.path == WATCH_UPDATER_STREAM_PATH) {
            try {
                // Launch Watch UI immediately so user sees "Receiving update..."
                val launchIntent = Intent(this, WatchMainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(EXTRA_STATUS, STATUS_DOWNLOADING)
                }
                startActivity(launchIntent)

                // Get InputStream from ChannelClient and receive byte chunks
                Wearable.getChannelClient(this).getInputStream(channel)
                    .addOnSuccessListener { inputStream ->
                        Thread {
                            try {
                                val destinationFile = File(cacheDir, TARGET_APK_NAME)
                                if (destinationFile.exists()) {
                                    destinationFile.delete()
                                }

                                val fileOutputStream = FileOutputStream(destinationFile)

                                // Read 8-byte length header
                                val headerBuffer = ByteArray(8)
                                var headerBytesRead = 0
                                while (headerBytesRead < 8) {
                                    val r = inputStream.read(headerBuffer, headerBytesRead, 8 - headerBytesRead)
                                    if (r == -1) break
                                    headerBytesRead += r
                                }

                                val totalBytes = if (headerBytesRead == 8) {
                                    ByteBuffer.wrap(headerBuffer).long
                                } else 0L

                                Log.i(TAG, "Incoming APK stream size: $totalBytes bytes")

                                val buffer = ByteArray(16 * 1024)
                                var bytesReceived = 0L
                                var bytesRead: Int
                                var lastBroadcastTime = 0L

                                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                    fileOutputStream.write(buffer, 0, bytesRead)
                                    bytesReceived += bytesRead

                                    val progress = if (totalBytes > 0) (bytesReceived.toDouble() / totalBytes).toFloat() else 0f
                                    val now = System.currentTimeMillis()

                                    // Limit progress updates to ~100ms or 100%
                                    if (now - lastBroadcastTime > 100 || progress >= 1.0f) {
                                        lastBroadcastTime = now
                                        WearUpdateBus.sendEvent(WearUpdateEvent.Progress(progress))
                                    }
                                }

                                fileOutputStream.flush()
                                fileOutputStream.close()
                                inputStream.close()

                                Log.i(TAG, "Stream complete: ${destinationFile.absolutePath} (${destinationFile.length()} bytes)")

                                WearUpdateBus.sendEvent(WearUpdateEvent.Ready(destinationFile.absolutePath))
                            } catch (e: Exception) {
                                Log.e(TAG, "Error reading stream on watch: ${e.stackTraceToString()}")
                            }
                        }.start()
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to get input stream on watch: ${e.stackTraceToString()}")
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Failed in onChannelOpened: ${e.stackTraceToString()}")
            }
        }
    }

    companion object {
        const val WATCH_UPDATER_STREAM_PATH = "/watch_updater_stream"
        const val TARGET_APK_NAME = "wear-release.apk"
        const val EXTRA_STATUS = "STATUS"
        const val STATUS_DOWNLOADING = "DOWNLOADING"
        private const val TAG = "WearApkReceiverService"
    }
}

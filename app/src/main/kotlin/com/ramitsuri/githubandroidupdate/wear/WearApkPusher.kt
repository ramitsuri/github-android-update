package com.ramitsuri.githubandroidupdate.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer

object WearApkPusher {
    private const val WATCH_UPDATER_STREAM_PATH = "/watch_updater_stream"
    private const val TAG = "WearApkPusher"

    suspend fun pushApkToWatch(
        context: Context,
        localDownloadedApk: File,
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val nodeClient = Wearable.getNodeClient(context)
            val channelClient = Wearable.getChannelClient(context)

            // Find connected Wear OS device
            val nodes = Tasks.await(nodeClient.connectedNodes)
            val watchNodeId = nodes.firstOrNull { it.isNearby }?.id ?: nodes.firstOrNull()?.id ?: run {
                Log.e(TAG, "No connected Wear OS nodes found")
                return@withContext false
            }

            Log.i(TAG, "Opening channel to watch node: $watchNodeId")
            val channel = Tasks.await(channelClient.openChannel(watchNodeId, WATCH_UPDATER_STREAM_PATH))

            val totalBytes = localDownloadedApk.length()
            Log.i(TAG, "Streaming file: ${localDownloadedApk.absolutePath} ($totalBytes bytes)")

            val outputStream = Tasks.await(channelClient.getOutputStream(channel))

            // Write 8-byte length header
            val headerBuffer = ByteBuffer.allocate(8).putLong(totalBytes).array()
            outputStream.write(headerBuffer)

            // Stream file bytes with progress updates
            val fileInputStream = FileInputStream(localDownloadedApk)
            val buffer = ByteArray(16 * 1024)
            var bytesSent = 0L
            var bytesRead: Int

            while (fileInputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                bytesSent += bytesRead
                val progress = if (totalBytes > 0) (bytesSent.toDouble() / totalBytes).toFloat() else 0f
                onProgress(progress)
            }

            outputStream.flush()
            outputStream.close()
            fileInputStream.close()

            Log.i(TAG, "Pushed apk successfully ($bytesSent bytes sent)")

            // Close channel after sending completes
            Tasks.await(channelClient.close(channel))
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to push apk: ${e.stackTraceToString()}")
            false
        }
    }
}

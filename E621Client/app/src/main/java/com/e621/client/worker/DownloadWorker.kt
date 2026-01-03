package com.e621.client.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.e621.client.R
import com.e621.client.data.preferences.UserPreferences
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

class DownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val prefs = UserPreferences(context)
    
    // OkHttpClient with longer timeouts for large files
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    companion object {
        const val KEY_URL = "key_url"
        const val KEY_FILE_NAME = "key_file_name"
        const val KEY_POST_ID = "key_post_id"
        const val KEY_TITLE = "key_title"
        
        const val CHANNEL_ID = "download_channel"
        const val NOTIFICATION_ID_BASE = 10000
        const val RESULT_NOTIFICATION_ID_BASE = 20000
    }

    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_URL) ?: return Result.failure()
        val fileName = inputData.getString(KEY_FILE_NAME) ?: "file"
        val postId = inputData.getInt(KEY_POST_ID, 0)
        val title = inputData.getString(KEY_TITLE) ?: "Downloading..."
        
        val notificationId = NOTIFICATION_ID_BASE + postId
        val resultNotificationId = RESULT_NOTIFICATION_ID_BASE + postId

        createNotificationChannel()
        
        // Start as foreground service
        setForeground(createForegroundInfo(notificationId, title, 0, true))

        return try {
            downloadFile(url, fileName, notificationId, resultNotificationId, title, postId)
            Result.success()
        } catch (e: Exception) {
            Log.e("DownloadWorker", "Error downloading", e)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                showErrorNotification(resultNotificationId, title, "Download failed")
                Result.failure()
            }
        }
    }

    private suspend fun downloadFile(
        url: String, 
        fileName: String, 
        notificationId: Int, 
        resultNotificationId: Int,
        title: String,
        postId: Int
    ) {
        // Determine output stream
        val useCustomFolder = prefs.storageCustomFolderEnabled && !prefs.storageCustomFolder.isNullOrEmpty()
        
        // Prepare request
        val requestBuilder = Request.Builder().url(url)
        
        // Check for existing partial file to resume (simplified for now: just overwrite or new)
        // Implementing full resume logic with SAF is complex due to file access. 
        // For robustness, we rely on WorkManager retry which restarts the download.
        // To support true resume, we would need to manage a temp file and check its size.
        
        val response = client.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful) throw Exception("Network error: ${response.code}")
        
        val body = response.body ?: throw Exception("Empty body")
        val totalSize = body.contentLength()
        
        var outputStream: OutputStream? = null
        var finalUri: Uri? = null
        var tempFile: File? = null
        
        try {
            if (useCustomFolder) {
                val result = openSafOutputStream(fileName)
                outputStream = result?.first
                finalUri = result?.second
            }
            
            // Try MediaStore for Android 10+ (API 29) if not using custom folder
            if (outputStream == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val result = openMediaStoreOutputStream(fileName)
                if (result != null) {
                    outputStream = result.first
                    finalUri = result.second
                }
            }
            
            if (outputStream == null) {
                // Fallback to public File (Legacy < Android 10)
                val file = getPublicFile(fileName)
                tempFile = file
                outputStream = FileOutputStream(file)
                
                // Get URI using FileProvider
                finalUri = try {
                    FileProvider.getUriForFile(
                        applicationContext,
                        "${applicationContext.packageName}.fileprovider",
                        file
                    )
                } catch (e: Exception) {
                    null
                }
            }

            val inputStream = body.byteStream()
            val buffer = ByteArray(8 * 1024)
            var bytesRead: Int
            var downloadedBytes = 0L
            var lastUpdate = 0L

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                downloadedBytes += bytesRead
                
                val now = System.currentTimeMillis()
                if (now - lastUpdate > 500) {
                    val progress = if (totalSize > 0) (downloadedBytes * 100 / totalSize).toInt() else 0
                    setForeground(createForegroundInfo(notificationId, title, progress, totalSize <= 0))
                    lastUpdate = now
                }
            }
            
            outputStream.flush()
            
            // Finalize MediaStore entry (remove pending status)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && finalUri != null && tempFile == null && !useCustomFolder) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                applicationContext.contentResolver.update(finalUri, values, null, null)
            }
            
            // Final success notification
            val mimeType = getMimeType(fileName)
            
            // Scan file to make it visible in Gallery/Downloads
            if (tempFile != null) {
                // Legacy file approach - scan directly
                MediaScannerConnection.scanFile(
                    applicationContext,
                    arrayOf(tempFile.absolutePath),
                    arrayOf(mimeType),
                    null
                )
            } else if (useCustomFolder && finalUri != null) {
                // SAF approach - try to get real path and scan
                scanSafFile(finalUri, mimeType)
            }
            
            showSuccessNotification(resultNotificationId, postId, finalUri, mimeType)
            
        } catch (e: Exception) {
            // Cleanup
            try {
                if (useCustomFolder) {
                    // Hard to delete SAF file on error without keeping reference to DocumentFile
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && finalUri != null && tempFile == null) {
                    // Delete incomplete MediaStore entry
                    applicationContext.contentResolver.delete(finalUri, null, null)
                } else {
                    tempFile?.delete()
                }
            } catch (cleanupEx: Exception) { }
            throw e
        } finally {
            outputStream?.close()
            body.close()
        }
    }

    private fun openMediaStoreOutputStream(fileName: String): Pair<OutputStream, Uri>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        
        try {
            val resolver = applicationContext.contentResolver
            val mimeType = getMimeType(fileName)
            
            val parts = fileName.split("/")
            val actualName = parts.last()
            val subDir = if (parts.size > 1) parts.dropLast(1).joinToString("/") else ""
            
            // Determine the correct MediaStore collection and relative path based on mime type
            val (collection, relativePath) = when {
                mimeType.startsWith("image/") -> {
                    val path = if (subDir.isNotEmpty()) "${Environment.DIRECTORY_PICTURES}/E621/$subDir" else "${Environment.DIRECTORY_PICTURES}/E621"
                    Pair(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, path)
                }
                mimeType.startsWith("video/") -> {
                    val path = if (subDir.isNotEmpty()) "${Environment.DIRECTORY_MOVIES}/E621/$subDir" else "${Environment.DIRECTORY_MOVIES}/E621"
                    Pair(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, path)
                }
                else -> {
                    // Fallback to Downloads for other types
                    val path = if (subDir.isNotEmpty()) "${Environment.DIRECTORY_DOWNLOADS}/E621/$subDir" else "${Environment.DIRECTORY_DOWNLOADS}/E621"
                    Pair(MediaStore.Downloads.EXTERNAL_CONTENT_URI, path)
                }
            }
            
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, actualName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            
            val uri = resolver.insert(collection, values) ?: return null
            val stream = resolver.openOutputStream(uri) ?: return null
            
            return Pair(stream, uri)
        } catch (e: Exception) {
            Log.e("DownloadWorker", "MediaStore Error", e)
            return null
        }
    }

    private fun openSafOutputStream(fileName: String): Pair<OutputStream, Uri>? {
        try {
            val uriString = prefs.storageCustomFolder
            if (uriString.isNullOrEmpty()) return null
            
            val treeUri = Uri.parse(uriString)
            val docFile = DocumentFile.fromTreeUri(applicationContext, treeUri) ?: return null
            
            if (!docFile.canWrite()) return null
            
            // Handle subfolders if any in fileName (e.g. "folder/image.jpg")
            var currentDir = docFile
            val parts = fileName.split("/")
            val actualName = parts.last()
            
            if (parts.size > 1) {
                for (i in 0 until parts.size - 1) {
                    val subName = parts[i]
                    val existing = currentDir.findFile(subName)
                    currentDir = if (existing != null && existing.isDirectory) {
                        existing
                    } else {
                        currentDir.createDirectory(subName) ?: return null
                    }
                }
            }
            
            // Check overwrite
            val existingFile = currentDir.findFile(actualName)
            if (existingFile != null) {
                if (prefs.storageOverwrite) {
                    existingFile.delete()
                } else {
                    // File exists and no overwrite -> skip (simulate success)
                    return null // Special case: handle upstream? For now, let's just overwrite or create new
                }
            }
            
            val mimeType = getMimeType(actualName)
            val newFile = currentDir.createFile(mimeType, actualName.substringBeforeLast(".")) ?: return null
            val stream = applicationContext.contentResolver.openOutputStream(newFile.uri) ?: return null
            return Pair(stream, newFile.uri)
            
        } catch (e: Exception) {
            Log.e("DownloadWorker", "SAF Error", e)
            return null
        }
    }

    private fun getPublicFile(fileName: String): File {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val baseDir = File(downloadDir, "E621")
        
        val parts = fileName.split("/")
        val actualName = parts.last()
        val parent = if (parts.size > 1) File(baseDir, parts.dropLast(1).joinToString("/")) else baseDir
        
        if (!parent.exists()) parent.mkdirs()
        
        return File(parent, actualName)
    }

    private fun getMimeType(fileName: String): String {
        return when (fileName.substringAfterLast(".", "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webm" -> "video/webm"
            "mp4" -> "video/mp4"
            else -> "application/octet-stream"
        }
    }
    
    /**
     * Scan SAF file to make it visible in gallery and other apps.
     * Attempts to get the real file path from the SAF URI and scan it.
     */
    private fun scanSafFile(uri: Uri, mimeType: String) {
        try {
            // Try to get the real file path from SAF URI
            val realPath = getRealPathFromSafUri(uri)
            if (realPath != null) {
                MediaScannerConnection.scanFile(
                    applicationContext,
                    arrayOf(realPath),
                    arrayOf(mimeType),
                    null
                )
                Log.d("DownloadWorker", "Scanned SAF file: $realPath")
            } else {
                Log.w("DownloadWorker", "Could not get real path for SAF URI: $uri")
            }
        } catch (e: Exception) {
            Log.e("DownloadWorker", "Error scanning SAF file", e)
        }
    }
    
    /**
     * Try to extract real file path from SAF URI.
     * Works for primary external storage documents.
     */
    private fun getRealPathFromSafUri(uri: Uri): String? {
        try {
            // Check if it's a document URI
            if (!DocumentsContract.isDocumentUri(applicationContext, uri)) {
                return null
            }
            
            val docId = DocumentsContract.getDocumentId(uri)
            val authority = uri.authority
            
            // Handle external storage documents (most common case)
            if (authority == "com.android.externalstorage.documents") {
                val split = docId.split(":")
                if (split.size >= 2) {
                    val type = split[0]
                    val relativePath = split[1]
                    
                    if ("primary".equals(type, ignoreCase = true)) {
                        return "${Environment.getExternalStorageDirectory()}/$relativePath"
                    } else {
                        // Handle secondary storage (SD card)
                        val externalDirs = applicationContext.getExternalFilesDirs(null)
                        for (dir in externalDirs) {
                            if (dir != null) {
                                val path = dir.absolutePath
                                if (path.contains(type)) {
                                    val basePath = path.substring(0, path.indexOf("/Android"))
                                    return "$basePath/$relativePath"
                                }
                            }
                        }
                    }
                }
            }
            
            return null
        } catch (e: Exception) {
            Log.e("DownloadWorker", "Error getting real path from SAF URI", e)
            return null
        }
    }

    private fun createForegroundInfo(notificationId: Int, title: String, progress: Int, indeterminate: Boolean): ForegroundInfo {
        val intent = Intent(applicationContext, com.e621.client.ui.post.PostActivity::class.java) // Or open file
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(if (indeterminate) "Downloading..." else "$progress%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, progress, indeterminate)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }
    
    private fun showSuccessNotification(notificationId: Int, postId: Int, fileUri: Uri?, mimeType: String) {
        val title = applicationContext.getString(R.string.download_complete_title)
        val message = applicationContext.getString(R.string.download_complete_post, postId)
        
        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setOngoing(false)
            .setProgress(0, 0, false)
            .setAutoCancel(true)
            
        if (fileUri != null) {
            val openIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(fileUri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            val pendingIntent = PendingIntent.getActivity(
                applicationContext,
                postId,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setContentIntent(pendingIntent)
        }
            
        notificationManager.notify(notificationId, builder.build())
    }

    private fun showErrorNotification(notificationId: Int, title: String, message: String) {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(notificationId, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }
}

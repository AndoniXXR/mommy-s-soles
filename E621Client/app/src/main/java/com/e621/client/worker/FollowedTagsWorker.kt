package com.e621.client.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.e621.client.E621Application
import com.e621.client.R
import com.e621.client.ui.MainActivity
import com.e621.client.utils.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Background worker that periodically checks for new posts with followed tags
 * and shows notifications when new content is found.
 */
class FollowedTagsWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "followed_tags_worker"
        private const val CHANNEL_ID = "followed_tags_channel"
        private const val TAG = "FollowedTagsWorker"
        private const val USER_AGENT = "E621Client/1.0 (Android; by e621_client)"
    }

    private val userPreferences by lazy { E621Application.instance.userPreferences }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting followed tags check...")
            AppLog.check(applicationContext, "Starting followed tags check")
            
            // Get followed tags
            val followedTags = userPreferences.followedTags
            if (followedTags.isEmpty()) {
                Log.d(TAG, "No followed tags, skipping check")
                AppLog.info(applicationContext, "No followed tags, skipping check")
                return@withContext Result.success()
            }

            AppLog.info(applicationContext, "Checking ${followedTags.size} followed tags")
            
            // Create notification channel
            createNotificationChannel()

            // Check each followed tag for new posts
            for (tag in followedTags) {
                try {
                    checkNewPostsForTag(tag)
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking tag: $tag", e)
                    AppLog.error(applicationContext, "Error checking tag: ${e.message}", tag)
                }
            }

            Log.d(TAG, "Followed tags check completed")
            AppLog.success(applicationContext, "Followed tags check completed")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in doWork", e)
            AppLog.error(applicationContext, "Worker error: ${e.message}")
            Result.retry()
        }
    }

    /**
     * Check for new posts with the specified tag
     */
    private suspend fun checkNewPostsForTag(tag: String) {
        val baseUrl = if (userPreferences.safeMode) "https://e926.net" else "https://e621.net"
        val apiUrl = "$baseUrl/posts.json?tags=${tag.replace(" ", "+")}&limit=10"
        
        Log.d(TAG, "Checking tag: $tag")
        AppLog.check(applicationContext, "Checking tag", tag)
        
        val url = URL(apiUrl)
        val connection = url.openConnection() as HttpURLConnection
        connection.apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", USER_AGENT)
            connectTimeout = 15000
            readTimeout = 15000
        }

        try {
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "API returned error code: $responseCode for tag: $tag")
                AppLog.error(applicationContext, "API error: $responseCode", tag)
                return
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val jsonResponse = JSONObject(response)
            val posts = jsonResponse.getJSONArray("posts")
            
            if (posts.length() == 0) {
                Log.d(TAG, "No posts found for tag: $tag")
                AppLog.info(applicationContext, "No posts found", tag)
                return
            }

            // Get the newest post ID
            val newestPost = posts.getJSONObject(0)
            val newestPostId = newestPost.getInt("id")
            
            // Get last seen post ID for this tag
            val lastSeenIds = userPreferences.getLastSeenPostIds()
            val lastSeenId = lastSeenIds[tag] ?: 0
            
            Log.d(TAG, "Tag: $tag, newest: $newestPostId, lastSeen: $lastSeenId")
            
            if (lastSeenId == 0) {
                // First time checking this tag, just save the current newest
                userPreferences.saveLastSeenPostId(tag, newestPostId)
                Log.d(TAG, "First check for tag: $tag, saved newest ID: $newestPostId")
                AppLog.info(applicationContext, "First check, saved post ID: $newestPostId", tag)
                return
            }
            
            if (newestPostId > lastSeenId) {
                // Count new posts
                var newPostCount = 0
                var thumbnailUrl: String? = null
                
                for (i in 0 until posts.length()) {
                    val post = posts.getJSONObject(i)
                    val postId = post.getInt("id")
                    if (postId > lastSeenId) {
                        newPostCount++
                        // Get thumbnail from first new post
                        if (thumbnailUrl == null && post.has("preview")) {
                            val preview = post.getJSONObject("preview")
                            thumbnailUrl = preview.optString("url", null)
                        }
                    }
                }
                
                if (newPostCount > 0) {
                    Log.d(TAG, "Found $newPostCount new posts for tag: $tag")
                    AppLog.success(applicationContext, "Found $newPostCount new posts", tag)
                    showNotification(tag, newPostCount, thumbnailUrl)
                    AppLog.notification(applicationContext, "Notification sent for $newPostCount new posts", tag)
                    userPreferences.saveLastSeenPostId(tag, newestPostId)
                }
            } else {
                AppLog.info(applicationContext, "No new posts (last: $lastSeenId)", tag)
            }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Create notification channel for Android O and above
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = applicationContext.getString(R.string.notification_channel_followed_tags)
            val descriptionText = applicationContext.getString(R.string.notification_channel_followed_tags_desc)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Show notification for new posts
     */
    private suspend fun showNotification(tag: String, newPostCount: Int, thumbnailUrl: String?) {
        // Check notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "Notification permission not granted")
                return
            }
        }

        // Create intent for when notification is tapped
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("search_tag", tag)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            tag.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build notification text - respect displayTag preference
        val displayTag = userPreferences.followingDisplayTag
        val title = if (displayTag) {
            applicationContext.getString(R.string.notification_new_posts_title, tag)
        } else {
            applicationContext.getString(R.string.notification_new_posts_title_no_tag)
        }
        val text = if (displayTag) {
            if (newPostCount == 1) {
                applicationContext.getString(R.string.notification_new_posts_text_one, tag)
            } else {
                applicationContext.getString(R.string.notification_new_posts_text, newPostCount, tag)
            }
        } else {
            if (newPostCount == 1) {
                applicationContext.getString(R.string.notification_new_posts_text_one_no_tag)
            } else {
                applicationContext.getString(R.string.notification_new_posts_text_no_tag, newPostCount)
            }
        }

        // Build notification
        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setGroup("followed_tags")

        // Try to load thumbnail
        thumbnailUrl?.let { url ->
            try {
                val bitmap = loadThumbnail(url)
                bitmap?.let {
                    builder.setLargeIcon(it)
                    builder.setStyle(
                        NotificationCompat.BigPictureStyle()
                            .bigPicture(it)
                            .bigLargeIcon(null as Bitmap?)
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading thumbnail", e)
            }
        }

        // Show notification
        with(NotificationManagerCompat.from(applicationContext)) {
            try {
                notify(tag.hashCode(), builder.build())
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException showing notification", e)
            }
        }
    }

    /**
     * Load thumbnail bitmap from URL with size limits to prevent memory issues
     * and ensure compatibility with notification system
     */
    private suspend fun loadThumbnail(urlString: String): Bitmap? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
                connectTimeout = 15000
                readTimeout = 15000
                instanceFollowRedirects = true
            }
            
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "Thumbnail request failed with code: $responseCode")
                return@withContext null
            }
            
            // Read the stream into a byte array first to avoid stream issues
            val inputStream = connection.inputStream
            val bytes = inputStream.readBytes()
            inputStream.close()
            
            if (bytes.isEmpty()) {
                Log.w(TAG, "Empty response for thumbnail")
                return@withContext null
            }
            
            // Decode with options to limit size and prevent OOM
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            
            // Calculate sample size to limit bitmap to max 512x512
            val maxSize = 512
            var sampleSize = 1
            while (options.outWidth / sampleSize > maxSize || options.outHeight / sampleSize > maxSize) {
                sampleSize *= 2
            }
            
            // Decode the actual bitmap with the calculated sample size
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565  // Use less memory
            }
            
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
            
            if (bitmap == null) {
                Log.w(TAG, "Failed to decode bitmap from bytes")
                return@withContext null
            }
            
            Log.d(TAG, "Thumbnail loaded: ${bitmap.width}x${bitmap.height}")
            bitmap
            
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OutOfMemoryError loading thumbnail", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error loading thumbnail: ${e.message}", e)
            null
        } finally {
            try {
                connection?.disconnect()
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        }
    }
}

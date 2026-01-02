package com.e621.client.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.media.AudioAttributes
import android.net.Uri
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.e621.client.E621Application
import com.e621.client.R
import com.e621.client.ui.MainActivity
import com.e621.client.utils.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Foreground service for "Instant" monitoring of followed tags.
 * Checks for new posts every 30 seconds.
 */
class TagMonitoringService : Service() {

    companion object {
        private const val TAG = "TagMonitoringService"
        private const val NOTIFICATION_CHANNEL_ID = "tag_monitoring_service_channel_v2" // Changed ID to reset settings
        private const val NOTIFICATION_ID = 1001
        private const val CHECK_INTERVAL_MS = 30_000L // 30 seconds
        private const val USER_AGENT = "E621Client/1.0 (Android; by e621_client)"
        private const val NOTIFICATION_CHANNEL_ALERTS_ID = "followed_tags_channel_v2" // Changed ID to reset settings

        fun start(context: Context) {
            val intent = Intent(context, TagMonitoringService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, TagMonitoringService::class.java)
            context.stopService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var monitoringJob: Job? = null
    private val userPreferences by lazy { E621Application.instance.userPreferences }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createForegroundNotification())
        startMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        // Ensure we are in foreground
        startForeground(NOTIFICATION_ID, createForegroundNotification())
        
        if (monitoringJob == null || !monitoringJob!!.isActive) {
            startMonitoring()
        }
        
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        monitoringJob?.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Service Channel (Hidden as much as possible)
            val name = "Tag Monitoring Service"
            val descriptionText = "Background monitoring service"
            val importance = NotificationManager.IMPORTANCE_MIN // Min importance to hide icon
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            
            // Alerts Channel (With Custom Sound)
            val alertsName = getString(R.string.notification_channel_followed_tags)
            val alertsDesc = getString(R.string.notification_channel_followed_tags_desc)
            val alertsImportance = NotificationManager.IMPORTANCE_HIGH // High importance for heads-up
            
            val soundUri = Uri.parse("android.resource://" + packageName + "/" + R.raw.notification)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()

            val alertsChannel = NotificationChannel(NOTIFICATION_CHANNEL_ALERTS_ID, alertsName, alertsImportance).apply {
                description = alertsDesc
                setSound(soundUri, audioAttributes)
                enableVibration(true)
                enableLights(true)
            }
            notificationManager.createNotificationChannel(alertsChannel)
        }
    }

    private fun createForegroundNotification(): Notification {
        val pendingIntent: PendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
        }

        // Minimal notification to satisfy Foreground Service requirement but annoy user less
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("E621 Client")
            .setContentText("Monitoring active")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MIN) // Min priority
            .setSilent(true)
            .setOngoing(true)
            .build()
    }

    private fun startMonitoring() {
        monitoringJob = serviceScope.launch {
            while (isActive) {
                try {
                    checkTags()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in monitoring loop", e)
                }
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    private suspend fun checkTags() {
        // Check network constraints
        if (userPreferences.followingOnlyWifi) {
            if (!isWifiConnected()) {
                Log.d(TAG, "Skipping check: WiFi required but not connected")
                return
            }
        }

        val followedTags = userPreferences.followedTags
        if (followedTags.isEmpty()) return

        Log.d(TAG, "Checking ${followedTags.size} tags...")
        AppLog.check(applicationContext, "Instant check: ${followedTags.size} tags")

        for (tag in followedTags) {
            if (!kotlin.coroutines.coroutineContext.isActive) break
            try {
                checkNewPostsForTag(tag)
            } catch (e: Exception) {
                Log.e(TAG, "Error checking tag: $tag", e)
            }
        }
    }

    private fun isWifiConnected(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            @Suppress("DEPRECATION")
            return networkInfo != null && networkInfo.type == android.net.ConnectivityManager.TYPE_WIFI
        }
    }

    // Logic duplicated from FollowedTagsWorker to ensure independence
    private suspend fun checkNewPostsForTag(tag: String) {
        val baseUrl = if (userPreferences.safeMode) "https://e926.net" else "https://e621.net"
        val apiUrl = "$baseUrl/posts.json?tags=${tag.replace(" ", "+")}&limit=10"
        
        val url = URL(apiUrl)
        val connection = url.openConnection() as HttpURLConnection
        connection.apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", USER_AGENT)
            connectTimeout = 15000
            readTimeout = 15000
        }

        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val jsonResponse = JSONObject(response)
            val posts = jsonResponse.getJSONArray("posts")
            
            if (posts.length() == 0) return

            val newestPost = posts.getJSONObject(0)
            val newestPostId = newestPost.getInt("id")
            
            val lastSeenIds = userPreferences.getLastSeenPostIds()
            val lastSeenId = lastSeenIds[tag] ?: 0
            
            if (lastSeenId == 0) {
                userPreferences.saveLastSeenPostId(tag, newestPostId)
                return
            }
            
            if (newestPostId > lastSeenId) {
                var newPostCount = 0
                var thumbnailUrl: String? = null
                
                for (i in 0 until posts.length()) {
                    val post = posts.getJSONObject(i)
                    val postId = post.getInt("id")
                    if (postId > lastSeenId) {
                        newPostCount++
                        if (thumbnailUrl == null && post.has("preview")) {
                            val preview = post.getJSONObject("preview")
                            thumbnailUrl = preview.optString("url", null)
                        }
                    }
                }
                
                if (newPostCount > 0) {
                    showNotification(tag, newPostCount, thumbnailUrl)
                    userPreferences.saveLastSeenPostId(tag, newestPostId)
                    AppLog.success(applicationContext, "Instant: Found $newPostCount new posts", tag)
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun showNotification(tag: String, newPostCount: Int, thumbnailUrl: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("search_tag", tag)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            tag.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val displayTag = userPreferences.followingDisplayTag
        val title = if (displayTag) {
            getString(R.string.notification_new_posts_title, tag)
        } else {
            getString(R.string.notification_new_posts_title_no_tag)
        }
        val text = if (displayTag) {
            if (newPostCount == 1) {
                getString(R.string.notification_new_posts_text_one, tag)
            } else {
                getString(R.string.notification_new_posts_text, newPostCount, tag)
            }
        } else {
            if (newPostCount == 1) {
                getString(R.string.notification_new_posts_text_one_no_tag)
            } else {
                getString(R.string.notification_new_posts_text_no_tag, newPostCount)
            }
        }

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ALERTS_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // High priority for heads-up
            .setSound(Uri.parse("android.resource://" + packageName + "/" + R.raw.notification)) // Explicit sound
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setGroup("followed_tags")

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

        with(NotificationManagerCompat.from(this)) {
            try {
                notify(tag.hashCode(), builder.build())
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException showing notification", e)
            }
        }
    }

    private suspend fun loadThumbnail(urlString: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
                connectTimeout = 10000
                readTimeout = 10000
            }
            
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream = connection.inputStream
                BitmapFactory.decodeStream(inputStream)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}

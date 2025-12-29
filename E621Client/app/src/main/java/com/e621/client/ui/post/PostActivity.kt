package com.e621.client.ui.post

import android.Manifest
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.e621.client.E621Application
import com.e621.client.R
import com.e621.client.data.model.Post
import com.e621.client.ui.comments.CommentsActivity
import com.e621.client.ui.pools.PoolViewActivity
import com.e621.client.ui.profile.ProfileActivity
import com.e621.client.ui.wiki.WikiActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.e621.client.data.api.CloudFlareException
import com.e621.client.data.api.ServerDownException
import com.e621.client.data.api.NetworkException
import com.e621.client.data.api.NetworkErrorType
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Post Detail Activity with ViewPager2 for swiping between posts
 * Based on decompiled PostActivity structure from original app
 */
class PostActivity : AppCompatActivity(), PostViewPagerAdapter.PostInteractionListener {

    private lateinit var viewPager: ViewPager2
    private lateinit var adapter: PostViewPagerAdapter
    
    // Bottom action buttons
    private lateinit var btnUpvote: ImageButton
    private lateinit var btnDownvote: ImageButton
    private lateinit var btnFavorite: ImageButton
    private lateinit var btnComments: ImageButton
    private lateinit var btnDownload: ImageButton
    private lateinit var btnMore: ImageButton
    private lateinit var txtCommentCount: TextView
    
    // Navigation buttons
    private lateinit var btnBackLeft: ImageView
    private lateinit var btnBackRight: ImageView
    private lateinit var imgRandomPost: ImageView
    private lateinit var layoutButtons: LinearLayout
    
    // Tap zones for navigation
    private lateinit var tapZoneLeft1: View
    private lateinit var tapZoneLeft2: View
    private lateinit var tapZoneRight1: View
    private lateinit var tapZoneRight2: View
    
    private val posts = mutableListOf<Post>()
    private var startPosition = 0
    private var isRandom = false
    private var hideNavBar = false
    private var hideStatusBar = false
    private var uiHidden = false
    private var pendingFollowTag: String? = null
    
    // Download state management
    private var isDownloading = false
    private var currentDownloadId: Long = -1
    private val activeDownloads = mutableMapOf<Long, Int>() // downloadId -> postId
    private val completedDownloads = mutableSetOf<Int>() // Track completed postIds to prevent progress updates overwriting complete notification
    private var downloadReceiver: BroadcastReceiver? = null
    private val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
    private val mainHandler = Handler(Looper.getMainLooper())
    
    companion object {
        private const val DOWNLOAD_CHANNEL_ID = "download_channel"
        private const val DOWNLOAD_NOTIFICATION_ID = 9001
        private const val REQUEST_EDIT_POST = 3
        var POSTS_TO_SHOW: List<Post>? = null
        const val EXTRA_POSITION = "position"
        const val EXTRA_RANDOM = "random"
        const val EXTRA_POST_ID = "post_id"
    }
    
    private val prefs by lazy { E621Application.instance.userPreferences }
    private val api by lazy { E621Application.instance.api }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_post)
        
        // Get posts from companion object or intent
        val passedPosts = POSTS_TO_SHOW
        POSTS_TO_SHOW = null
        
        if (passedPosts != null) {
            posts.addAll(passedPosts)
        }
        
        startPosition = intent.getIntExtra(EXTRA_POSITION, 0)
        isRandom = intent.getBooleanExtra(EXTRA_RANDOM, false)
        
        // If single post by ID
        val postId = intent.getIntExtra(EXTRA_POST_ID, -1)
        if (postId > 0 && posts.isEmpty()) {
            loadSinglePost(postId)
            return
        }
        
        if (posts.isEmpty()) {
            finish()
            return
        }
        
        // Load preferences
        hideNavBar = prefs.postHideNavBar
        hideStatusBar = prefs.postHideStatusBar
        
        if (prefs.postKeepScreenAwake) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        
        setupViews()
        setupViewPager()
        setupButtons()
        setupTapZones()
        updateSystemUI()
    }
    
    private fun setupViews() {
        viewPager = findViewById(R.id.viewPager)
        
        // Bottom buttons
        btnUpvote = findViewById(R.id.btnUpvote)
        btnDownvote = findViewById(R.id.btnDownvote)
        btnFavorite = findViewById(R.id.btnFavorite)
        btnComments = findViewById(R.id.btnComments)
        btnDownload = findViewById(R.id.btnDownload)
        btnMore = findViewById(R.id.btnMore)
        txtCommentCount = findViewById(R.id.txtCommentCount)
        layoutButtons = findViewById(R.id.layoutButtons)
        
        // Navigation
        btnBackLeft = findViewById(R.id.btnBackLeft)
        btnBackRight = findViewById(R.id.btnBackRight)
        imgRandomPost = findViewById(R.id.imgRandomPost)
        
        // Tap zones
        tapZoneLeft1 = findViewById(R.id.tapZoneLeft1)
        tapZoneLeft2 = findViewById(R.id.tapZoneLeft2)
        tapZoneRight1 = findViewById(R.id.tapZoneRight1)
        tapZoneRight2 = findViewById(R.id.tapZoneRight2)
        
        // Back button visibility based on preferences
        if (prefs.postBackButton) {
            val location = prefs.postBackButtonLocation
            if (location == "0" || location == "2") { // 0=top-left, 2=bottom-left
                btnBackLeft.visibility = View.VISIBLE
                btnBackRight.visibility = View.GONE
            } else { // 1=top-right, 3=bottom-right
                btnBackLeft.visibility = View.GONE
                btnBackRight.visibility = View.VISIBLE
            }
        }
        
        btnBackLeft.setOnClickListener { finish() }
        btnBackRight.setOnClickListener { finish() }
        
        // Random post button
        if (isRandom) {
            imgRandomPost.visibility = View.VISIBLE
            imgRandomPost.setOnClickListener { loadRandomPost() }
        }
        
        // Apply WindowInsets to layoutButtons for navigation bar
        ViewCompat.setOnApplyWindowInsetsListener(layoutButtons) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, insets.bottom)
            windowInsets
        }
    }
    
    private fun setupViewPager() {
        adapter = PostViewPagerAdapter(posts, this)
        viewPager.adapter = adapter
        viewPager.orientation = ViewPager2.ORIENTATION_HORIZONTAL
        viewPager.offscreenPageLimit = 1
        
        // Set initial visible position before setting current item
        adapter.setCurrentVisiblePosition(startPosition)
        
        if (startPosition in posts.indices) {
            viewPager.setCurrentItem(startPosition, false)
        }
        
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                // Update visible position and manage video playback
                adapter.setCurrentVisiblePosition(position)
                adapter.pauseAllPlayers()
                adapter.resumePlayer(position)
                updateButtonStates(position)
            }
        })
        
        updateButtonStates(startPosition)
    }
    
    private fun setupButtons() {
        btnUpvote.setOnClickListener { vote(1) }
        btnDownvote.setOnClickListener { vote(-1) }
        btnFavorite.setOnClickListener { toggleFavorite() }
        btnDownload.setOnClickListener { downloadPost() }
        btnMore.setOnClickListener { showMoreMenu(it) }
        
        // Hide comments button if preference enabled
        if (prefs.postHideComments) {
            btnComments.visibility = View.GONE
            txtCommentCount.visibility = View.GONE
        } else {
            btnComments.setOnClickListener { openComments() }
        }
    }
    
    private fun setupTapZones() {
        // Only enable edge navigation if preference is enabled
        if (!prefs.postEdgeNavigation) {
            tapZoneLeft1.visibility = View.GONE
            tapZoneLeft2.visibility = View.GONE
            tapZoneRight1.visibility = View.GONE
            tapZoneRight2.visibility = View.GONE
            return
        }
        
        val goToPrevious = View.OnClickListener {
            val current = viewPager.currentItem
            if (current > 0) {
                viewPager.setCurrentItem(current - 1, true)
            } else {
                finish()
            }
        }
        
        val goToNext = View.OnClickListener {
            val current = viewPager.currentItem
            if (current < posts.size - 1) {
                viewPager.setCurrentItem(current + 1, true)
            }
        }
        
        tapZoneLeft1.setOnClickListener(goToPrevious)
        tapZoneLeft2.setOnClickListener(goToPrevious)
        tapZoneRight1.setOnClickListener(goToNext)
        tapZoneRight2.setOnClickListener(goToNext)
    }
    
    private fun updateButtonStates(position: Int) {
        val post = posts.getOrNull(position) ?: return
        
        // Update vote buttons based on user's vote state
        btnUpvote.setImageResource(
            if (post.userVote > 0) R.drawable.ic_arrow_up_filled
            else R.drawable.ic_arrow_up
        )
        btnUpvote.setColorFilter(
            if (post.userVote > 0) getColor(R.color.score_positive)
            else getColor(R.color.text_secondary)
        )
        
        btnDownvote.setImageResource(
            if (post.userVote < 0) R.drawable.ic_arrow_down_filled
            else R.drawable.ic_arrow_down
        )
        btnDownvote.setColorFilter(
            if (post.userVote < 0) getColor(R.color.score_negative)
            else getColor(R.color.text_secondary)
        )
        
        // Update favorite button
        btnFavorite.setImageResource(
            if (post.isFavorited == true) R.drawable.ic_favorite_filled
            else R.drawable.ic_favorite_outline
        )
        btnFavorite.setColorFilter(
            if (post.isFavorited == true) getColor(R.color.favorite_color)
            else getColor(R.color.text_secondary)
        )
        
        // Update comment count
        val commentCount = post.commentCount
        if (commentCount > 0) {
            txtCommentCount.visibility = View.VISIBLE
            txtCommentCount.text = if (commentCount > 99) "99+" else commentCount.toString()
        } else {
            txtCommentCount.visibility = View.GONE
        }
    }
    
    private fun loadSinglePost(postId: Int) {
        lifecycleScope.launch {
            try {
                val response = api.posts.get(postId)
                if (response.isSuccessful) {
                    response.body()?.post?.let { post ->
                        posts.add(post)
                        setupViews()
                        setupViewPager()
                        setupButtons()
                        setupTapZones()
                        updateSystemUI()
                    }
                } else {
                    Toast.makeText(this@PostActivity, R.string.error_loading, Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: CloudFlareException) {
                Toast.makeText(this@PostActivity, R.string.error_cloudflare, Toast.LENGTH_LONG).show()
                finish()
            } catch (e: ServerDownException) {
                Toast.makeText(this@PostActivity, R.string.error_server_down, Toast.LENGTH_LONG).show()
                finish()
            } catch (e: NetworkException) {
                val message = when (e.type) {
                    NetworkErrorType.TIMEOUT -> getString(R.string.error_timeout)
                    NetworkErrorType.NO_INTERNET -> getString(R.string.error_no_internet)
                    else -> getString(R.string.error_connection)
                }
                Toast.makeText(this@PostActivity, message, Toast.LENGTH_LONG).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@PostActivity, e.message, Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
    
    private fun loadRandomPost() {
        lifecycleScope.launch {
            try {
                val response = api.posts.list(tags = "order:random", page = 1, limit = 1)
                if (response.isSuccessful) {
                    response.body()?.posts?.firstOrNull()?.let { post ->
                        posts.add(post)
                        adapter.notifyItemInserted(posts.size - 1)
                        viewPager.setCurrentItem(posts.size - 1, true)
                    }
                }
            } catch (e: CloudFlareException) {
                Toast.makeText(this@PostActivity, R.string.error_cloudflare, Toast.LENGTH_LONG).show()
            } catch (e: ServerDownException) {
                Toast.makeText(this@PostActivity, R.string.error_server_down, Toast.LENGTH_LONG).show()
            } catch (e: NetworkException) {
                val message = when (e.type) {
                    NetworkErrorType.TIMEOUT -> getString(R.string.error_timeout)
                    NetworkErrorType.NO_INTERNET -> getString(R.string.error_no_internet)
                    else -> getString(R.string.error_connection)
                }
                Toast.makeText(this@PostActivity, message, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this@PostActivity, e.message, Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun vote(score: Int) {
        if (!prefs.isLoggedIn) {
            Toast.makeText(this, R.string.profile_not_logged_in, Toast.LENGTH_SHORT).show()
            return
        }
        
        val post = getCurrentPost() ?: return
        
        // Determine if we're toggling off or changing vote
        val currentVote = post.userVote
        val newVote = if (currentVote == score) 0 else score
        val noUnvote = newVote != 0
        
        // Disable buttons during operation
        btnUpvote.isEnabled = false
        btnDownvote.isEnabled = false
        
        lifecycleScope.launch {
            try {
                val response = api.posts.vote(post.id, if (newVote == 0) score else newVote)
                if (response.isSuccessful) {
                    val voteResponse = response.body()
                    
                    // Update local state
                    if (newVote == 0) {
                        // Remove vote
                        post.userVote = 0
                        val messageRes = if (score > 0) R.string.vote_removed_up else R.string.vote_removed_down
                        Toast.makeText(this@PostActivity, messageRes, Toast.LENGTH_SHORT).show()
                    } else {
                        // Apply vote
                        post.userVote = newVote
                        val messageRes = if (newVote > 0) R.string.voted_up else R.string.voted_down
                        Toast.makeText(this@PostActivity, messageRes, Toast.LENGTH_SHORT).show()
                    }
                    
                    // Update score from response
                    voteResponse?.let {
                        it.up?.let { up -> post.score.up = up }
                        it.down?.let { down -> post.score.down = down }
                        it.score?.let { score -> post.score.total = score }
                    }
                    
                    updateButtonStates(viewPager.currentItem)
                } else {
                    val errorMsg = when (response.code()) {
                        422 -> getString(R.string.error_vote_own_post)
                        403 -> getString(R.string.error_forbidden)
                        else -> getString(R.string.error_voting)
                    }
                    Toast.makeText(this@PostActivity, errorMsg, Toast.LENGTH_SHORT).show()
                }
            } catch (e: CloudFlareException) {
                Toast.makeText(this@PostActivity, R.string.error_cloudflare, Toast.LENGTH_LONG).show()
            } catch (e: ServerDownException) {
                Toast.makeText(this@PostActivity, R.string.error_server_down, Toast.LENGTH_LONG).show()
            } catch (e: NetworkException) {
                val message = when (e.type) {
                    NetworkErrorType.TIMEOUT -> getString(R.string.error_timeout)
                    NetworkErrorType.NO_INTERNET -> getString(R.string.error_no_internet)
                    else -> getString(R.string.error_connection)
                }
                Toast.makeText(this@PostActivity, message, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this@PostActivity, e.message, Toast.LENGTH_SHORT).show()
            } finally {
                btnUpvote.isEnabled = true
                btnDownvote.isEnabled = true
            }
        }
    }
    
    private fun toggleFavorite() {
        if (!prefs.isLoggedIn) {
            Toast.makeText(this, R.string.profile_not_logged_in, Toast.LENGTH_SHORT).show()
            return
        }
        
        val post = getCurrentPost() ?: return
        
        lifecycleScope.launch {
            try {
                if (post.isFavorited == true) {
                    api.posts.unfavorite(post.id)
                    post.isFavorited = false
                    Toast.makeText(this@PostActivity, R.string.post_unfavorited, Toast.LENGTH_SHORT).show()
                } else {
                    api.posts.favorite(post.id)
                    post.isFavorited = true
                    Toast.makeText(this@PostActivity, R.string.post_favorited, Toast.LENGTH_SHORT).show()
                    
                    // Auto-upvote on favorite if preference enabled
                    if (prefs.postActionUpvoteOnFav && post.userVote <= 0) {
                        try {
                            api.posts.vote(post.id, 1)
                            post.userVote = 1
                        } catch (e: Exception) {
                            // Silently fail auto-upvote
                        }
                    }
                }
                updateButtonStates(viewPager.currentItem)
            } catch (e: CloudFlareException) {
                Toast.makeText(this@PostActivity, R.string.error_cloudflare, Toast.LENGTH_LONG).show()
            } catch (e: ServerDownException) {
                Toast.makeText(this@PostActivity, R.string.error_server_down, Toast.LENGTH_LONG).show()
            } catch (e: NetworkException) {
                val message = when (e.type) {
                    NetworkErrorType.TIMEOUT -> getString(R.string.error_timeout)
                    NetworkErrorType.NO_INTERNET -> getString(R.string.error_no_internet)
                    else -> getString(R.string.error_connection)
                }
                Toast.makeText(this@PostActivity, message, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this@PostActivity, e.message, Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun openComments() {
        val post = getCurrentPost() ?: return
        val intent = Intent(this, CommentsActivity::class.java)
        intent.putExtra("post_id", post.id)
        startActivity(intent)
    }
    
    private fun downloadPost() {
        val post = getCurrentPost() ?: return
        val url = post.file.url
        
        if (url.isNullOrEmpty()) {
            Toast.makeText(this, R.string.download_no_url, Toast.LENGTH_SHORT).show()
            return
        }
        
        // Prevent multiple clicks while downloading
        if (isDownloading) {
            Toast.makeText(this, R.string.download_in_progress, Toast.LENGTH_SHORT).show()
            return
        }
        
        // Check notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        // Check storage permission for Android < 10
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                return
            }
        }
        
        performDownload(post)
    }
    
    private fun performDownload(post: Post) {
        val url = post.file.url ?: return
        val fileName = generateFileName(post)
        
        Log.d("E621Download", "Starting download: $url")
        Log.d("E621Download", "Filename: $fileName")
        
        // Mark as downloading immediately
        isDownloading = true
        btnDownload.isEnabled = false
        btnDownload.alpha = 0.5f
        
        // Show immediate feedback
        Toast.makeText(this, getString(R.string.downloading_post, post.id), Toast.LENGTH_SHORT).show()
        
        // Auto-actions on download if preferences enabled
        if (prefs.isLoggedIn) {
            lifecycleScope.launch {
                // Auto-upvote on download
                if (prefs.postActionUpvoteOnDownload && post.userVote <= 0) {
                    try {
                        api.posts.vote(post.id, 1)
                        post.userVote = 1
                        updateButtonStates(viewPager.currentItem)
                    } catch (e: Exception) {
                        // Silently fail
                    }
                }
                
                // Auto-favorite on download
                if (prefs.postActionFavOnDownload && post.isFavorited != true) {
                    try {
                        api.posts.favorite(post.id)
                        post.isFavorited = true
                        updateButtonStates(viewPager.currentItem)
                    } catch (e: Exception) {
                        // Silently fail
                    }
                }
            }
        }
        
        // Create notification channel and show immediate notification
        createDownloadNotificationChannel()
        showDownloadStartNotification(post.id, fileName)
        
        // Register receiver if not already registered
        registerDownloadReceiver()
        
        // Use coroutine for immediate download - more reliable than DownloadManager for e621
        performDirectDownload(url, fileName, post)
    }
    
    private fun createDownloadNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                DOWNLOAD_CHANNEL_ID,
                getString(R.string.download_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.download_channel_description)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Generate file name based on storage preferences mask
     * Available placeholders: %artist%, %id%, %character%, %tags%, %score%, %favs%, %rating%, %timesaved%, %yyyy%, %mm%, %dd%
     */
    private fun generateFileName(post: Post): String {
        val mask = prefs.storageFileNameMask
        val extension = post.file.ext
        
        // Current date for %timesaved%, %yyyy%, %mm%, %dd%
        val calendar = java.util.Calendar.getInstance()
        val year = calendar.get(java.util.Calendar.YEAR).toString()
        val month = String.format("%02d", calendar.get(java.util.Calendar.MONTH) + 1)
        val day = String.format("%02d", calendar.get(java.util.Calendar.DAY_OF_MONTH))
        val timeSaved = "${year}${month}${day}"
        
        // Get first artist from tags
        val artist = post.tags.artist?.firstOrNull()?.replace(" ", "_") ?: "unknown"
        
        // Get first character from tags
        val character = post.tags.character?.firstOrNull()?.replace(" ", "_") ?: "unknown"
        
        // Get general tags (limited to first 5 to avoid too long names)
        val tags = post.tags.general?.take(5)?.joinToString("_") ?: ""
        
        // Rating (s=safe, q=questionable, e=explicit)
        val rating = post.rating
        
        // Replace placeholders
        var fileName = mask
            .replace("%artist%", sanitizeFileName(artist))
            .replace("%id%", post.id.toString())
            .replace("%character%", sanitizeFileName(character))
            .replace("%tags%", sanitizeFileName(tags))
            .replace("%score%", post.score.total.toString())
            .replace("%favs%", post.favCount.toString())
            .replace("%rating%", rating)
            .replace("%timesaved%", timeSaved)
            .replace("%yyyy%", year)
            .replace("%mm%", month)
            .replace("%dd%", day)
        
        // Apply hide prefix if enabled (add . to hide from gallery)
        if (prefs.storageHide) {
            // Check if there's a subfolder in the name
            val lastSlash = fileName.lastIndexOf('/')
            if (lastSlash >= 0) {
                // Add dot before the actual filename, after the last subfolder
                fileName = fileName.substring(0, lastSlash + 1) + "." + fileName.substring(lastSlash + 1)
            } else {
                fileName = ".$fileName"
            }
        }
        
        // Add extension
        return "$fileName.$extension"
    }
    
    /**
     * Sanitize file name by removing/replacing invalid characters
     */
    private fun sanitizeFileName(name: String): String {
        return name
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("_{2,}"), "_")
            .take(50) // Limit length
    }
    
    private fun showDownloadStartNotification(postId: Int, fileName: String) {
        val notification = NotificationCompat.Builder(this, DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(getString(R.string.downloading_post, postId))
            .setContentText(getString(R.string.download_starting))
            .setProgress(100, 0, true) // Indeterminate progress
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
        
        notificationManager.notify(DOWNLOAD_NOTIFICATION_ID + postId, notification)
    }
    
    private fun showDownloadCompleteNotification(postId: Int, fileName: String, success: Boolean, fileExtension: String? = null) {
        // Mark download as complete BEFORE showing notification to prevent race condition
        completedDownloads.add(postId)
        
        val builder = NotificationCompat.Builder(this, DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(if (success) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setContentTitle(
                if (success) getString(R.string.download_complete_title)
                else getString(R.string.download_failed_title)
            )
            .setContentText(
                if (success) getString(R.string.download_complete_post, postId)
                else getString(R.string.download_failed_post, postId)
            )
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        
        // Add click action to open the file in gallery/viewer (only for public storage)
        if (success && (!prefs.storageCustomFolderEnabled || prefs.storageCustomFolder == null)) {
            try {
                val file = getOutputFileForPublicStorage(fileName)
                
                if (file.exists()) {
                    // Get URI using FileProvider for Android 7.0+
                    val fileUri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        FileProvider.getUriForFile(
                            this,
                            "${packageName}.fileprovider",
                            file
                        )
                    } else {
                        Uri.fromFile(file)
                    }
                    
                    // Determine MIME type
                    val mimeType = when (fileExtension?.lowercase() ?: fileName.substringAfterLast(".", "").lowercase()) {
                        "jpg", "jpeg" -> "image/jpeg"
                        "png" -> "image/png"
                        "gif" -> "image/gif"
                        "webp" -> "image/webp"
                        "webm" -> "video/webm"
                        "mp4" -> "video/mp4"
                        else -> "image/*"
                    }
                    
                    // Create intent to open file
                    val openIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(fileUri, mimeType)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    
                    val pendingIntent = PendingIntent.getActivity(
                        this,
                        postId,
                        openIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    
                    builder.setContentIntent(pendingIntent)
                    Log.d("E621Download", "Added pending intent to open: $fileUri with type: $mimeType")
                }
            } catch (e: Exception) {
                Log.e("E621Download", "Error creating pending intent: ${e.message}", e)
            }
        }
        
        notificationManager.notify(DOWNLOAD_NOTIFICATION_ID + postId, builder.build())
    }
    
    private fun registerDownloadReceiver() {
        if (downloadReceiver != null) return
        
        downloadReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val downloadId = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) ?: return
                val postId = activeDownloads[downloadId] ?: return
                
                Log.d("E621Download", "Download complete broadcast received for ID: $downloadId, post: $postId")
                
                val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager.query(query)
                
                if (cursor.moveToFirst()) {
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val status = cursor.getInt(statusIndex)
                    
                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            Log.d("E621Download", "Download successful")
                            showDownloadCompleteNotification(postId, "$postId", true)
                            Toast.makeText(context, R.string.download_complete, Toast.LENGTH_SHORT).show()
                        }
                        DownloadManager.STATUS_FAILED -> {
                            val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                            val reason = cursor.getInt(reasonIndex)
                            Log.e("E621Download", "Download failed with reason: $reason")
                            showDownloadCompleteNotification(postId, "$postId", false)
                        }
                    }
                }
                cursor.close()
                
                activeDownloads.remove(downloadId)
                resetDownloadState()
            }
        }
        
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(downloadReceiver, filter)
        }
    }
    
    private fun resetDownloadState() {
        isDownloading = false
        currentDownloadId = -1
        // Clean up completed downloads set after a delay to allow notification to be shown
        mainHandler.postDelayed({
            completedDownloads.clear()
        }, 1000)
        mainHandler.post {
            btnDownload.isEnabled = true
            btnDownload.alpha = 1.0f
        }
    }
    
    private fun performDirectDownload(urlString: String, fileName: String, post: Post) {
        val fileExtension = post.file.ext
        lifecycleScope.launch {
            var downloadSuccess = false
            var lastException: Exception? = null
            
            // Retry logic - try up to 3 times
            for (attempt in 1..3) {
                try {
                    Log.d("E621Download", "Download attempt $attempt/3 for post ${post.id}")
                    
                    val result = withContext(Dispatchers.IO) {
                        downloadFileRobust(urlString, fileName, post.id)
                    }
                    
                    if (result) {
                        downloadSuccess = true
                        break
                    } else {
                        Log.w("E621Download", "Attempt $attempt failed, retrying...")
                        // Wait before retry (exponential backoff)
                        if (attempt < 3) {
                            kotlinx.coroutines.delay(1000L * attempt)
                        }
                    }
                } catch (e: Exception) {
                    lastException = e
                    Log.e("E621Download", "Attempt $attempt exception: ${e.message}")
                    if (attempt < 3) {
                        kotlinx.coroutines.delay(1000L * attempt)
                    }
                }
            }
            
            if (downloadSuccess) {
                Log.d("E621Download", "Download successful")
                showDownloadCompleteNotification(post.id, fileName, true, fileExtension)
                Toast.makeText(this@PostActivity, R.string.download_complete, Toast.LENGTH_SHORT).show()
                
                // Notify media scanner for non-SAF downloads
                if (!prefs.storageCustomFolderEnabled || prefs.storageCustomFolder == null) {
                    try {
                        val outputFile = getOutputFileForPublicStorage(fileName)
                        if (outputFile.exists()) {
                            val uri = Uri.fromFile(outputFile)
                            sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri))
                        }
                    } catch (e: Exception) {
                        Log.e("E621Download", "Media scanner error: ${e.message}")
                    }
                }
            } else {
                Log.e("E621Download", "All download attempts failed")
                showDownloadCompleteNotification(post.id, fileName, false, fileExtension)
                val errorMsg = lastException?.message ?: getString(R.string.download_failed)
                Toast.makeText(this@PostActivity, errorMsg, Toast.LENGTH_LONG).show()
            }
            
            resetDownloadState()
        }
    }
    
    /**
     * Robust download function with proper SAF support
     */
    private fun downloadFileRobust(urlString: String, fileName: String, postId: Int): Boolean {
        return try {
            // Create connection with timeout and retries
            val url = URL(urlString)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 30000  // 30 seconds
                readTimeout = 60000     // 60 seconds for large files
                instanceFollowRedirects = true
                useCaches = false
                setRequestProperty("User-Agent", "E621Client/1.0 (by e621_client on e621)")
                setRequestProperty("Accept", "*/*")
                setRequestProperty("Connection", "keep-alive")
                
                if (prefs.isLoggedIn) {
                    val credentials = "${prefs.username}:${prefs.apiKey}"
                    val basicAuth = "Basic " + android.util.Base64.encodeToString(
                        credentials.toByteArray(), android.util.Base64.NO_WRAP
                    )
                    setRequestProperty("Authorization", basicAuth)
                }
            }
            
            connection.connect()
            val responseCode = connection.responseCode
            Log.d("E621Download", "HTTP response: $responseCode")
            
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e("E621Download", "HTTP error: $responseCode - ${connection.responseMessage}")
                connection.disconnect()
                return false
            }
            
            val totalSize = connection.contentLengthLong
            Log.d("E621Download", "File size: $totalSize bytes")
            
            // Determine if using SAF or public storage
            val useCustomFolder = prefs.storageCustomFolderEnabled && prefs.storageCustomFolder != null
            
            val downloadResult = if (useCustomFolder) {
                downloadToCustomFolder(connection.inputStream, fileName, postId, totalSize)
            } else {
                downloadToPublicStorage(connection.inputStream, fileName, postId, totalSize)
            }
            
            connection.disconnect()
            downloadResult
            
        } catch (e: java.net.SocketTimeoutException) {
            Log.e("E621Download", "Connection timeout: ${e.message}")
            false
        } catch (e: java.net.UnknownHostException) {
            Log.e("E621Download", "Network error: ${e.message}")
            false
        } catch (e: java.io.IOException) {
            Log.e("E621Download", "IO error: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e("E621Download", "Download exception: ${e.message}", e)
            false
        }
    }
    
    /**
     * Download to custom folder using SAF (Storage Access Framework)
     */
    private fun downloadToCustomFolder(
        inputStream: java.io.InputStream,
        fileName: String,
        postId: Int,
        totalSize: Long
    ): Boolean {
        return try {
            val customUri = android.net.Uri.parse(prefs.storageCustomFolder)
            var targetDir = androidx.documentfile.provider.DocumentFile.fromTreeUri(this, customUri)
            
            if (targetDir == null || !targetDir.canWrite()) {
                Log.e("E621Download", "Cannot write to custom folder, falling back to public storage")
                // Fallback to public storage
                return downloadToPublicStorage(inputStream, fileName, postId, totalSize)
            }
            
            // Handle subfolders in filename
            val parts = fileName.split("/")
            val actualFileName = parts.last()
            
            if (parts.size > 1) {
                // Create subfolders
                for (i in 0 until parts.size - 1) {
                    val folderName = parts[i]
                    val existingDir = targetDir?.findFile(folderName)
                    targetDir = if (existingDir != null && existingDir.isDirectory) {
                        existingDir
                    } else {
                        targetDir?.createDirectory(folderName) ?: run {
                            Log.e("E621Download", "Failed to create subfolder: $folderName")
                            return downloadToPublicStorage(inputStream, fileName, postId, totalSize)
                        }
                    }
                }
            }
            
            // Check for existing file
            val existingFile = targetDir?.findFile(actualFileName)
            if (existingFile != null) {
                if (!prefs.storageOverwrite) {
                    Log.d("E621Download", "File exists and overwrite disabled")
                    inputStream.close()
                    return true
                }
                existingFile.delete()
            }
            
            // Determine MIME type
            val extension = actualFileName.substringAfterLast(".", "").lowercase()
            val mimeType = when (extension) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "gif" -> "image/gif"
                "webp" -> "image/webp"
                "webm" -> "video/webm"
                "mp4" -> "video/mp4"
                else -> "application/octet-stream"
            }
            
            // Create file in custom folder
            val newFile = targetDir?.createFile(mimeType, actualFileName.substringBeforeLast("."))
            if (newFile == null) {
                Log.e("E621Download", "Failed to create file in custom folder")
                return downloadToPublicStorage(inputStream, fileName, postId, totalSize)
            }
            
            // Write using ContentResolver for SAF
            val outputStream = contentResolver.openOutputStream(newFile.uri)
            if (outputStream == null) {
                Log.e("E621Download", "Failed to open output stream for SAF")
                newFile.delete()
                return downloadToPublicStorage(inputStream, fileName, postId, totalSize)
            }
            
            // Copy with progress
            val success = copyStreamWithProgress(inputStream, outputStream, postId, totalSize)
            
            if (success) {
                Log.d("E621Download", "SAF download complete: ${newFile.uri}")
            } else {
                newFile.delete()
            }
            
            success
            
        } catch (e: Exception) {
            Log.e("E621Download", "SAF download error: ${e.message}", e)
            // Try fallback
            try {
                downloadToPublicStorage(inputStream, fileName, postId, totalSize)
            } catch (e2: Exception) {
                false
            }
        }
    }
    
    /**
     * Download to public Downloads/E621 folder
     */
    private fun downloadToPublicStorage(
        inputStream: java.io.InputStream,
        fileName: String,
        postId: Int,
        totalSize: Long
    ): Boolean {
        return try {
            val outputFile = getOutputFileForPublicStorage(fileName)
            
            // Check overwrite preference
            if (outputFile.exists() && !prefs.storageOverwrite) {
                Log.d("E621Download", "File exists and overwrite disabled")
                inputStream.close()
                return true
            }
            
            // Create parent directories
            outputFile.parentFile?.mkdirs()
            
            val outputStream = FileOutputStream(outputFile)
            val success = copyStreamWithProgress(inputStream, outputStream, postId, totalSize)
            
            if (success) {
                Log.d("E621Download", "Public storage download complete: ${outputFile.absolutePath}")
            } else {
                outputFile.delete()
            }
            
            success
            
        } catch (e: Exception) {
            Log.e("E621Download", "Public storage download error: ${e.message}", e)
            false
        }
    }
    
    /**
     * Copy stream with progress updates showing MB downloaded/total
     */
    private fun copyStreamWithProgress(
        input: java.io.InputStream,
        output: java.io.OutputStream,
        postId: Int,
        totalSize: Long
    ): Boolean {
        return try {
            var downloadedSize = 0L
            var lastUpdateTime = System.currentTimeMillis()
            val buffer = ByteArray(16384) // 16KB buffer for better performance
            var bytesRead: Int
            
            input.use { inputStream ->
                output.use { outputStream ->
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        downloadedSize += bytesRead
                        
                        // Update progress every 100ms for smooth updates
                        val now = System.currentTimeMillis()
                        if (now - lastUpdateTime >= 100) {
                            lastUpdateTime = now
                            updateDownloadProgressDetailed(postId, downloadedSize, totalSize)
                        }
                    }
                    outputStream.flush()
                }
            }
            
            // Final progress update
            updateDownloadProgressDetailed(postId, downloadedSize, totalSize)
            
            // Verify downloaded size matches expected
            if (totalSize > 0 && downloadedSize != totalSize) {
                Log.w("E621Download", "Size mismatch: expected $totalSize, got $downloadedSize")
                // Still consider it success if we got data
                downloadedSize > 0
            } else {
                true
            }
            
        } catch (e: Exception) {
            Log.e("E621Download", "Stream copy error: ${e.message}", e)
            false
        }
    }
    
    /**
     * Get file path for public storage downloads
     */
    private fun getOutputFileForPublicStorage(fileName: String): File {
        val parts = fileName.split("/")
        val actualFileName = parts.last()
        val subfolders = if (parts.size > 1) parts.dropLast(1).joinToString("/") else null
        
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val baseDir = File(downloadDir, "E621")
        
        val targetDir = if (subfolders != null) {
            File(baseDir, subfolders)
        } else {
            baseDir
        }
        
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        
        return File(targetDir, actualFileName)
    }
    
    private fun updateDownloadProgress(postId: Int, progress: Int) {
        mainHandler.post {
            val notification = NotificationCompat.Builder(this, DOWNLOAD_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(getString(R.string.app_name))
                .setContentText("$progress%")
                .setProgress(100, progress, false)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            
            notificationManager.notify(DOWNLOAD_NOTIFICATION_ID + postId, notification)
        }
    }
    
    /**
     * Update download progress with detailed MB information
     */
    private fun updateDownloadProgressDetailed(postId: Int, downloadedBytes: Long, totalBytes: Long) {
        mainHandler.post {
            // Don't update if download is already marked as complete (prevents race condition)
            if (completedDownloads.contains(postId)) {
                return@post
            }
            
            val downloadedMB = downloadedBytes / (1024.0 * 1024.0)
            val totalMB = totalBytes / (1024.0 * 1024.0)
            val progress = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt() else 0
            
            val progressText = if (totalBytes > 0) {
                String.format("%.1f MB / %.1f MB (%d%%)", downloadedMB, totalMB, progress)
            } else {
                String.format("%.1f MB", downloadedMB)
            }
            
            val notification = NotificationCompat.Builder(this, DOWNLOAD_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(getString(R.string.downloading_post, postId))
                .setContentText(progressText)
                .setProgress(100, progress, totalBytes <= 0)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .build()
            
            notificationManager.notify(DOWNLOAD_NOTIFICATION_ID + postId, notification)
        }
    }
    
    private fun tryDownloadManagerFallback(url: String, fileName: String, post: Post): Boolean {
        return try {
            val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            
            // Parse the URL
            val downloadUri = Uri.parse(url)
            Log.d("E621Download", "Download URI: $downloadUri")
            
            val request = DownloadManager.Request(downloadUri).apply {
                // Add required User-Agent header for e621
                addRequestHeader("User-Agent", "E621Client/1.0 (by e621_client on e621)")
                
                // Add auth headers if logged in
                if (prefs.isLoggedIn) {
                    val credentials = "${prefs.username}:${prefs.apiKey}"
                    val basicAuth = "Basic " + android.util.Base64.encodeToString(
                        credentials.toByteArray(), android.util.Base64.NO_WRAP
                    )
                    addRequestHeader("Authorization", basicAuth)
                }
                
                // Set destination to Downloads/E621/
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "E621/$fileName")
                
                // Set notification visibility
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                
                // Set title and description
                setTitle("E621 - $fileName")
                setDescription(getString(R.string.downloading_post, post.id))
                
                // Set MIME type
                val mimeType = when (post.file.ext?.lowercase()) {
                    "jpg", "jpeg" -> "image/jpeg"
                    "png" -> "image/png"
                    "gif" -> "image/gif"
                    "webm" -> "video/webm"
                    "mp4" -> "video/mp4"
                    "webp" -> "image/webp"
                    else -> "application/octet-stream"
                }
                setMimeType(mimeType)
                
                // Network settings
                setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }
            
            val downloadId = downloadManager.enqueue(request)
            Log.d("E621Download", "DownloadManager enqueued, ID: $downloadId")
            
            if (downloadId > 0) {
                currentDownloadId = downloadId
                activeDownloads[downloadId] = post.id
                true
            } else {
                Log.e("E621Download", "DownloadManager returned invalid ID")
                false
            }
        } catch (e: Exception) {
            Log.e("E621Download", "DownloadManager error: ${e.message}", e)
            false
        }
    }
    
    // Permission launchers
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Just continue, notifications are optional
    }
    
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            getCurrentPost()?.let { performDownload(it) }
        } else {
            Toast.makeText(this, R.string.storage_permission_denied, Toast.LENGTH_SHORT).show()
        }
    }
    
    private val followTagPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Proceed to follow tag regardless of permission result
        // Notifications will just not be shown if permission denied
        pendingFollowTag?.let { tag ->
            performFollowTag(tag)
            pendingFollowTag = null
        }
    }
    
    private fun performFollowTag(tag: String) {
        if (prefs.isFollowingTag(tag)) {
            Toast.makeText(this, getString(R.string.tag_already_followed, tag), Toast.LENGTH_SHORT).show()
        } else {
            prefs.followTag(tag)
            Toast.makeText(this, getString(R.string.tag_followed, tag), Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showMoreMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.menu_post_more, popup.menu)
        
        // Hide share options if preference enabled
        if (prefs.postDisableShare) {
            popup.menu.findItem(R.id.action_share)?.isVisible = false
            popup.menu.findItem(R.id.action_open_browser)?.isVisible = false
            popup.menu.findItem(R.id.action_copy_link)?.isVisible = false
        }
        
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_slideshow -> {
                    showSlideshowDialog()
                    true
                }
                R.id.action_edit_post -> {
                    editPost()
                    true
                }
                R.id.action_add_to_set -> {
                    showAddToSetDialog()
                    true
                }
                R.id.action_open_browser -> {
                    openInBrowser()
                    true
                }
                R.id.action_share -> {
                    sharePost()
                    true
                }
                R.id.action_copy_link -> {
                    copyLink()
                    true
                }
                R.id.action_copy_tags -> {
                    copyTags()
                    true
                }
                R.id.action_reload_post -> {
                    reloadPost()
                    true
                }
                R.id.action_check_notes -> {
                    checkForNotes()
                    true
                }
                R.id.action_view_json -> {
                    viewJsonData()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }
    
    // Slideshow functionality
    private var slideshowHandler: Handler? = null
    private var slideshowRunnable: Runnable? = null
    private var slideshowInterval = 5000L
    private var slideshowLoop = true
    private var isSlideshowRunning = false
    
    private fun showSlideshowDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_slideshow, null)
        val etInterval = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etInterval)
        val cbLoop = dialogView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.cbLoop)
        
        etInterval.setText((slideshowInterval / 1000).toString())
        cbLoop.isChecked = slideshowLoop
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.slideshow_settings)
            .setView(dialogView)
            .setPositiveButton(R.string.slideshow_start) { _, _ ->
                val interval = etInterval.text.toString().toLongOrNull() ?: 5
                slideshowInterval = interval * 1000
                slideshowLoop = cbLoop.isChecked
                startSlideshow()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun startSlideshow() {
        if (isSlideshowRunning) {
            stopSlideshow()
            return
        }
        
        isSlideshowRunning = true
        slideshowHandler = Handler(Looper.getMainLooper())
        slideshowRunnable = object : Runnable {
            override fun run() {
                if (!isSlideshowRunning) return
                
                val nextPosition = viewPager.currentItem + 1
                if (nextPosition < posts.size) {
                    viewPager.setCurrentItem(nextPosition, true)
                    slideshowHandler?.postDelayed(this, slideshowInterval)
                } else if (slideshowLoop && posts.isNotEmpty()) {
                    viewPager.setCurrentItem(0, true)
                    slideshowHandler?.postDelayed(this, slideshowInterval)
                } else {
                    stopSlideshow()
                }
            }
        }
        slideshowHandler?.postDelayed(slideshowRunnable!!, slideshowInterval)
        Toast.makeText(this, R.string.slideshow_started, Toast.LENGTH_SHORT).show()
        
        // Keep screen awake during slideshow
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    
    private fun stopSlideshow() {
        isSlideshowRunning = false
        slideshowRunnable?.let { slideshowHandler?.removeCallbacks(it) }
        slideshowHandler = null
        slideshowRunnable = null
        Toast.makeText(this, R.string.slideshow_stopped, Toast.LENGTH_SHORT).show()
        
        // Allow screen to turn off
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    
    private fun editPost() {
        if (!prefs.isLoggedIn) {
            Toast.makeText(this, R.string.post_menu_not_logged_in, Toast.LENGTH_SHORT).show()
            return
        }
        
        val post = getCurrentPost() ?: return
        val intent = Intent(this, EditPostActivity::class.java).apply {
            putExtra("post_id", post.id)
            putExtra("position", viewPager.currentItem)
        }
        startActivityForResult(intent, REQUEST_EDIT_POST)
    }
    
    private fun showAddToSetDialog() {
        if (!prefs.isLoggedIn) {
            Toast.makeText(this, R.string.post_menu_not_logged_in, Toast.LENGTH_SHORT).show()
            return
        }
        
        val post = getCurrentPost() ?: return
        
        // Show loading dialog
        val loadingDialog = AlertDialog.Builder(this)
            .setMessage(R.string.loading_sets)
            .setCancelable(false)
            .create()
        loadingDialog.show()
        
        lifecycleScope.launch {
            try {
                val username = prefs.username ?: throw Exception("Not logged in")
                val response = api.sets.list(creatorName = username)
                loadingDialog.dismiss()
                
                val sets = if (response.isSuccessful) response.body() ?: emptyList() else emptyList()
                
                if (sets.isEmpty()) {
                    Toast.makeText(this@PostActivity, R.string.no_sets_available, Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                val setNames = sets.map { it.name }.toTypedArray()
                AlertDialog.Builder(this@PostActivity)
                    .setTitle(R.string.select_set)
                    .setItems(setNames) { _, which ->
                        addPostToSet(post.id, sets[which].id)
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            } catch (e: Exception) {
                loadingDialog.dismiss()
                Toast.makeText(this@PostActivity, R.string.error_adding_to_set, Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun addPostToSet(postId: Int, setId: Int) {
        lifecycleScope.launch {
            try {
                api.sets.addPosts(setId, listOf(postId))
                Toast.makeText(this@PostActivity, R.string.post_added_to_set, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@PostActivity, R.string.error_adding_to_set, Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun sharePost() {
        val post = getCurrentPost() ?: return
        val shareUrl = "${prefs.baseUrl}posts/${post.id}"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareUrl)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.post_share)))
    }
    
    private fun openInBrowser() {
        val post = getCurrentPost() ?: return
        val url = "${prefs.baseUrl}posts/${post.id}"
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
    
    private fun copyLink() {
        val post = getCurrentPost() ?: return
        val url = "${prefs.baseUrl}posts/${post.id}"
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Post URL", url)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }
    
    private fun copyTags() {
        val post = getCurrentPost() ?: return
        val allTags = StringBuilder()
        
        post.tags?.let { tags ->
            tags.artist?.let { allTags.append(it.joinToString(" ")).append(" ") }
            tags.copyright?.let { allTags.append(it.joinToString(" ")).append(" ") }
            tags.character?.let { allTags.append(it.joinToString(" ")).append(" ") }
            tags.species?.let { allTags.append(it.joinToString(" ")).append(" ") }
            tags.general?.let { allTags.append(it.joinToString(" ")).append(" ") }
            tags.meta?.let { allTags.append(it.joinToString(" ")).append(" ") }
            tags.lore?.let { allTags.append(it.joinToString(" ")).append(" ") }
            tags.invalid?.let { allTags.append(it.joinToString(" ")) }
        }
        
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Post Tags", allTags.toString().trim())
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, R.string.post_tags_copied, Toast.LENGTH_SHORT).show()
    }
    
    private fun reloadPost() {
        val post = getCurrentPost() ?: return
        val position = viewPager.currentItem
        
        Toast.makeText(this, R.string.post_reloading, Toast.LENGTH_SHORT).show()
        
        lifecycleScope.launch {
            try {
                val response = api.posts.get(post.id)
                
                if (response.isSuccessful) {
                    response.body()?.post?.let { updatedPost ->
                        posts[position] = updatedPost
                        adapter.notifyItemChanged(position)
                        Toast.makeText(this@PostActivity, R.string.post_reloaded, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@PostActivity, R.string.post_reload_error, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@PostActivity, R.string.post_reload_error, Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun checkForNotes() {
        val post = getCurrentPost() ?: return
        
        lifecycleScope.launch {
            try {
                val response = api.notes.getForPost(post.id)
                
                if (response.isSuccessful) {
                    val notes = response.body() ?: emptyList()
                    if (notes.isEmpty()) {
                        Toast.makeText(this@PostActivity, R.string.post_no_notes, Toast.LENGTH_SHORT).show()
                    } else {
                        showNotesDialog(notes)
                    }
                } else {
                    Toast.makeText(this@PostActivity, R.string.post_no_notes, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@PostActivity, R.string.post_no_notes, Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun showNotesDialog(notes: List<com.e621.client.data.model.Note>) {
        val noteTexts = notes.mapIndexed { index, note -> 
            "${index + 1}. ${android.text.Html.fromHtml(note.body, android.text.Html.FROM_HTML_MODE_COMPACT)}"
        }
        
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.post_notes_found, notes.size))
            .setItems(noteTexts.toTypedArray()) { _, _ -> }
            .setPositiveButton(R.string.ok, null)
            .show()
    }
    
    private fun viewJsonData() {
        val post = getCurrentPost() ?: return
        
        // Convert post to formatted JSON string
        val gson = com.google.gson.GsonBuilder().setPrettyPrinting().create()
        val jsonString = gson.toJson(post)
        
        // Show in scrollable dialog
        val scrollView = android.widget.ScrollView(this)
        val textView = TextView(this).apply {
            text = jsonString
            setPadding(32, 32, 32, 32)
            setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 12f
        }
        scrollView.addView(textView)
        
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.post_json_title, post.id))
            .setView(scrollView)
            .setPositiveButton(R.string.ok, null)
            .setNeutralButton(R.string.post_copy_link) { _, _ ->
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Post JSON", jsonString)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
            }
            .show()
    }
    
    private fun getCurrentPost(): Post? = posts.getOrNull(viewPager.currentItem)
    
    private fun updateSystemUI() {
        val decorView = window.decorView
        var flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        
        if (hideNavBar) {
            flags = flags or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
        
        if (hideStatusBar) {
            flags = flags or View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
        
        decorView.systemUiVisibility = flags
    }
    
    private fun toggleUIVisibility() {
        uiHidden = !uiHidden
        layoutButtons.visibility = if (uiHidden) View.GONE else View.VISIBLE
        
        if (uiHidden) {
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
        } else {
            updateSystemUI()
        }
    }
    
    // PostInteractionListener implementation
    override fun onTagClicked(tag: String) {
        // Return to main with this tag as search
        val resultIntent = Intent()
        resultIntent.putExtra("tag", tag)
        setResult(RESULT_OK, resultIntent)
        finish()
    }
    
    override fun onTagLongClicked(tag: String, anchor: View) {
        showTagOptionsMenu(tag, anchor)
    }
    
    private fun showTagOptionsMenu(tag: String, anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.menu_tag_options, popup.menu)
        
        // Show/hide follow/unfollow based on current state
        val isFollowing = prefs.isFollowingTag(tag)
        popup.menu.findItem(R.id.action_follow_tag)?.isVisible = !isFollowing
        popup.menu.findItem(R.id.action_unfollow_tag)?.isVisible = isFollowing
        
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_search_tag -> {
                    // Search for this tag
                    val resultIntent = Intent()
                    resultIntent.putExtra("tag", tag)
                    setResult(RESULT_OK, resultIntent)
                    finish()
                    true
                }
                R.id.action_add_to_saved_searches -> {
                    // Add to saved searches
                    if (prefs.isSavedSearch(tag)) {
                        Toast.makeText(this, getString(R.string.tag_already_saved, tag), Toast.LENGTH_SHORT).show()
                    } else {
                        prefs.addSavedSearch(tag)
                        Toast.makeText(this, getString(R.string.tag_added_saved_search, tag), Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                R.id.action_add_to_blacklist -> {
                    // Add to blacklist
                    if (prefs.isBlacklisted(tag)) {
                        Toast.makeText(this, getString(R.string.tag_already_blacklisted, tag), Toast.LENGTH_SHORT).show()
                    } else {
                        prefs.addToBlacklist(tag)
                        Toast.makeText(this, getString(R.string.tag_added_blacklist, tag), Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                R.id.action_follow_tag -> {
                    // Follow tag - request notification permission first
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                            != PackageManager.PERMISSION_GRANTED) {
                            pendingFollowTag = tag
                            followTagPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            return@setOnMenuItemClickListener true
                        }
                    }
                    performFollowTag(tag)
                    true
                }
                R.id.action_unfollow_tag -> {
                    // Unfollow tag
                    if (!prefs.isFollowingTag(tag)) {
                        Toast.makeText(this, getString(R.string.tag_not_followed, tag), Toast.LENGTH_SHORT).show()
                    } else {
                        prefs.unfollowTag(tag)
                        Toast.makeText(this, getString(R.string.tag_unfollowed, tag), Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                R.id.action_add_to_search -> {
                    // Add to current search (will be handled by MainActivity)
                    val resultIntent = Intent()
                    resultIntent.putExtra("add_tag", tag)
                    setResult(RESULT_OK, resultIntent)
                    Toast.makeText(this, getString(R.string.tag_added_to_search, tag), Toast.LENGTH_SHORT).show()
                    finish()
                    true
                }
                R.id.action_exclude_from_search -> {
                    // Exclude from search
                    val resultIntent = Intent()
                    resultIntent.putExtra("exclude_tag", tag)
                    setResult(RESULT_OK, resultIntent)
                    Toast.makeText(this, getString(R.string.tag_removed_from_search, tag), Toast.LENGTH_SHORT).show()
                    finish()
                    true
                }
                R.id.action_view_wiki -> {
                    // Open wiki page for tag using WikiActivity
                    val intent = Intent(this, WikiActivity::class.java)
                    intent.putExtra(WikiActivity.EXTRA_TAG_NAME, tag)
                    startActivity(intent)
                    true
                }
                R.id.action_copy_tag -> {
                    // Copy tag to clipboard
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("tag", tag)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(this, R.string.tag_copied, Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    override fun onArtistClicked(artist: String) {
        // Search for posts by this artist
        val resultIntent = Intent()
        resultIntent.putExtra("tag", artist)
        setResult(RESULT_OK, resultIntent)
        finish()
    }
    
    override fun onUserClicked(username: String) {
        val intent = Intent(this, ProfileActivity::class.java)
        intent.putExtra("username", username)
        startActivity(intent)
    }
    
    override fun onParentClicked(parentId: Int) {
        val intent = Intent(this, PostActivity::class.java)
        intent.putExtra(EXTRA_POST_ID, parentId)
        startActivity(intent)
    }
    
    override fun onChildrenClicked(postId: Int) {
        // Search for children: parent:postId - stay in PostActivity flow
        lifecycleScope.launch {
            try {
                val response = api.posts.list(tags = "parent:$postId", page = 1, limit = 75)
                if (response.isSuccessful) {
                    val childPosts = response.body()?.posts
                    if (!childPosts.isNullOrEmpty()) {
                        // Open new PostActivity with children posts
                        POSTS_TO_SHOW = childPosts
                        val intent = Intent(this@PostActivity, PostActivity::class.java)
                        intent.putExtra(EXTRA_POSITION, 0)
                        startActivity(intent)
                    } else {
                        Toast.makeText(this@PostActivity, "No children found", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@PostActivity, "Error loading children", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onPoolClicked(poolId: Int) {
        // Get current post to check all pools
        val currentPost = posts.getOrNull(viewPager.currentItem)
        val poolsList = currentPost?.pools ?: listOf(poolId)
        
        if (poolsList.size > 1) {
            // Show dialog to select pool
            showPoolSelectionDialog(poolsList)
        } else {
            // Load single pool directly
            loadPoolPosts(poolId)
        }
    }
    
    private fun showPoolSelectionDialog(pools: List<Int>) {
        lifecycleScope.launch {
            try {
                // Fetch pool names
                val poolNames = mutableListOf<Pair<Int, String>>()
                
                for (poolId in pools) {
                    try {
                        val response = api.pools.get(poolId)
                        if (response.isSuccessful) {
                            response.body()?.let { pool ->
                                poolNames.add(Pair(pool.id, pool.name ?: "Pool $poolId"))
                            }
                        } else {
                            poolNames.add(Pair(poolId, "Pool $poolId"))
                        }
                    } catch (e: Exception) {
                        poolNames.add(Pair(poolId, "Pool $poolId"))
                    }
                }
                
                val names = poolNames.map { it.second }.toTypedArray()
                
                AlertDialog.Builder(this@PostActivity, R.style.Theme_E621Client_AlertDialog)
                    .setTitle(R.string.post_pools)
                    .setItems(names) { _, which ->
                        loadPoolPosts(poolNames[which].first)
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
                    
            } catch (e: Exception) {
                Toast.makeText(this@PostActivity, "Error loading pools", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun loadPoolPosts(poolId: Int) {
        // Open PoolViewActivity to show all posts from the pool
        val intent = Intent(this, PoolViewActivity::class.java)
        intent.putExtra(PoolViewActivity.EXTRA_POOL_ID, poolId)
        startActivity(intent)
    }
    
    override fun onSourceClicked(source: String) {
        try {
            if (source.startsWith("http")) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(source)))
            }
        } catch (e: Exception) {
            Toast.makeText(this, source, Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onImageTapped() {
        toggleUIVisibility()
    }
    
    override fun onImageLongPress(post: Post) {
        // Show options: save, share, set as wallpaper, etc
        showMoreMenu(btnMore)
    }
    
    // Keyboard navigation (for Android TV or physical keyboard)
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                val current = viewPager.currentItem
                if (current > 0) {
                    viewPager.setCurrentItem(current - 1, true)
                } else {
                    finish()
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val current = viewPager.currentItem
                if (current < posts.size - 1) {
                    viewPager.setCurrentItem(current + 1, true)
                }
                true
            }
            else -> super.onKeyUp(keyCode, event)
        }
    }
    
    override fun onPause() {
        super.onPause()
        // Pause all video players when activity is paused
        if (::adapter.isInitialized) {
            adapter.pauseAllPlayers()
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Resume current video player
        if (::adapter.isInitialized) {
            adapter.resumePlayer(viewPager.currentItem)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Release all video players
        if (::adapter.isInitialized) {
            adapter.releaseAllPlayers()
        }
        
        // Unregister download receiver
        downloadReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Log.w("E621Download", "Receiver not registered: ${e.message}")
            }
        }
        downloadReceiver = null
        
        if (!prefs.postKeepScreenAwake) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

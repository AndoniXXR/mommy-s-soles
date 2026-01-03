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
import com.e621.client.data.api.CloudFlareException
import com.e621.client.data.api.ServerDownException
import com.e621.client.ui.comments.CommentsActivity
import com.e621.client.ui.pools.PoolViewActivity
import com.e621.client.ui.profile.ProfileActivity
import com.e621.client.ui.wiki.WikiActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.e621.client.worker.DownloadWorker
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
    


    private val mainHandler = Handler(Looper.getMainLooper())
    
    // Debounce for clicks
    private var lastClickTime: Long = 0
    private val CLICK_DEBOUNCE = 1000L
    
    companion object {

        private const val REQUEST_EDIT_POST = 3
        var POSTS_TO_SHOW: List<Post>? = null
        const val EXTRA_POSITION = "position"
        const val EXTRA_RANDOM = "random"
        const val EXTRA_POST_ID = "post_id"
    }
    
    private val prefs by lazy { E621Application.instance.userPreferences }
    private val api by lazy { E621Application.instance.api }

    private fun isSafeClick(): Boolean {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastClickTime < CLICK_DEBOUNCE) return false
        lastClickTime = now
        return true
    }

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
        
        Log.d("E621Download", "Starting download via WorkManager: $url")
        
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
        
        // Enqueue WorkManager task
        val data = workDataOf(
            DownloadWorker.KEY_URL to url,
            DownloadWorker.KEY_FILE_NAME to fileName,
            DownloadWorker.KEY_POST_ID to post.id,
            DownloadWorker.KEY_TITLE to getString(R.string.downloading_post, post.id)
        )
        
        val downloadRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(data)
            .addTag("download_${post.id}")
            .build()
            
        WorkManager.getInstance(this).enqueue(downloadRequest)
        
        Toast.makeText(this, getString(R.string.downloading_post, post.id), Toast.LENGTH_SHORT).show()
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
    

    

    
    // Old download methods removed in favor of WorkManager
    

    
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
        // Disabled to prevent accidental hiding of UI
        /*
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
        */
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
        if (!isSafeClick()) return
        val intent = Intent(this, PostActivity::class.java)
        intent.putExtra(EXTRA_POST_ID, parentId)
        startActivity(intent)
    }
    
    override fun onChildrenClicked(parentId: Int) {
        if (!isSafeClick()) return
        // Load all children and show selection dialog
        lifecycleScope.launch {
            try {
                val response = api.posts.list(tags = "parent:$parentId", page = 1, limit = 320)
                if (response.isSuccessful) {
                    val childPosts = response.body()?.posts
                    if (!childPosts.isNullOrEmpty()) {
                        if (childPosts.size == 1) {
                            // Only 1 child - open directly
                            val intent = Intent(this@PostActivity, PostActivity::class.java)
                            intent.putExtra(EXTRA_POST_ID, childPosts[0].id)
                            startActivity(intent)
                        } else {
                            // Multiple children - show selection dialog
                            showChildrenSelectionDialog(childPosts)
                        }
                    } else {
                        Toast.makeText(this@PostActivity, "No children found", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@PostActivity, "Error loading children", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun showChildrenSelectionDialog(children: List<Post>) {
        // Create array of child IDs as strings for the dialog
        val childIds = children.map { "#${it.id}" }.toTypedArray()
        
        AlertDialog.Builder(this, R.style.Theme_E621Client_AlertDialog)
            .setTitle("Children Posts")
            .setMessage("Tap on a child post to view it")
            .setItems(childIds) { _, which ->
                // Open the selected child post
                val intent = Intent(this, PostActivity::class.java)
                intent.putExtra(EXTRA_POST_ID, children[which].id)
                startActivity(intent)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
    
    override fun onPoolClicked(poolId: Int) {
        if (!isSafeClick()) return
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
        // toggleUIVisibility() - Disabled to prevent accidental hiding of UI
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
        

        if (!prefs.postKeepScreenAwake) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

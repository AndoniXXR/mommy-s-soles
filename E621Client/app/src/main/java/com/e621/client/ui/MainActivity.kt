package com.e621.client.ui

import android.app.Activity
import android.content.Intent
import android.database.MatrixCursor
import android.os.Bundle
import android.provider.BaseColumns
import android.util.Log
import android.view.View
import android.view.ViewConfiguration
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.cursoradapter.widget.CursorAdapter
import androidx.cursoradapter.widget.SimpleCursorAdapter
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.e621.client.ui.views.CustomSwipeRefreshLayout
import com.e621.client.E621Application
import com.e621.client.R
import com.e621.client.data.model.Post
import com.e621.client.ui.adapter.PostPageAdapter
import com.e621.client.ui.auth.LoginActivity
import com.e621.client.ui.comments.CommentsActivity
import com.e621.client.ui.dmail.DmailActivity
import com.e621.client.ui.post.PostActivity
import com.e621.client.ui.saved.SavedSearchesActivity
import com.e621.client.ui.sets.SetsActivity
import com.e621.client.ui.settings.SettingsActivity
import com.e621.client.ui.profile.ProfileActivity
import com.e621.client.ui.popular.PopularActivity
import com.e621.client.ui.about.AboutActivity
import com.e621.client.ui.browse.BrowsePoolsActivity
import com.e621.client.ui.browse.BrowseTagsActivity
import com.e621.client.ui.browse.BrowseUsersActivity
import com.e621.client.data.api.CloudFlareException
import com.e621.client.data.api.ServerDownException
import com.e621.client.data.api.AuthenticationException
import com.e621.client.data.api.NetworkException
import com.e621.client.data.api.NetworkErrorType
import com.e621.client.util.UpdateChecker
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
/**
 * Main Activity - Post grid with swipe pagination (ViewPager2)
 * Swipe left/right to navigate between pages
 * Based on original app's MainActivity structure
 */
class MainActivity : BaseActivity(), PostPageAdapter.OnPostClickListener {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var viewPager: ViewPager2
    private lateinit var swipeRefresh: CustomSwipeRefreshLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var txtPageNr: TextView
    private lateinit var searchView: SearchView
    private lateinit var btnMenu: ImageButton
    private lateinit var btnSearch: ImageButton
    
    // Track current bottom nav menu mode
    private var currentBottomNavMode = 0 // 0 = normal, 1 = selection
    
    private lateinit var pageAdapter: PostPageAdapter
    private lateinit var suggestionsAdapter: SimpleCursorAdapter
    private var searchAutoComplete: AutoCompleteTextView? = null
    
    private var currentTags = ""
    private var searchJob: Job? = null
    
    // Stack to track search history for back navigation (tags + page)
    private val searchStack = java.util.Stack<Pair<String, Int>>()
    
    // Flag to prevent dropdown from opening during back navigation
    private var isNavigatingBack = false
    
    // ActivityResultLauncher for PostActivity
    private lateinit var postActivityLauncher: ActivityResultLauncher<Intent>
    
    // ActivityResultLauncher for SavedSearchesActivity
    private lateinit var savedSearchesLauncher: ActivityResultLauncher<Intent>
    
    // ActivityResultLauncher for SettingsActivity
    private lateinit var settingsLauncher: ActivityResultLauncher<Intent>
    
    // Update checker
    private val updateChecker by lazy { UpdateChecker(this) }
    
    private val api by lazy { E621Application.instance.api }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        setupActivityResultLauncher()
        setupViews()
        setupToolbar()
        setupViewPager()
        setupBottomNavigation()
        setupSwipeRefresh()
        setupSearch()
        
        // Handle search tag from followed tags notification
        handleNotificationIntent(intent)
        
        // Handle search query from intent (e.g., from SetsActivity)
        intent.getStringExtra("search_query")?.let { query ->
            currentTags = query
        }
        
        // Restore last search state if no intent extras
        if (intent.getStringExtra("search_query") == null && intent.getStringExtra("search_tag") == null) {
            restoreSearchState()
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Handle notification tap when app is already running
        handleNotificationIntent(intent)
    }
    
    override fun onStop() {
        super.onStop()
        // Save current search state when app goes to background
        prefs.saveSearchState(currentTags, viewPager.currentItem)
    }
    
    /**
     * Restore the last search state from preferences
     */
    private fun restoreSearchState() {
        val lastSearch = prefs.lastSearch ?: ""
        val lastPage = prefs.lastSearchPage
        
        if (lastSearch.isNotEmpty() || lastPage > 0) {
            currentTags = lastSearch
            pageAdapter.setTags(lastSearch)
            viewPager.adapter = null
            viewPager.adapter = pageAdapter
            
            // Navigate to last page after adapter is set
            if (lastPage > 0) {
                viewPager.post {
                    viewPager.setCurrentItem(lastPage, false)
                    updatePageIndicator(lastPage + 1)
                }
            }
            
            // Update search view to show current tags
            if (lastSearch.isNotEmpty()) {
                searchView.setQuery(lastSearch, false)
                searchView.clearFocus()
            }
        }
    }
    
    private fun handleNotificationIntent(intent: Intent?) {
        if (intent == null) return
        
        // Handle update notification tap
        if (intent.getBooleanExtra("check_update", false)) {
            val version = intent.getStringExtra("update_version") ?: return
            val url = intent.getStringExtra("update_url") ?: return
            val changelog = intent.getStringExtra("update_changelog") ?: ""
            
            val updateInfo = UpdateChecker.UpdateInfo(
                versionName = version,
                versionCode = 0,  // Not used for display
                downloadUrl = url,
                changelog = changelog,
                isNewer = true  // Already verified by worker
            )
            showUpdateDialog(updateInfo)
            
            // Clear extras to prevent re-processing
            intent.removeExtra("check_update")
            intent.removeExtra("update_version")
            intent.removeExtra("update_url")
            intent.removeExtra("update_changelog")
            return
        }
        
        // Handle search tag from followed tags notification
        intent.getStringExtra("search_tag")?.let { tag ->
            // Set the tag in search view and perform search
            currentTags = tag
            searchView.post {
                searchView.setQuery(tag, false)
                searchView.clearFocus()
                performSearch(tag)
            }
            // Clear the extra to prevent re-processing
            intent.removeExtra("search_tag")
        }
    }
    
    private fun setupActivityResultLauncher() {
        postActivityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data ?: return@registerForActivityResult
                
                when {
                    // Direct tag search
                    data.hasExtra("tag") -> {
                        val tag = data.getStringExtra("tag") ?: return@registerForActivityResult
                        performSearch(tag)
                    }
                    
                    // Add tag to current search
                    data.hasExtra("add_tag") -> {
                        val tag = data.getStringExtra("add_tag") ?: return@registerForActivityResult
                        val newSearch = if (currentTags.isNotBlank()) {
                            "$currentTags $tag"
                        } else {
                            tag
                        }
                        performSearch(newSearch)
                    }
                    
                    // Exclude tag from current search
                    data.hasExtra("exclude_tag") -> {
                        val tag = data.getStringExtra("exclude_tag") ?: return@registerForActivityResult
                        val newSearch = if (currentTags.isNotBlank()) {
                            "$currentTags -$tag"
                        } else {
                            "-$tag"
                        }
                        performSearch(newSearch)
                    }
                }
            }
        }
        
        // Launcher for SavedSearchesActivity
        savedSearchesLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val searchTags = result.data?.getStringExtra(SavedSearchesActivity.RESULT_SEARCH_TAGS)
                if (!searchTags.isNullOrBlank()) {
                    performSearch(searchTags)
                }
            }
        }
        
        // Launcher for SettingsActivity - refresh when host changes
        settingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == SettingsActivity.RESULT_HOST_CHANGED) {
                // Host was changed, refresh the current view
                refreshCurrentPage()
            }
        }
    }
    
    private fun setupViews() {
        toolbar = findViewById(R.id.toolbar)
        viewPager = findViewById(R.id.viewPager)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        progressBar = findViewById(R.id.progressBar)
        bottomNav = findViewById(R.id.bottomNav)
        txtPageNr = findViewById(R.id.txtPageNr)
        searchView = findViewById(R.id.searchView)
        btnMenu = findViewById(R.id.btnMenu)
        btnSearch = findViewById(R.id.btnSearch)
    }
    
    private fun updateBottomNavMenu() {
        val count = pageAdapter.selectedPostIds.size
        val newMode = if (count > 0) 1 else 0
        
        if (currentBottomNavMode != newMode) {
            currentBottomNavMode = newMode
            bottomNav.menu.clear()
            if (count > 0) {
                bottomNav.inflateMenu(R.menu.menu_bottom_nav_selection)
            } else {
                bottomNav.inflateMenu(R.menu.menu_bottom_nav_pagination)
            }
        }
        
        // Update badge with selection count if in selection mode
        if (count > 0) {
            bottomNav.getOrCreateBadge(R.id.nav_close_selection).apply {
                isVisible = true
                number = count
            }
        }
    }
    
    private fun favoriteSelectedPosts() {
        if (!prefs.isLoggedIn) {
            Toast.makeText(this, R.string.login_required, Toast.LENGTH_SHORT).show()
            return
        }
        
        val selectedPosts = pageAdapter.getSelectedPosts()
        if (selectedPosts.isEmpty()) return
        
        val totalCount = selectedPosts.size
        
        // Clear selection immediately for better UX
        pageAdapter.clearSelection()
        
        // Show immediate feedback
        Toast.makeText(this, getString(R.string.selection_favoriting, totalCount), Toast.LENGTH_SHORT).show()
        
        // Process favorites in background
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            var successCount = 0
            var errorCount = 0
            
            for (post in selectedPosts) {
                try {
                    val response = api.posts.favorite(post.id)
                    if (response.isSuccessful) {
                        successCount++
                    } else {
                        errorCount++
                    }
                } catch (e: Exception) {
                    Log.e("E621Client", "Error favoriting post ${post.id}: ${e.message}")
                    errorCount++
                }
            }
            
            // Show result on main thread
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                val message = if (errorCount == 0) {
                    getString(R.string.selection_favorited, successCount)
                } else {
                    getString(R.string.selection_favorited_partial, successCount, errorCount)
                }
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun downloadSelectedPosts() {
        val selectedPosts = pageAdapter.getSelectedPosts()
        if (selectedPosts.isEmpty()) return
        
        val totalCount = selectedPosts.size
        
        // Clear selection immediately for responsive UX
        pageAdapter.clearSelection()
        
        // Show immediate feedback
        Toast.makeText(this, getString(R.string.selection_downloading, totalCount), Toast.LENGTH_SHORT).show()
        
        // Start sequential download queue with real progress
        startSequentialDownloadsWithProgress(selectedPosts.toList())
    }
    
    /**
     * Downloads posts sequentially with real-time progress notification
     * Shows: "Downloading 1/3 - Post #12345 - 45% (2.5 MB / 5.5 MB)"
     */
    private fun startSequentialDownloadsWithProgress(posts: List<Post>) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        val channelId = "download_progress"
        val notificationId = 9999
        
        // Create notification channel for Android 8+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "Download Progress",
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows download progress"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        // Use applicationScope to ensure downloads continue even if activity is destroyed
        E621Application.instance.applicationScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val total = posts.size
            var completed = 0
            var failed = 0
            val downloadedFiles = mutableListOf<java.io.File>()
            
            for ((index, post) in posts.withIndex()) {
                val current = index + 1
                val ext = post.file.ext ?: "jpg"
                val fileName = "${post.id}.$ext"
                val url = post.file.url
                
                if (url == null) {
                    failed++
                    continue
                }
                
                try {
                    // Download with real progress updates using robust OkHttp client
                    val downloadedFile = downloadWithProgressReturningFile(
                        post = post,
                        url = url,
                        fileName = fileName,
                        currentIndex = current,
                        totalCount = total,
                        notificationManager = notificationManager,
                        channelId = channelId,
                        notificationId = notificationId
                    )
                    
                    if (downloadedFile != null) {
                        completed++
                        downloadedFiles.add(downloadedFile)
                    } else {
                        failed++
                    }
                } catch (e: Exception) {
                    Log.e("E621Client", "Error downloading post ${post.id}: ${e.message}")
                    failed++
                }
            }
            
            // Show completion notification with list of files
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                notificationManager.cancel(notificationId)
                
                showDownloadCompletionNotification(
                    notificationManager = notificationManager,
                    channelId = channelId,
                    notificationId = notificationId + 1,
                    downloadedFiles = downloadedFiles,
                    completed = completed,
                    failed = failed
                )
                
                // Only show toast if activity is still valid
                if (!isFinishing && !isDestroyed) {
                    val resultText = if (failed == 0) {
                        getString(R.string.selection_download_complete, completed)
                    } else {
                        getString(R.string.selection_download_complete_partial, completed, failed)
                    }
                    Toast.makeText(this@MainActivity, resultText, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    /**
     * Shows a notification with list of downloaded files
     * Tapping the notification opens the Downloads/E621 folder
     * Each file can be viewed in gallery
     */
    private fun showDownloadCompletionNotification(
        notificationManager: android.app.NotificationManager,
        channelId: String,
        notificationId: Int,
        downloadedFiles: List<java.io.File>,
        completed: Int,
        failed: Int
    ) {
        val resultText = if (failed == 0) {
            getString(R.string.selection_download_complete, completed)
        } else {
            getString(R.string.selection_download_complete_partial, completed, failed)
        }
        
        // Create intent to open the E621 folder
        val mainIntent = if (downloadedFiles.isNotEmpty()) {
            // Open folder intent
            Intent(Intent.ACTION_VIEW).apply {
                val folderUri = android.net.Uri.parse("content://com.android.externalstorage.documents/document/primary:Download%2FE621")
                setDataAndType(folderUri, "resource/folder")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            null
        }
        
        val mainPendingIntent = mainIntent?.let {
            android.app.PendingIntent.getActivity(
                this,
                System.currentTimeMillis().toInt(),
                it,
                android.app.PendingIntent.FLAG_ONE_SHOT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
        }
        
        // Build notification with InboxStyle to show list of files
        val inboxStyle = androidx.core.app.NotificationCompat.InboxStyle()
            .setBigContentTitle(getString(R.string.selection_download_done))
            .setSummaryText(resultText)
        
        // Add each file to the inbox style with better formatting (max 6 lines shown)
        val filesToShow = downloadedFiles.take(6)
        for ((index, file) in filesToShow.withIndex()) {
            val sizeKB = file.length() / 1024.0
            val sizeText = if (sizeKB > 1024) {
                String.format("%.1f MB", sizeKB / 1024.0)
            } else {
                String.format("%.0f KB", sizeKB)
            }
            inboxStyle.addLine("${index + 1}. ${file.nameWithoutExtension} ($sizeText)")
        }
        if (downloadedFiles.size > 6) {
            inboxStyle.addLine("    + ${downloadedFiles.size - 6} más...")
        }
        
        val builder = androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(getString(R.string.selection_download_done))
            .setContentText(resultText)
            .setStyle(inboxStyle)
            .setAutoCancel(true)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
            .setNumber(completed)
        
        if (mainPendingIntent != null) {
            builder.setContentIntent(mainPendingIntent)
        }
        
        // Add action buttons for first 3 files to open individually
        // Each file has a unique URI so PendingIntents will be unique
        val actionFiles = downloadedFiles.take(3)
        for ((index, file) in actionFiles.withIndex()) {
            val viewIntent = createViewFileIntent(file, index)
            if (viewIntent != null) {
                // Use Intent.createChooser to let user pick gallery app
                val chooserIntent = Intent.createChooser(viewIntent, "Ver ${file.name}")
                chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                
                val uniqueRequestCode = (System.currentTimeMillis() + index).toInt()
                val pendingIntent = android.app.PendingIntent.getActivity(
                    this,
                    uniqueRequestCode,
                    chooserIntent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )
                val actionTitle = file.nameWithoutExtension
                builder.addAction(R.drawable.ic_image, "#$actionTitle", pendingIntent)
            }
        }
        
        notificationManager.notify(notificationId, builder.build())
    }
    
    /**
     * Creates an intent to view a file in the gallery/viewer
     * Each intent uses the unique file URI which makes PendingIntents unique
     */
    private fun createViewFileIntent(file: java.io.File, index: Int = 0): Intent? {
        return try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )
            val mimeType = getMimeType(file.extension)
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } catch (e: Exception) {
            Log.e("E621Client", "Error creating view intent: ${e.message}")
            null
        }
    }
    
    /**
     * Download a single file with real-time progress notification
     * Returns the downloaded File on success, null on failure
     */
    private suspend fun downloadWithProgressReturningFile(
        post: Post,
        url: String,
        fileName: String,
        currentIndex: Int,
        totalCount: Int,
        notificationManager: android.app.NotificationManager,
        channelId: String,
        notificationId: Int
    ): java.io.File? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            // Use OkHttp for robust downloading (same as PostActivity)
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build()
                
            val requestBuilder = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", "E621Client/1.0 (Android)")
                
            // Add auth if logged in
            if (prefs.isLoggedIn) {
                val credentials = "${prefs.username}:${prefs.apiKey}"
                val basicAuth = "Basic " + android.util.Base64.encodeToString(
                    credentials.toByteArray(), android.util.Base64.NO_WRAP
                )
                requestBuilder.header("Authorization", basicAuth)
            }
            
            val response = client.newCall(requestBuilder.build()).execute()
            
            if (!response.isSuccessful) {
                Log.e("E621Client", "HTTP error: ${response.code}")
                response.close()
                return@withContext null
            }
            
            val body = response.body ?: return@withContext null
            val totalBytes = body.contentLength()
            val totalSizeMB = if (totalBytes > 0) totalBytes / (1024.0 * 1024.0) else 0.0
            
            // Create output file in Downloads/E621
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS
            )
            val e621Dir = java.io.File(downloadsDir, "E621")
            if (!e621Dir.exists()) e621Dir.mkdirs()
            val outputFile = java.io.File(e621Dir, fileName)
            
            var downloadedBytes = 0L
            var lastNotificationUpdate = 0L
            val buffer = ByteArray(8192)
            
            body.byteStream().use { input ->
                java.io.FileOutputStream(outputFile).use { output ->
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        
                        // Update notification every 100ms to avoid too frequent updates
                        val now = System.currentTimeMillis()
                        if (now - lastNotificationUpdate > 100) {
                            lastNotificationUpdate = now
                            
                            val progress = if (totalBytes > 0) {
                                ((downloadedBytes * 100) / totalBytes).toInt()
                            } else 0
                            
                            val downloadedMB = downloadedBytes / (1024.0 * 1024.0)
                            
                            val progressText = if (totalBytes > 0) {
                                String.format("%.1f MB / %.1f MB (%d%%)", downloadedMB, totalSizeMB, progress)
                            } else {
                                String.format("%.1f MB", downloadedMB)
                            }
                            
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                val notification = androidx.core.app.NotificationCompat.Builder(this@MainActivity, channelId)
                                    .setSmallIcon(R.drawable.ic_download)
                                    .setContentTitle("[$currentIndex/$totalCount] Post #${post.id}")
                                    .setContentText(progressText)
                                    .setProgress(100, progress, totalBytes <= 0)
                                    .setOngoing(true)
                                    .setOnlyAlertOnce(true)
                                    .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
                                    .build()
                                notificationManager.notify(notificationId, notification)
                            }
                        }
                    }
                }
            }
            
            response.close()
            
            // Notify media scanner
            try {
                val uri = android.net.Uri.fromFile(outputFile)
                sendBroadcast(android.content.Intent(android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri))
            } catch (e: Exception) {
                Log.e("E621Client", "Media scanner error: ${e.message}")
            }
            
            outputFile
        } catch (e: Exception) {
            Log.e("E621Client", "Download error for post ${post.id}: ${e.message}")
            null
        }
    }
    
    /**
     * Get MIME type based on file extension for proper download handling
     */
    private fun getMimeType(extension: String): String {
        return when (extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "webm" -> "video/webm"
            "mp4" -> "video/mp4"
            else -> "application/octet-stream"
        }
    }
    
    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        
        // Menu button click
        btnMenu.setOnClickListener {
            showMainMenu()
        }
        
        // Search button - always execute search (empty = go to page 1)
        btnSearch.setOnClickListener {
            val query = searchView.query?.toString()?.trim() ?: ""
            searchView.clearFocus()
            performSearch(query)
        }
    }
    
    private fun setupSearch() {
        // Get the internal AutoCompleteTextView from SearchView
        searchAutoComplete = searchView.findViewById<AutoCompleteTextView>(
            androidx.appcompat.R.id.search_src_text
        )
        
        // Configure AutoCompleteTextView to show suggestions immediately
        searchAutoComplete?.threshold = 0  // Show dropdown with 0 characters
        
        // Handle Enter key to execute search even when empty
        searchAutoComplete?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO) {
                val query = searchView.query?.toString()?.trim() ?: ""
                performSearch(query)
                true
            } else {
                false
            }
        }
        
        // Setup suggestions adapter with custom layout and icon binding
        val from = arrayOf("suggest_text", "suggest_info", "suggest_icon")
        val to = intArrayOf(android.R.id.text1, android.R.id.text2, R.id.suggestionIcon)
        
        suggestionsAdapter = SimpleCursorAdapter(
            this,
            R.layout.search_suggestion_item,
            null,
            from,
            to,
            CursorAdapter.FLAG_REGISTER_CONTENT_OBSERVER
        )
        
        // Custom ViewBinder to set icons
        suggestionsAdapter.viewBinder = SimpleCursorAdapter.ViewBinder { view, cursor, columnIndex ->
            if (view is ImageView && view.id == R.id.suggestionIcon) {
                val iconRes = cursor.getInt(columnIndex)
                view.setImageResource(iconRes)
                true
            } else {
                false
            }
        }
        
        searchView.suggestionsAdapter = suggestionsAdapter
        
        // Handle suggestion click
        searchView.setOnSuggestionListener(object : SearchView.OnSuggestionListener {
            override fun onSuggestionSelect(position: Int): Boolean = false
            
            override fun onSuggestionClick(position: Int): Boolean {
                val cursor = suggestionsAdapter.cursor
                if (cursor.moveToPosition(position)) {
                    val suggestion = cursor.getString(cursor.getColumnIndexOrThrow("suggest_text"))
                    val type = try {
                        cursor.getString(cursor.getColumnIndexOrThrow("suggest_type"))
                    } catch (e: Exception) { "tag" }
                    
                    when (type) {
                        "clear" -> {
                            // Clear search history
                            prefs.clearSearchHistory()
                            showSearchHistory()
                            Toast.makeText(this@MainActivity, "Search history cleared", Toast.LENGTH_SHORT).show()
                        }
                        "hint" -> {
                            // Do nothing for hint items
                        }
                        "history" -> {
                            // History items: execute search directly (they are complete queries)
                            performSearch(suggestion)
                        }
                        else -> {
                            // Tag suggestions: insert into current query, replacing only the current word
                            insertTagSuggestion(suggestion)
                        }
                    }
                }
                return true
            }
        })
        
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                performSearch(query?.trim() ?: "")
                return true
            }
            
            override fun onQueryTextChange(newText: String?): Boolean {
                // Cancel previous search
                searchJob?.cancel()
                
                val query = newText?.trim() ?: ""
                if (query.isEmpty()) {
                    // Show search history when empty
                    showSearchHistory()
                    searchAutoComplete?.showDropDown()
                    return true
                }
                
                // Debounce: wait 300ms before fetching suggestions
                searchJob = lifecycleScope.launch {
                    delay(300)
                    fetchTagSuggestions(query)
                }
                return true
            }
        })
        
        // Show history when search view is focused
        searchAutoComplete?.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && !isNavigatingBack) {
                // Always show history when focused (but not during back navigation)
                showSearchHistory()
                searchAutoComplete?.post {
                    if (!isNavigatingBack) {
                        searchAutoComplete?.showDropDown()
                    }
                }
            } else if (!hasFocus) {
                // When losing focus, show current search as the query if we have tags
                if (currentTags.isNotEmpty() && searchView.query.isNullOrEmpty()) {
                    searchView.setQuery(currentTags, false)
                }
            }
        }
        
        // Handle click on search view to show history
        searchAutoComplete?.setOnClickListener {
            if (searchView.query.isNullOrEmpty()) {
                showSearchHistory()
            }
            searchAutoComplete?.showDropDown()
        }
        
        // Handle close button (X) to clear tags and reload default
        searchView.setOnCloseListener {
            if (currentTags.isNotEmpty()) {
                currentTags = ""
                pageAdapter.setTags("")
                // Force ViewPager to recreate views
                viewPager.adapter = null
                viewPager.adapter = pageAdapter
                viewPager.setCurrentItem(0, false)
                updatePageIndicator(1)
            }
            false // Return false to allow default close behavior
        }
    }
    
    private fun performSearch(query: String) {
        val trimmedQuery = query.trim()
        
        if (trimmedQuery.isNotBlank()) {
            // Only push to stack if the query is different from current (avoid duplicates)
            if (trimmedQuery != currentTags) {
                // Save current state (tags + current page) before changing
                val currentPage = viewPager.currentItem
                searchStack.push(Pair(currentTags, currentPage))
            }
            
            // Save to history
            prefs.addToSearchHistory(trimmedQuery)
            
            // Perform search
            currentTags = trimmedQuery
            pageAdapter.setTags(trimmedQuery)
            viewPager.adapter = null
            viewPager.adapter = pageAdapter
            viewPager.setCurrentItem(0, false)
            updatePageIndicator(1)
        } else {
            // Empty search = clear filters and show all posts
            currentTags = ""
            pageAdapter.setTags("")
            
            // Force refresh (clear cache) just like swipe-to-refresh
            pageAdapter.clearCache()
            
            // Force ViewPager to recreate views
            viewPager.adapter = null
            viewPager.adapter = pageAdapter
            viewPager.setCurrentItem(0, false)
            updatePageIndicator(1)
            Toast.makeText(this, "Showing all posts", Toast.LENGTH_SHORT).show()
        }
        
        // Hide keyboard and clear focus like T2 does
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(window.decorView.windowToken, 0)
        searchView.setQuery(trimmedQuery, false)
        searchView.clearFocus()
        searchAutoComplete?.dismissDropDown()
        viewPager.requestFocus()
    }
    
    private fun showSearchHistory() {
        val cursor = MatrixCursor(arrayOf(BaseColumns._ID, "suggest_text", "suggest_info", "suggest_type", "suggest_icon"))
        var id = 0
        
        val history = prefs.searchHistory
        if (history.isNotEmpty()) {
            // Add history items with history icon
            history.take(10).forEach { item ->
                cursor.addRow(arrayOf(id++, item, "Recent search", "history", R.drawable.ic_history))
            }
            
            // Add "Clear history" option at the end with delete icon
            cursor.addRow(arrayOf(id++, "Clear search history", "", "clear", R.drawable.ic_delete))
        } else {
            // Show hint when no history
            cursor.addRow(arrayOf(id++, "Start typing to search tags...", "No recent searches", "hint", R.drawable.ic_search))
        }
        
        suggestionsAdapter.changeCursor(cursor)
    }
    
    private fun fetchTagSuggestions(query: String) {
        lifecycleScope.launch {
            try {
                val cursor = MatrixCursor(arrayOf(BaseColumns._ID, "suggest_text", "suggest_info", "suggest_type", "suggest_icon"))
                var id = 0
                
                // Extract the last word (current word being typed) for tag suggestions
                val lastWord = getLastWord(query)
                
                // Add matching history items first with history icon (match against full query)
                prefs.searchHistory
                    .filter { it.contains(query, ignoreCase = true) }
                    .take(3)
                    .forEach { item ->
                        cursor.addRow(arrayOf(id++, item, "Recent search", "history", R.drawable.ic_history))
                    }
                
                // Fetch tag suggestions for the LAST WORD only (not the full query)
                if (lastWord.length >= 2) {
                    val response = api.tags.autocomplete(lastWord, 10)
                    if (response.isSuccessful) {
                        response.body()?.forEach { tag ->
                            val categoryName = getCategoryName(tag.category ?: 0)
                            val postCount = formatCount(tag.postCount ?: 0)
                            val info = "$categoryName • $postCount posts"
                            cursor.addRow(arrayOf(id++, tag.name, info, "tag", R.drawable.ic_tag))
                        }
                    }
                }
                
                suggestionsAdapter.changeCursor(cursor)
            } catch (e: Exception) {
                Log.e("E621Client", "Error fetching suggestions: ${e.message}")
            }
        }
    }
    
    /**
     * Get the last word being typed (after the last space)
     */
    private fun getLastWord(query: String): String {
        val trimmed = query.trim()
        if (trimmed.isEmpty() || trimmed.endsWith(" ")) {
            return ""
        }
        val lastSpaceIndex = trimmed.lastIndexOf(' ')
        return if (lastSpaceIndex >= 0) {
            trimmed.substring(lastSpaceIndex + 1)
        } else {
            trimmed
        }
    }
    
    /**
     * Insert a tag suggestion into the current query, replacing only the current word being typed.
     * This preserves existing tags and only replaces the partial word.
     */
    private fun insertTagSuggestion(tagName: String) {
        val currentQuery = searchView.query?.toString() ?: ""
        val newQuery = buildQueryWithTag(currentQuery, tagName)
        
        // Update SearchView without triggering search
        searchView.setQuery(newQuery, false)
        
        // Move cursor to end
        searchAutoComplete?.setSelection(newQuery.length)
    }
    
    /**
     * Build new query by inserting tag, replacing only the current word being typed.
     * Mimics the behavior of the original app's MySearchView.
     */
    private fun buildQueryWithTag(currentQuery: String, newTag: String): String {
        val trimmedTag = newTag.trim()
        val trimmedQuery = currentQuery.trim()
        
        return when {
            // Case 1: Empty query - just use the new tag
            trimmedQuery.isEmpty() -> trimmedTag
            
            // Case 2: Query ends with space - append the new tag
            currentQuery.endsWith(" ") -> "$trimmedQuery $trimmedTag"
            
            // Case 3: Query has content - replace the last word with the new tag
            else -> {
                if (trimmedQuery.contains(" ")) {
                    // Multiple words: keep everything before the last space, add new tag
                    val beforeLastWord = trimmedQuery.substring(0, trimmedQuery.lastIndexOf(" "))
                    "$beforeLastWord $trimmedTag"
                } else {
                    // Single word: replace it entirely
                    trimmedTag
                }
            }
        }
    }
    
    private fun getCategoryName(category: Int): String {
        return when (category) {
            0 -> "General"
            1 -> "Artist"
            3 -> "Copyright"
            4 -> "Character"
            5 -> "Species"
            6 -> "Invalid"
            7 -> "Meta"
            8 -> "Lore"
            else -> "Tag"
        }
    }
    
    private fun formatCount(count: Int): String {
        return when {
            count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
            count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
            else -> count.toString()
        }
    }
    
    private fun setupViewPager() {
        pageAdapter = PostPageAdapter(this) { page ->
            // Callback when a page starts loading
            Log.d("E621Client", "Page $page loading...")
        }
        
        // Apply saved filter preferences
        pageAdapter.applyFilters(prefs.filterRating, prefs.filterType, prefs.filterOrder)
        
        viewPager.adapter = pageAdapter
        
        // Cache adjacent pages for smooth swiping (1 page before and after)
        viewPager.offscreenPageLimit = 1
        
        // Reduce swipe sensitivity - require more drag distance to change page
        reduceDragSensitivity(viewPager, 3)
        
        // Listen for page changes to update the indicator
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val pageNumber = position + 1
                updatePageIndicator(pageNumber)
            }
        })
        
        // Start at page 1
        updatePageIndicator(1)
    }
    
    /**
     * Reduces the drag sensitivity of ViewPager2 so users need to swipe more
     * to trigger a page change. This prevents accidental page changes.
     * @param viewPager The ViewPager2 to modify
     * @param sensitivityMultiplier Higher = less sensitive (default is 1)
     */
    private fun reduceDragSensitivity(viewPager: ViewPager2, sensitivityMultiplier: Int) {
        try {
            val recyclerViewField = ViewPager2::class.java.getDeclaredField("mRecyclerView")
            recyclerViewField.isAccessible = true
            val recyclerView = recyclerViewField.get(viewPager) as RecyclerView
            
            val touchSlopField = RecyclerView::class.java.getDeclaredField("mTouchSlop")
            touchSlopField.isAccessible = true
            val touchSlop = touchSlopField.get(recyclerView) as Int
            touchSlopField.set(recyclerView, touchSlop * sensitivityMultiplier)
        } catch (e: Exception) {
            Log.w("E621Client", "Could not reduce ViewPager2 sensitivity: ${e.message}")
        }
    }
    
    private fun setupBottomNavigation() {
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                // Normal mode items
                R.id.nav_saved -> {
                    // Open SavedSearchesActivity
                    val intent = Intent(this, SavedSearchesActivity::class.java).apply {
                        putExtra(SavedSearchesActivity.EXTRA_CURRENT_SEARCH, currentTags)
                    }
                    savedSearchesLauncher.launch(intent)
                    false
                }
                R.id.nav_filter -> {
                    showFilterMenu()
                    false
                }
                R.id.nav_favorites -> {
                    if (prefs.isLoggedIn) {
                        // Search for user's favorites
                        currentTags = "fav:${prefs.username}"
                        searchView.setQuery(currentTags, false)
                        pageAdapter.clearCache()
                        pageAdapter.setTags(currentTags)
                        viewPager.setCurrentItem(0, false)
                        updatePageIndicator(1)
                        Toast.makeText(this, getString(R.string.menu_my_favourites), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Login required", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this, LoginActivity::class.java))
                    }
                    false
                }
                // Selection mode items
                R.id.nav_close_selection -> {
                    pageAdapter.clearSelection()
                    false
                }
                R.id.nav_select_all -> {
                    pageAdapter.selectAll()
                    false
                }
                R.id.nav_favorite_selected -> {
                    favoriteSelectedPosts()
                    false
                }
                R.id.nav_download_selected -> {
                    downloadSelectedPosts()
                    false
                }
                else -> false
            }
        }
    }
    
    private fun showFilterMenu() {
        // Find the filter item in bottom nav to anchor the popup
        val filterView = bottomNav.findViewById<View>(R.id.nav_filter) ?: bottomNav
        
        val popup = android.widget.PopupMenu(this, filterView)
        popup.menuInflater.inflate(R.menu.menu_main_filter, popup.menu)
        
        // Set current filter states from preferences
        updateFilterMenuCheckStates(popup.menu)
        
        popup.setOnMenuItemClickListener { item ->
            handleFilterItemClick(item)
        }
        
        popup.show()
    }
    
    private fun updateFilterMenuCheckStates(menu: android.view.Menu) {
        // Rating (using bitmask from preferences)
        menu.findItem(R.id.filter_rating_e)?.isChecked = prefs.showExplicit()
        menu.findItem(R.id.filter_rating_q)?.isChecked = prefs.showQuestionable()
        menu.findItem(R.id.filter_rating_s)?.isChecked = prefs.showSafe()
        
        // Type (0=all, 1=images, 2=videos, 3=gifs)
        val filterType = prefs.filterType
        menu.findItem(R.id.filter_type_all)?.isChecked = filterType == 0
        menu.findItem(R.id.filter_type_images)?.isChecked = filterType == 1
        menu.findItem(R.id.filter_type_videos)?.isChecked = filterType == 2
        menu.findItem(R.id.filter_type_gifs)?.isChecked = filterType == 3
        
        // Order (0=newest, 1=oldest, 2=score, 3=favs)
        val filterOrder = prefs.filterOrder
        menu.findItem(R.id.filter_order_by_newest)?.isChecked = filterOrder == 0
        menu.findItem(R.id.filter_order_by_oldest)?.isChecked = filterOrder == 1
        menu.findItem(R.id.filter_order_by_score)?.isChecked = filterOrder == 2
        menu.findItem(R.id.filter_order_by_favs)?.isChecked = filterOrder == 3
    }
    
    private fun handleFilterItemClick(item: android.view.MenuItem): Boolean {
        when (item.itemId) {
            // Rating toggles (toggle individual bits)
            R.id.filter_rating_e -> {
                val current = prefs.filterRating
                prefs.filterRating = current xor 1 // Toggle bit 0 (explicit)
            }
            R.id.filter_rating_q -> {
                val current = prefs.filterRating
                prefs.filterRating = current xor 2 // Toggle bit 1 (questionable)
            }
            R.id.filter_rating_s -> {
                val current = prefs.filterRating
                prefs.filterRating = current xor 4 // Toggle bit 2 (safe)
            }
            
            // Type - mutually exclusive (0=all, 1=images, 2=videos, 3=gifs)
            R.id.filter_type_all -> prefs.filterType = 0
            R.id.filter_type_images -> prefs.filterType = 1
            R.id.filter_type_videos -> prefs.filterType = 2
            R.id.filter_type_gifs -> prefs.filterType = 3
            
            // Order - mutually exclusive (0=newest, 1=oldest, 2=score, 3=favs)
            R.id.filter_order_by_newest -> prefs.filterOrder = 0
            R.id.filter_order_by_oldest -> prefs.filterOrder = 1
            R.id.filter_order_by_score -> prefs.filterOrder = 2
            R.id.filter_order_by_favs -> prefs.filterOrder = 3
            
            else -> return false
        }
        
        // Ensure at least one rating is selected
        if (prefs.filterRating == 0) {
            prefs.filterRating = 7 // Reset to all if none selected
        }
        
        // Apply filters locally to cached posts
        applyFilters()
        return true
    }
    
    private fun applyFilters() {
        // Apply filters to the adapter (filters posts locally, no new API request)
        pageAdapter.applyFilters(
            rating = prefs.filterRating,
            type = prefs.filterType,
            order = prefs.filterOrder
        )
    }
    
    private fun setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener {
            // Refresh current page
            pageAdapter.clearCache()
            swipeRefresh.isRefreshing = false
        }
        swipeRefresh.setColorSchemeResources(R.color.accent)
    }
    
    /**
     * Refresh the current page - used when host changes or settings are updated
     */
    private fun refreshCurrentPage() {
        // Clear cache and reload with new API instance
        pageAdapter.clearCache()
        viewPager.adapter = null
        viewPager.adapter = pageAdapter
        viewPager.setCurrentItem(0, false)
        updatePageIndicator(1)
    }
    
    private fun updatePageIndicator(page: Int) {
        txtPageNr.text = getString(R.string.main_page_number, page)
    }
    
    override fun onPostClick(post: Post, position: Int) {
        // Obtener todos los posts cargados para navegación entre ellos
        val allPosts = pageAdapter.getAllLoadedPosts()
        
        // Encontrar el índice del post en la lista completa
        val indexInAll = allPosts.indexOfFirst { it.id == post.id }
        
        // Pasar posts a PostActivity usando el companion object
        PostActivity.POSTS_TO_SHOW = allPosts
        
        val intent = Intent(this, PostActivity::class.java).apply {
            putExtra(PostActivity.EXTRA_POSITION, if (indexInAll >= 0) indexInAll else 0)
        }
        postActivityLauncher.launch(intent)
    }
    
    override fun onPostLongClick(post: Post, position: Int): Boolean {
        // Long click is handled by the adapter (toggles selection)
        return true
    }
    
    override fun onSelectionChanged() {
        updateBottomNavMenu()
    }
    
    private fun showMainMenu() {
        val popup = android.widget.PopupMenu(this, btnMenu)
        popup.menuInflater.inflate(R.menu.menu_main, popup.menu)
        
        // Show/hide login groups based on login state
        val groupLoggedIn = popup.menu.findItem(R.id.menu_my_profile)?.isVisible ?: false
        popup.menu.setGroupVisible(R.id.groupNotLoggedIn, !prefs.isLoggedIn)
        popup.menu.setGroupVisible(R.id.groupLoggedIn, prefs.isLoggedIn)
        
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                // Login
                R.id.menu_login -> {
                    startActivity(Intent(this, LoginActivity::class.java))
                    true
                }
                
                // My Account
                R.id.menu_my_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    true
                }
                R.id.menu_my_favourites -> {
                    performSearch("fav:${prefs.username}")
                    true
                }
                R.id.menu_my_upvotes -> {
                    performSearch("votedup:${prefs.username}")
                    true
                }
                R.id.menu_my_posts -> {
                    performSearch("user:${prefs.username}")
                    true
                }
                R.id.menu_my_comments -> {
                    // Open comments activity filtered by current user
                    startActivity(Intent(this, CommentsActivity::class.java))
                    true
                }
                R.id.menu_my_dmail -> {
                    startActivity(Intent(this, DmailActivity::class.java))
                    true
                }
                R.id.menu_my_sets -> {
                    val intent = Intent(this, SetsActivity::class.java)
                    intent.putExtra(SetsActivity.EXTRA_CREATOR_NAME, prefs.username)
                    startActivity(intent)
                    true
                }
                R.id.menu_logout -> {
                    prefs.clearCredentials()
                    Toast.makeText(this, R.string.menu_logout, Toast.LENGTH_SHORT).show()
                    true
                }
                
                // Go to
                R.id.menu_go_to_page -> {
                    showGoToPageDialog()
                    true
                }
                R.id.menu_go_to_random -> {
                    goToRandomPost()
                    true
                }
                R.id.menu_go_to_user -> {
                    showGoToUserDialog()
                    true
                }
                
                // Popular
                R.id.menu_popular_day -> {
                    val intent = Intent(this, PopularActivity::class.java)
                    intent.putExtra(PopularActivity.EXTRA_SCALE, PopularActivity.SCALE_DAY)
                    startActivity(intent)
                    true
                }
                R.id.menu_popular_week -> {
                    val intent = Intent(this, PopularActivity::class.java)
                    intent.putExtra(PopularActivity.EXTRA_SCALE, PopularActivity.SCALE_WEEK)
                    startActivity(intent)
                    true
                }
                R.id.menu_popular_month -> {
                    val intent = Intent(this, PopularActivity::class.java)
                    intent.putExtra(PopularActivity.EXTRA_SCALE, PopularActivity.SCALE_MONTH)
                    startActivity(intent)
                    true
                }
                
                // Browse
                R.id.menu_browse_tags -> {
                    startActivity(Intent(this, BrowseTagsActivity::class.java))
                    true
                }
                R.id.menu_browse_pools -> {
                    startActivity(Intent(this, BrowsePoolsActivity::class.java))
                    true
                }
                R.id.menu_browse_artists -> {
                    // Search for artist tags
                    performSearch("*")
                    Toast.makeText(this, "Tip: Use artist:name to search artists", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.menu_browse_comments -> {
                    startActivity(Intent(this, CommentsActivity::class.java))
                    true
                }
                R.id.menu_browse_sets -> {
                    startActivity(Intent(this, SetsActivity::class.java))
                    true
                }
                R.id.menu_browse_wiki -> {
                    Toast.makeText(this, "Browse Wiki (coming soon)", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.menu_browse_blips -> {
                    Toast.makeText(this, "Browse Blips (coming soon)", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.menu_browse_users -> {
                    startActivity(Intent(this, BrowseUsersActivity::class.java))
                    true
                }
                
                // About
                R.id.menu_about_news -> {
                    Toast.makeText(this, "News (coming soon)", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.menu_about_help -> {
                    showHelpDialog()
                    true
                }
                R.id.menu_about_about -> {
                    startActivity(Intent(this, AboutActivity::class.java))
                    true
                }
                R.id.menu_about_updates -> {
                    checkForUpdates()
                    true
                }
                R.id.menu_about_feedback -> {
                    Toast.makeText(this, "Send feedback (coming soon)", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.menu_about_changelog -> {
                    Toast.makeText(this, "Changelog (coming soon)", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.menu_about_licenses -> {
                    Toast.makeText(this, "Licences (coming soon)", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.menu_about_translate -> {
                    Toast.makeText(this, "Translate (coming soon)", Toast.LENGTH_SHORT).show()
                    true
                }
                
                // Downloads
                R.id.menu_downloads -> {
                    Toast.makeText(this, "Downloads (coming soon)", Toast.LENGTH_SHORT).show()
                    true
                }
                
                // Followed tag posts
                R.id.menu_following_tags -> {
                    Toast.makeText(this, "Followed tag posts (coming soon)", Toast.LENGTH_SHORT).show()
                    true
                }
                
                // Donate
                R.id.menu_donate -> {
                    Toast.makeText(this, "Thank you for your support! ❤️", Toast.LENGTH_SHORT).show()
                    true
                }
                
                // Check for Updates (main menu item)
                R.id.menu_check_updates -> {
                    checkForUpdates()
                    true
                }
                
                // Settings
                R.id.menu_settings -> {
                    settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
                    true
                }
                
                else -> false
            }
        }
        popup.show()
    }
    
    private fun showGoToPageDialog() {
        // Cerrar el dropdown del SearchView para evitar que aparezca al escribir
        searchAutoComplete?.dismissDropDown()
        searchView.clearFocus()
        
        val dialogView = layoutInflater.inflate(R.layout.dialog_input, null)
        val inputLayout = dialogView.findViewById<TextInputLayout>(R.id.inputLayout)
        val editText = dialogView.findViewById<TextInputEditText>(R.id.editInput)
        val titleText = dialogView.findViewById<TextView>(R.id.txtTitle)
        
        editText.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        inputLayout.hint = getString(R.string.go_to_page_hint)
        titleText.text = getString(R.string.menu_go_to_page)
        
        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.Theme_E621Client_AlertDialog)
            .setView(dialogView)
            .setPositiveButton(R.string.ok) { _, _ ->
                val pageNum = editText.text.toString().toIntOrNull()
                if (pageNum != null && pageNum > 0) {
                    viewPager.setCurrentItem(pageNum - 1, true)
                    updatePageIndicator(pageNum)
                } else {
                    Toast.makeText(this, R.string.invalid_page, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        
        dialog.show()
    }
    
    private fun showGoToUserDialog() {
        // Cerrar el dropdown del SearchView para evitar que aparezca al escribir
        searchAutoComplete?.dismissDropDown()
        searchView.clearFocus()
        
        val dialogView = layoutInflater.inflate(R.layout.dialog_input, null)
        val inputLayout = dialogView.findViewById<TextInputLayout>(R.id.inputLayout)
        val editText = dialogView.findViewById<TextInputEditText>(R.id.editInput)
        val titleText = dialogView.findViewById<TextView>(R.id.txtTitle)
        
        editText.inputType = android.text.InputType.TYPE_CLASS_TEXT
        inputLayout.hint = getString(R.string.go_to_user_hint)
        titleText.text = getString(R.string.menu_go_to_user)
        
        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.Theme_E621Client_AlertDialog)
            .setView(dialogView)
            .setPositiveButton(R.string.ok) { _, _ ->
                val username = editText.text.toString().trim()
                if (username.isNotEmpty()) {
                    val intent = Intent(this, ProfileActivity::class.java)
                    intent.putExtra("username", username)
                    startActivity(intent)
                } else {
                    Toast.makeText(this, R.string.enter_username, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        
        dialog.show()
    }
    
    private fun goToRandomPost() {
        // Si hay posts cargados, elegir uno aleatorio de ellos para ser más rápido
        val loadedPosts = pageAdapter.getAllLoadedPosts()
        if (loadedPosts.isNotEmpty()) {
            val randomIndex = loadedPosts.indices.random()
            PostActivity.POSTS_TO_SHOW = loadedPosts
            val intent = Intent(this, PostActivity::class.java)
            intent.putExtra(PostActivity.EXTRA_POSITION, randomIndex)
            intent.putExtra(PostActivity.EXTRA_RANDOM, true)
            startActivity(intent)
        } else {
            // Si no hay posts cargados, buscar uno de la API
            Toast.makeText(this, R.string.loading_random, Toast.LENGTH_SHORT).show()
            lifecycleScope.launch {
                try {
                    val response = api.posts.list(tags = "order:random", page = 1, limit = 1)
                    if (response.isSuccessful) {
                        val posts = response.body()?.posts
                        if (!posts.isNullOrEmpty()) {
                            val randomPost = posts[0]
                            PostActivity.POSTS_TO_SHOW = posts
                            val intent = Intent(this@MainActivity, PostActivity::class.java)
                            intent.putExtra(PostActivity.EXTRA_POSITION, 0)
                            intent.putExtra(PostActivity.EXTRA_RANDOM, true)
                            startActivity(intent)
                        } else {
                            Toast.makeText(this@MainActivity, R.string.no_posts_found, Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@MainActivity, R.string.error_loading, Toast.LENGTH_SHORT).show()
                    }
                } catch (e: CloudFlareException) {
                    Toast.makeText(this@MainActivity, R.string.error_cloudflare, Toast.LENGTH_LONG).show()
                } catch (e: ServerDownException) {
                    Toast.makeText(this@MainActivity, R.string.error_server_down, Toast.LENGTH_LONG).show()
                } catch (e: NetworkException) {
                    val message = when (e.type) {
                        NetworkErrorType.TIMEOUT -> getString(R.string.error_timeout)
                        NetworkErrorType.NO_INTERNET -> getString(R.string.error_no_internet)
                        else -> getString(R.string.error_connection)
                    }
                    Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, R.string.error_loading, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun showHelpDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.menu_about_help)
            .setMessage("Search Tips:\n\n" +
                    "• Use tags separated by spaces\n" +
                    "• Use - to exclude tags (e.g., -male)\n" +
                    "• Use order:score for popular posts\n" +
                    "• Use rating:s for safe posts\n" +
                    "• Use fav:username for favorites\n" +
                    "• Swipe left/right to change pages")
            .setPositiveButton(R.string.ok, null)
            .show()
    }
    
    private fun showAboutDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.menu_about_about)
            .setMessage("E621 Client\n\nVersion: ${getString(R.string.app_version)}\n\nAn unofficial client for e621.net")
            .setPositiveButton(R.string.ok, null)
            .show()
    }
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // If in selection mode, clear selection instead of going back
        if (pageAdapter.isInSelectionMode) {
            pageAdapter.clearSelection()
        } else if (searchStack.isNotEmpty()) {
            // Go back to previous search state
            isNavigatingBack = true
            
            val (previousTags, previousPage) = searchStack.pop()
            val tagsChanged = currentTags != previousTags
            currentTags = previousTags
            pageAdapter.setTags(previousTags)
            
            if (tagsChanged) {
                // Tags changed - need to reload adapter, start from page 0
                viewPager.adapter = null
                viewPager.adapter = pageAdapter
                viewPager.setCurrentItem(0, false)
                updatePageIndicator(1)
            } else {
                // Same tags - just navigate to the previous page without reloading
                viewPager.setCurrentItem(previousPage, false)
                updatePageIndicator(previousPage + 1)
            }
            
            // Hide keyboard and clear focus like in performSearch
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(window.decorView.windowToken, 0)
            searchView.setQuery(previousTags, false)
            searchView.clearFocus()
            searchAutoComplete?.dismissDropDown()
            viewPager.requestFocus()
            
            // Reset flag after a short delay to allow all async operations to complete
            viewPager.postDelayed({ isNavigatingBack = false }, 100)
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
    
    /**
     * Check for app updates from GitHub releases
     */
    private fun checkForUpdates() {
        Toast.makeText(this, R.string.update_checking, Toast.LENGTH_SHORT).show()
        
        lifecycleScope.launch {
            val result = updateChecker.checkForUpdate()
            
            result.onSuccess { updateInfo ->
                if (updateInfo != null && updateInfo.isNewer) {
                    // Update available - show dialog
                    showUpdateDialog(updateInfo)
                } else {
                    // No update available
                    Toast.makeText(
                        this@MainActivity, 
                        R.string.update_no_update, 
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }.onFailure { error ->
                Log.e("MainActivity", "Error checking for updates", error)
                Toast.makeText(
                    this@MainActivity, 
                    R.string.update_error, 
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    /**
     * Show update available dialog
     */
    private fun showUpdateDialog(updateInfo: UpdateChecker.UpdateInfo) {
        val message = getString(
            R.string.update_install_message,
            updateInfo.versionName,
            updateChecker.getCurrentVersion()
        )
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.Theme_E621Client_AlertDialog)
            .setTitle(R.string.update_install_title)
            .setMessage(message)
            .setPositiveButton(R.string.update_download) { _, _ ->
                downloadAndInstallUpdate(updateInfo)
            }
            .setNegativeButton(R.string.update_later, null)
            .show()
    }
    
    /**
     * Download and install the update
     */
    private fun downloadAndInstallUpdate(updateInfo: UpdateChecker.UpdateInfo) {
        Toast.makeText(this, R.string.update_downloading, Toast.LENGTH_LONG).show()
        
        updateChecker.downloadUpdate(updateInfo) { file ->
            runOnUiThread {
                if (file != null && file.exists()) {
                    Toast.makeText(this, R.string.update_download_complete, Toast.LENGTH_SHORT).show()
                    updateChecker.installApk(file)
                } else {
                    Toast.makeText(this, R.string.update_download_error, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

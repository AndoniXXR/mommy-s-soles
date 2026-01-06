package com.e621.client.ui.post

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.e621.client.E621Application
import com.e621.client.R
import com.e621.client.data.model.Post
import com.e621.client.util.MediaCacheManager
import com.e621.client.util.NetworkMonitor
import com.github.chrisbanes.photoview.PhotoView
import com.google.android.flexbox.FlexboxLayout
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * ViewPager2 adapter for displaying posts in a swipeable view
 * Based on decompiled e0 (PostPagerAdapter) from original app
 */
class PostViewPagerAdapter(
    private val posts: MutableList<Post>,
    private val onPostInteraction: PostInteractionListener
) : RecyclerView.Adapter<PostViewPagerAdapter.PostViewHolder>() {

    private val prefs = E621Application.instance.userPreferences
    
    // Initialize expand states from preferences
    private var tagsExpanded = prefs.postExpandTags
    private var detailsExpanded = prefs.postExpandTags // Same as tags
    private var descriptionExpanded = prefs.postExpandDescription
    
    // Track active ExoPlayers for cleanup
    private val activePlayers = mutableMapOf<Int, ExoPlayer>()
    
    // Track current visible position to prevent auto-play of off-screen videos
    private var currentVisiblePosition = 0
    
    // Video file extensions
    private val videoExtensions = listOf("webm", "mp4", "mov", "avi", "mkv")

    interface PostInteractionListener {
        fun onTagClicked(tag: String)
        fun onTagLongClicked(tag: String, anchor: View)
        fun onArtistClicked(artist: String)
        fun onUserClicked(username: String)
        fun onParentClicked(parentId: Int)
        fun onChildrenClicked(parentId: Int)
        fun onPoolClicked(poolId: Int)
        fun onSourceClicked(source: String)
        fun onImageTapped()
        fun onImageLongPress(post: Post)
    }

    inner class PostViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val scrollView: ScrollView = itemView.findViewById(R.id.scrollView)
        val imgPreview: PhotoView = itemView.findViewById(R.id.imgPreview)
        val playerView: PlayerView = itemView.findViewById(R.id.playerView)
        val contentFrame: FrameLayout = itemView.findViewById(R.id.contentFrame)
        val progressLoading: ProgressBar = itemView.findViewById(R.id.progressLoading)
        val layoutError: LinearLayout = itemView.findViewById(R.id.layoutError)
        val txtError: TextView = itemView.findViewById(R.id.txtError)
        val btnRetry: Button = itemView.findViewById(R.id.btnRetry)
        
        var currentPlayer: ExoPlayer? = null
        var currentVideoUrl: String? = null
        var currentSpeed: Float = 1.0f
        
        // Track bound position to verify ViewHolder hasn't been recycled
        var boundPosition: Int = RecyclerView.NO_POSITION
        
        // Pending fullscreen runnable to cancel on recycle
        private var pendingFullscreenRunnable: Runnable? = null
        
        fun cancelPendingFullscreen() {
            pendingFullscreenRunnable?.let { playerView.removeCallbacks(it) }
            pendingFullscreenRunnable = null
        }
        
        fun schedulePendingFullscreen(runnable: Runnable, delay: Long) {
            cancelPendingFullscreen()
            pendingFullscreenRunnable = runnable
            playerView.postDelayed(runnable, delay)
        }
        
        // Title card
        val txtArtist: TextView = itemView.findViewById(R.id.txtArtist)
        val txtPosition: TextView = itemView.findViewById(R.id.txtPosition)
        val txtRatingE: TextView = itemView.findViewById(R.id.txtRatingE)
        val txtRatingQ: TextView = itemView.findViewById(R.id.txtRatingQ)
        val txtRatingS: TextView = itemView.findViewById(R.id.txtRatingS)
        val txtPostId: TextView = itemView.findViewById(R.id.txtPostId)
        val txtScoreUp: TextView = itemView.findViewById(R.id.txtScoreUp)
        val txtScoreDown: TextView = itemView.findViewById(R.id.txtScoreDown)
        val txtFavCount: TextView = itemView.findViewById(R.id.txtFavCount)
        val txtResolution: TextView = itemView.findViewById(R.id.txtResolution)
        val txtFileSize: TextView = itemView.findViewById(R.id.txtFileSize)
        val txtFileType: TextView = itemView.findViewById(R.id.txtFileType)
        val txtDate: TextView = itemView.findViewById(R.id.txtDate)
        val layoutSources: LinearLayout = itemView.findViewById(R.id.layoutSources)
        val layoutSourcesList: LinearLayout = itemView.findViewById(R.id.layoutSourcesList)
        val imgSourcesExpand: ImageView = itemView.findViewById(R.id.imgSourcesExpand)
        
        // Relations
        val layoutRelations: LinearLayout = itemView.findViewById(R.id.layoutRelations)
        val txtParent: TextView = itemView.findViewById(R.id.txtParent)
        val txtChildren: TextView = itemView.findViewById(R.id.txtChildren)
        val txtPool: TextView = itemView.findViewById(R.id.txtPool)
        
        // Description
        val layoutDescription: LinearLayout = itemView.findViewById(R.id.layoutDescription)
        val txtDescription: TextView = itemView.findViewById(R.id.txtDescription)
        val imgDescExpand: ImageView = itemView.findViewById(R.id.imgDescExpand)
        
        // Tags section
        val layoutTagsHeader: LinearLayout = itemView.findViewById(R.id.layoutTagsHeader)
        val layoutTags: LinearLayout = itemView.findViewById(R.id.layoutTags)
        val imgTagsExpand: ImageView = itemView.findViewById(R.id.imgTagsExpand)
        
        // Tag categories
        val layoutTagsArtist: LinearLayout = itemView.findViewById(R.id.layoutTagsArtist)
        val chipGroupArtist: FlexboxLayout = itemView.findViewById(R.id.chipGroupArtist)
        val layoutTagsCopyright: LinearLayout = itemView.findViewById(R.id.layoutTagsCopyright)
        val chipGroupCopyright: FlexboxLayout = itemView.findViewById(R.id.chipGroupCopyright)
        val layoutTagsCharacter: LinearLayout = itemView.findViewById(R.id.layoutTagsCharacter)
        val chipGroupCharacter: FlexboxLayout = itemView.findViewById(R.id.chipGroupCharacter)
        val layoutTagsSpecies: LinearLayout = itemView.findViewById(R.id.layoutTagsSpecies)
        val chipGroupSpecies: FlexboxLayout = itemView.findViewById(R.id.chipGroupSpecies)
        val layoutTagsGeneral: LinearLayout = itemView.findViewById(R.id.layoutTagsGeneral)
        val chipGroupGeneral: FlexboxLayout = itemView.findViewById(R.id.chipGroupGeneral)
        val layoutTagsMeta: LinearLayout = itemView.findViewById(R.id.layoutTagsMeta)
        val chipGroupMeta: FlexboxLayout = itemView.findViewById(R.id.chipGroupMeta)
        val layoutTagsLore: LinearLayout = itemView.findViewById(R.id.layoutTagsLore)
        val chipGroupLore: FlexboxLayout = itemView.findViewById(R.id.chipGroupLore)
        val layoutTagsInvalid: LinearLayout = itemView.findViewById(R.id.layoutTagsInvalid)
        val chipGroupInvalid: FlexboxLayout = itemView.findViewById(R.id.chipGroupInvalid)
        
        // Details section
        val layoutDetailsHeader: LinearLayout = itemView.findViewById(R.id.layoutDetailsHeader)
        val layoutDetails: LinearLayout = itemView.findViewById(R.id.layoutDetails)
        val imgDetailsExpand: ImageView = itemView.findViewById(R.id.imgDetailsExpand)
        val txtUploader: TextView = itemView.findViewById(R.id.txtUploader)
        val layoutApprover: LinearLayout = itemView.findViewById(R.id.layoutApprover)
        val txtApprover: TextView = itemView.findViewById(R.id.txtApprover)
        val txtStatus: TextView = itemView.findViewById(R.id.txtStatus)
        val txtRatingFull: TextView = itemView.findViewById(R.id.txtRatingFull)
        
        // Fullscreen
        val contentFrameFullscreen: FrameLayout = itemView.findViewById(R.id.contentFrameFullscreen)
        val layoutLoading: FrameLayout = itemView.findViewById(R.id.layoutLoading)
        
        // Loading indicator at top
        val layoutLoadingIndicator: LinearLayout = itemView.findViewById(R.id.layoutLoadingIndicator)
        val txtLoadingProgress: TextView = itemView.findViewById(R.id.txtLoadingProgress)
        
        private var sourcesExpanded = false
        
        fun bind(post: Post, position: Int) {
            // Cancel any pending fullscreen from previous bind and track new position
            cancelPendingFullscreen()
            boundPosition = position
            
            // Position indicator
            txtPosition.text = "${position + 1}/${posts.size}"
            
            // Load media (image or video)
            loadMedia(post)
            
            // Artist
            val artists = post.tags.artist?.filter { it != "conditional_dnp" && it != "unknown_artist" }
            txtArtist.text = artists?.joinToString(", ") { it.replace("_", " ") } ?: "Unknown"
            txtArtist.setOnClickListener {
                artists?.firstOrNull()?.let { onPostInteraction.onArtistClicked(it) }
            }
            
            // Rating
            txtRatingE.visibility = if (post.rating == "e") View.VISIBLE else View.GONE
            txtRatingQ.visibility = if (post.rating == "q") View.VISIBLE else View.GONE
            txtRatingS.visibility = if (post.rating == "s") View.VISIBLE else View.GONE
            
            // Post ID
            txtPostId.text = "#${post.id}"
            
            // Scores - hide if preference enabled
            if (prefs.postHideScore) {
                txtScoreUp.visibility = View.GONE
                txtScoreDown.visibility = View.GONE
                txtFavCount.visibility = View.GONE
            } else {
                txtScoreUp.visibility = View.VISIBLE
                txtScoreDown.visibility = View.VISIBLE
                txtFavCount.visibility = View.VISIBLE
                txtScoreUp.text = "${post.score.up}"
                txtScoreDown.text = "${post.score.down}"
                txtFavCount.text = "${post.favCount}"
            }
            
            // File info
            txtResolution.text = "${post.file.width}x${post.file.height}"
            txtFileSize.text = formatFileSize(post.file.size ?: 0L)
            txtFileType.text = post.file.ext?.uppercase() ?: ""
            
            // Date
            txtDate.text = formatDate(post.createdAt)
            
            // Sources
            if (!post.sources.isNullOrEmpty()) {
                layoutSources.visibility = View.VISIBLE
                setupSources(post)
            } else {
                layoutSources.visibility = View.GONE
            }
            
            // Relations (parent, children, pool)
            setupRelations(post)
            
            // Description
            if (!post.description.isNullOrBlank()) {
                layoutDescription.visibility = View.VISIBLE
                txtDescription.text = post.description
                txtDescription.visibility = if (descriptionExpanded) View.VISIBLE else View.GONE
                updateExpandIcon(imgDescExpand, descriptionExpanded)
                
                layoutDescription.setOnClickListener {
                    descriptionExpanded = !descriptionExpanded
                    txtDescription.visibility = if (descriptionExpanded) View.VISIBLE else View.GONE
                    updateExpandIcon(imgDescExpand, descriptionExpanded)
                }
            } else {
                layoutDescription.visibility = View.GONE
                txtDescription.visibility = View.GONE
            }
            
            // Tags
            setupTags(post)
            
            // Details
            setupDetails(post)
            
            // Expand/collapse listeners
            layoutTagsHeader.setOnClickListener {
                tagsExpanded = !tagsExpanded
                layoutTags.visibility = if (tagsExpanded) View.VISIBLE else View.GONE
                updateExpandIcon(imgTagsExpand, tagsExpanded)
            }
            layoutTags.visibility = if (tagsExpanded) View.VISIBLE else View.GONE
            updateExpandIcon(imgTagsExpand, tagsExpanded)
            
            layoutDetailsHeader.setOnClickListener {
                detailsExpanded = !detailsExpanded
                layoutDetails.visibility = if (detailsExpanded) View.VISIBLE else View.GONE
                updateExpandIcon(imgDetailsExpand, detailsExpanded)
            }
            layoutDetails.visibility = if (detailsExpanded) View.VISIBLE else View.GONE
            updateExpandIcon(imgDetailsExpand, detailsExpanded)
            
            // Setup double tap detector for fullscreen
            val context = itemView.context
            val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    openFullscreen(post)
                    return true
                }
                
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    onPostInteraction.onImageTapped()
                    return true
                }
            })
            
            // Image interaction with double tap support
            imgPreview.setOnDoubleTapListener(object : GestureDetector.OnDoubleTapListener {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    openFullscreen(post)
                    return true
                }
                
                override fun onDoubleTapEvent(e: MotionEvent): Boolean = false
                
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    onPostInteraction.onImageTapped()
                    return true
                }
            })
            
            imgPreview.setOnLongClickListener { 
                onPostInteraction.onImageLongPress(post)
                true
            }
            
            // Video interaction with double tap support
            playerView.setOnTouchListener { _, event ->
                gestureDetector.onTouchEvent(event)
                false
            }
        }
        
        private fun openFullscreen(post: Post) {
            val context = itemView.context
            val fileExt = post.file.ext?.lowercase() ?: ""
            val isVideo = videoExtensions.contains(fileExt)
            val url = post.file.url ?: return
            
            if (isVideo) {
                currentPlayer?.let { player ->
                    player.pause()
                    FullscreenVideoActivity.start(
                        context,
                        url,
                        player.currentPosition,
                        currentSpeed
                    )
                }
            } else {
                FullscreenImageActivity.start(context, url)
            }
        }
        
        private fun loadMedia(post: Post) {
            progressLoading.visibility = View.VISIBLE
            layoutError.visibility = View.GONE
            
            // Show loading indicator with file size
            showLoadingIndicator(post)
            
            // Check for post error states (like decompiled app e0.java lines 974-978)
            val context = itemView.context
            
            // Check if post is deleted
            if (post.flags.deleted == true) {
                hideLoadingIndicator()
                showPostError(context.getString(R.string.post_error_deleted))
                return
            }
            
            // Check if URL is available (null URL means login required for some posts)
            val fileUrl = post.file.url
            if (fileUrl.isNullOrEmpty() || !fileUrl.startsWith("http")) {
                hideLoadingIndicator()
                // Check if user is logged in
                if (!prefs.isLoggedIn) {
                    showPostError(context.getString(R.string.post_error_login_required))
                } else {
                    showPostError(context.getString(R.string.post_no_image))
                }
                return
            }
            
            val fileExt = post.file.ext?.lowercase() ?: ""
            val isVideo = videoExtensions.contains(fileExt)
            
            if (isVideo) {
                loadVideo(post)
            } else {
                loadImage(post)
            }
        }
        
        @androidx.annotation.OptIn(UnstableApi::class)
        private fun loadVideo(post: Post) {
            // Get video URL based on quality and format preferences
            // If on mobile data with poor connection, override to lower quality
            val userVideoQuality = prefs.postDefaultVideoQuality.toIntOrNull() ?: 2  // Default 480p
            val videoQuality = if (NetworkMonitor.isMetered() && 
                NetworkMonitor.getConnectionQuality() <= NetworkMonitor.ConnectionQuality.MODERATE) {
                // Force 480p on slow mobile connections
                maxOf(userVideoQuality, 2)
            } else {
                userVideoQuality
            }
            val videoFormat = prefs.postDefaultVideoFormat.toIntOrNull() ?: 2    // Default auto
            val videoUrl = post.getVideoUrl(videoQuality, videoFormat) ?: post.file.url
            
            if (videoUrl.isNullOrEmpty()) {
                progressLoading.visibility = View.GONE
                layoutError.visibility = View.VISIBLE
                txtError.text = itemView.context.getString(R.string.post_no_image)
                return
            }
            
            // Hide image, show video player
            imgPreview.visibility = View.GONE
            playerView.visibility = View.VISIBLE
            
            // Calculate and set PlayerView height based on video aspect ratio
            val videoWidth = post.file.width
            val videoHeight = post.file.height
            if (videoWidth > 0 && videoHeight > 0) {
                val screenWidth = itemView.context.resources.displayMetrics.widthPixels
                val aspectRatio = videoHeight.toFloat() / videoWidth.toFloat()
                val calculatedHeight = (screenWidth * aspectRatio).toInt()
                playerView.layoutParams.height = calculatedHeight
                playerView.requestLayout()
            }
            
            // Release previous player if exists
            currentPlayer?.release()
            currentPlayer = null
            
            // Release player from activePlayers map using boundPosition
            val position = boundPosition
            if (position != RecyclerView.NO_POSITION) {
                activePlayers[position]?.release()
                activePlayers.remove(position)
            }
            
            // Limit maximum active players to prevent resource exhaustion
            // Reduced to 1 to be more aggressive about releasing resources
            cleanupExcessPlayers(maxActivePlayers = 1)
            
            val context = itemView.context
            
            // Show loading indicator with file size
            val fileSize = post.file.size ?: 0L
            showStreamingIndicator(fileSize)
            
            // Create ExoPlayer with CacheDataSource for streaming with progressive cache
            // This is the T2 approach - video plays while downloading to cache
            val cacheDataSourceFactory = MediaCacheManager.createCacheDataSourceFactory(context)
            val mediaSourceFactory = DefaultMediaSourceFactory(cacheDataSourceFactory)
            
            // Configure buffer sizes based on network quality
            // Larger buffers for slow connections to prevent rebuffering
            val loadControl = when (NetworkMonitor.getConnectionQuality()) {
                NetworkMonitor.ConnectionQuality.EXCELLENT -> {
                    DefaultLoadControl.Builder()
                        .setBufferDurationsMs(
                            DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                            DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                            DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                            DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
                        )
                        .build()
                }
                NetworkMonitor.ConnectionQuality.GOOD -> {
                    DefaultLoadControl.Builder()
                        .setBufferDurationsMs(
                            30_000,  // Min buffer 30s
                            60_000,  // Max buffer 60s
                            3_000,   // Buffer for playback 3s
                            5_000    // Buffer after rebuffer 5s
                        )
                        .build()
                }
                else -> {
                    // Moderate, Poor, or Unknown - use larger buffers
                    DefaultLoadControl.Builder()
                        .setBufferDurationsMs(
                            50_000,  // Min buffer 50s
                            120_000, // Max buffer 2 min
                            5_000,   // Buffer for playback 5s
                            8_000    // Buffer after rebuffer 8s
                        )
                        .build()
                }
            }
            
            val player = ExoPlayer.Builder(context)
                .setMediaSourceFactory(mediaSourceFactory)
                .setLoadControl(loadControl)
                .build()
            
            // Configure audio attributes for media playback with audio focus
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()
            player.setAudioAttributes(audioAttributes, true)
            
            playerView.player = player
            currentPlayer = player
            currentVideoUrl = videoUrl
            
            // Store in activePlayers map only with valid position
            if (position != RecyclerView.NO_POSITION) {
                activePlayers[position] = player
            }
            
            // Set media item from URL - ExoPlayer will stream and cache progressively
            val mediaItem = MediaItem.fromUri(Uri.parse(videoUrl))
            player.setMediaItem(mediaItem)
            
            // Configure player based on preferences
            player.repeatMode = Player.REPEAT_MODE_ALL
            
            // Apply autoplay preference - only auto-play if preference enabled AND visible page
            val shouldAutoplay = prefs.postAutoplayVideos && (position == currentVisiblePosition)
            player.playWhenReady = shouldAutoplay
            
            // Apply mute preference
            if (prefs.postMuteVideos) {
                player.volume = 0f
            }
            
            player.setPlaybackSpeed(currentSpeed)
            
            // Setup controls after view is laid out to ensure they're found
            playerView.post {
                setupVideoControls(context, player, videoUrl, position)
            }
            
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> progressLoading.visibility = View.VISIBLE
                        Player.STATE_READY -> {
                            progressLoading.visibility = View.GONE
                            hideLoadingIndicator()
                        }
                        Player.STATE_ENDED -> { /* Loop handled by REPEAT_MODE_ALL */ }
                        Player.STATE_IDLE -> { }
                    }
                }
                
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    // Verify ViewHolder hasn't been recycled
                    if (boundPosition == position) {
                        hideLoadingIndicator()
                        progressLoading.visibility = View.GONE
                        layoutError.visibility = View.VISIBLE
                        txtError.text = error.message ?: context.getString(R.string.post_error_loading)
                    }
                }
            })
            
            player.prepare()
            
            btnRetry.setOnClickListener {
                loadVideo(post)
            }
        }
        
        /**
         * Show streaming indicator with file size
         */
        private fun showStreamingIndicator(fileSize: Long) {
            val sizeStr = formatFileSize(fileSize)
            txtLoadingProgress.text = itemView.context.getString(R.string.loading_size, sizeStr)
            layoutLoadingIndicator.visibility = View.VISIBLE
            progressLoading.visibility = View.VISIBLE
        }
        
        private fun showSpeedSelector(context: Context, player: ExoPlayer, speedButton: TextView) {
            SpeedSelectorDialog(context, currentSpeed) { speed ->
                currentSpeed = speed
                player.setPlaybackSpeed(speed)
                speedButton.text = formatSpeed(speed)
            }.show()
        }
        
        private fun setupVideoControls(context: Context, player: ExoPlayer, videoUrl: String, boundPosition: Int) {
            // Setup speed control - YouTube style bottom sheet
            val speedButton = playerView.findViewById<TextView>(R.id.exo_speed)
            if (speedButton != null) {
                speedButton.text = formatSpeed(currentSpeed)
                speedButton.setOnClickListener { 
                    showSpeedSelector(context, player, speedButton)
                }
            }
            
            // Setup fullscreen button
            val fullscreenButton = playerView.findViewById<ImageButton>(R.id.exo_fullscreen)
            if (fullscreenButton != null) {
                fullscreenButton.visibility = View.VISIBLE
                fullscreenButton.setOnClickListener {
                    openFullscreenVideo(context)
                }
            }
            
            // Auto-open fullscreen if preference enabled
            // Only open if this is the currently visible position to prevent wrong video
            if (prefs.postFullscreenVideos && boundPosition == currentVisiblePosition) {
                // Capture the URL and position NOW, not after the delay
                val capturedUrl = videoUrl
                val capturedPosition = boundPosition
                val capturedPlayer = player
                val capturedSpeed = currentSpeed
                
                // Small delay to ensure video is loaded
                // Use schedulePendingFullscreen so it can be cancelled if ViewHolder is recycled
                schedulePendingFullscreen(Runnable {
                    // Double-check that this ViewHolder is still showing the same position
                    // and is still the visible page
                    if (adapterPosition == capturedPosition && 
                        capturedPosition == currentVisiblePosition &&
                        currentVideoUrl == capturedUrl) {
                        capturedPlayer.pause()
                        FullscreenVideoActivity.start(
                            context,
                            capturedUrl,
                            capturedPlayer.currentPosition,
                            capturedSpeed
                        )
                    }
                }, 300)
            }
        }
        
        private fun openFullscreenVideo(context: Context) {
            val url = currentVideoUrl
            if (url != null && currentPlayer != null) {
                currentPlayer?.pause()
                FullscreenVideoActivity.start(
                    context,
                    url,
                    currentPlayer?.currentPosition ?: 0L,
                    currentSpeed
                )
            }
        }
        
        private fun formatSpeed(speed: Float): String {
            return if (speed == 1.0f) {
                "1x"
            } else if (speed == speed.toInt().toFloat()) {
                "${speed.toInt()}x"
            } else {
                "${speed}x"
            }
        }
        
        private fun loadImage(post: Post) {
            // Show image, hide video player
            imgPreview.visibility = View.VISIBLE
            playerView.visibility = View.GONE
            
            // Release any video player
            currentPlayer?.release()
            currentPlayer = null
            currentVideoUrl = null
            
            progressLoading.visibility = View.VISIBLE
            layoutError.visibility = View.GONE
            
            // Determine image quality based on preferences
            val imageUrl = when {
                prefs.postDataSaver -> post.preview?.url ?: post.sample?.url ?: post.file.url
                prefs.postLoadHQ -> post.file.url ?: post.sample?.url
                else -> post.getDisplayUrl(prefs.postQuality)
            }
            
            if (imageUrl.isNullOrEmpty()) {
                progressLoading.visibility = View.GONE
                hideLoadingIndicator()
                layoutError.visibility = View.VISIBLE
                txtError.text = itemView.context.getString(R.string.post_no_image)
                return
            }
            
            Glide.with(itemView.context)
                .load(imageUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .transition(DrawableTransitionOptions.withCrossFade())
                .placeholder(R.drawable.ic_image_placeholder)
                .error(R.drawable.ic_image_error)
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable>,
                        isFirstResource: Boolean
                    ): Boolean {
                        progressLoading.visibility = View.GONE
                        hideLoadingIndicator()
                        layoutError.visibility = View.VISIBLE
                        txtError.text = itemView.context.getString(R.string.post_error_loading)
                        return false // Let Glide handle the error drawable
                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        model: Any,
                        target: Target<Drawable>?,
                        dataSource: DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        progressLoading.visibility = View.GONE
                        hideLoadingIndicator()
                        return false // Let Glide handle setting the drawable
                    }
                })
                .into(imgPreview)
            
            btnRetry.setOnClickListener {
                loadImage(post)
            }
        }
        
        fun releasePlayer() {
            currentPlayer?.release()
            currentPlayer = null
            currentVideoUrl = null
            playerView.player = null
            
            // Also remove from activePlayers map
            val position = boundPosition
            if (position != RecyclerView.NO_POSITION) {
                activePlayers.remove(position)
            }
        }
        
        /**
         * Show error state for posts that cannot be loaded
         * Based on decompiled app's l() method in e0.java
         */
        private fun showPostError(message: String) {
            progressLoading.visibility = View.GONE
            hideLoadingIndicator()
            layoutError.visibility = View.VISIBLE
            txtError.text = message
            
            // Hide media views
            imgPreview.visibility = View.GONE
            playerView.visibility = View.GONE
            
            // Release any existing player
            currentPlayer?.release()
            currentPlayer = null
            currentVideoUrl = null
        }
        
        /**
         * Show loading indicator at top with file size
         */
        private fun showLoadingIndicator(post: Post) {
            val fileSize = post.file.size ?: 0L
            val formattedSize = formatFileSize(fileSize)
            txtLoadingProgress.text = itemView.context.getString(R.string.loading_size, formattedSize)
            layoutLoadingIndicator.visibility = View.VISIBLE
        }
        
        /**
         * Hide loading indicator at top
         */
        private fun hideLoadingIndicator() {
            layoutLoadingIndicator.visibility = View.GONE
        }
        
        private fun setupSources(post: Post) {
            imgSourcesExpand.setOnClickListener {
                sourcesExpanded = !sourcesExpanded
                layoutSourcesList.visibility = if (sourcesExpanded) View.VISIBLE else View.GONE
                updateExpandIcon(imgSourcesExpand, sourcesExpanded)
            }
            
            layoutSources.setOnClickListener {
                sourcesExpanded = !sourcesExpanded
                layoutSourcesList.visibility = if (sourcesExpanded) View.VISIBLE else View.GONE
                updateExpandIcon(imgSourcesExpand, sourcesExpanded)
            }
            
            layoutSourcesList.removeAllViews()
            post.sources?.forEach { source ->
                val textView = TextView(itemView.context).apply {
                    text = source
                    setTextColor(itemView.context.getColor(R.color.accent))
                    textSize = 12f
                    setPadding(0, 4, 0, 4)
                    setOnClickListener { onPostInteraction.onSourceClicked(source) }
                }
                layoutSourcesList.addView(textView)
            }
        }
        
        private fun setupRelations(post: Post) {
            val hasParent = post.relationships.parentId != null
            val hasChildren = post.relationships.hasChildren == true
            val childrenList = post.relationships.children ?: emptyList()
            val hasPools = post.pools?.isNotEmpty() == true
            
            if (hasParent || hasChildren || hasPools) {
                layoutRelations.visibility = View.VISIBLE
                
                // Parent: "Padre: ID"
                if (hasParent) {
                    txtParent.visibility = View.VISIBLE
                    val parentIdStr = post.relationships.parentId.toString()
                    val fullText = itemView.context.getString(R.string.post_parent, parentIdStr)
                    val sb = android.text.SpannableStringBuilder(fullText)
                    
                    val start = fullText.indexOf(parentIdStr)
                    if (start != -1) {
                        val end = start + parentIdStr.length
                        // Label Red
                        sb.setSpan(android.text.style.ForegroundColorSpan(android.graphics.Color.parseColor("#FF5555")), 0, start, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        // ID Blue (Accent) like children
                        sb.setSpan(android.text.style.ForegroundColorSpan(itemView.context.getColor(R.color.accent)), start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    } else {
                        // Fallback
                        sb.setSpan(android.text.style.ForegroundColorSpan(android.graphics.Color.parseColor("#FF5555")), 0, fullText.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    
                    txtParent.text = sb
                    
                    // Add visual feedback (Ripple)
                    val outValue = android.util.TypedValue()
                    itemView.context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                    txtParent.setBackgroundResource(outValue.resourceId)
                    txtParent.isClickable = true
                    txtParent.isFocusable = true
                    
                    txtParent.setOnClickListener {
                        post.relationships.parentId?.let { onPostInteraction.onParentClicked(it) }
                    }
                } else {
                    txtParent.visibility = View.GONE
                }
                
                // Children: Show "Hijos" indicator when post has children
                if (hasChildren) {
                    txtChildren.visibility = View.VISIBLE
                    
                    // Show children count or indicator - the full list will be loaded on click
                    val childCount = childrenList.size
                    val displayText = if (childCount > 0) {
                        if (childCount == 1) "Hijo: ${childrenList[0]}" else "Hijos: $childCount posts"
                    } else {
                        "Hijos: Ver todos"  // has_children is true but list is empty/truncated
                    }
                    
                    txtChildren.text = displayText
                    txtChildren.setTextColor(itemView.context.getColor(R.color.accent))
                    
                    // Add visual feedback (Ripple)
                    val outValue = android.util.TypedValue()
                    itemView.context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                    txtChildren.setBackgroundResource(outValue.resourceId)
                    txtChildren.isClickable = true
                    txtChildren.isFocusable = true
                    
                    // Single click handler - will load full list and show dialog
                    txtChildren.setOnClickListener {
                        onPostInteraction.onChildrenClicked(post.id)
                    }
                } else {
                    txtChildren.visibility = View.GONE
                }
                
                // Pool: "Lista: ID" or "Listas: ID1, ID2"
                if (hasPools) {
                    txtPool.visibility = View.VISIBLE
                    val poolIds = post.pools!!.joinToString(", ")
                    val pluralRes = if (post.pools!!.size == 1) R.string.post_pool else R.string.post_pools_plural
                    txtPool.text = itemView.context.getString(pluralRes, poolIds)
                    
                    // Add visual feedback (Ripple)
                    val outValue = android.util.TypedValue()
                    itemView.context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                    txtPool.setBackgroundResource(outValue.resourceId)
                    txtPool.isClickable = true
                    txtPool.isFocusable = true
                    
                    txtPool.setOnClickListener {
                        post.pools?.firstOrNull()?.let { onPostInteraction.onPoolClicked(it) }
                    }
                } else {
                    txtPool.visibility = View.GONE
                }
            } else {
                layoutRelations.visibility = View.GONE
            }
        }
        
        private fun setupTags(post: Post) {
            setupTagCategory(post.tags.artist, chipGroupArtist, layoutTagsArtist, R.color.tag_artist)
            setupTagCategory(post.tags.copyright, chipGroupCopyright, layoutTagsCopyright, R.color.tag_copyright)
            setupTagCategory(post.tags.character, chipGroupCharacter, layoutTagsCharacter, R.color.tag_character)
            setupTagCategory(post.tags.species, chipGroupSpecies, layoutTagsSpecies, R.color.tag_species)
            setupTagCategory(post.tags.general, chipGroupGeneral, layoutTagsGeneral, R.color.tag_general)
            setupTagCategory(post.tags.meta, chipGroupMeta, layoutTagsMeta, R.color.tag_meta)
            setupTagCategory(post.tags.lore, chipGroupLore, layoutTagsLore, R.color.tag_lore)
            setupTagCategory(post.tags.invalid, chipGroupInvalid, layoutTagsInvalid, R.color.tag_invalid)
        }
        
        private fun setupTagCategory(tags: List<String>?, flexbox: FlexboxLayout, layout: LinearLayout, colorRes: Int) {
            flexbox.removeAllViews()
            
            if (tags.isNullOrEmpty()) {
                layout.visibility = View.GONE
                return
            }
            
            layout.visibility = View.VISIBLE
            
            tags.forEach { tag ->
                val textView = TextView(itemView.context).apply {
                    text = tag.replace("_", " ")
                    setTextColor(itemView.context.getColor(colorRes))
                    textSize = 14f
                    setPadding(0, 6, 24, 6)
                    
                    // PressableTextView effect - dim on press
                    isClickable = true
                    isFocusable = true
                    setOnTouchListener { v, event ->
                        when (event.action) {
                            android.view.MotionEvent.ACTION_DOWN -> alpha = 0.5f
                            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> alpha = 1.0f
                        }
                        false
                    }
                    
                    setOnClickListener { onPostInteraction.onTagLongClicked(tag, this) }
                }
                flexbox.addView(textView)
            }
        }
        
        private fun setupDetails(post: Post) {
            // Uploader
            txtUploader.text = post.uploaderId?.toString() ?: "Unknown"
            txtUploader.setOnClickListener {
                // TODO: get username from ID
            }
            
            // Approver
            if (post.approverId != null) {
                layoutApprover.visibility = View.VISIBLE
                txtApprover.text = post.approverId.toString()
            } else {
                layoutApprover.visibility = View.GONE
            }
            
            // Status
            val statusFlags = mutableListOf<String>()
            if (post.flags.pending == true) statusFlags.add("Pending")
            if (post.flags.flagged == true) statusFlags.add("Flagged")
            if (post.flags.deleted == true) statusFlags.add("Deleted")
            if (statusFlags.isEmpty()) statusFlags.add("Active")
            txtStatus.text = statusFlags.joinToString(", ")
            
            // Rating full
            val context = itemView.context
            when (post.rating) {
                "e" -> {
                    txtRatingFull.text = context.getString(R.string.rating_explicit)
                    txtRatingFull.setTextColor(context.getColor(R.color.rating_explicit))
                }
                "q" -> {
                    txtRatingFull.text = context.getString(R.string.rating_questionable)
                    txtRatingFull.setTextColor(context.getColor(R.color.rating_questionable))
                }
                "s" -> {
                    txtRatingFull.text = context.getString(R.string.rating_safe)
                    txtRatingFull.setTextColor(context.getColor(R.color.rating_safe))
                }
            }
        }
        
        private fun updateExpandIcon(imageView: ImageView, expanded: Boolean) {
            imageView.setImageResource(
                if (expanded) R.drawable.ic_expand_less
                else R.drawable.ic_expand_more
            )
        }
        
        private fun formatFileSize(bytes: Long): String {
            return when {
                bytes >= 1_000_000 -> String.format("%.1f MB", bytes / 1_000_000.0)
                bytes >= 1_000 -> String.format("%.1f KB", bytes / 1_000.0)
                else -> "$bytes B"
            }
        }
        
        private fun formatDate(dateString: String?): String {
            if (dateString == null) return ""
            return try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
                val outputFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
                val date = inputFormat.parse(dateString)
                date?.let { outputFormat.format(it) } ?: dateString
            } catch (e: Exception) {
                dateString.substringBefore("T")
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_post_page, parent, false)
        return PostViewHolder(view)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        holder.bind(posts[position], position)
    }
    
    override fun onViewRecycled(holder: PostViewHolder) {
        super.onViewRecycled(holder)
        holder.releasePlayer()
    }

    override fun getItemCount(): Int = posts.size
    
    fun getPost(position: Int): Post? = posts.getOrNull(position)
    
    fun releaseAllPlayers() {
        activePlayers.values.forEach { it.release() }
        activePlayers.clear()
    }
    
    /**
     * Force refresh the current item to recreate video player if needed
     * Called when activity resumes after players were released in onStop
     */
    fun refreshCurrentItem(position: Int) {
        currentVisiblePosition = position
        // Notify the adapter to rebind the current item, which will recreate the player
        notifyItemChanged(position)
    }
    
    /**
     * Cleanup excess players to prevent resource exhaustion
     * Keeps only the closest players to currentVisiblePosition
     */
    private fun cleanupExcessPlayers(maxActivePlayers: Int) {
        if (activePlayers.size <= maxActivePlayers) return
        
        // Sort positions by distance from current visible position
        val sortedPositions = activePlayers.keys.sortedBy { kotlin.math.abs(it - currentVisiblePosition) }
        
        // Release players that are furthest from current position
        val positionsToRemove = sortedPositions.drop(maxActivePlayers)
        positionsToRemove.forEach { pos ->
            activePlayers[pos]?.release()
            activePlayers.remove(pos)
        }
    }
    
    fun pauseAllPlayers() {
        activePlayers.values.forEach { 
            it.playWhenReady = false
            it.pause() 
        }
    }
    
    fun resumePlayer(position: Int) {
        // Update the visible position
        currentVisiblePosition = position
        
        // Pause all other players first
        activePlayers.forEach { (pos, player) ->
            if (pos != position) {
                try {
                    player.playWhenReady = false
                    player.pause()
                } catch (e: Exception) {
                    // Player may already be released, remove from map
                    activePlayers.remove(pos)
                }
            }
        }
        
        // Resume the current player only if autoplay is enabled
        if (prefs.postAutoplayVideos) {
            activePlayers[position]?.let { player ->
                try {
                    player.playWhenReady = true
                    player.play()
                } catch (e: Exception) {
                    // Player may be in invalid state, remove from map
                    activePlayers.remove(position)
                }
            }
        }
    }
    
    fun setCurrentVisiblePosition(position: Int) {
        currentVisiblePosition = position
    }
    
    fun updatePost(position: Int, post: Post) {
        if (position in posts.indices) {
            posts[position] = post
            notifyItemChanged(position)
        }
    }
    
    fun addPosts(newPosts: List<Post>) {
        val startPos = posts.size
        posts.addAll(newPosts)
        notifyItemRangeInserted(startPos, newPosts.size)
    }
}

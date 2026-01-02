package com.e621.client.ui.post

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.e621.client.E621Application
import com.e621.client.R

/**
 * Fullscreen video player activity
 */
class FullscreenVideoActivity : AppCompatActivity() {

    private lateinit var playerView: PlayerView
    private var player: ExoPlayer? = null
    
    private val prefs by lazy { E621Application.instance.userPreferences }
    
    private var videoUrl: String? = null
    private var currentPosition: Long = 0
    private var currentSpeed: Float = 1.0f
    
    companion object {
        private const val TAG = "FullscreenVideo"
        private const val EXTRA_VIDEO_URL = "video_url"
        private const val EXTRA_POSITION = "position"
        private const val EXTRA_SPEED = "speed"
        
        fun start(context: Context, videoUrl: String, position: Long, speed: Float) {
            Log.d(TAG, "Starting fullscreen with URL: $videoUrl, position: $position, speed: $speed")
            val intent = Intent(context, FullscreenVideoActivity::class.java).apply {
                putExtra(EXTRA_VIDEO_URL, videoUrl)
                putExtra(EXTRA_POSITION, position)
                putExtra(EXTRA_SPEED, speed)
                // Make sure we start fresh
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(intent)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fullscreen_video)
        
        // Hide system bars for fullscreen
        hideSystemBars()
        
        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // Force landscape if preference enabled
        if (prefs.postLandscapeVideos) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        
        videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL)
        currentPosition = intent.getLongExtra(EXTRA_POSITION, 0)
        currentSpeed = intent.getFloatExtra(EXTRA_SPEED, 1.0f)
        
        Log.d(TAG, "onCreate - URL: $videoUrl, position: $currentPosition")
        
        playerView = findViewById(R.id.playerView)
        
        // Hide controls if preference disabled
        if (!prefs.postControlsFullscreen) {
            playerView.useController = false
        }
        
        initializePlayer()
        setupControls()
        setupGestures()
    }
    
    private fun setupGestures() {
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                finish()
                return true
            }
        })

        playerView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            // Return false to allow PlayerView to handle controls toggling
            false
        }
    }
    
    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
    
    private fun initializePlayer() {
        val url = videoUrl
        if (url.isNullOrEmpty()) {
            Log.e(TAG, "Video URL is null or empty")
            finish()
            return
        }
        
        // Release any existing player first
        releasePlayer()
        
        Log.d(TAG, "Creating new ExoPlayer for: $url")
        
        player = ExoPlayer.Builder(this).build().also { exoPlayer ->
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()
            exoPlayer.setAudioAttributes(audioAttributes, true)
            
            // Create media item from URI
            val mediaItem = MediaItem.fromUri(Uri.parse(url))
            exoPlayer.setMediaItem(mediaItem)
            
            exoPlayer.repeatMode = Player.REPEAT_MODE_ALL
            exoPlayer.setPlaybackSpeed(currentSpeed)
            
            // Add listener for debugging
            exoPlayer.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    val stateStr = when (playbackState) {
                        Player.STATE_IDLE -> "IDLE"
                        Player.STATE_BUFFERING -> "BUFFERING"
                        Player.STATE_READY -> "READY"
                        Player.STATE_ENDED -> "ENDED"
                        else -> "UNKNOWN"
                    }
                    Log.d(TAG, "Playback state changed: $stateStr")
                }
            })
            
            // Attach player to view BEFORE preparing
            playerView.player = exoPlayer
            
            // Prepare and start
            exoPlayer.prepare()
            exoPlayer.seekTo(currentPosition)
            exoPlayer.playWhenReady = true
        }
    }
    
    private fun releasePlayer() {
        player?.let {
            it.stop()
            it.release()
        }
        player = null
        playerView.player = null
    }
    
    private fun setupControls() {
        // Speed button - YouTube style
        val speedButton = playerView.findViewById<TextView>(R.id.exo_speed)
        speedButton?.let { btn ->
            btn.text = formatSpeed(currentSpeed)
            btn.setOnClickListener { 
                SpeedSelectorDialog(this, currentSpeed) { speed ->
                    currentSpeed = speed
                    player?.setPlaybackSpeed(speed)
                    btn.text = formatSpeed(speed)
                }.show()
            }
        }
        
        // Exit fullscreen button
        val fullscreenButton = playerView.findViewById<ImageButton>(R.id.exo_fullscreen)
        fullscreenButton?.let { btn ->
            btn.setImageResource(R.drawable.ic_fullscreen_exit)
            btn.setOnClickListener { finish() }
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
    
    override fun onStart() {
        super.onStart()
        // Re-initialize if player was released
        if (player == null && !videoUrl.isNullOrEmpty()) {
            initializePlayer()
        }
    }
    
    override fun onPause() {
        super.onPause()
        player?.pause()
    }
    
    override fun onResume() {
        super.onResume()
        player?.playWhenReady = true
    }
    
    override fun onStop() {
        super.onStop()
        releasePlayer()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
    }
}

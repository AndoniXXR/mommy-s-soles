package com.e621.client.ui.post

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.bumptech.glide.Glide
import com.e621.client.R
import com.github.chrisbanes.photoview.PhotoView

/**
 * Fullscreen image/GIF viewer activity
 */
class FullscreenImageActivity : AppCompatActivity() {

    private lateinit var photoView: PhotoView
    private lateinit var gestureDetector: GestureDetector
    
    private var imageUrl: String? = null
    
    companion object {
        private const val EXTRA_IMAGE_URL = "image_url"
        
        fun start(context: Context, imageUrl: String) {
            val intent = Intent(context, FullscreenImageActivity::class.java).apply {
                putExtra(EXTRA_IMAGE_URL, imageUrl)
            }
            context.startActivity(intent)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fullscreen_image)
        
        // Hide system bars for fullscreen
        hideSystemBars()
        
        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        imageUrl = intent.getStringExtra(EXTRA_IMAGE_URL)
        
        photoView = findViewById(R.id.photoView)
        
        loadImage()
        setupGestures()
    }
    
    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
    
    private fun loadImage() {
        val url = imageUrl ?: return
        
        Glide.with(this)
            .load(url)
            .into(photoView)
    }
    
    private fun setupGestures() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                // Double tap to exit fullscreen
                finish()
                return true
            }
            
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                // Toggle system bars visibility on single tap
                toggleSystemBars()
                return true
            }
        })
        
        photoView.setOnDoubleTapListener(object : GestureDetector.OnDoubleTapListener {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                // Let PhotoView handle zoom on double tap
                return false
            }
            
            override fun onDoubleTapEvent(e: MotionEvent): Boolean {
                return false
            }
            
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                toggleSystemBars()
                return true
            }
        })
    }
    
    private var systemBarsVisible = false
    
    private fun toggleSystemBars() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (systemBarsVisible) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
        systemBarsVisible = !systemBarsVisible
    }
}

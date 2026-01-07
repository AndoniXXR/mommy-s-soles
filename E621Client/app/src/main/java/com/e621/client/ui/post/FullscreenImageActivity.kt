package com.e621.client.ui.post

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
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
import java.util.Locale

/**
 * Fullscreen image/GIF viewer activity
 */
class FullscreenImageActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("${newBase.packageName}_preferences", Context.MODE_PRIVATE)
        val languageCode = prefs.getString("general_language", "en") ?: "en"
        if (languageCode != "system" && languageCode.isNotEmpty()) {
            val locale = Locale.forLanguageTag(languageCode)
            Locale.setDefault(locale)
            val config = Configuration(newBase.resources.configuration)
            config.setLocale(locale)
            super.attachBaseContext(newBase.createConfigurationContext(config))
        } else {
            super.attachBaseContext(newBase)
        }
    }

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
            .override(com.bumptech.glide.request.target.Target.SIZE_ORIGINAL)
            .format(com.bumptech.glide.load.DecodeFormat.PREFER_ARGB_8888)
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
                // Double tap to exit fullscreen
                finish()
                return true
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

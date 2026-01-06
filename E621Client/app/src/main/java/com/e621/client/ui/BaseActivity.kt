package com.e621.client.ui

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.e621.client.E621Application

/**
 * Base Activity that applies common settings to all activities:
 * - FLAG_SECURE for hide in tasks
 * - Theme application
 * - Application initialization check (handles process death restoration)
 */
abstract class BaseActivity : AppCompatActivity() {

    protected val prefs by lazy { E621Application.instance.userPreferences }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Check if application is properly initialized
        // This handles the case when Android restores an Activity after process death
        if (!E621Application.isInitialized) {
            // Redirect to LauncherActivity to properly initialize the app
            restartApp()
            return
        }
        
        // Apply theme before super.onCreate
        applyTheme()
        super.onCreate(savedInstanceState)
        
        // Apply FLAG_SECURE if needed
        applySecureFlag()
    }
    
    /**
     * Restart the app from LauncherActivity when the application wasn't properly initialized
     * This happens when Android restores an Activity after the process was killed
     */
    private fun restartApp() {
        val intent = Intent(this, LauncherActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }

    override fun onResume() {
        super.onResume()
        // Re-apply in case settings changed
        applySecureFlag()
    }

    private fun applyTheme() {
        when (prefs.theme) {
            1 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            2 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    private fun applySecureFlag() {
        if (prefs.hideInTasks) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

package com.e621.client.ui

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.e621.client.E621Application
import com.e621.client.ui.saved.SavedSearchesActivity

/**
 * Launcher Activity - Entry point for the app
 * Checks if PIN is required and redirects accordingly
 * Also handles startInSaved preference
 */
class LauncherActivity : AppCompatActivity() {

    private val prefs by lazy { E621Application.instance.userPreferences }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply theme before super.onCreate
        applyTheme()
        super.onCreate(savedInstanceState)
        
        // Apply FLAG_SECURE if needed
        if (prefs.hideInTasks) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }
        
        // Determine where to navigate
        navigateToNextScreen()
    }

    private fun applyTheme() {
        when (prefs.theme) {
            1 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            2 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    private fun navigateToNextScreen() {
        // Check if PIN is required
        if (prefs.isPinSet()) {
            // Show PIN screen
            val intent = Intent(this, PinCodeActivity::class.java)
            startActivity(intent)
            finish()
            overridePendingTransition(0, 0)
            return
        }
        
        // No PIN required, go to main screen
        goToMainScreen()
    }

    private fun goToMainScreen() {
        val intent = if (prefs.startInSaved) {
            Intent(this, SavedSearchesActivity::class.java)
        } else {
            Intent(this, MainActivity::class.java)
        }
        startActivity(intent)
        finish()
        overridePendingTransition(0, 0)
    }
}


package com.e621.client.ui.about

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.e621.client.R
import com.google.android.material.appbar.MaterialToolbar
import java.util.Locale

/**
 * About Activity - Shows app information and links
 */
class AboutActivity : AppCompatActivity() {

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        setupToolbar()
        setupViews()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun setupViews() {
        // Version
        val txtVersion = findViewById<TextView>(R.id.txtVersion)
        try {
            val versionName = packageManager.getPackageInfo(packageName, 0).versionName
            txtVersion.text = getString(R.string.about_version, versionName)
        } catch (e: Exception) {
            txtVersion.text = getString(R.string.about_version, "1.0.0")
        }

        // Website link
        findViewById<TextView>(R.id.btnWebsite).setOnClickListener {
            openUrl("https://e621.net")
        }

        // Wiki link
        findViewById<TextView>(R.id.btnWiki).setOnClickListener {
            openUrl("https://e621.net/wiki_pages/help:home")
        }

        // Terms link
        findViewById<TextView>(R.id.btnTerms).setOnClickListener {
            openUrl("https://e621.net/wiki_pages/help:terms_of_service")
        }
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }
}

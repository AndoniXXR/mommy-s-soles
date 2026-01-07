package com.e621.client.ui.profile

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.e621.client.E621Application
import com.e621.client.R
import com.e621.client.data.model.User
import com.e621.client.ui.MainActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * Profile Activity - Shows user profile information
 */
class ProfileActivity : AppCompatActivity() {

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

    companion object {
        const val EXTRA_USERNAME = "username"
    }

    private lateinit var scrollView: ScrollView
    private lateinit var progressBar: ProgressBar
    private lateinit var layoutError: View
    private lateinit var txtError: TextView
    
    private lateinit var imgAvatar: ImageView
    private lateinit var txtUsername: TextView
    private lateinit var txtLevel: TextView
    private lateinit var txtJoinDate: TextView
    private lateinit var txtFavoriteCount: TextView
    private lateinit var txtUploadCount: TextView
    private lateinit var txtPostUpdateCount: TextView
    private lateinit var txtNoteUpdateCount: TextView
    
    private var username: String? = null
    private var user: User? = null
    private val prefs by lazy { E621Application.instance.userPreferences }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        username = intent.getStringExtra(EXTRA_USERNAME) ?: prefs.username

        setupToolbar()
        setupViews()
        loadProfile()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun setupViews() {
        scrollView = findViewById(R.id.scrollView)
        progressBar = findViewById(R.id.progressBar)
        layoutError = findViewById(R.id.layoutError)
        txtError = findViewById(R.id.txtError)
        
        imgAvatar = findViewById(R.id.imgAvatar)
        txtUsername = findViewById(R.id.txtUsername)
        txtLevel = findViewById(R.id.txtLevel)
        txtJoinDate = findViewById(R.id.txtJoinDate)
        txtFavoriteCount = findViewById(R.id.txtFavoriteCount)
        txtUploadCount = findViewById(R.id.txtUploadCount)
        txtPostUpdateCount = findViewById(R.id.txtPostUpdateCount)
        txtNoteUpdateCount = findViewById(R.id.txtNoteUpdateCount)

        findViewById<MaterialButton>(R.id.btnRetry).setOnClickListener { loadProfile() }
        
        findViewById<MaterialButton>(R.id.btnViewFavorites).setOnClickListener {
            user?.let { openSearch("fav:${it.name}") }
        }
        
        findViewById<MaterialButton>(R.id.btnViewUploads).setOnClickListener {
            user?.let { openSearch("user:${it.name}") }
        }
        
        // Make favorites stat clickable
        findViewById<View>(R.id.layoutFavorites).setOnClickListener {
            user?.let { openSearch("fav:${it.name}") }
        }
        
        // Make uploads stat clickable
        findViewById<View>(R.id.layoutUploads).setOnClickListener {
            user?.let { openSearch("user:${it.name}") }
        }
    }

    private fun loadProfile() {
        if (username.isNullOrBlank()) {
            showError(getString(R.string.profile_not_logged_in))
            return
        }

        showLoading()

        val api = E621Application.instance.api

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    api.users.getByName(username!!)
                }

                if (response.isSuccessful) {
                    user = response.body()
                    user?.let { displayUser(it) } ?: showError(getString(R.string.profile_user_not_found))
                } else {
                    showError("Error: ${response.message()}")
                }
            } catch (e: Exception) {
                showError("Error: ${e.message}")
            }
        }
    }

    private fun displayUser(user: User) {
        showContent()
        
        // Update title
        supportActionBar?.title = user.name
        
        // Username
        txtUsername.text = user.name
        
        // Level
        txtLevel.text = user.getLevelName()
        
        // Join date
        user.createdAt?.let {
            try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US)
                val outputFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)
                val date = inputFormat.parse(it)
                txtJoinDate.text = getString(R.string.profile_joined, outputFormat.format(date!!))
            } catch (e: Exception) {
                txtJoinDate.visibility = View.GONE
            }
        } ?: run {
            txtJoinDate.visibility = View.GONE
        }
        
        // Stats
        txtFavoriteCount.text = formatNumber(user.favoriteCount ?: 0)
        txtUploadCount.text = formatNumber(user.postUploadCount ?: 0)
        txtPostUpdateCount.text = formatNumber(user.postUpdateCount ?: 0)
        txtNoteUpdateCount.text = formatNumber(user.noteUpdateCount ?: 0)
        
        // Avatar
        user.avatarId?.let { avatarId ->
            loadAvatar(avatarId)
        }
    }

    private fun loadAvatar(avatarId: Int) {
        val api = E621Application.instance.api
        
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    api.posts.get(avatarId)
                }
                
                if (response.isSuccessful) {
                    val post = response.body()?.post
                    post?.let {
                        val avatarUrl = it.preview.url ?: it.sample.url ?: it.file.url
                        Glide.with(this@ProfileActivity)
                            .load(avatarUrl)
                            .placeholder(R.drawable.ic_account)
                            .error(R.drawable.ic_account)
                            .circleCrop()
                            .into(imgAvatar)
                    }
                }
            } catch (e: Exception) {
                // Ignore avatar load errors
            }
        }
    }

    private fun openSearch(query: String) {
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("search_query", query)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
    }

    private fun showLoading() {
        progressBar.visibility = View.VISIBLE
        scrollView.visibility = View.GONE
        layoutError.visibility = View.GONE
    }

    private fun showContent() {
        progressBar.visibility = View.GONE
        scrollView.visibility = View.VISIBLE
        layoutError.visibility = View.GONE
    }

    private fun showError(message: String) {
        progressBar.visibility = View.GONE
        scrollView.visibility = View.GONE
        layoutError.visibility = View.VISIBLE
        txtError.text = message
    }

    private fun formatNumber(number: Int): String {
        return when {
            number >= 1_000_000 -> String.format("%.1fM", number / 1_000_000.0)
            number >= 1_000 -> String.format("%.1fK", number / 1_000.0)
            else -> number.toString()
        }
    }
}

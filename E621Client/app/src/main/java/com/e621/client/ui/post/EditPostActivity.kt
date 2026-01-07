package com.e621.client.ui.post

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.MenuItem
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.e621.client.E621Application
import com.e621.client.R
import com.e621.client.data.model.Post
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Activity for editing post tags and rating
 */
class EditPostActivity : AppCompatActivity() {

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
    
    private lateinit var toolbar: Toolbar
    private lateinit var etArtistTags: EditText
    private lateinit var etCopyrightTags: EditText
    private lateinit var etCharacterTags: EditText
    private lateinit var etSpeciesTags: EditText
    private lateinit var etGeneralTags: EditText
    private lateinit var etEditReason: EditText
    private lateinit var rgRating: RadioGroup
    private lateinit var rbSafe: RadioButton
    private lateinit var rbQuestionable: RadioButton
    private lateinit var rbExplicit: RadioButton
    private lateinit var progressBar: ProgressBar
    
    private var postId: Int = -1
    private var position: Int = -1
    private var originalPost: Post? = null
    private var hasChanges = false
    
    private val prefs by lazy { E621Application.instance.userPreferences }
    private val api by lazy { E621Application.instance.api }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_post)
        
        postId = intent.getIntExtra("post_id", -1)
        position = intent.getIntExtra("position", -1)
        
        if (postId < 0) {
            finish()
            return
        }
        
        initViews()
        setupToolbar()
        loadPost()
    }
    
    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        etArtistTags = findViewById(R.id.etArtistTags)
        etCopyrightTags = findViewById(R.id.etCopyrightTags)
        etCharacterTags = findViewById(R.id.etCharacterTags)
        etSpeciesTags = findViewById(R.id.etSpeciesTags)
        etGeneralTags = findViewById(R.id.etGeneralTags)
        etEditReason = findViewById(R.id.etEditReason)
        rgRating = findViewById(R.id.rgRating)
        rbSafe = findViewById(R.id.rbSafe)
        rbQuestionable = findViewById(R.id.rbQuestionable)
        rbExplicit = findViewById(R.id.rbExplicit)
        progressBar = findViewById(R.id.progressBar)
    }
    
    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.post_menu_edit_post)
    }
    
    private fun loadPost() {
        progressBar.visibility = android.view.View.VISIBLE
        
        lifecycleScope.launch {
            try {
                val response = api.posts.get(postId)
                if (response.isSuccessful) {
                    response.body()?.post?.let { post ->
                        originalPost = post
                        populateFields(post)
                    }
                } else {
                    Toast.makeText(this@EditPostActivity, R.string.post_error_loading, Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                Toast.makeText(this@EditPostActivity, R.string.post_error_loading, Toast.LENGTH_SHORT).show()
                finish()
            } finally {
                progressBar.visibility = android.view.View.GONE
            }
        }
    }
    
    private fun populateFields(post: Post) {
        toolbar.title = getString(R.string.post_title, post.id)
        
        post.tags?.let { tags ->
            etArtistTags.setText(tags.artist?.joinToString(" ") ?: "")
            etCopyrightTags.setText(tags.copyright?.joinToString(" ") ?: "")
            etCharacterTags.setText(tags.character?.joinToString(" ") ?: "")
            etSpeciesTags.setText(tags.species?.joinToString(" ") ?: "")
            etGeneralTags.setText(tags.general?.joinToString(" ") ?: "")
        }
        
        when (post.rating) {
            "s" -> rbSafe.isChecked = true
            "q" -> rbQuestionable.isChecked = true
            "e" -> rbExplicit.isChecked = true
        }
    }
    
    private fun getSelectedRating(): String {
        return when (rgRating.checkedRadioButtonId) {
            R.id.rbSafe -> "s"
            R.id.rbQuestionable -> "q"
            R.id.rbExplicit -> "e"
            else -> "e"
        }
    }
    
    private fun saveChanges() {
        val reason = etEditReason.text.toString().trim()
        if (reason.isEmpty()) {
            etEditReason.error = "Please provide an edit reason"
            return
        }
        
        progressBar.visibility = android.view.View.VISIBLE
        
        // Build tags string
        val allTags = StringBuilder()
        allTags.append(etArtistTags.text.toString().trim()).append(" ")
        allTags.append(etCopyrightTags.text.toString().trim()).append(" ")
        allTags.append(etCharacterTags.text.toString().trim()).append(" ")
        allTags.append(etSpeciesTags.text.toString().trim()).append(" ")
        allTags.append(etGeneralTags.text.toString().trim())
        
        val newRating = getSelectedRating()
        
        lifecycleScope.launch {
            try {
                // Note: e621 API requires specific endpoints for editing
                // This is a placeholder - actual implementation would use proper API
                Toast.makeText(this@EditPostActivity, "Edit submitted", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK, intent.putExtra("pos", position))
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@EditPostActivity, "Error saving changes", Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = android.view.View.GONE
            }
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    override fun onBackPressed() {
        if (hasChanges) {
            AlertDialog.Builder(this)
                .setTitle("Discard changes?")
                .setMessage("You have unsaved changes. Are you sure you want to discard them?")
                .setPositiveButton("Discard") { _, _ -> super.onBackPressed() }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            super.onBackPressed()
        }
    }
}

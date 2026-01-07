package com.e621.client.ui.wiki

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.e621.client.E621Application
import com.e621.client.R
import com.e621.client.data.model.WikiPage
import com.google.android.material.appbar.MaterialToolbar
import com.e621.client.data.api.CloudFlareException
import com.e621.client.data.api.ServerDownException
import com.e621.client.data.api.NetworkException
import com.e621.client.data.api.NetworkErrorType
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.regex.Pattern

/**
 * Activity to display wiki pages for tags
 * Shows native formatted content from e621 wiki API
 */
class WikiActivity : AppCompatActivity() {

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

    private lateinit var toolbar: MaterialToolbar
    private lateinit var progressBar: ProgressBar
    private lateinit var scrollView: ScrollView
    private lateinit var contentLayout: LinearLayout
    private lateinit var txtTitle: TextView
    private lateinit var txtBody: TextView
    private lateinit var txtInfo: TextView
    private lateinit var txtOtherNames: TextView
    private lateinit var errorLayout: LinearLayout
    private lateinit var txtError: TextView
    
    private var tagName: String = ""
    private val api by lazy { E621Application.instance.api }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wiki)
        
        tagName = intent.getStringExtra(EXTRA_TAG_NAME) ?: ""
        
        if (tagName.isEmpty()) {
            finish()
            return
        }
        
        setupViews()
        loadWikiPage()
    }
    
    private fun setupViews() {
        toolbar = findViewById(R.id.toolbar)
        progressBar = findViewById(R.id.progressBar)
        scrollView = findViewById(R.id.scrollView)
        contentLayout = findViewById(R.id.contentLayout)
        txtTitle = findViewById(R.id.txtTitle)
        txtBody = findViewById(R.id.txtBody)
        txtInfo = findViewById(R.id.txtInfo)
        txtOtherNames = findViewById(R.id.txtOtherNames)
        errorLayout = findViewById(R.id.errorLayout)
        txtError = findViewById(R.id.txtError)
        
        // Setup toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "Wiki"
        }
        
        // Enable link clicking in body
        txtBody.movementMethod = LinkMovementMethod.getInstance()
    }
    
    private fun loadWikiPage() {
        showLoading()
        
        lifecycleScope.launch {
            try {
                val response = api.wiki.getByTitle(tagName)
                
                if (response.isSuccessful) {
                    val wikiPages = response.body()
                    
                    if (!wikiPages.isNullOrEmpty()) {
                        // Find exact match or first result
                        val wikiPage = wikiPages.find { it.title.equals(tagName, ignoreCase = true) }
                            ?: wikiPages.first()
                        
                        displayWikiPage(wikiPage)
                    } else {
                        showError(getString(R.string.error_wiki_not_found))
                    }
                } else {
                    showApiError(response.code())
                }
            } catch (e: CloudFlareException) {
                showError(getString(R.string.error_cloudflare))
            } catch (e: ServerDownException) {
                showError(getString(R.string.error_server_down))
            } catch (e: NetworkException) {
                val message = when (e.type) {
                    NetworkErrorType.TIMEOUT -> getString(R.string.error_timeout)
                    NetworkErrorType.NO_INTERNET -> getString(R.string.error_no_internet)
                    else -> getString(R.string.error_connection)
                }
                showError(message)
            } catch (e: Exception) {
                showError(getString(R.string.error_wiki_loading))
            }
        }
    }
    
    private fun displayWikiPage(wikiPage: WikiPage) {
        runOnUiThread {
            progressBar.visibility = View.GONE
            scrollView.visibility = View.VISIBLE
            errorLayout.visibility = View.GONE
            
            // Title
            txtTitle.text = wikiPage.title.replace("_", " ")
            
            // Other names
            if (!wikiPage.otherNames.isNullOrEmpty()) {
                txtOtherNames.visibility = View.VISIBLE
                txtOtherNames.text = "Also known as: ${wikiPage.otherNames.joinToString(", ") { it.replace("_", " ") }}"
            } else {
                txtOtherNames.visibility = View.GONE
            }
            
            // Body - parse DText format
            val bodyText = wikiPage.body ?: "No content available."
            txtBody.text = parseDText(bodyText)
            
            // Info line
            val infoBuilder = StringBuilder()
            wikiPage.creatorName?.let { 
                infoBuilder.append("Created by: $it")
            }
            wikiPage.updatedAt?.let {
                try {
                    val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
                    val outputFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)
                    val date = inputFormat.parse(it)
                    date?.let { d ->
                        if (infoBuilder.isNotEmpty()) infoBuilder.append(" • ")
                        infoBuilder.append("Updated: ${outputFormat.format(d)}")
                    }
                } catch (e: Exception) {
                    // Ignore date parsing errors
                }
            }
            
            // Category
            val categoryName = getCategoryName(wikiPage.categoryId)
            if (categoryName.isNotEmpty()) {
                if (infoBuilder.isNotEmpty()) infoBuilder.append(" • ")
                infoBuilder.append("Category: $categoryName")
            }
            
            if (infoBuilder.isNotEmpty()) {
                txtInfo.visibility = View.VISIBLE
                txtInfo.text = infoBuilder.toString()
            } else {
                txtInfo.visibility = View.GONE
            }
        }
    }
    
    /**
     * Parse DText format used by e621 wiki
     * https://e621.net/wiki_pages/dtext_help
     */
    private fun parseDText(text: String): SpannableStringBuilder {
        val builder = SpannableStringBuilder()
        var currentText = text
        
        // Remove thumb tags (image thumbnails)
        currentText = currentText.replace(Regex("thumb #\\d+"), "")
        
        // Process line by line
        val lines = currentText.split("\n")
        
        for ((index, line) in lines.withIndex()) {
            var processedLine = line.trim()
            
            if (processedLine.isEmpty()) {
                if (index > 0) builder.append("\n")
                continue
            }
            
            // Headers (h1. h2. h3. h4. h5. h6.)
            val headerMatch = Regex("^h([1-6])\\.\\s*(.+)$").find(processedLine)
            if (headerMatch != null) {
                val level = headerMatch.groupValues[1].toInt()
                val headerText = headerMatch.groupValues[2]
                
                val start = builder.length
                builder.append(headerText)
                builder.append("\n")
                
                // Style based on header level
                val size = when (level) {
                    1 -> 1.6f
                    2 -> 1.4f
                    3 -> 1.25f
                    4 -> 1.15f
                    else -> 1.1f
                }
                builder.setSpan(RelativeSizeSpan(size), start, start + headerText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(StyleSpan(Typeface.BOLD), start, start + headerText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(ForegroundColorSpan(ContextCompat.getColor(this, R.color.accent)), start, start + headerText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                
                continue
            }
            
            // List items (* or **)
            if (processedLine.startsWith("*")) {
                val depth = processedLine.takeWhile { it == '*' }.length
                processedLine = processedLine.dropWhile { it == '*' }.trim()
                val indent = "  ".repeat(depth - 1)
                processedLine = "$indent• $processedLine"
            }
            
            // Process inline formatting
            val formattedLine = parseInlineFormatting(processedLine)
            builder.append(formattedLine)
            
            if (index < lines.size - 1) {
                builder.append("\n")
            }
        }
        
        return builder
    }
    
    /**
     * Parse inline DText formatting
     */
    private fun parseInlineFormatting(text: String): SpannableStringBuilder {
        val builder = SpannableStringBuilder()
        var currentPos = 0
        var remaining = text
        
        // Patterns for inline elements
        val linkPattern = Pattern.compile("\\[\\[([^|\\]]+)(\\|([^\\]]+))?\\]\\]")
        val boldPattern = Pattern.compile("\\[b\\](.+?)\\[/b\\]")
        val italicPattern = Pattern.compile("\\[i\\](.+?)\\[/i\\]")
        val urlPattern = Pattern.compile("\"([^\"]+)\":((https?://[^\\s]+)|(#[^\\s]+))")
        val externalUrlPattern = Pattern.compile("https?://[^\\s]+")
        
        // Process wiki links [[tag]] or [[tag|display text]]
        while (remaining.isNotEmpty()) {
            val linkMatcher = linkPattern.matcher(remaining)
            val boldMatcher = boldPattern.matcher(remaining)
            val italicMatcher = italicPattern.matcher(remaining)
            val urlMatcher = urlPattern.matcher(remaining)
            
            // Find earliest match
            var earliestMatch: Pair<Int, String>? = null
            var matchType = ""
            var matchEnd = 0
            var displayText = ""
            var targetText = ""
            
            if (linkMatcher.find()) {
                earliestMatch = Pair(linkMatcher.start(), "link")
                matchType = "link"
                matchEnd = linkMatcher.end()
                targetText = linkMatcher.group(1) ?: ""
                displayText = linkMatcher.group(3) ?: targetText
            }
            
            if (boldMatcher.find() && (earliestMatch == null || boldMatcher.start() < earliestMatch.first)) {
                earliestMatch = Pair(boldMatcher.start(), "bold")
                matchType = "bold"
                matchEnd = boldMatcher.end()
                displayText = boldMatcher.group(1) ?: ""
            }
            
            if (italicMatcher.find() && (earliestMatch == null || italicMatcher.start() < earliestMatch.first)) {
                earliestMatch = Pair(italicMatcher.start(), "italic")
                matchType = "italic"
                matchEnd = italicMatcher.end()
                displayText = italicMatcher.group(1) ?: ""
            }
            
            if (urlMatcher.find() && (earliestMatch == null || urlMatcher.start() < earliestMatch.first)) {
                earliestMatch = Pair(urlMatcher.start(), "url")
                matchType = "url"
                matchEnd = urlMatcher.end()
                displayText = urlMatcher.group(1) ?: ""
                targetText = urlMatcher.group(2) ?: ""
            }
            
            if (earliestMatch == null) {
                // No more matches, append rest
                builder.append(remaining.replace("_", " "))
                break
            }
            
            // Append text before match
            if (earliestMatch.first > 0) {
                builder.append(remaining.substring(0, earliestMatch.first).replace("_", " "))
            }
            
            // Process the match
            val start = builder.length
            val cleanDisplayText = displayText.replace("_", " ")
            builder.append(cleanDisplayText)
            
            when (matchType) {
                "link" -> {
                    val finalTargetText = targetText
                    builder.setSpan(object : ClickableSpan() {
                        override fun onClick(widget: View) {
                            openWikiLink(finalTargetText)
                        }
                        
                        override fun updateDrawState(ds: TextPaint) {
                            ds.color = ContextCompat.getColor(this@WikiActivity, R.color.accent)
                            ds.isUnderlineText = false
                        }
                    }, start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                "bold" -> {
                    builder.setSpan(StyleSpan(Typeface.BOLD), start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                "italic" -> {
                    builder.setSpan(StyleSpan(Typeface.ITALIC), start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                "url" -> {
                    val finalUrl = targetText
                    builder.setSpan(object : ClickableSpan() {
                        override fun onClick(widget: View) {
                            openExternalUrl(finalUrl)
                        }
                        
                        override fun updateDrawState(ds: TextPaint) {
                            ds.color = ContextCompat.getColor(this@WikiActivity, R.color.text_important)
                            ds.isUnderlineText = true
                        }
                    }, start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            
            // Move past this match
            remaining = remaining.substring(matchEnd)
        }
        
        return builder
    }
    
    private fun openWikiLink(tag: String) {
        // Open another wiki page
        val intent = Intent(this, WikiActivity::class.java)
        intent.putExtra(EXTRA_TAG_NAME, tag)
        startActivity(intent)
    }
    
    private fun openExternalUrl(url: String) {
        try {
            if (url.startsWith("http")) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open URL", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun getCategoryName(categoryId: Int?): String {
        return when (categoryId) {
            0 -> "General"
            1 -> "Artist"
            3 -> "Copyright"
            4 -> "Character"
            5 -> "Species"
            6 -> "Invalid"
            7 -> "Meta"
            8 -> "Lore"
            else -> ""
        }
    }
    
    private fun showLoading() {
        progressBar.visibility = View.VISIBLE
        scrollView.visibility = View.GONE
        errorLayout.visibility = View.GONE
    }
    
    private fun showError(message: String) {
        runOnUiThread {
            progressBar.visibility = View.GONE
            scrollView.visibility = View.GONE
            errorLayout.visibility = View.VISIBLE
            txtError.text = message
        }
    }
    
    private fun showApiError(code: Int) {
        val message = when (code) {
            401 -> getString(R.string.error_session_expired)
            403 -> getString(R.string.error_forbidden)
            404 -> getString(R.string.error_wiki_not_found)
            else -> getString(R.string.error_http_code, code)
        }
        showError(message)
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
    
    companion object {
        const val EXTRA_TAG_NAME = "extra_tag_name"
    }
}

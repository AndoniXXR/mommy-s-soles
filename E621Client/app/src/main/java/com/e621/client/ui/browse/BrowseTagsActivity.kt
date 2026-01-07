package com.e621.client.ui.browse

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.e621.client.E621Application
import com.e621.client.R
import com.e621.client.data.model.E621Tag
import com.e621.client.ui.MainActivity
import com.e621.client.data.api.CloudFlareException
import com.e621.client.data.api.ServerDownException
import com.e621.client.data.api.NetworkException
import com.e621.client.data.api.NetworkErrorType
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Activity for browsing tags
 * Allows searching and sorting tags
 */
class BrowseTagsActivity : AppCompatActivity() {

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
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: TagAdapter
    
    private val api by lazy { E621Application.instance.api }
    
    private var tags = mutableListOf<E621Tag>()
    private var currentPage = 1
    private var isLoading = false
    private var searchQuery: String? = null
    private var sortOrder = SortOrder.DATE // 0=date, 1=name, 2=count
    
    enum class SortOrder(val apiValue: String) {
        DATE("date"),
        NAME("name"),
        COUNT("count")
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browse_tags)
        
        setupViews()
        setupToolbar()
        setupRecyclerView()
        loadTags()
    }
    
    private fun setupViews() {
        toolbar = findViewById(R.id.toolbar)
        recyclerView = findViewById(R.id.recyclerView)
        progressBar = findViewById(R.id.progressBar)
    }
    
    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.menu_browse_tags)
        }
    }
    
    private fun setupRecyclerView() {
        adapter = TagAdapter { tag ->
            // Click on tag - search for it
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("search_query", tag.name)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
        }
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        
        // Pagination on scroll
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
                
                if (!isLoading && 
                    (visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 5 &&
                    firstVisibleItemPosition >= 0) {
                    loadMoreTags()
                }
            }
        })
    }
    
    private fun loadTags() {
        currentPage = 1
        tags.clear()
        adapter.submitList(emptyList())
        showLoading(true)
        
        lifecycleScope.launch {
            try {
                val response = api.tags.getTags(
                    nameMatches = searchQuery?.let { "*$it*" },
                    order = sortOrder.apiValue,
                    page = currentPage,
                    limit = 75
                )
                
                if (response.isSuccessful) {
                    response.body()?.let { tagList ->
                        tags.addAll(tagList)
                        adapter.submitList(tags.toList())
                    }
                } else {
                    showApiError(response.code())
                }
            } catch (e: CloudFlareException) {
                Toast.makeText(this@BrowseTagsActivity, R.string.error_cloudflare, Toast.LENGTH_LONG).show()
            } catch (e: ServerDownException) {
                Toast.makeText(this@BrowseTagsActivity, R.string.error_server_down, Toast.LENGTH_LONG).show()
            } catch (e: NetworkException) {
                val message = when (e.type) {
                    NetworkErrorType.TIMEOUT -> getString(R.string.error_timeout)
                    NetworkErrorType.NO_INTERNET -> getString(R.string.error_no_internet)
                    else -> getString(R.string.error_connection)
                }
                Toast.makeText(this@BrowseTagsActivity, message, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this@BrowseTagsActivity, R.string.error_tags_loading, Toast.LENGTH_SHORT).show()
            } finally {
                showLoading(false)
            }
        }
    }
    
    private fun loadMoreTags() {
        if (isLoading) return
        isLoading = true
        currentPage++
        
        lifecycleScope.launch {
            try {
                val response = api.tags.getTags(
                    nameMatches = searchQuery?.let { "*$it*" },
                    order = sortOrder.apiValue,
                    page = currentPage,
                    limit = 75
                )
                
                if (response.isSuccessful) {
                    response.body()?.let { tagList ->
                        tags.addAll(tagList)
                        adapter.submitList(tags.toList())
                    }
                }
            } catch (e: Exception) {
                currentPage--
            } finally {
                isLoading = false
            }
        }
    }
    
    private fun showLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        isLoading = loading
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_browse_tags, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            R.id.menu_search -> {
                showSearchDialog()
                true
            }
            R.id.menu_sort -> {
                showSortMenu(findViewById(R.id.menu_sort))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun showSearchDialog() {
        val editText = EditText(this).apply {
            hint = getString(R.string.browse_tags_search_hint)
            isSingleLine = true
            setText(searchQuery ?: "")
        }
        
        AlertDialog.Builder(this)
            .setTitle(R.string.search)
            .setMessage(R.string.browse_tags_search_message)
            .setView(editText)
            .setPositiveButton(R.string.search) { _, _ ->
                searchQuery = editText.text.toString().trim().ifEmpty { null }
                loadTags()
            }
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.clear) { _, _ ->
                searchQuery = null
                loadTags()
            }
            .show()
    }
    
    private fun showSortMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.menu_tags_sort, popup.menu)
        
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.sort_by_date -> {
                    sortOrder = SortOrder.DATE
                    loadTags()
                    true
                }
                R.id.sort_by_name -> {
                    sortOrder = SortOrder.NAME
                    loadTags()
                    true
                }
                R.id.sort_by_count -> {
                    sortOrder = SortOrder.COUNT
                    loadTags()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }
    
    private fun showApiError(code: Int) {
        val message = when (code) {
            401 -> getString(R.string.error_session_expired)
            403 -> getString(R.string.error_forbidden)
            404 -> getString(R.string.error_not_found)
            else -> getString(R.string.error_http_code, code)
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

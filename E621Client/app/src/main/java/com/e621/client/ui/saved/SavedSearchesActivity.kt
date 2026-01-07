package com.e621.client.ui.saved

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.e621.client.E621Application
import com.e621.client.R
import com.e621.client.ui.adapter.SavedSearchAdapter
import com.e621.client.ui.settings.SettingsActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

/**
 * Activity for managing saved searches
 * Based on decompiled SavedSearchesActivity
 */
class SavedSearchesActivity : AppCompatActivity() {

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
    private lateinit var emptyState: View
    private lateinit var fabAdd: FloatingActionButton
    private lateinit var searchEditText: EditText
    
    private lateinit var adapter: SavedSearchAdapter
    
    private val prefs by lazy { E621Application.instance.userPreferences }
    
    // Current search tags passed from MainActivity
    private var currentSearchTags: String = ""
    
    // Filter text for search
    private var filterText: String = ""
    
    // Current order
    private var currentOrder = ORDER_DATE_CREATED
    
    // Launcher for file export
    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri?.let { exportToFile(it) }
    }
    
    // Launcher for file import
    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { importFromFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Check if application is properly initialized
        if (!E621Application.isInitialized) {
            restartApp()
            return
        }
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_saved_searches)
        
        // Get current search from intent
        currentSearchTags = intent.getStringExtra(EXTRA_CURRENT_SEARCH) ?: ""
        
        setupViews()
        setupToolbar()
        setupRecyclerView()
        setupFab()
        
        updateList()
    }
    
    private fun setupViews() {
        toolbar = findViewById(R.id.toolbar)
        recyclerView = findViewById(R.id.recyclerSavedSearches)
        emptyState = findViewById(R.id.emptyState)
        fabAdd = findViewById(R.id.fabAdd)
        searchEditText = findViewById(R.id.editSearch)
        
        // Setup search filter
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterText = s?.toString() ?: ""
                updateList()
            }
        })
    }
    
    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener {
            finish()
        }
    }
    
    private fun setupRecyclerView() {
        adapter = SavedSearchAdapter(
            onSearchClick = { searchTags ->
                // Return the selected search to MainActivity
                val resultIntent = Intent().apply {
                    putExtra(RESULT_SEARCH_TAGS, searchTags)
                }
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            },
            onDeleteClick = { searchTags ->
                showDeleteConfirmation(searchTags)
            }
        )
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }
    
    private fun setupFab() {
        fabAdd.setOnClickListener {
            addCurrentSearch()
        }
    }
    
    private fun showDeleteConfirmation(searchTags: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete)
            .setMessage(getString(R.string.delete_search_confirm, searchTags))
            .setPositiveButton(R.string.delete) { _, _ ->
                prefs.removeSavedSearch(searchTags)
                updateList()
                Toast.makeText(this, R.string.search_removed, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun updateList() {
        var savedSearches = prefs.savedSearches.toList()
        
        // Apply filter
        if (filterText.isNotBlank()) {
            savedSearches = savedSearches.filter { 
                it.contains(filterText, ignoreCase = true) 
            }
        }
        
        // Apply ordering
        savedSearches = when (currentOrder) {
            ORDER_DATE_USED -> savedSearches // Keep natural order (most recently used first)
            ORDER_DATE_CREATED -> savedSearches.reversed() // Oldest first
            ORDER_NAME -> savedSearches.sortedBy { it.lowercase() } // Alphabetical
            ORDER_TAGS -> savedSearches.sortedByDescending { it.split(" ").size } // By tag count
            else -> savedSearches
        }
        
        adapter.submitList(savedSearches)
        
        if (savedSearches.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyState.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyState.visibility = View.GONE
        }
    }
    
    // Menu
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_saved_searches, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_add -> {
                addCurrentSearch()
                true
            }
            R.id.action_clear -> {
                showClearConfirmation()
                true
            }
            R.id.order_by_date_used -> {
                currentOrder = ORDER_DATE_USED
                updateList()
                true
            }
            R.id.order_by_date_created -> {
                currentOrder = ORDER_DATE_CREATED
                updateList()
                true
            }
            R.id.order_by_name -> {
                currentOrder = ORDER_NAME
                updateList()
                true
            }
            R.id.order_by_tags -> {
                currentOrder = ORDER_TAGS
                updateList()
                true
            }
            R.id.order_tags_alphabetically -> {
                orderTagsAlphabetically()
                true
            }
            R.id.action_export -> {
                exportSavedSearches()
                true
            }
            R.id.action_import -> {
                importSavedSearches()
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun addCurrentSearch() {
        if (currentSearchTags.isBlank()) {
            Toast.makeText(this, R.string.no_search_to_save, Toast.LENGTH_SHORT).show()
        } else if (prefs.isSavedSearch(currentSearchTags)) {
            Toast.makeText(this, R.string.search_already_saved, Toast.LENGTH_SHORT).show()
        } else {
            prefs.addSavedSearch(currentSearchTags)
            updateList()
            Toast.makeText(this, R.string.search_saved, Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showClearConfirmation() {
        AlertDialog.Builder(this)
            .setTitle(R.string.saved_menu_clear)
            .setMessage(R.string.saved_menu_clear_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                prefs.clearSavedSearches()
                updateList()
                Toast.makeText(this, R.string.saved_searches_cleared, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun exportSavedSearches() {
        val searches = prefs.savedSearches
        if (searches.isEmpty()) {
            Toast.makeText(this, R.string.saved_menu_no_searches, Toast.LENGTH_SHORT).show()
            return
        }
        exportLauncher.launch("saved_searches.txt")
    }
    
    private fun exportToFile(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { output ->
                val searches = prefs.savedSearches.toList()
                output.write(searches.joinToString("\n").toByteArray())
            }
            Toast.makeText(this, R.string.saved_menu_exported, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun importSavedSearches() {
        importLauncher.launch(arrayOf("text/plain"))
    }
    
    private fun importFromFile(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                val reader = BufferedReader(InputStreamReader(input))
                val lines = reader.readLines()
                var imported = 0
                lines.forEach { line ->
                    val search = line.trim()
                    if (search.isNotBlank() && !prefs.isSavedSearch(search)) {
                        prefs.addSavedSearch(search)
                        imported++
                    }
                }
                updateList()
                Toast.makeText(this, R.string.saved_menu_imported, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, R.string.saved_menu_import_error, Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Orders the tags within each saved search alphabetically
     */
    private fun orderTagsAlphabetically() {
        val savedSearches = prefs.savedSearches.toList()
        prefs.clearSavedSearches()
        
        savedSearches.forEach { search ->
            val orderedTags = search.split(" ")
                .filter { it.isNotBlank() }
                .sortedBy { it.lowercase() }
                .joinToString(" ")
            prefs.addSavedSearch(orderedTags)
        }
        
        updateList()
        Toast.makeText(this, R.string.saved_menu_order_tags_alphabetically, Toast.LENGTH_SHORT).show()
    }
    
    private fun restartApp() {
        val intent = Intent(this, com.e621.client.ui.LauncherActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }
    
    companion object {
        const val EXTRA_CURRENT_SEARCH = "current_search"
        const val RESULT_SEARCH_TAGS = "search_tags"
        
        // Order constants
        private const val ORDER_DATE_USED = 0
        private const val ORDER_DATE_CREATED = 1
        private const val ORDER_NAME = 2
        private const val ORDER_TAGS = 3
    }
}

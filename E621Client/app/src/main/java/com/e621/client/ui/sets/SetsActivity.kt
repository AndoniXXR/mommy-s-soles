package com.e621.client.ui.sets

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.e621.client.E621Application
import com.e621.client.R
import com.e621.client.data.model.PostSet
import com.e621.client.ui.adapter.SetAdapter
import com.e621.client.ui.MainActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * SetsActivity - Browse post sets
 */
class SetsActivity : AppCompatActivity() {

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
        const val EXTRA_CREATOR_NAME = "creator_name"
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyView: TextView
    private lateinit var fabNewSet: FloatingActionButton

    private lateinit var adapter: SetAdapter
    private val sets = mutableListOf<PostSet>()
    
    private var currentPage = 1
    private var isLoading = false
    private var hasMore = true
    
    private var searchName: String? = null
    private var searchShortname: String? = null
    private var searchCreator: String? = null
    private var currentOrder = "updated_at" // most recently updated

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sets)

        // Check for creator filter from intent
        searchCreator = intent.getStringExtra(EXTRA_CREATOR_NAME)

        setupToolbar()
        setupViews()
        loadSets()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        
        supportActionBar?.title = if (searchCreator != null) {
            "${getString(R.string.sets_title)} - $searchCreator"
        } else {
            getString(R.string.sets_title)
        }
    }

    private fun setupViews() {
        recyclerView = findViewById(R.id.setsRecyclerView)
        progressBar = findViewById(R.id.progressBar)
        emptyView = findViewById(R.id.emptyView)
        fabNewSet = findViewById(R.id.fabNewSet)

        adapter = SetAdapter(
            onSetClick = { set -> openSet(set) }
        )
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // Pagination
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                if (!isLoading && hasMore) {
                    if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 5
                        && firstVisibleItemPosition >= 0) {
                        loadMoreSets()
                    }
                }
            }
        })

        // FAB for new set (only if logged in)
        if (E621Application.instance.prefs.isLoggedIn) {
            fabNewSet.visibility = View.VISIBLE
            fabNewSet.setOnClickListener { showNewSetDialog() }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_sets, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_search -> {
                showSearchDialog()
                true
            }
            R.id.action_sort -> {
                showSortDialog()
                true
            }
            R.id.action_refresh -> {
                refreshSets()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showSearchDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 0)
        }
        
        val nameInput = EditText(this).apply {
            hint = getString(R.string.set_name)
            setText(searchName ?: "")
        }
        val shortnameInput = EditText(this).apply {
            hint = getString(R.string.set_shortname)
            setText(searchShortname ?: "")
        }
        val creatorInput = EditText(this).apply {
            hint = getString(R.string.login_username)
            setText(searchCreator ?: "")
        }
        
        layout.addView(nameInput)
        layout.addView(shortnameInput)
        layout.addView(creatorInput)
        
        AlertDialog.Builder(this)
            .setTitle(R.string.search)
            .setView(layout)
            .setPositiveButton(R.string.search) { _, _ ->
                searchName = nameInput.text.toString().trim().ifEmpty { null }
                searchShortname = shortnameInput.text.toString().trim().ifEmpty { null }
                searchCreator = creatorInput.text.toString().trim().ifEmpty { null }
                refreshSets()
            }
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton("Clear") { _, _ ->
                searchName = null
                searchShortname = null
                searchCreator = null
                refreshSets()
            }
            .show()
    }

    private fun showSortDialog() {
        val options = arrayOf(
            "Most recent",
            "Name",
            "Post count",
            "Created date"
        )
        
        AlertDialog.Builder(this)
            .setTitle(R.string.sort_by)
            .setItems(options) { _, which ->
                currentOrder = when (which) {
                    0 -> "updated_at"
                    1 -> "name"
                    2 -> "post_count"
                    3 -> "created_at"
                    else -> "updated_at"
                }
                refreshSets()
            }
            .show()
    }

    private fun refreshSets() {
        currentPage = 1
        hasMore = true
        sets.clear()
        adapter.submitList(emptyList())
        loadSets()
    }

    private fun loadSets() {
        if (isLoading) return
        isLoading = true

        progressBar.visibility = if (sets.isEmpty()) View.VISIBLE else View.GONE
        emptyView.visibility = View.GONE

        val api = E621Application.instance.api

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    api.sets.list(
                        name = searchName,
                        shortname = searchShortname,
                        creatorName = searchCreator,
                        order = currentOrder,
                        page = currentPage,
                        limit = 50
                    )
                }

                if (response.isSuccessful) {
                    val newSets = response.body() ?: emptyList()
                    
                    if (newSets.isEmpty() && sets.isEmpty()) {
                        emptyView.visibility = View.VISIBLE
                    } else {
                        sets.addAll(newSets)
                        adapter.submitList(sets.toList())
                        hasMore = newSets.size >= 50
                    }
                } else {
                    Toast.makeText(this@SetsActivity, R.string.error, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@SetsActivity, e.message ?: getString(R.string.error), Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
                progressBar.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
            }
        }
    }

    private fun loadMoreSets() {
        currentPage++
        loadSets()
    }

    private fun openSet(set: PostSet) {
        // Open MainActivity with set:shortname tag
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("search_query", "set:${set.shortname}")
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
    }

    private fun showNewSetDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 0)
        }
        
        val nameInput = EditText(this).apply {
            hint = getString(R.string.set_name)
        }
        val shortnameInput = EditText(this).apply {
            hint = getString(R.string.set_shortname)
        }
        val descriptionInput = EditText(this).apply {
            hint = getString(R.string.set_description)
            minLines = 2
        }
        
        layout.addView(nameInput)
        layout.addView(shortnameInput)
        layout.addView(descriptionInput)
        
        AlertDialog.Builder(this)
            .setTitle(R.string.new_set)
            .setView(layout)
            .setPositiveButton(R.string.ok) { _, _ ->
                val name = nameInput.text.toString().trim()
                val shortname = shortnameInput.text.toString().trim()
                val description = descriptionInput.text.toString().trim().ifEmpty { null }
                
                if (name.isNotEmpty() && shortname.isNotEmpty()) {
                    createSet(name, shortname, description)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun createSet(name: String, shortname: String, description: String?) {
        val api = E621Application.instance.api
        
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    api.sets.create(name, shortname, description)
                }
                
                if (response.isSuccessful) {
                    Toast.makeText(this@SetsActivity, "Set created!", Toast.LENGTH_SHORT).show()
                    refreshSets()
                } else {
                    Toast.makeText(this@SetsActivity, R.string.error, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@SetsActivity, R.string.error, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

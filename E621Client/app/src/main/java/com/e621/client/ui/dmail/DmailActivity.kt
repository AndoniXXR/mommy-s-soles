package com.e621.client.ui.dmail

import android.content.Context
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
import com.e621.client.data.model.Dmail
import com.e621.client.ui.adapter.DmailAdapter
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * DmailActivity - Private messaging inbox
 */
class DmailActivity : AppCompatActivity() {

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

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyView: TextView
    private lateinit var fabNewDmail: FloatingActionButton

    private lateinit var adapter: DmailAdapter
    private val dmails = mutableListOf<Dmail>()
    
    private var currentPage = 1
    private var isLoading = false
    private var hasMore = true
    private var filterUnread = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dmail)

        // Check if logged in
        if (!E621Application.instance.prefs.isLoggedIn) {
            Toast.makeText(this, R.string.profile_not_logged_in, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setupToolbar()
        setupViews()
        loadDmails()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        supportActionBar?.title = getString(R.string.dmail_title)
    }

    private fun setupViews() {
        recyclerView = findViewById(R.id.dmailRecyclerView)
        progressBar = findViewById(R.id.progressBar)
        emptyView = findViewById(R.id.emptyView)
        fabNewDmail = findViewById(R.id.fabNewDmail)

        adapter = DmailAdapter(
            onDmailClick = { dmail -> openDmail(dmail) }
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
                        loadMoreDmails()
                    }
                }
            }
        })

        // FAB for new dmail
        fabNewDmail.visibility = View.VISIBLE
        fabNewDmail.setOnClickListener { showNewDmailDialog() }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_dmail, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_filter -> {
                showFilterDialog()
                true
            }
            R.id.action_refresh -> {
                refreshDmails()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showFilterDialog() {
        val options = arrayOf(
            getString(R.string.dmail_all),
            getString(R.string.dmail_unread)
        )
        
        val selectedIndex = if (filterUnread) 1 else 0
        
        AlertDialog.Builder(this)
            .setTitle(R.string.filter)
            .setSingleChoiceItems(options, selectedIndex) { dialog, which ->
                filterUnread = which == 1
                refreshDmails()
                dialog.dismiss()
            }
            .show()
    }

    private fun refreshDmails() {
        currentPage = 1
        hasMore = true
        dmails.clear()
        adapter.submitList(emptyList())
        loadDmails()
    }

    private fun loadDmails() {
        if (isLoading) return
        isLoading = true

        progressBar.visibility = if (dmails.isEmpty()) View.VISIBLE else View.GONE
        emptyView.visibility = View.GONE

        val api = E621Application.instance.api

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    api.dmails.list(
                        read = if (filterUnread) false else null,
                        page = currentPage,
                        limit = 50
                    )
                }

                if (response.isSuccessful) {
                    val newDmails = response.body() ?: emptyList()
                    
                    if (newDmails.isEmpty() && dmails.isEmpty()) {
                        emptyView.visibility = View.VISIBLE
                    } else {
                        dmails.addAll(newDmails)
                        adapter.submitList(dmails.toList())
                        hasMore = newDmails.size >= 50
                    }
                } else {
                    Toast.makeText(this@DmailActivity, R.string.error, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@DmailActivity, e.message ?: getString(R.string.error), Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
                progressBar.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
            }
        }
    }

    private fun loadMoreDmails() {
        currentPage++
        loadDmails()
    }

    private fun openDmail(dmail: Dmail) {
        // Mark as read
        markDmailRead(dmail)
        
        // Show dmail content in dialog
        AlertDialog.Builder(this)
            .setTitle(dmail.title)
            .setMessage("${getString(R.string.dmail_from, dmail.fromName ?: "User #${dmail.fromId}")}\n\n${dmail.body ?: ""}")
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun markDmailRead(dmail: Dmail) {
        if (dmail.isRead == true) return
        
        val api = E621Application.instance.api
        
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    api.dmails.markRead(dmail.id)
                }
                
                // Update local state
                val index = dmails.indexOfFirst { it.id == dmail.id }
                if (index >= 0) {
                    dmails[index] = dmail.copy(isRead = true)
                    adapter.submitList(dmails.toList())
                }
            } catch (e: Exception) {
                // Silently fail
            }
        }
    }

    private fun showNewDmailDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 0)
        }
        
        val toInput = EditText(this).apply {
            hint = getString(R.string.dmail_to)
        }
        val titleInput = EditText(this).apply {
            hint = getString(R.string.dmail_subject)
        }
        val bodyInput = EditText(this).apply {
            hint = getString(R.string.dmail_body)
            minLines = 4
        }
        
        layout.addView(toInput)
        layout.addView(titleInput)
        layout.addView(bodyInput)
        
        AlertDialog.Builder(this)
            .setTitle(R.string.new_dmail)
            .setView(layout)
            .setPositiveButton(R.string.dmail_send) { _, _ ->
                val to = toInput.text.toString().trim()
                val title = titleInput.text.toString().trim()
                val body = bodyInput.text.toString().trim()
                
                if (to.isNotEmpty() && title.isNotEmpty() && body.isNotEmpty()) {
                    sendDmail(to, title, body)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun sendDmail(toName: String, title: String, body: String) {
        val api = E621Application.instance.api
        
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    api.dmails.create(toName, title, body)
                }
                
                if (response.isSuccessful) {
                    Toast.makeText(this@DmailActivity, R.string.dmail_sent, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@DmailActivity, R.string.dmail_error, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@DmailActivity, R.string.dmail_error, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

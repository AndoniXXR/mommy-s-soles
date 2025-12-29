package com.e621.client.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.e621.client.R
import com.e621.client.utils.AppLog
import com.e621.client.utils.LogEntry
import com.e621.client.ui.adapter.LogAdapter
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Activity to view following/app logs
 * Shows history of tag checks and notifications
 */
class FollowingLogActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var txtEmpty: TextView
    private lateinit var adapter: LogAdapter
    
    private val logs = mutableListOf<LogEntry>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_following_log)
        
        setupToolbar()
        setupViews()
        loadLogs()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.pref_following_view_log_title)
    }

    private fun setupViews() {
        recyclerView = findViewById(R.id.recyclerView)
        progressBar = findViewById(R.id.progressBar)
        txtEmpty = findViewById(R.id.txtEmpty)
        
        adapter = LogAdapter(logs)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadLogs() {
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        txtEmpty.visibility = View.GONE
        
        CoroutineScope(Dispatchers.IO).launch {
            val logEntries = AppLog.getLogs(this@FollowingLogActivity)
            
            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                
                if (logEntries.isEmpty()) {
                    txtEmpty.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    txtEmpty.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    logs.clear()
                    logs.addAll(logEntries)
                    adapter.notifyDataSetChanged()
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_log, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            R.id.action_clear -> {
                showClearConfirmation()
                true
            }
            R.id.action_share -> {
                shareLogs()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showClearConfirmation() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.clear_logs_title)
            .setMessage(R.string.clear_logs_message)
            .setPositiveButton(R.string.clear) { _, _ ->
                clearLogs()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun clearLogs() {
        CoroutineScope(Dispatchers.IO).launch {
            AppLog.clearLogs(this@FollowingLogActivity)
            
            withContext(Dispatchers.Main) {
                logs.clear()
                adapter.notifyDataSetChanged()
                txtEmpty.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            }
        }
    }

    private fun shareLogs() {
        if (logs.isEmpty()) return
        
        val logText = logs.joinToString("\n") { it.toString() }
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "App Logs")
            putExtra(Intent.EXTRA_TEXT, logText)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_logs)))
    }
}

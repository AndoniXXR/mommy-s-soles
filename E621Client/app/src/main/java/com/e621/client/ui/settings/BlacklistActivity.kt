package com.e621.client.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.e621.client.E621Application
import com.e621.client.R
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Activity for managing the blacklist (import/export)
 * Based on decompiled BlacklistActivity
 */
class BlacklistActivity : AppCompatActivity() {

    private lateinit var editText: EditText
    private val prefs by lazy { E621Application.instance.userPreferences }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blacklist)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.blacklist)

        editText = findViewById(R.id.editTextBlacklist)
        
        // Hide keyboard initially
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(window.decorView.windowToken, 0)
        editText.clearFocus()

        // Load current blacklist
        loadBlacklist()
    }

    private fun loadBlacklist() {
        val blacklist = prefs.blacklist ?: ""
        editText.setText(blacklist)
    }

    private fun saveBlacklist() {
        val text = editText.text.toString().trim()
        prefs.blacklist = text
        Toast.makeText(this, R.string.blacklist_saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun copyToClipboard() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText("Blacklist", editText.text.toString().trim())
        clipboard?.setPrimaryClip(clip)
        Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }

    private fun shareAsPlainText() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, editText.text.toString().trim())
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_blacklist)))
    }

    private fun orderAlphabetically() {
        val lines = editText.text.toString()
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .sortedBy { it.lowercase() }
        
        editText.setText(lines.joinToString("\n"))
    }

    private fun showHelp() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.blacklist_menu_help)
            .setMessage(R.string.blacklist_menu_help_text)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_blacklist, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            R.id.save_blacklist -> {
                saveBlacklist()
                true
            }
            R.id.copy -> {
                copyToClipboard()
                true
            }
            R.id.share_plain_text -> {
                shareAsPlainText()
                true
            }
            R.id.order_alphabetically -> {
                orderAlphabetically()
                true
            }
            R.id.help -> {
                showHelp()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

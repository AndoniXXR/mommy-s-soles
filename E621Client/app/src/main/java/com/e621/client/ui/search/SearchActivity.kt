package com.e621.client.ui.search

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.e621.client.R
import com.google.android.material.appbar.MaterialToolbar

/**
 * Search Activity placeholder
 */
class SearchActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)
        
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.nav_search)
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

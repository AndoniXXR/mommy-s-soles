package com.e621.client.ui.pools

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.e621.client.R
import com.google.android.material.appbar.MaterialToolbar

/**
 * Pools Activity placeholder
 */
class PoolsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pools)
        
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.pools_title)
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

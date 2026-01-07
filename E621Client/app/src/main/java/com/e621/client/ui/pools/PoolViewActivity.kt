package com.e621.client.ui.pools

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.e621.client.E621Application
import com.e621.client.R
import com.e621.client.data.model.Post
import com.e621.client.ui.adapter.PostGridAdapter
import com.e621.client.ui.post.PostActivity
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Activity to display posts from a specific pool
 * Similar to the decompiled app's PoolActivity
 */
class PoolViewActivity : AppCompatActivity(), PostGridAdapter.OnPostClickListener {

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
        const val EXTRA_POOL_ID = "pool_id"
        const val EXTRA_POOL_NAME = "pool_name"
    }

    private lateinit var toolbar: MaterialToolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var layoutHeader: View
    private lateinit var txtPoolName: TextView
    private lateinit var txtPoolInfo: TextView
    private lateinit var layoutEmpty: View
    private lateinit var txtEmpty: TextView

    private val api by lazy { E621Application.instance.api }
    private var poolId: Int = -1
    private var poolName: String? = null
    private val posts = mutableListOf<Post>()
    private val selectedPostIds = mutableSetOf<Int>()
    private lateinit var adapter: PostGridAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pool_view)

        poolId = intent.getIntExtra(EXTRA_POOL_ID, -1)
        poolName = intent.getStringExtra(EXTRA_POOL_NAME)

        if (poolId == -1) {
            Toast.makeText(this, "Invalid pool ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupViews()
        setupRecyclerView()
        loadPool()
    }

    private fun setupViews() {
        toolbar = findViewById(R.id.toolbar)
        recyclerView = findViewById(R.id.recyclerView)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        progressBar = findViewById(R.id.progressBar)
        layoutHeader = findViewById(R.id.layoutHeader)
        txtPoolName = findViewById(R.id.txtPoolName)
        txtPoolInfo = findViewById(R.id.txtPoolInfo)
        layoutEmpty = findViewById(R.id.layoutEmpty)
        txtEmpty = findViewById(R.id.txtEmpty)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = poolName ?: getString(R.string.pool_title, poolId)

        swipeRefresh.setOnRefreshListener {
            loadPool()
        }
    }

    private fun setupRecyclerView() {
        adapter = PostGridAdapter(posts, selectedPostIds, this)
        recyclerView.layoutManager = GridLayoutManager(this, 3)
        recyclerView.adapter = adapter
    }

    private fun loadPool() {
        lifecycleScope.launch {
            try {
                progressBar.visibility = View.VISIBLE
                layoutEmpty.visibility = View.GONE

                // First, get pool info
                val poolResponse = api.pools.get(poolId)
                if (poolResponse.isSuccessful) {
                    val pool = poolResponse.body()
                    pool?.let {
                        poolName = it.name?.replace("_", " ")
                        supportActionBar?.title = poolName
                        
                        layoutHeader.visibility = View.VISIBLE
                        txtPoolName.text = poolName
                        
                        val postCount = it.postCount ?: it.postIds?.size ?: 0
                        txtPoolInfo.text = getString(R.string.pool_info, postCount)
                    }
                }

                // Load posts from pool
                val postsResponse = api.posts.list(tags = "pool:$poolId", page = 1, limit = 320)
                if (postsResponse.isSuccessful) {
                    val newPosts = postsResponse.body()?.posts ?: emptyList()
                    posts.clear()
                    posts.addAll(newPosts)
                    adapter.notifyDataSetChanged()

                    if (posts.isEmpty()) {
                        layoutEmpty.visibility = View.VISIBLE
                    }
                } else {
                    Toast.makeText(this@PoolViewActivity, "Error loading pool posts", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                Toast.makeText(this@PoolViewActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = View.GONE
                swipeRefresh.isRefreshing = false
            }
        }
    }

    override fun onPostClick(post: Post, position: Int) {
        // Open PostActivity with all pool posts
        PostActivity.POSTS_TO_SHOW = posts
        val intent = Intent(this, PostActivity::class.java)
        intent.putExtra(PostActivity.EXTRA_POSITION, position)
        startActivity(intent)
    }

    override fun onPostLongClick(post: Post, position: Int): Boolean {
        // Could show post info dialog or other actions
        return true
    }

    override fun onSelectionChanged() {
        // Pool view doesn't have selection bar, so just refresh the adapter
        adapter.notifyDataSetChanged()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

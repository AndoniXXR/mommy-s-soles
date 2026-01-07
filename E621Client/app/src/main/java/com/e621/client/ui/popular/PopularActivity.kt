package com.e621.client.ui.popular

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.e621.client.E621Application
import com.e621.client.R
import com.e621.client.data.model.Post
import com.e621.client.ui.adapter.SelectablePostGridAdapter
import com.e621.client.ui.post.PostActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * Popular Activity - Shows popular posts by day/week/month
 */
class PopularActivity : AppCompatActivity() {

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
        const val EXTRA_SCALE = "scale"
        const val SCALE_DAY = "day"
        const val SCALE_WEEK = "week"
        const val SCALE_MONTH = "month"
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var layoutEmpty: View
    private lateinit var txtDate: TextView
    private lateinit var tabLayout: TabLayout
    private lateinit var selectionBar: MaterialCardView
    private lateinit var txtSelectionCount: TextView
    
    private lateinit var adapter: SelectablePostGridAdapter
    private val posts = mutableListOf<Post>()
    private val selectedPosts = mutableSetOf<Int>()
    
    private var currentScale = SCALE_DAY
    private var currentDate = Calendar.getInstance()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayDateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.US)
    private val displayWeekFormat = SimpleDateFormat("'Week of' MMM d, yyyy", Locale.US)
    private val displayMonthFormat = SimpleDateFormat("MMMM yyyy", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_popular)

        // Get initial scale from intent
        currentScale = intent.getStringExtra(EXTRA_SCALE) ?: SCALE_DAY

        setupToolbar()
        setupViews()
        setupTabs()
        loadPosts()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        updateTitle()
    }

    private fun setupViews() {
        recyclerView = findViewById(R.id.recyclerView)
        progressBar = findViewById(R.id.progressBar)
        layoutEmpty = findViewById(R.id.layoutEmpty)
        txtDate = findViewById(R.id.txtDate)
        tabLayout = findViewById(R.id.tabLayout)
        selectionBar = findViewById(R.id.selectionBar)
        txtSelectionCount = findViewById(R.id.txtSelectionCount)

        // Setup RecyclerView
        adapter = SelectablePostGridAdapter(
            posts = posts,
            selectedPosts = selectedPosts,
            onPostClick = { post -> openPost(post) },
            onPostLongClick = { post -> toggleSelection(post) },
            onSelectionChange = { updateSelectionBar() }
        )
        recyclerView.layoutManager = GridLayoutManager(this, 3)
        recyclerView.adapter = adapter

        // Date navigation
        txtDate.setOnClickListener { showDatePicker() }
        findViewById<ImageButton>(R.id.btnPrevious).setOnClickListener { navigateDate(-1) }
        findViewById<ImageButton>(R.id.btnNext).setOnClickListener { navigateDate(1) }

        // Selection bar buttons
        findViewById<ImageButton>(R.id.btnClearSelection).setOnClickListener { clearSelection() }
        findViewById<ImageButton>(R.id.btnDownloadSelected).setOnClickListener { downloadSelected() }

        updateDateDisplay()
    }

    private fun setupTabs() {
        tabLayout.addTab(tabLayout.newTab().setText(R.string.popular_day))
        tabLayout.addTab(tabLayout.newTab().setText(R.string.popular_week))
        tabLayout.addTab(tabLayout.newTab().setText(R.string.popular_month))

        // Select initial tab
        val initialTab = when (currentScale) {
            SCALE_WEEK -> 1
            SCALE_MONTH -> 2
            else -> 0
        }
        tabLayout.selectTab(tabLayout.getTabAt(initialTab))

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentScale = when (tab?.position) {
                    1 -> SCALE_WEEK
                    2 -> SCALE_MONTH
                    else -> SCALE_DAY
                }
                updateTitle()
                updateDateDisplay()
                loadPosts()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun updateTitle() {
        supportActionBar?.title = when (currentScale) {
            SCALE_WEEK -> getString(R.string.menu_popular_week)
            SCALE_MONTH -> getString(R.string.menu_popular_month)
            else -> getString(R.string.menu_popular_day)
        }
    }

    private fun updateDateDisplay() {
        txtDate.text = when (currentScale) {
            SCALE_WEEK -> displayWeekFormat.format(currentDate.time)
            SCALE_MONTH -> displayMonthFormat.format(currentDate.time)
            else -> displayDateFormat.format(currentDate.time)
        }
    }

    private fun navigateDate(direction: Int) {
        when (currentScale) {
            SCALE_DAY -> currentDate.add(Calendar.DAY_OF_MONTH, direction)
            SCALE_WEEK -> currentDate.add(Calendar.WEEK_OF_YEAR, direction)
            SCALE_MONTH -> currentDate.add(Calendar.MONTH, direction)
        }
        updateDateDisplay()
        loadPosts()
    }

    private fun showDatePicker() {
        val year = currentDate.get(Calendar.YEAR)
        val month = currentDate.get(Calendar.MONTH)
        val day = currentDate.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
            currentDate.set(selectedYear, selectedMonth, selectedDay)
            updateDateDisplay()
            loadPosts()
        }, year, month, day).apply {
            datePicker.maxDate = System.currentTimeMillis()
            show()
        }
    }

    private fun loadPosts() {
        progressBar.visibility = View.VISIBLE
        layoutEmpty.visibility = View.GONE
        recyclerView.visibility = View.GONE

        val api = E621Application.instance.api
        val dateStr = dateFormat.format(currentDate.time)

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    api.popular.get(currentScale, dateStr)
                }

                if (response.isSuccessful) {
                    val newPosts = response.body()?.posts ?: emptyList()
                    posts.clear()
                    posts.addAll(newPosts)
                    adapter.notifyDataSetChanged()

                    if (posts.isEmpty()) {
                        layoutEmpty.visibility = View.VISIBLE
                        recyclerView.visibility = View.GONE
                    } else {
                        layoutEmpty.visibility = View.GONE
                        recyclerView.visibility = View.VISIBLE
                    }
                } else {
                    Toast.makeText(this@PopularActivity, 
                        "Error: ${response.message()}", Toast.LENGTH_SHORT).show()
                    layoutEmpty.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                Toast.makeText(this@PopularActivity, 
                    "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                layoutEmpty.visibility = View.VISIBLE
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun openPost(post: Post) {
        if (selectedPosts.isNotEmpty()) {
            toggleSelection(post)
        } else {
            val intent = Intent(this, PostActivity::class.java)
            intent.putExtra("post_id", post.id)
            startActivity(intent)
        }
    }

    private fun toggleSelection(post: Post) {
        if (selectedPosts.contains(post.id)) {
            selectedPosts.remove(post.id)
        } else {
            selectedPosts.add(post.id)
        }
        adapter.notifyDataSetChanged()
        updateSelectionBar()
    }

    private fun updateSelectionBar() {
        if (selectedPosts.isEmpty()) {
            selectionBar.visibility = View.GONE
        } else {
            selectionBar.visibility = View.VISIBLE
            txtSelectionCount.text = getString(R.string.popular_selected, selectedPosts.size)
        }
    }

    private fun clearSelection() {
        selectedPosts.clear()
        adapter.notifyDataSetChanged()
        updateSelectionBar()
    }

    private fun downloadSelected() {
        // TODO: Implement download queue
        Toast.makeText(this, "Download ${selectedPosts.size} posts - Coming soon", Toast.LENGTH_SHORT).show()
        clearSelection()
    }
}

package com.e621.client.ui.comments

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
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
import com.e621.client.data.api.CloudFlareException
import com.e621.client.data.api.ServerDownException
import com.e621.client.data.api.NetworkException
import com.e621.client.data.api.NetworkErrorType
import com.e621.client.data.model.Comment
import com.e621.client.ui.adapter.CommentAdapter
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * CommentsActivity - Browse comments or view comments for a specific post
 */
class CommentsActivity : AppCompatActivity() {

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
        const val EXTRA_POST_ID = "post_id"
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyView: TextView
    private lateinit var fabAddComment: FloatingActionButton

    private lateinit var adapter: CommentAdapter
    private val comments = mutableListOf<Comment>()
    
    private var postId: Int? = null
    private var currentPage = 1
    private var isLoading = false
    private var hasMore = true
    private var currentOrder = "id_desc" // newest first

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_comments)

        postId = if (intent.hasExtra(EXTRA_POST_ID)) {
            intent.getIntExtra(EXTRA_POST_ID, -1).takeIf { it > 0 }
        } else null

        setupToolbar()
        setupViews()
        loadComments()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        
        supportActionBar?.title = if (postId != null) {
            getString(R.string.post_comments, 0)
        } else {
            getString(R.string.comments_title)
        }
    }

    private fun setupViews() {
        recyclerView = findViewById(R.id.commentsRecyclerView)
        progressBar = findViewById(R.id.progressBar)
        emptyView = findViewById(R.id.emptyView)
        fabAddComment = findViewById(R.id.fabAddComment)

        adapter = CommentAdapter(
            onVoteUp = { comment -> voteComment(comment, 1) },
            onVoteDown = { comment -> voteComment(comment, -1) },
            onUserClick = { username -> /* Navigate to user profile */ }
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
                        loadMoreComments()
                    }
                }
            }
        })

        // FAB for adding comment (only if logged in and viewing post comments)
        if (E621Application.instance.prefs.isLoggedIn && postId != null) {
            fabAddComment.visibility = View.VISIBLE
            fabAddComment.setOnClickListener { showAddCommentDialog() }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_comments, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_sort -> {
                showSortDialog()
                true
            }
            R.id.action_refresh -> {
                refreshComments()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showSortDialog() {
        val options = arrayOf(
            getString(R.string.sort_newest),
            getString(R.string.sort_oldest),
            getString(R.string.sort_score)
        )
        
        AlertDialog.Builder(this)
            .setTitle(R.string.sort_by)
            .setItems(options) { _, which ->
                currentOrder = when (which) {
                    0 -> "id_desc"
                    1 -> "id_asc"
                    2 -> "score"
                    else -> "id_desc"
                }
                refreshComments()
            }
            .show()
    }

    private fun refreshComments() {
        currentPage = 1
        hasMore = true
        comments.clear()
        adapter.submitList(emptyList())
        loadComments()
    }

    private fun loadComments() {
        if (isLoading) return
        isLoading = true

        progressBar.visibility = if (comments.isEmpty()) View.VISIBLE else View.GONE
        emptyView.visibility = View.GONE

        val api = E621Application.instance.api

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    api.comments.list(
                        postId = postId,
                        order = currentOrder,
                        page = currentPage,
                        limit = 50
                    )
                }

                if (response.isSuccessful) {
                    val newComments = response.body() ?: emptyList()
                    
                    if (newComments.isEmpty() && comments.isEmpty()) {
                        emptyView.visibility = View.VISIBLE
                    } else {
                        comments.addAll(newComments)
                        adapter.submitList(comments.toList())
                        hasMore = newComments.size >= 50
                    }

                    // Update title with count for post comments
                    if (postId != null) {
                        supportActionBar?.title = getString(R.string.post_comments, comments.size)
                    }
                } else {
                    showApiError(response.code())
                }
            } catch (e: CloudFlareException) {
                Toast.makeText(this@CommentsActivity, R.string.error_cloudflare, Toast.LENGTH_LONG).show()
            } catch (e: ServerDownException) {
                Toast.makeText(this@CommentsActivity, R.string.error_server_down, Toast.LENGTH_LONG).show()
            } catch (e: NetworkException) {
                val message = when (e.type) {
                    NetworkErrorType.TIMEOUT -> getString(R.string.error_timeout)
                    NetworkErrorType.NO_INTERNET -> getString(R.string.error_no_internet)
                    else -> getString(R.string.error_connection)
                }
                Toast.makeText(this@CommentsActivity, message, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this@CommentsActivity, e.message ?: getString(R.string.error), Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
                progressBar.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
            }
        }
    }

    private fun loadMoreComments() {
        currentPage++
        loadComments()
    }

    private fun voteComment(comment: Comment, score: Int) {
        val api = E621Application.instance.api
        
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    api.comments.vote(comment.id, score)
                }
                
                if (response.isSuccessful) {
                    // Refresh comment to show updated score
                    val index = comments.indexOfFirst { it.id == comment.id }
                    if (index >= 0) {
                        val updatedComment = comment.copy(score = response.body()?.score ?: comment.score)
                        comments[index] = updatedComment
                        adapter.submitList(comments.toList())
                    }
                } else {
                    // Handle specific vote errors
                    val errorMsg = when (response.code()) {
                        422 -> {
                            val authorName = comment.creatorName ?: "User #${comment.creatorId}"
                            getString(R.string.error_vote_own_comment_name, authorName)
                        }
                        else -> getString(R.string.error_voting)
                    }
                    Toast.makeText(this@CommentsActivity, errorMsg, Toast.LENGTH_SHORT).show()
                }
            } catch (e: CloudFlareException) {
                Toast.makeText(this@CommentsActivity, R.string.error_cloudflare, Toast.LENGTH_LONG).show()
            } catch (e: NetworkException) {
                val message = when (e.type) {
                    NetworkErrorType.TIMEOUT -> getString(R.string.error_timeout)
                    NetworkErrorType.NO_INTERNET -> getString(R.string.error_no_internet)
                    else -> getString(R.string.error_connection)
                }
                Toast.makeText(this@CommentsActivity, message, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this@CommentsActivity, e.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showAddCommentDialog() {
        val editText = EditText(this).apply {
            hint = getString(R.string.comment_hint)
            minLines = 3
        }
        
        AlertDialog.Builder(this)
            .setTitle(R.string.add_comment)
            .setView(editText)
            .setPositiveButton(R.string.ok) { _, _ ->
                val body = editText.text.toString().trim()
                if (body.isNotEmpty() && postId != null) {
                    postComment(postId!!, body)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun postComment(postId: Int, body: String) {
        val api = E621Application.instance.api
        
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    api.comments.create(postId, body)
                }
                
                if (response.isSuccessful) {
                    Toast.makeText(this@CommentsActivity, R.string.comment_added, Toast.LENGTH_SHORT).show()
                    refreshComments()
                } else {
                    val errorMsg = when (response.code()) {
                        422 -> getString(R.string.error_comment_too_short)
                        else -> getString(R.string.error_comment_failed)
                    }
                    Toast.makeText(this@CommentsActivity, errorMsg, Toast.LENGTH_SHORT).show()
                }
            } catch (e: CloudFlareException) {
                Toast.makeText(this@CommentsActivity, R.string.error_cloudflare, Toast.LENGTH_LONG).show()
            } catch (e: NetworkException) {
                val message = when (e.type) {
                    NetworkErrorType.TIMEOUT -> getString(R.string.error_timeout)
                    NetworkErrorType.NO_INTERNET -> getString(R.string.error_no_internet)
                    else -> getString(R.string.error_connection)
                }
                Toast.makeText(this@CommentsActivity, message, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this@CommentsActivity, R.string.error_comment_failed, Toast.LENGTH_SHORT).show()
            }
        }
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

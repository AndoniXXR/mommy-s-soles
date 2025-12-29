package com.e621.client.ui.adapter

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.e621.client.E621Application
import com.e621.client.R
import com.e621.client.data.api.ApiErrorHandler
import com.e621.client.data.api.AuthenticationException
import com.e621.client.data.api.CloudFlareException
import com.e621.client.data.api.NetworkErrorType
import com.e621.client.data.api.NetworkException
import com.e621.client.data.api.ServerDownException
import com.e621.client.data.model.Post
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewPager2 adapter that shows a page of posts as a grid.
 * Each page is a RecyclerView with posts for that page number.
 * This allows swipe gestures to navigate between pages with caching.
 */
class PostPageAdapter(
    private val listener: OnPostClickListener,
    private val onPageLoad: (page: Int) -> Unit
) : RecyclerView.Adapter<PostPageAdapter.PageViewHolder>() {

    interface OnPostClickListener {
        fun onPostClick(post: Post, position: Int)
        fun onPostLongClick(post: Post, position: Int): Boolean
        fun onSelectionChanged()
    }

    // Cache of loaded pages: page number -> list of posts (unfiltered)
    private val pageCache = mutableMapOf<Int, List<Post>>()
    // Filtered posts per page
    private val filteredCache = mutableMapOf<Int, List<Post>>()
    private var currentTags = ""
    private var totalPages = Int.MAX_VALUE // We don't know the total until we get an empty response
    private var tagsVersion = 0 // Version counter to invalidate old requests
    
    // Filter state
    private var filterRating = 7 // bitmask: 1=e, 2=q, 4=s
    private var filterType = 0 // 0=all, 1=images, 2=videos, 3=gifs
    private var filterOrder = 0 // 0=newest, 1=oldest, 2=score, 3=favcount
    
    // Selection state
    val selectedPostIds = mutableSetOf<Int>()
    val isInSelectionMode: Boolean
        get() = selectedPostIds.isNotEmpty()
    
    // Use getter instead of lazy to always get current API instance (may change when host changes)
    private val api get() = E621Application.instance.api
    private val prefs by lazy { E621Application.instance.userPreferences }

    fun setTags(tags: String) {
        val newTags = tags.trim()
        if (currentTags != newTags) {
            currentTags = newTags
            tagsVersion++ // Increment version to invalidate pending requests
            clearCache()
        }
    }
    
    fun forceRefresh() {
        tagsVersion++
        clearCache()
    }
    
    /**
     * Apply filters from preferences and refresh display
     */
    fun applyFilters(rating: Int, type: Int, order: Int) {
        filterRating = rating
        filterType = type
        filterOrder = order
        
        // Re-filter all cached pages
        filteredCache.clear()
        for ((page, posts) in pageCache) {
            filteredCache[page] = filterAndSortPosts(posts)
        }
        notifyDataSetChanged()
    }
    
    /**
     * Filter and sort a list of posts based on current filter settings
     */
    private fun filterAndSortPosts(posts: List<Post>): List<Post> {
        // Filter by rating
        val showExplicit = (filterRating and 1) != 0
        val showQuestionable = (filterRating and 2) != 0
        val showSafe = (filterRating and 4) != 0
        
        var filtered = posts.filter { post ->
            when (post.rating) {
                "e" -> showExplicit
                "q" -> showQuestionable
                "s" -> showSafe
                else -> true
            }
        }
        
        // Filter by type
        filtered = when (filterType) {
            1 -> filtered.filter { it.isImage() } // Only images
            2 -> filtered.filter { it.isVideo() } // Only videos
            3 -> filtered.filter { it.isGif() }   // Only gifs
            else -> filtered // 0 = all
        }
        
        // Sort by order
        val sorted = when (filterOrder) {
            1 -> filtered.sortedBy { it.id } // Oldest first (lowest ID)
            2 -> filtered.sortedByDescending { it.score.total } // Highest score
            3 -> filtered.sortedByDescending { it.favCount } // Most favorites
            else -> filtered // 0 = newest (API default order)
        }
        
        return sorted
    }

    fun clearCache() {
        pageCache.clear()
        filteredCache.clear()
        totalPages = Int.MAX_VALUE
        notifyDataSetChanged()
    }

    fun getCachedPosts(page: Int): List<Post>? = filteredCache[page]

    fun setCachedPosts(page: Int, posts: List<Post>) {
        pageCache[page] = posts
        filteredCache[page] = filterAndSortPosts(posts)
        if (posts.isEmpty() && page < totalPages) {
            totalPages = page
        }
    }
    
    /**
     * Returns all posts currently loaded in cache (filtered)
     */
    fun getAllLoadedPosts(): List<Post> {
        return filteredCache.values.flatten()
    }
    
    /**
     * Get list of selected posts
     */
    fun getSelectedPosts(): List<Post> {
        return getAllLoadedPosts().filter { selectedPostIds.contains(it.id) }
    }
    
    /**
     * Clear selection
     */
    fun clearSelection() {
        selectedPostIds.clear()
        notifyDataSetChanged()
        listener.onSelectionChanged()
    }
    
    /**
     * Select all visible posts
     */
    fun selectAll() {
        getAllLoadedPosts().forEach { selectedPostIds.add(it.id) }
        notifyDataSetChanged()
        listener.onSelectionChanged()
    }

    override fun getItemCount(): Int = totalPages

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.page_post_grid, parent, false)
        return PageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        val pageNumber = position + 1 // Pages are 1-indexed
        holder.bind(pageNumber)
    }

    inner class PageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val recyclerView: RecyclerView = itemView.findViewById(R.id.pageRecyclerView)
        private val progressBar: View = itemView.findViewById(R.id.pageProgressBar)
        private val errorContainer: View = itemView.findViewById(R.id.errorContainer)
        private val errorText: TextView = itemView.findViewById(R.id.pageErrorText)
        private val retryButton: Button = itemView.findViewById(R.id.btnRetry)
        
        private var currentPage = 0
        private val posts = mutableListOf<Post>()
        private lateinit var gridAdapter: PostGridItemAdapter

        fun bind(page: Int) {
            currentPage = page
            
            // Setup grid adapter
            gridAdapter = PostGridItemAdapter(posts, listener)
            recyclerView.layoutManager = GridLayoutManager(itemView.context, prefs.gridWidth)
            recyclerView.adapter = gridAdapter
            
            // Check cache first (use filtered cache for display)
            val cachedPosts = filteredCache[page]
            if (cachedPosts != null) {
                posts.clear()
                posts.addAll(cachedPosts)
                gridAdapter.notifyDataSetChanged()
                showContent()
                return
            }

            // Load posts for this page
            loadPage(page)
        }

        private fun loadPage(page: Int) {
            showLoading()
            onPageLoad(page)
            
            // Capture current version to detect if tags changed during load
            val requestVersion = tagsVersion
            val requestTags = currentTags
            
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    Log.d("E621Client", "Loading page $page, tags='$requestTags', version=$requestVersion")
                    
                    val response = api.posts.list(
                        tags = requestTags.ifEmpty { null },
                        page = page,
                        limit = 75
                    )
                    
                    withContext(Dispatchers.Main) {
                        // Check if tags changed during request - if so, discard result
                        if (requestVersion != tagsVersion) {
                            Log.d("E621Client", "Discarding stale response for page $page (version $requestVersion, current $tagsVersion)")
                            return@withContext
                        }
                        
                        if (response.isSuccessful) {
                            val newPosts = response.body()?.posts ?: emptyList()
                            Log.d("E621Client", "Page $page loaded ${newPosts.size} posts")
                            
                            // Update cache (raw posts)
                            pageCache[page] = newPosts
                            // Update filtered cache
                            val filteredPosts = filterAndSortPosts(newPosts)
                            filteredCache[page] = filteredPosts
                            
                            // Check if this is the last page
                            if (newPosts.isEmpty()) {
                                totalPages = page
                                notifyDataSetChanged()
                            }
                            
                            // Update UI if still on same page (use filtered posts)
                            if (currentPage == page) {
                                posts.clear()
                                posts.addAll(filteredPosts)
                                gridAdapter.notifyDataSetChanged()
                                showContent()
                            }
                        } else {
                            Log.e("E621Client", "API error: ${response.code()}")
                            val errorMessage = getErrorMessageForCode(response.code())
                            showError(errorMessage, page)
                        }
                    }
                } catch (e: CloudFlareException) {
                    Log.e("E621Client", "CloudFlare error on page $page: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        if (requestVersion == tagsVersion) {
                            showError(itemView.context.getString(R.string.error_cloudflare), page)
                        }
                    }
                } catch (e: ServerDownException) {
                    Log.e("E621Client", "Server down on page $page: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        if (requestVersion == tagsVersion) {
                            showError(itemView.context.getString(R.string.error_server_down), page)
                        }
                    }
                } catch (e: AuthenticationException) {
                    Log.e("E621Client", "Auth error on page $page: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        if (requestVersion == tagsVersion) {
                            showError(itemView.context.getString(R.string.error_auth_failed), page)
                        }
                    }
                } catch (e: NetworkException) {
                    Log.e("E621Client", "Network error on page $page: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        if (requestVersion == tagsVersion) {
                            val message = when (e.type) {
                                NetworkErrorType.TIMEOUT -> itemView.context.getString(R.string.error_timeout)
                                NetworkErrorType.NO_INTERNET -> itemView.context.getString(R.string.error_no_internet)
                                else -> itemView.context.getString(R.string.error_connection)
                            }
                            showError(message, page)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("E621Client", "Exception loading page $page: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        // Only show error if tags haven't changed
                        if (requestVersion == tagsVersion) {
                            showError(itemView.context.getString(R.string.error_generic, e.message ?: "Unknown error"), page)
                        }
                    }
                }
            }
        }
        
        private fun getErrorMessageForCode(code: Int): String {
            return when (code) {
                401 -> itemView.context.getString(R.string.error_auth_failed)
                403 -> itemView.context.getString(R.string.error_forbidden)
                404 -> itemView.context.getString(R.string.error_not_found)
                500, 502, 503, 504 -> itemView.context.getString(R.string.error_server_down)
                else -> itemView.context.getString(R.string.error_http_code, code)
            }
        }

        private fun showLoading() {
            progressBar.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            errorContainer.visibility = View.GONE
        }

        private fun showContent() {
            progressBar.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            errorContainer.visibility = View.GONE
        }

        private fun showError(message: String, page: Int) {
            progressBar.visibility = View.GONE
            recyclerView.visibility = View.GONE
            errorContainer.visibility = View.VISIBLE
            errorText.text = message
            
            // Setup retry button
            retryButton.setOnClickListener {
                loadPage(page)
            }
        }
    }

    /**
     * Inner adapter for the grid items within each page
     */
    inner class PostGridItemAdapter(
        private val posts: List<Post>,
        private val listener: OnPostClickListener
    ) : RecyclerView.Adapter<PostGridItemAdapter.PostViewHolder>() {

        inner class PostViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val imageView: ImageView = itemView.findViewById(R.id.imageView)
            private val imgTypeIndicator: ImageView = itemView.findViewById(R.id.imgTypeIndicator)
            private val imgSelected: ImageView = itemView.findViewById(R.id.imgSelected)
            private val txtInfo: TextView = itemView.findViewById(R.id.txtInfo)
            private val viewRating: View = itemView.findViewById(R.id.viewRating)
            private val fLOverlay: FrameLayout = itemView.findViewById(R.id.fLOverlay)

            fun bind(post: Post) {
                // Load thumbnail
                val imageUrl = post.preview.url ?: post.sample.url ?: post.file.url
                Glide.with(itemView.context)
                    .load(imageUrl)
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_image_error)
                    .centerCrop()
                    .into(imageView)

                // Type indicator
                when {
                    post.isVideo() -> {
                        imgTypeIndicator.visibility = View.VISIBLE
                        imgTypeIndicator.setImageResource(R.drawable.ic_video)
                    }
                    post.isGif() -> {
                        imgTypeIndicator.visibility = View.VISIBLE
                        imgTypeIndicator.setImageResource(R.drawable.ic_gif)
                    }
                    else -> imgTypeIndicator.visibility = View.GONE
                }

                // Score
                val score = post.score.total
                if (score != 0) {
                    txtInfo.visibility = View.VISIBLE
                    txtInfo.text = if (score > 0) "↑$score" else "↓$score"
                } else {
                    txtInfo.visibility = View.GONE
                }

                // Rating color
                val ratingColor = when (post.rating) {
                    "s" -> R.color.rating_safe
                    "q" -> R.color.rating_questionable
                    else -> R.color.rating_explicit
                }
                viewRating.setBackgroundColor(ContextCompat.getColor(itemView.context, ratingColor))
                
                // Selection state
                val isSelected = selectedPostIds.contains(post.id)
                imgSelected.visibility = if (isSelected) View.VISIBLE else View.GONE
                fLOverlay.visibility = if (isSelected) View.VISIBLE else View.GONE

                // Click listeners
                itemView.setOnClickListener {
                    if (isInSelectionMode) {
                        toggleSelection(post)
                    } else {
                        listener.onPostClick(post, bindingAdapterPosition)
                    }
                }
                itemView.setOnLongClickListener {
                    toggleSelection(post)
                    true
                }
            }
            
            private fun toggleSelection(post: Post) {
                val isCurrentlySelected = selectedPostIds.contains(post.id)
                
                if (isCurrentlySelected) {
                    selectedPostIds.remove(post.id)
                } else {
                    selectedPostIds.add(post.id)
                }
                
                // Update UI
                imgSelected.visibility = if (!isCurrentlySelected) View.VISIBLE else View.GONE
                fLOverlay.visibility = if (!isCurrentlySelected) View.VISIBLE else View.GONE
                
                // Vibration feedback
                vibrate()
                
                // Notify listener
                listener.onSelectionChanged()
            }
            
            private fun vibrate() {
                val vibrator = itemView.context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.let {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        it.vibrate(VibrationEffect.createOneShot(5, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        it.vibrate(5)
                    }
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_post_grid, parent, false)
            return PostViewHolder(view)
        }

        override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
            holder.bind(posts[position])
        }

        override fun getItemCount(): Int = posts.size
    }
}

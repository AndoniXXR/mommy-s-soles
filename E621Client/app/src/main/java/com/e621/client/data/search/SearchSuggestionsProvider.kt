package com.e621.client.data.search

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.BaseColumns
import android.util.Log
import com.e621.client.E621Application
import kotlinx.coroutines.runBlocking

/**
 * Content Provider for search suggestions.
 * Provides both search history and real-time tag suggestions from the API.
 */
class SearchSuggestionsProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.e621.client.searchprovider"
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/suggestions")
        
        private const val SUGGESTIONS = 1
        
        // Cursor columns for suggestions
        val COLUMNS = arrayOf(
            BaseColumns._ID,
            "suggest_text_1",
            "suggest_text_2", 
            "suggest_icon_1",
            "suggest_intent_data"
        )
    }
    
    private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
        addURI(AUTHORITY, "suggestions/*", SUGGESTIONS)
    }
    
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val cursor = MatrixCursor(COLUMNS)
        
        // Get the search query from the URI
        val query = uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: return cursor
        
        Log.d("SearchProvider", "Query: $query")
        
        val context = context ?: return cursor
        val prefs = E621Application.instance.userPreferences
        
        // First add history matches
        val history = prefs.searchHistory
        var id = 0L
        
        history.filter { it.contains(query, ignoreCase = true) }
            .take(3)
            .forEach { historyItem ->
                cursor.addRow(arrayOf(
                    id++,
                    historyItem,
                    "History",
                    android.R.drawable.ic_menu_recent_history,
                    historyItem
                ))
            }
        
        // Then add API tag suggestions
        if (query.length >= 2) {
            try {
                runBlocking {
                    val api = E621Application.instance.api
                    val response = api.tags.autocomplete(query, 7)
                    
                    if (response.isSuccessful) {
                        response.body()?.forEach { tag ->
                            cursor.addRow(arrayOf(
                                id++,
                                tag.name,
                                "${tag.category}: ${tag.postCount} posts",
                                android.R.drawable.ic_menu_search,
                                tag.name
                            ))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SearchProvider", "Error fetching suggestions: ${e.message}")
            }
        }
        
        return cursor
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}

package com.e621.client.data.api

import com.e621.client.data.model.*
import com.e621.client.data.preferences.UserPreferences
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

/**
 * e621 API interface
 * Based on official e621 API documentation
 */
interface E621ApiService {

    // ==================== POSTS ====================
    
    /**
     * List posts with optional search tags
     */
    @GET("posts.json")
    suspend fun getPosts(
        @Query("tags") tags: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 75
    ): Response<PostsResponse>
    
    /**
     * Get a single post by ID
     */
    @GET("posts/{id}.json")
    suspend fun getPost(
        @Path("id") postId: Int
    ): Response<PostResponse>
    
    /**
     * Vote on a post (requires authentication)
     */
    @FormUrlEncoded
    @POST("posts/{id}/votes.json")
    suspend fun votePost(
        @Path("id") postId: Int,
        @Field("score") score: Int, // 1, -1, or 0 to remove
        @Field("no_unvote") noUnvote: Boolean = false
    ): Response<VoteResponse>
    
    /**
     * Favorite a post (requires authentication)
     */
    @FormUrlEncoded
    @POST("favorites.json")
    suspend fun addFavorite(
        @Field("post_id") postId: Int
    ): Response<FavoriteResponse>
    
    /**
     * Remove favorite (requires authentication)
     */
    @DELETE("favorites/{id}.json")
    suspend fun removeFavorite(
        @Path("id") postId: Int
    ): Response<Unit>
    
    // ==================== POOLS ====================
    
    /**
     * List pools
     */
    @GET("pools.json")
    suspend fun getPools(
        @Query("search[name_matches]") nameMatches: String? = null,
        @Query("search[category]") category: String? = null,
        @Query("search[order]") order: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 75
    ): Response<List<Pool>>
    
    /**
     * Get a single pool
     */
    @GET("pools/{id}.json")
    suspend fun getPool(
        @Path("id") poolId: Int
    ): Response<Pool>
    
    // ==================== TAGS ====================
    
    /**
     * Search tags
     */
    @GET("tags.json")
    suspend fun getTags(
        @Query("search[name_matches]") nameMatches: String? = null,
        @Query("search[order]") order: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 75
    ): Response<List<E621Tag>>
    
    /**
     * Tag autocomplete
     */
    @GET("tags/autocomplete.json")
    suspend fun autocomplete(
        @Query("search[name_matches]") query: String,
        @Query("limit") limit: Int = 10
    ): Response<List<TagAutocomplete>>
    
    // ==================== USERS ====================
    
    /**
     * Get current user info
     */
    @GET("users/{id}.json")
    suspend fun getUser(
        @Path("id") userId: Int
    ): Response<User>
    
    /**
     * Get user by name
     */
    @GET("users/{name}.json")
    suspend fun getUserByName(
        @Path("name") username: String
    ): Response<User>
    
    /**
     * List/search users
     */
    @GET("users.json")
    suspend fun getUsers(
        @Query("search[name_matches]") nameMatches: String? = null,
        @Query("search[level]") level: Int? = null,
        @Query("search[order]") order: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 75
    ): Response<List<User>>

    // ==================== WIKI PAGES ====================
    
    /**
     * Search wiki pages
     */
    @GET("wiki_pages.json")
    suspend fun getWikiPages(
        @Query("search[title]") title: String? = null,
        @Query("search[body_matches]") bodyMatches: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 75
    ): Response<List<WikiPage>>
    
    /**
     * Get a single wiki page by ID
     */
    @GET("wiki_pages/{id}.json")
    suspend fun getWikiPage(
        @Path("id") wikiId: Int
    ): Response<WikiPage>

    // ==================== POPULAR ====================
    
    /**
     * Get popular posts
     * @param scale - day, week, or month
     * @param date - date in YYYY-MM-DD format (optional)
     */
    @GET("popular.json")
    suspend fun getPopular(
        @Query("scale") scale: String,
        @Query("date") date: String? = null
    ): Response<PostsResponse>
    
    // ==================== COMMENTS ====================
    
    /**
     * List comments (requires group_by=comment for proper results)
     */
    @GET("comments.json")
    suspend fun getComments(
        @Query("group_by") groupBy: String = "comment",
        @Query("search[post_id]") postId: Int? = null,
        @Query("search[creator_name]") creatorName: String? = null,
        @Query("search[body_matches]") bodyMatches: String? = null,
        @Query("search[order]") order: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 75
    ): Response<List<Comment>>
    
    /**
     * Get a single comment
     */
    @GET("comments/{id}.json")
    suspend fun getComment(
        @Path("id") commentId: Int
    ): Response<Comment>
    
    /**
     * Create a comment (requires authentication)
     */
    @FormUrlEncoded
    @POST("comments.json")
    suspend fun createComment(
        @Field("comment[post_id]") postId: Int,
        @Field("comment[body]") body: String,
        @Field("comment[do_not_bump_post]") doNotBump: Boolean = false
    ): Response<Comment>
    
    /**
     * Update a comment (requires authentication)
     */
    @FormUrlEncoded
    @PATCH("comments/{id}.json")
    suspend fun updateComment(
        @Path("id") commentId: Int,
        @Field("comment[body]") body: String
    ): Response<Comment>
    
    /**
     * Delete a comment (requires authentication)
     */
    @DELETE("comments/{id}.json")
    suspend fun deleteComment(
        @Path("id") commentId: Int
    ): Response<Unit>
    
    /**
     * Vote on a comment
     */
    @FormUrlEncoded
    @POST("comments/{id}/votes.json")
    suspend fun voteComment(
        @Path("id") commentId: Int,
        @Field("score") score: Int // 1 or -1
    ): Response<CommentVoteResponse>
    
    /**
     * Remove vote from comment
     */
    @DELETE("comments/{id}/votes.json")
    suspend fun unvoteComment(
        @Path("id") commentId: Int
    ): Response<Unit>
    
    // ==================== DMAILS ====================
    
    /**
     * List dmails (requires authentication)
     */
    @GET("dmails.json")
    suspend fun getDmails(
        @Query("search[title_matches]") titleMatches: String? = null,
        @Query("search[message_matches]") messageMatches: String? = null,
        @Query("search[from_name]") fromName: String? = null,
        @Query("search[to_name]") toName: String? = null,
        @Query("search[read]") read: Boolean? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 75
    ): Response<List<Dmail>>
    
    /**
     * Get a single dmail
     */
    @GET("dmails/{id}.json")
    suspend fun getDmail(
        @Path("id") dmailId: Int
    ): Response<Dmail>
    
    /**
     * Create a dmail (requires authentication)
     */
    @FormUrlEncoded
    @POST("dmails.json")
    suspend fun createDmail(
        @Field("dmail[to_name]") toName: String,
        @Field("dmail[title]") title: String,
        @Field("dmail[body]") body: String
    ): Response<Dmail>
    
    /**
     * Mark dmail as read
     */
    @PUT("dmails/{id}/mark_as_read.json")
    suspend fun markDmailRead(
        @Path("id") dmailId: Int
    ): Response<Unit>
    
    /**
     * Delete (hide) a dmail
     */
    @DELETE("dmails/{id}.json")
    suspend fun deleteDmail(
        @Path("id") dmailId: Int
    ): Response<Unit>
    
    // ==================== POST SETS ====================
    
    /**
     * List post sets
     */
    @GET("post_sets.json")
    suspend fun getPostSets(
        @Query("search[name]") name: String? = null,
        @Query("search[shortname]") shortname: String? = null,
        @Query("search[creator_name]") creatorName: String? = null,
        @Query("search[order]") order: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 75
    ): Response<List<PostSet>>
    
    /**
     * Get a single post set
     */
    @GET("post_sets/{id}.json")
    suspend fun getPostSet(
        @Path("id") setId: Int
    ): Response<PostSet>
    
    /**
     * Create a post set (requires authentication)
     */
    @FormUrlEncoded
    @POST("post_sets.json")
    suspend fun createPostSet(
        @Field("post_set[name]") name: String,
        @Field("post_set[shortname]") shortname: String,
        @Field("post_set[description]") description: String? = null,
        @Field("post_set[is_public]") isPublic: Boolean = true
    ): Response<PostSet>
    
    /**
     * Update a post set
     */
    @FormUrlEncoded
    @PATCH("post_sets/{id}.json")
    suspend fun updatePostSet(
        @Path("id") setId: Int,
        @Field("post_set[name]") name: String? = null,
        @Field("post_set[shortname]") shortname: String? = null,
        @Field("post_set[description]") description: String? = null,
        @Field("post_set[is_public]") isPublic: Boolean? = null
    ): Response<PostSet>
    
    /**
     * Delete a post set
     */
    @DELETE("post_sets/{id}.json")
    suspend fun deletePostSet(
        @Path("id") setId: Int
    ): Response<Unit>
    
    /**
     * Add posts to a set
     */
    @FormUrlEncoded
    @POST("post_sets/{id}/add_posts.json")
    suspend fun addPostsToSet(
        @Path("id") setId: Int,
        @Field("post_ids[]") postIds: List<Int>
    ): Response<Unit>
    
    /**
     * Remove posts from a set
     */
    @FormUrlEncoded
    @POST("post_sets/{id}/remove_posts.json")
    suspend fun removePostsFromSet(
        @Path("id") setId: Int,
        @Field("post_ids[]") postIds: List<Int>
    ): Response<Unit>
    
    // ==================== NOTES ====================
    
    /**
     * List notes for a post
     */
    @GET("notes.json")
    suspend fun getNotes(
        @Query("search[post_id]") postId: Int,
        @Query("search[is_active]") isActive: Boolean? = true,
        @Query("limit") limit: Int = 1000
    ): Response<List<Note>>
}

/**
 * Vote response
 */
data class VoteResponse(
    val score: Int?,
    val up: Int?,
    val down: Int?,
    val our_score: Int?
)

/**
 * Favorite response
 */
data class FavoriteResponse(
    val post_id: Int?,
    val user_id: Int?
)

/**
 * API wrapper with authentication support
 */
class E621Api private constructor(
    private val service: E621ApiService,
    private val prefs: UserPreferences
) {
    
    val posts get() = PostsApi(service)
    val pools get() = PoolsApi(service)
    val tags get() = TagsApi(service)
    val users get() = UsersApi(service)
    val popular get() = PopularApi(service)
    val comments get() = CommentsApi(service)
    val dmails get() = DmailsApi(service)
    val sets get() = SetsApi(service)
    val wiki get() = WikiApi(service)
    val notes get() = NotesApi(service)
    
    companion object {
        private const val USER_AGENT = "E621Client/1.0 (Android; by YourUsername)"
        
        fun create(prefs: UserPreferences): E621Api {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            
            // Configure Gson to be lenient with unknown fields
            // Register custom deserializer for PostSample to handle alternates field
            val gson = GsonBuilder()
                .setLenient()
                .registerTypeAdapter(PostSample::class.java, PostSampleDeserializer())
                .create()
            
            val client = OkHttpClient.Builder()
                // Request interceptor - adds headers
                .addInterceptor { chain ->
                    val original = chain.request()
                    val requestBuilder = original.newBuilder()
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "application/json")
                    
                    // Add auth header if logged in
                    prefs.getAuthHeader()?.let {
                        requestBuilder.header("Authorization", it)
                    }
                    
                    chain.proceed(requestBuilder.build())
                }
                // Error handling interceptor - detects CloudFlare, auth errors, server issues
                .addInterceptor { chain ->
                    val response = try {
                        chain.proceed(chain.request())
                    } catch (e: java.net.SocketTimeoutException) {
                        throw ApiErrorHandler.getExceptionForThrowable(e)
                    } catch (e: java.net.UnknownHostException) {
                        throw ApiErrorHandler.getExceptionForThrowable(e)
                    } catch (e: java.net.ConnectException) {
                        throw ApiErrorHandler.getExceptionForThrowable(e)
                    } catch (e: javax.net.ssl.SSLException) {
                        throw ApiErrorHandler.getExceptionForThrowable(e)
                    }
                    
                    // Check for error HTTP codes
                    when (response.code) {
                        401 -> {
                            response.close()
                            throw AuthenticationException("Invalid credentials or API key expired")
                        }
                        403, 503 -> {
                            // Check if it's CloudFlare by looking at headers or body
                            val cfRay = response.header("CF-RAY")
                            val server = response.header("Server")
                            if (cfRay != null || server?.contains("cloudflare", ignoreCase = true) == true) {
                                response.close()
                                throw CloudFlareException(
                                    "Request blocked by CloudFlare. Please try again later.",
                                    response.code
                                )
                            }
                            // Not CloudFlare, return response for normal handling
                            response
                        }
                        500, 502, 504 -> {
                            response.close()
                            throw ServerDownException("Server error (${response.code}). The server may be experiencing issues.")
                        }
                        else -> response
                    }
                }
                .addInterceptor(loggingInterceptor)
                // Reduced timeouts for better mobile data experience
                // Shorter connect timeout to fail fast on bad connections
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                // Enable retry on connection failure
                .retryOnConnectionFailure(true)
                .build()
            
            val retrofit = Retrofit.Builder()
                .baseUrl(prefs.baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()
            
            return E621Api(retrofit.create(E621ApiService::class.java), prefs)
        }
    }
    
    /**
     * Posts API helper
     */
    class PostsApi(private val service: E621ApiService) {
        suspend fun list(tags: String? = null, page: Int = 1, limit: Int = 75) = 
            service.getPosts(tags, page, limit)
        
        suspend fun get(id: Int) = service.getPost(id)
        
        suspend fun vote(id: Int, score: Int) = service.votePost(id, score)
        
        suspend fun favorite(id: Int) = service.addFavorite(id)
        
        suspend fun unfavorite(id: Int) = service.removeFavorite(id)
    }
    
    /**
     * Pools API helper
     */
    class PoolsApi(private val service: E621ApiService) {
        suspend fun list(
            nameMatches: String? = null,
            category: String? = null,
            order: String? = null,
            page: Int = 1
        ) = service.getPools(nameMatches, category, order, page)
        
        suspend fun get(id: Int) = service.getPool(id)
        
        suspend fun getPools(
            nameMatches: String? = null,
            category: String? = null,
            order: String? = null,
            page: Int = 1,
            limit: Int = 75
        ) = service.getPools(nameMatches, category, order, page, limit)
    }
    
    /**
     * Tags API helper
     */
    class TagsApi(private val service: E621ApiService) {
        suspend fun list(nameMatches: String? = null, order: String? = null, page: Int = 1) = 
            service.getTags(nameMatches, order, page)
        
        suspend fun autocomplete(query: String, limit: Int = 10) = 
            service.autocomplete(query, limit)
            
        suspend fun getTags(
            nameMatches: String? = null,
            order: String? = null,
            page: Int = 1,
            limit: Int = 75
        ) = service.getTags(nameMatches, order, page, limit)
    }
    
    /**
     * Users API helper
     */
    class UsersApi(private val service: E621ApiService) {
        suspend fun get(id: Int) = service.getUser(id)
        suspend fun getByName(name: String) = service.getUserByName(name)
        suspend fun getUsers(
            nameMatches: String? = null,
            level: Int? = null,
            order: String? = null,
            page: Int = 1,
            limit: Int = 75
        ) = service.getUsers(nameMatches, level, order, page, limit)
    }

    /**
     * Popular API helper
     */
    class PopularApi(private val service: E621ApiService) {
        suspend fun get(scale: String, date: String? = null) = service.getPopular(scale, date)
    }
    
    /**
     * Comments API helper
     */
    class CommentsApi(private val service: E621ApiService) {
        suspend fun list(
            postId: Int? = null,
            creatorName: String? = null,
            bodyMatches: String? = null,
            order: String? = null,
            page: Int = 1,
            limit: Int = 75
        ) = service.getComments("comment", postId, creatorName, bodyMatches, order, page, limit)
        
        suspend fun get(id: Int) = service.getComment(id)
        
        suspend fun create(postId: Int, body: String, doNotBump: Boolean = false) = 
            service.createComment(postId, body, doNotBump)
        
        suspend fun update(id: Int, body: String) = service.updateComment(id, body)
        
        suspend fun delete(id: Int) = service.deleteComment(id)
        
        suspend fun vote(id: Int, score: Int) = service.voteComment(id, score)
        
        suspend fun unvote(id: Int) = service.unvoteComment(id)
    }
    
    /**
     * Dmails API helper
     */
    class DmailsApi(private val service: E621ApiService) {
        suspend fun list(
            titleMatches: String? = null,
            messageMatches: String? = null,
            fromName: String? = null,
            toName: String? = null,
            read: Boolean? = null,
            page: Int = 1,
            limit: Int = 75
        ) = service.getDmails(titleMatches, messageMatches, fromName, toName, read, page, limit)
        
        suspend fun get(id: Int) = service.getDmail(id)
        
        suspend fun create(toName: String, title: String, body: String) = 
            service.createDmail(toName, title, body)
        
        suspend fun markRead(id: Int) = service.markDmailRead(id)
        
        suspend fun delete(id: Int) = service.deleteDmail(id)
    }
    
    /**
     * Post Sets API helper
     */
    class SetsApi(private val service: E621ApiService) {
        suspend fun list(
            name: String? = null,
            shortname: String? = null,
            creatorName: String? = null,
            order: String? = null,
            page: Int = 1,
            limit: Int = 75
        ) = service.getPostSets(name, shortname, creatorName, order, page, limit)
        
        suspend fun get(id: Int) = service.getPostSet(id)
        
        suspend fun create(name: String, shortname: String, description: String? = null, isPublic: Boolean = true) = 
            service.createPostSet(name, shortname, description, isPublic)
        
        suspend fun update(id: Int, name: String? = null, shortname: String? = null, description: String? = null, isPublic: Boolean? = null) = 
            service.updatePostSet(id, name, shortname, description, isPublic)
        
        suspend fun delete(id: Int) = service.deletePostSet(id)
        
        suspend fun addPosts(id: Int, postIds: List<Int>) = service.addPostsToSet(id, postIds)
        
        suspend fun removePosts(id: Int, postIds: List<Int>) = service.removePostsFromSet(id, postIds)
    }
    
    /**
     * Wiki API helper
     */
    class WikiApi(private val service: E621ApiService) {
        suspend fun search(title: String? = null, bodyMatches: String? = null, page: Int = 1, limit: Int = 75) = 
            service.getWikiPages(title, bodyMatches, page, limit)
        
        suspend fun get(id: Int) = service.getWikiPage(id)
        
        suspend fun getByTitle(title: String) = service.getWikiPages(title, null, 1, 1)
    }
    
    /**
     * Notes API helper
     */
    class NotesApi(private val service: E621ApiService) {
        suspend fun getForPost(postId: Int) = service.getNotes(postId)
    }
}

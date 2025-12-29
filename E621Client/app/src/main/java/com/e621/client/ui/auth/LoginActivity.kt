package com.e621.client.ui.auth

import android.os.Bundle
import android.view.View
import android.view.autofill.AutofillManager
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.e621.client.E621Application
import com.e621.client.R
import com.e621.client.data.api.E621Api
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Login Activity
 * Based on decompiled LoginActivity pattern
 * Uses e621 API authentication (username + api_key)
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var etUsername: EditText
    private lateinit var etApiKey: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnCancel: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var txtSubtitle: TextView
    
    private val prefs by lazy { E621Application.instance.userPreferences }
    
    // Result sealed class for better error handling
    private sealed class LoginResult {
        object Success : LoginResult()
        data class InvalidCredentials(val code: Int) : LoginResult()
        object CloudFlareBlocked : LoginResult()
        object ServerDown : LoginResult()
        object Timeout : LoginResult()
        object NoInternet : LoginResult()
        data class Error(val message: String) : LoginResult()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        
        setupViews()
        loadSavedCredentials()
    }
    
    private fun setupViews() {
        etUsername = findViewById(R.id.etUsername)
        etApiKey = findViewById(R.id.etApiKey)
        btnLogin = findViewById(R.id.btnLogin)
        btnCancel = findViewById(R.id.btnCancel)
        progressBar = findViewById(R.id.progressBar)
        txtSubtitle = findViewById(R.id.txtSubtitle)
        
        txtSubtitle.text = getString(R.string.login_subtitle)
        
        btnLogin.setOnClickListener { attemptLogin() }
        btnCancel.setOnClickListener { finish() }
    }
    
    private fun loadSavedCredentials() {
        prefs.username?.let { etUsername.setText(it) }
        prefs.apiKey?.let { etApiKey.setText(it) }
    }
    
    private fun attemptLogin() {
        val username = etUsername.text.toString().trim()
        val apiKey = etApiKey.text.toString().trim()
        
        // Validation
        when {
            username.isEmpty() -> {
                showError(getString(R.string.login_username_empty))
                return
            }
            apiKey.isEmpty() -> {
                showError(getString(R.string.login_api_key_empty))
                return
            }
        }
        
        setLoading(true)
        
        // Verify credentials by making a test request
        // Based on decompiled code: POST to /posts/321786/votes.json?score=0
        lifecycleScope.launch {
            val result = verifyCredentials(username, apiKey)
            
            withContext(Dispatchers.Main) {
                setLoading(false)
                
                when (result) {
                    is LoginResult.Success -> {
                        prefs.saveCredentials(username, apiKey)
                        
                        // Notify AutofillManager that the form was submitted successfully
                        // This allows password managers (Google, Samsung Pass) to save credentials
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            getSystemService(AutofillManager::class.java)?.commit()
                        }
                        
                        Toast.makeText(
                            this@LoginActivity,
                            getString(R.string.login_success),
                            Toast.LENGTH_SHORT
                        ).show()
                        setResult(RESULT_OK)
                        finish()
                    }
                    is LoginResult.InvalidCredentials -> {
                        showError(getString(R.string.error_invalid_credentials))
                    }
                    is LoginResult.CloudFlareBlocked -> {
                        showError(getString(R.string.error_cloudflare))
                    }
                    is LoginResult.ServerDown -> {
                        showError(getString(R.string.error_server_down))
                    }
                    is LoginResult.Timeout -> {
                        showError(getString(R.string.error_timeout))
                    }
                    is LoginResult.NoInternet -> {
                        showError(getString(R.string.error_no_internet))
                    }
                    is LoginResult.Error -> {
                        showError(getString(R.string.error_generic, result.message))
                    }
                }
            }
        }
    }
    
    private suspend fun verifyCredentials(username: String, apiKey: String): LoginResult {
        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()
                
                val baseUrl = prefs.baseUrl
                val credentials = Credentials.basic(username, apiKey)
                
                // Test by voting on a known post (score=0 doesn't change anything)
                val request = Request.Builder()
                    .url("${baseUrl}posts/321786/votes.json?score=0")
                    .post(okhttp3.RequestBody.create(null, ByteArray(0)))
                    .header("Authorization", credentials)
                    .header("User-Agent", "E621Client/1.0 (Android)")
                    .build()
                
                val response = client.newCall(request).execute()
                
                when {
                    // 200 or 422 means valid credentials (based on decompiled code)
                    response.code == 200 || response.code == 422 -> LoginResult.Success
                    response.code == 401 -> LoginResult.InvalidCredentials(response.code)
                    response.code == 403 || response.code == 503 -> {
                        // Check if it's CloudFlare
                        val cfRay = response.header("CF-RAY")
                        val server = response.header("Server")
                        if (cfRay != null || server?.contains("cloudflare", ignoreCase = true) == true) {
                            LoginResult.CloudFlareBlocked
                        } else {
                            LoginResult.InvalidCredentials(response.code)
                        }
                    }
                    response.code >= 500 -> LoginResult.ServerDown
                    else -> LoginResult.InvalidCredentials(response.code)
                }
            } catch (e: java.net.SocketTimeoutException) {
                LoginResult.Timeout
            } catch (e: java.net.UnknownHostException) {
                LoginResult.NoInternet
            } catch (e: java.net.ConnectException) {
                LoginResult.NoInternet
            } catch (e: Exception) {
                LoginResult.Error(e.message ?: "Unknown error")
            }
        }
    }
    
    private fun setLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        btnLogin.isEnabled = !loading
        btnLogin.alpha = if (loading) 0.5f else 1.0f
        etUsername.isEnabled = !loading
        etApiKey.isEnabled = !loading
    }
    
    private fun showError(message: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.error)
            .setMessage(message)
            .setPositiveButton(R.string.ok, null)
            .show()
    }
}

package com.e621.client.ui.settings

import android.app.Activity
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.biometric.BiometricManager
import androidx.fragment.app.Fragment
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import com.e621.client.E621Application
import com.e621.client.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.appbar.MaterialToolbar
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.res.Configuration
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Settings Activity with multi-screen navigation
 * Based on decompiled SettingsActivity structure
 */
class SettingsActivity : AppCompatActivity(),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {
    
    companion object {
        const val RESULT_HOST_CHANGED = Activity.RESULT_FIRST_USER + 1
    }

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("${newBase.packageName}_preferences", Context.MODE_PRIVATE)
        val languageCode = prefs.getString("general_language", "en") ?: "en"
        
        if (languageCode != "system" && languageCode.isNotEmpty()) {
            val locale = Locale.forLanguageTag(languageCode)
            Locale.setDefault(locale)
            
            val config = Configuration(newBase.resources.configuration)
            config.setLocale(locale)
            
            val context = newBase.createConfigurationContext(config)
            super.attachBaseContext(context)
        } else {
            super.attachBaseContext(newBase)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings)
        
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settingsContainer, MainSettingsFragment())
                .commit()
        }
        
        supportFragmentManager.addOnBackStackChangedListener {
            if (supportFragmentManager.backStackEntryCount == 0) {
                supportActionBar?.title = getString(R.string.settings)
            }
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
            return true
        }
        finish()
        return true
    }
    
    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat,
        pref: Preference
    ): Boolean {
        val args = pref.extras
        val fragment = supportFragmentManager.fragmentFactory.instantiate(
            classLoader,
            pref.fragment ?: return false
        ).apply {
            arguments = args
            setTargetFragment(caller, 0)
        }
        
        supportFragmentManager.beginTransaction()
            .replace(R.id.settingsContainer, fragment)
            .addToBackStack(null)
            .commit()
        
        supportActionBar?.title = pref.title
        
        return true
    }

    /**
     * Main settings screen with category links and export/import functionality
     */
    class MainSettingsFragment : PreferenceFragmentCompat() {
        
        private val prefs by lazy { E621Application.instance.userPreferences }
        
        // Preferences file name (same as UserPreferences)
        private val PREFS_NAME = "e621_client_prefs"
        
        // Keys to exclude from export (sensitive data)
        private val excludedKeys = arrayOf("username", "api_key", "auth_token", "password")
        
        // Encryption constants (same as decompiled app for compatibility)
        private val ENCRYPTION_PASSWORD = "gf./dfmGFdf3_dfÖDSBY34/REW#("
        private val ENCRYPTION_SALT = "g.#hå"
        private val ENCRYPTION_IV = "D9FGH35MF3AG0IFD"
        
        // Export state
        private var pendingExportData: JSONObject? = null
        private var encryptExport: Boolean = true
        
        // Activity result launchers
        private val exportFileLauncher = registerForActivityResult(
            ActivityResultContracts.CreateDocument("text/*")
        ) { uri ->
            uri?.let { writeExportFile(it) }
        }
        
        private val importFileLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            uri?.let { readImportFile(it) }
        }
        
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences_main, rootKey)
            
            setupExportPreference()
            setupImportPreference()
            setupPrivacyPreferences()
            setupProxyPreference()
            setupCloudFlarePreference()
        }
        
        private fun setupExportPreference() {
            findPreference<Preference>("export_export")?.setOnPreferenceClickListener {
                showExportDialog()
                true
            }
        }
        
        private fun setupImportPreference() {
            findPreference<Preference>("export_import")?.setOnPreferenceClickListener {
                launchImportFilePicker()
                true
            }
        }
        
        private fun setupPrivacyPreferences() {
            // Crash reports
            findPreference<SwitchPreferenceCompat>("privacy_crash_reports")?.apply {
                isChecked = prefs.privacyCrashReports
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.privacyCrashReports = newValue as Boolean
                    Toast.makeText(
                        requireContext(),
                        R.string.restart_app_for_changes,
                        Toast.LENGTH_SHORT
                    ).show()
                    true
                }
            }
            
            // Analytics
            findPreference<SwitchPreferenceCompat>("privacy_analytics")?.apply {
                isChecked = prefs.privacyAnalytics
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.privacyAnalytics = newValue as Boolean
                    Toast.makeText(
                        requireContext(),
                        R.string.restart_app_for_changes,
                        Toast.LENGTH_SHORT
                    ).show()
                    true
                }
            }
        }
        
        private fun setupProxyPreference() {
            val proxyPref = findPreference<SwitchPreferenceCompat>("proxy") ?: return
            
            // Set initial state
            val proxyConfig = prefs.getProxyConfig()
            proxyPref.isChecked = proxyConfig != null
            
            // Update summary if proxy is configured
            if (proxyConfig != null && proxyConfig.port >= 0) {
                proxyPref.summary = getString(
                    R.string.pref_proxy_connected_summary,
                    proxyConfig.host,
                    proxyConfig.port
                )
            }
            
            proxyPref.setOnPreferenceChangeListener { preference, newValue ->
                val enabled = newValue as Boolean
                if (enabled) {
                    // Show proxy configuration dialog
                    showProxyDialog(preference as SwitchPreferenceCompat)
                    false // Don't change state yet, dialog will handle it
                } else {
                    // Disable proxy
                    prefs.proxyEnabled = false
                    preference.summary = getString(R.string.pref_proxy_enabled_summary)
                    Toast.makeText(requireContext(), R.string.proxy_disabled, Toast.LENGTH_SHORT).show()
                    true
                }
            }
            
            // Also allow clicking to reconfigure when already enabled
            proxyPref.setOnPreferenceClickListener {
                if (proxyPref.isChecked) {
                    showProxyDialog(proxyPref)
                }
                true
            }
        }
        
        private fun showProxyDialog(proxyPref: SwitchPreferenceCompat) {
            val dialogView = layoutInflater.inflate(R.layout.dialog_proxy, null)
            val etHost = dialogView.findViewById<TextInputEditText>(R.id.etProxyHost)
            val etPort = dialogView.findViewById<TextInputEditText>(R.id.etProxyPort)
            val etUsername = dialogView.findViewById<TextInputEditText>(R.id.etProxyUsername)
            val etPassword = dialogView.findViewById<TextInputEditText>(R.id.etProxyPassword)
            
            // Pre-fill with existing values
            prefs.proxyHost?.let { etHost.setText(it) }
            if (prefs.proxyPort >= 0) {
                etPort.setText(prefs.proxyPort.toString())
            }
            prefs.proxyUsername?.let { etUsername.setText(it) }
            prefs.proxyPassword?.let { etPassword.setText(it) }
            
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.pref_proxy_enabled_title)
                .setView(dialogView)
                .setPositiveButton(R.string.save) { _, _ ->
                    val host = etHost.text?.toString()?.trim() ?: ""
                    val portStr = etPort.text?.toString()?.trim() ?: ""
                    val username = etUsername.text?.toString()?.trim()
                    val password = etPassword.text?.toString()?.trim()
                    
                    if (host.isEmpty()) {
                        Toast.makeText(requireContext(), R.string.proxy_host_required, Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    
                    val port = try {
                        portStr.toInt()
                    } catch (e: NumberFormatException) {
                        -1
                    }
                    
                    if (port < 0 || port > 65535) {
                        Toast.makeText(requireContext(), R.string.proxy_invalid_port, Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    
                    // Save proxy configuration
                    prefs.proxyEnabled = true
                    prefs.proxyHost = host
                    prefs.proxyPort = port
                    prefs.proxyUsername = if (username.isNullOrEmpty()) null else username
                    prefs.proxyPassword = if (password.isNullOrEmpty()) null else password
                    
                    // Update UI
                    proxyPref.isChecked = true
                    proxyPref.summary = getString(R.string.pref_proxy_connected_summary, host, port)
                    
                    Toast.makeText(requireContext(), R.string.proxy_saved, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
        
        private fun setupCloudFlarePreference() {
            findPreference<Preference>("cloudflare")?.setOnPreferenceClickListener {
                // Open the website in browser to refresh cookies
                val host = prefs.host
                val url = if (host == "e621.net") "https://e926.net" else "https://$host"
                
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.cloudflare_reload_title)
                    .setMessage(R.string.cloudflare_reload_summary)
                    .setPositiveButton(R.string.ok) { _, _ ->
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            startActivity(intent)
                            Toast.makeText(requireContext(), R.string.cloudflare_loading, Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(requireContext(), R.string.cloudflare_error, Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
                true
            }
        }
        
        private fun showExportDialog() {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.export_dialog_title)
                .setMessage(R.string.export_dialog_message)
                .setPositiveButton(R.string.export_encrypt) { _, _ ->
                    encryptExport = true
                    startExport()
                }
                .setNegativeButton(R.string.export_plain) { _, _ ->
                    encryptExport = false
                    startExport()
                }
                .setNeutralButton(android.R.string.cancel, null)
                .show()
        }
        
        private fun startExport() {
            try {
                val exportData = generateExportData()
                if (exportData != null) {
                    pendingExportData = exportData
                    launchExportFilePicker()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    requireContext(),
                    getString(R.string.export_failed, e.message),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        
        private fun generateExportData(): JSONObject? {
            // Use the same preferences file as UserPreferences
            val appPrefs = requireContext().getSharedPreferences(
                PREFS_NAME, Context.MODE_PRIVATE
            )
            
            val result = JSONObject()
            val settingsArray = JSONArray()
            
            // Export all preferences
            for ((key, value) in appPrefs.all) {
                if (key !in excludedKeys) {
                    val typeCode = getTypeCode(value)
                    settingsArray.put("$typeCode:$key:$value")
                }
            }
            
            try {
                result.put("settings", settingsArray)
                return result
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    requireContext(),
                    getString(R.string.export_failed, e.message),
                    Toast.LENGTH_LONG
                ).show()
                return null
            }
        }
        
        private fun getTypeCode(value: Any?): String {
            return when (value) {
                is String -> "s"
                is Int -> "i"
                is Boolean -> "b"
                is Float -> "f"
                is Long -> "l"
                else -> "?"
            }
        }
        
        private fun launchExportFilePicker() {
            val dateFormat = SimpleDateFormat("yyMMdd_HHmmss", Locale.getDefault())
            val timestamp = dateFormat.format(Date())
            val suffix = if (encryptExport) "" else "_plain"
            val fileName = "E621_BU_$timestamp.e621$suffix"
            
            try {
                exportFileLauncher.launch(fileName)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    requireContext(),
                    getString(R.string.export_failed, e.message),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        
        private fun writeExportFile(uri: Uri) {
            val exportData = pendingExportData ?: run {
                Toast.makeText(requireContext(), R.string.export_failed, Toast.LENGTH_SHORT).show()
                return
            }
            
            try {
                var bytes = exportData.toString().toByteArray(StandardCharsets.UTF_8)
                
                // Encrypt if requested
                if (encryptExport) {
                    bytes = encryptData(bytes)
                }
                
                // Write to file
                requireContext().contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(bytes)
                }
                
                val fileName = getFileNameFromUri(uri)
                Toast.makeText(
                    requireContext(),
                    getString(R.string.export_success, fileName),
                    Toast.LENGTH_LONG
                ).show()
                
            } catch (e: GeneralSecurityException) {
                e.printStackTrace()
                Toast.makeText(
                    requireContext(),
                    getString(R.string.export_failed, "Encryption error"),
                    Toast.LENGTH_LONG
                ).show()
                // Try to delete failed file
                try {
                    requireContext().contentResolver.delete(uri, null, null)
                } catch (ignored: Exception) {}
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    requireContext(),
                    getString(R.string.export_failed, e.message),
                    Toast.LENGTH_LONG
                ).show()
                // Try to delete failed file
                try {
                    requireContext().contentResolver.delete(uri, null, null)
                } catch (ignored: Exception) {}
            } finally {
                pendingExportData = null
            }
        }
        
        private fun encryptData(data: ByteArray): ByteArray {
            val keySpec = PBEKeySpec(
                ENCRYPTION_PASSWORD.toCharArray(),
                ENCRYPTION_SALT.toByteArray(),
                65536,
                256
            )
            val secretKey = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(keySpec)
            val secretKeySpec = SecretKeySpec(secretKey.encoded, "AES")
            
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(
                Cipher.ENCRYPT_MODE,
                secretKeySpec,
                IvParameterSpec(ENCRYPTION_IV.toByteArray())
            )
            
            return cipher.doFinal(data)
        }
        
        private fun launchImportFilePicker() {
            try {
                importFileLauncher.launch(arrayOf("*/*"))
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    requireContext(),
                    getString(R.string.import_failed, e.message),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        
        private fun readImportFile(uri: Uri) {
            try {
                val fileName = getFileNameFromUri(uri)
                
                // Determine if file is encrypted based on extension
                val isEncrypted = when {
                    fileName.endsWith(".e621") -> true
                    fileName.endsWith(".e621_plain") -> false
                    fileName.endsWith(".tws") -> true  // Compatible with original app
                    fileName.endsWith(".tws_plain") -> false
                    else -> {
                        Toast.makeText(requireContext(), R.string.import_invalid_file, Toast.LENGTH_LONG).show()
                        return
                    }
                }
                
                // Read file content
                val inputStream = requireContext().contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    Toast.makeText(requireContext(), R.string.import_file_not_found, Toast.LENGTH_LONG).show()
                    return
                }
                
                val byteArrayOutputStream = ByteArrayOutputStream()
                val buffer = ByteArray(16384)
                var bytesRead: Int
                
                inputStream.use { stream ->
                    while (stream.read(buffer, 0, buffer.size).also { bytesRead = it } != -1) {
                        byteArrayOutputStream.write(buffer, 0, bytesRead)
                    }
                }
                
                var data = byteArrayOutputStream.toByteArray()
                
                // Decrypt if needed
                if (isEncrypted) {
                    try {
                        data = decryptData(data)
                    } catch (e: GeneralSecurityException) {
                        e.printStackTrace()
                        Toast.makeText(requireContext(), R.string.import_decrypt_failed, Toast.LENGTH_LONG).show()
                        return
                    }
                }
                
                // Parse and import
                val jsonString = String(data, StandardCharsets.UTF_8)
                importData(jsonString)
                
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(requireContext(), R.string.import_process_failed, Toast.LENGTH_LONG).show()
            }
        }
        
        private fun decryptData(data: ByteArray): ByteArray {
            val keySpec = PBEKeySpec(
                ENCRYPTION_PASSWORD.toCharArray(),
                ENCRYPTION_SALT.toByteArray(),
                65536,
                256
            )
            val secretKey = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(keySpec)
            val secretKeySpec = SecretKeySpec(secretKey.encoded, "AES")
            
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKeySpec,
                IvParameterSpec(ENCRYPTION_IV.toByteArray())
            )
            
            return cipher.doFinal(data)
        }
        
        private fun importData(jsonString: String) {
            if (jsonString.isEmpty()) {
                Toast.makeText(requireContext(), R.string.import_no_data, Toast.LENGTH_LONG).show()
                return
            }
            
            try {
                val jsonObject = JSONObject(jsonString)
                val settingsArray = jsonObject.getJSONArray("settings")
                
                // Use the same preferences file as UserPreferences
                val appPrefs = requireContext().getSharedPreferences(
                    PREFS_NAME, Context.MODE_PRIVATE
                )
                
                val editor = appPrefs.edit()
                
                // Import settings
                for (i in 0 until settingsArray.length()) {
                    val entry = settingsArray.getString(i)
                    applyPreferenceEntry(entry, editor)
                }
                
                // Also try to import from userInfo array for compatibility with old format
                if (jsonObject.has("userInfo")) {
                    val userArray = jsonObject.getJSONArray("userInfo")
                    for (i in 0 until userArray.length()) {
                        val entry = userArray.getString(i)
                        applyPreferenceEntry(entry, editor)
                    }
                }
                
                editor.apply()
                
                Toast.makeText(requireContext(), R.string.import_success, Toast.LENGTH_LONG).show()
                
                // Restart app after a short delay
                Handler(Looper.getMainLooper()).postDelayed({
                    restartApp()
                }, 1500)
                
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    requireContext(),
                    getString(R.string.import_failed, e.message),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        
        private fun applyPreferenceEntry(entry: String, editor: SharedPreferences.Editor) {
            val parts = entry.split(":", limit = 3)
            if (parts.size != 3) return
            
            val typeCode = parts[0]
            val key = parts[1]
            val value = parts[2]
            
            // Skip excluded keys
            if (key in excludedKeys) return
            
            when (typeCode) {
                "b" -> editor.putBoolean(key, value.toBoolean())
                "f" -> editor.putFloat(key, value.toFloat())
                "i" -> editor.putInt(key, value.toInt())
                "l" -> editor.putLong(key, value.toLong())
                "s" -> editor.putString(key, value)
            }
        }
        
        private fun getFileNameFromUri(uri: Uri): String {
            var fileName: String? = null
            
            // Try to get display name from content resolver
            if (uri.scheme == "content") {
                requireContext().contentResolver.query(
                    uri,
                    arrayOf("_display_name"),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex("_display_name")
                        if (index >= 0) {
                            fileName = cursor.getString(index)
                        }
                    }
                }
            }
            
            // Fallback to path
            if (fileName == null) {
                fileName = uri.path?.let { path ->
                    val lastSlash = path.lastIndexOf('/')
                    if (lastSlash != -1) path.substring(lastSlash + 1) else path
                }
            }
            
            return fileName ?: ""
        }
        
        private fun restartApp() {
            val context = requireContext()
            val packageManager = context.packageManager
            val intent = packageManager.getLaunchIntentForPackage(context.packageName)
            val componentName = intent?.component
            
            if (componentName != null) {
                val mainIntent = Intent.makeRestartActivityTask(componentName)
                context.startActivity(mainIntent)
                Runtime.getRuntime().exit(0)
            }
        }
    }
    
    /**
     * General settings fragment - Full implementation based on decompiled app
     */
    class GeneralSettingsFragment : PreferenceFragmentCompat() {
        
        private val prefs by lazy { E621Application.instance.userPreferences }
        
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences_general, rootKey)
            
            setupLanguagePreference()
            setupHostPreference()
            setupAgeConsentPreference()
            setupThemePreference()
            setupQualityPreferences()
            setupBlacklistPreferences()
            setupPrivacyPreferences()
            setupSecurityPreferences()
        }
        
        private fun setupLanguagePreference() {
            findPreference<ListPreference>("general_language")?.apply {
                // Read from the correct preferences file (PreferenceFragment uses packageName_preferences)
                val defaultPrefs = requireContext().getSharedPreferences(
                    "${requireContext().packageName}_preferences",
                    Context.MODE_PRIVATE
                )
                val currentLang = defaultPrefs.getString("general_language", "en") ?: "en"
                value = currentLang
                
                setOnPreferenceChangeListener { _, newValue ->
                    val languageCode = newValue as String
                    
                    // Check if user selected the same language
                    if (languageCode == currentLang) {
                        Toast.makeText(requireContext(), getString(R.string.language_already_selected), Toast.LENGTH_SHORT).show()
                        return@setOnPreferenceChangeListener false
                    }
                    
                    // Show changing language message
                    Toast.makeText(requireContext(), getString(R.string.language_changing), Toast.LENGTH_SHORT).show()
                    
                    // Save to the default preferences file (used by PreferenceFragment and attachBaseContext)
                    val sharedPrefs = requireContext().getSharedPreferences(
                        "${requireContext().packageName}_preferences",
                        Context.MODE_PRIVATE
                    )
                    sharedPrefs.edit().putString("general_language", languageCode).commit()
                    
                    // Also save to UserPreferences file for consistency
                    val userPrefs = requireContext().getSharedPreferences(
                        "e621_client_prefs",
                        Context.MODE_PRIVATE
                    )
                    userPrefs.edit().putString("general_language", languageCode).commit()
                    
                    // Restart the app to apply language change
                    Handler(Looper.getMainLooper()).postDelayed({
                        val intent = Intent(requireContext(), com.e621.client.ui.LauncherActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        requireContext().startActivity(intent)
                        Runtime.getRuntime().exit(0)
                    }, 500)
                    
                    true
                }
            }
        }
        
        private fun setupHostPreference() {
            findPreference<ListPreference>("general_change_host")?.apply {
                value = prefs.host
                
                setOnPreferenceChangeListener { _, newValue ->
                    val host = newValue as String
                    when (host) {
                        "e621.net" -> {
                            if (prefs.ageConsent) {
                                prefs.host = host
                                prefs.safeMode = false
                                // Enable all ratings when switching to e621
                                prefs.filterRating = 7 // All ratings: 1+2+4 = 7
                                
                                // Recreate API with new base URL and notify activity
                                E621Application.instance.recreateApi()
                                activity?.setResult(SettingsActivity.RESULT_HOST_CHANGED)
                                
                                Toast.makeText(
                                    requireContext(),
                                    R.string.host_changed_refresh,
                                    Toast.LENGTH_SHORT
                                ).show()
                                true
                            } else {
                                Toast.makeText(
                                    requireContext(),
                                    R.string.pref_general_edit_host_not_supported,
                                    Toast.LENGTH_SHORT
                                ).show()
                                false
                            }
                        }
                        "e926.net" -> {
                            prefs.host = host
                            prefs.safeMode = true
                            
                            // Recreate API with new base URL and notify activity
                            E621Application.instance.recreateApi()
                            activity?.setResult(SettingsActivity.RESULT_HOST_CHANGED)
                            
                            Toast.makeText(
                                requireContext(),
                                R.string.host_changed_refresh,
                                Toast.LENGTH_SHORT
                            ).show()
                            true
                        }
                        else -> false
                    }
                }
            }
        }
        
        private fun setupAgeConsentPreference() {
            findPreference<SwitchPreferenceCompat>("consent_above_18")?.apply {
                isChecked = prefs.ageConsent
                setOnPreferenceChangeListener { _, newValue ->
                    val consent = newValue as Boolean
                    prefs.ageConsent = consent
                    
                    // If unchecked, force e926 mode
                    if (!consent) {
                        findPreference<ListPreference>("general_change_host")?.apply {
                            value = "e926.net"
                        }
                        prefs.host = "e926.net"
                        prefs.safeMode = true
                        
                        // Recreate API with new base URL and notify activity
                        E621Application.instance.recreateApi()
                        activity?.setResult(SettingsActivity.RESULT_HOST_CHANGED)
                    }
                    true
                }
            }
        }
        
        private fun setupThemePreference() {
            findPreference<ListPreference>("general_dark_mode")?.apply {
                value = prefs.theme.toString()
                setOnPreferenceChangeListener { _, newValue ->
                    val themeValue = (newValue as String).toIntOrNull() ?: 0
                    prefs.theme = themeValue
                    applyTheme(themeValue)
                    Toast.makeText(
                        requireContext(),
                        R.string.restart_app_for_changes,
                        Toast.LENGTH_LONG
                    ).show()
                    true
                }
            }
        }
        
        private fun applyTheme(themeIndex: Int) {
            val mode = when (themeIndex) {
                1 -> AppCompatDelegate.MODE_NIGHT_NO      // Light
                2 -> AppCompatDelegate.MODE_NIGHT_YES    // Dark
                3 -> AppCompatDelegate.MODE_NIGHT_YES    // AMOLED (handled elsewhere)
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM  // System
            }
            AppCompatDelegate.setDefaultNightMode(mode)
        }
        
        private fun setupQualityPreferences() {
            // Post quality
            findPreference<ListPreference>("general_post_quality")?.apply {
                value = prefs.postQuality.toString()
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.postQuality = (newValue as String).toIntOrNull() ?: 1
                    true
                }
            }
            
            // Thumbnail quality
            findPreference<ListPreference>("general_thumb_quality")?.apply {
                value = prefs.thumbQuality.toString()
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.thumbQuality = (newValue as String).toIntOrNull() ?: 0
                    true
                }
            }
        }
        
        private fun setupBlacklistPreferences() {
            // Open blacklist editor
            findPreference<Preference>("general_blacklist")?.apply {
                setOnPreferenceClickListener {
                    startActivity(Intent(requireContext(), BlacklistActivity::class.java))
                    true
                }
            }
            
            // Blacklist enabled
            findPreference<SwitchPreferenceCompat>("general_blacklist_enabled")?.apply {
                isChecked = prefs.blacklistEnabled
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.blacklistEnabled = newValue as Boolean
                    true
                }
            }
            
            // Blacklist pool posts
            findPreference<SwitchPreferenceCompat>("general_blacklist_pool_posts")?.apply {
                isChecked = prefs.blacklistPoolPosts
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.blacklistPoolPosts = newValue as Boolean
                    true
                }
            }
        }
        
        private fun setupPrivacyPreferences() {
            // Hide in recent tasks
            findPreference<SwitchPreferenceCompat>("general_hide_in_tasks")?.apply {
                isChecked = prefs.hideInTasks
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.hideInTasks = newValue as Boolean
                    // Apply immediately
                    activity?.let { act ->
                        if (newValue) {
                            act.window.setFlags(
                                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                                android.view.WindowManager.LayoutParams.FLAG_SECURE
                            )
                        } else {
                            act.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                        }
                    }
                    true
                }
            }
            
            // Disguise mode (change app icon/name)
            findPreference<SwitchPreferenceCompat>("general_disguise")?.apply {
                isChecked = prefs.disguiseMode
                setOnPreferenceChangeListener { _, newValue ->
                    val disguise = newValue as Boolean
                    prefs.disguiseMode = disguise
                    
                    // This would require activity-alias in manifest to work properly
                    // For now, just save the preference
                    Toast.makeText(
                        requireContext(),
                        if (disguise) R.string.disguise_message_disguised 
                        else R.string.disguise_message_regular,
                        Toast.LENGTH_SHORT
                    ).show()
                    true
                }
            }
            
            // Start in saved searches
            findPreference<SwitchPreferenceCompat>("general_start_in_saved")?.apply {
                isChecked = prefs.startInSaved
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.startInSaved = newValue as Boolean
                    true
                }
            }
        }
        
        private fun setupSecurityPreferences() {
            // PIN unlock - main toggle
            findPreference<SwitchPreferenceCompat>("consent_pin_unlock")?.apply {
                isChecked = prefs.isPinSet()
                updatePinSummary(this)
                
                setOnPreferenceChangeListener { _, newValue ->
                    val enable = newValue as Boolean
                    if (enable) {
                        // Show PIN setup dialog with confirmation
                        showPinSetupDialog()
                        false // Don't change yet, dialog will handle it
                    } else {
                        // Verify current PIN before disabling
                        showVerifyPinDialog {
                            // On success, disable PIN
                            prefs.pinUnlock = false
                            prefs.pin = -1
                            isChecked = false
                            updatePinSummary(this)
                            // Also disable dependent preferences
                            prefs.pinAppLink = false
                            prefs.autoLock = false
                            prefs.autoLockInstantly = false
                            Toast.makeText(requireContext(), R.string.pin_disabled, Toast.LENGTH_SHORT).show()
                        }
                        false // Don't change yet, dialog will handle it
                    }
                }
            }
            
            // PIN app link - lock when opened from external links
            findPreference<SwitchPreferenceCompat>("consent_pin_app_link")?.apply {
                isChecked = prefs.pinAppLink
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.pinAppLink = newValue as Boolean
                    true
                }
            }
            
            // Biometrics - check device support
            findPreference<SwitchPreferenceCompat>("consent_biometrics")?.apply {
                val biometricManager = BiometricManager.from(requireContext())
                val canAuthenticate = biometricManager.canAuthenticate(
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
                )
                
                when (canAuthenticate) {
                    BiometricManager.BIOMETRIC_SUCCESS -> {
                        isEnabled = true
                        isChecked = prefs.biometricsEnabled
                    }
                    BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                        isEnabled = false
                        isChecked = false
                        summary = getString(R.string.biometric_not_available)
                    }
                    BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                        isEnabled = false
                        isChecked = false
                        summary = getString(R.string.biometric_not_available)
                    }
                    BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                        isEnabled = false
                        isChecked = false
                        summary = getString(R.string.biometric_not_enrolled)
                    }
                    else -> {
                        isEnabled = false
                        isChecked = false
                        summary = getString(R.string.biometric_not_available)
                    }
                }
                
                setOnPreferenceChangeListener { _, newValue ->
                    val enable = newValue as Boolean
                    if (enable && !prefs.isPinSet()) {
                        // Require PIN setup first
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.pin_required_title)
                            .setMessage(R.string.pin_required_message)
                            .setPositiveButton(R.string.setup_pin) { _, _ ->
                                showPinSetupDialog()
                            }
                            .setNegativeButton(R.string.cancel, null)
                            .show()
                        false
                    } else {
                        prefs.biometricsEnabled = enable
                        true
                    }
                }
            }
            
            // Auto lock when switching apps
            findPreference<SwitchPreferenceCompat>("consent_pin_auto_lock")?.apply {
                isChecked = prefs.autoLock
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.autoLock = newValue as Boolean
                    // If disabling auto lock, also disable instant lock
                    if (!(newValue as Boolean)) {
                        prefs.autoLockInstantly = false
                        findPreference<SwitchPreferenceCompat>("consent_pin_auto_lock_instantly")?.isChecked = false
                    }
                    true
                }
            }
            
            // Auto lock instantly vs with delay
            findPreference<SwitchPreferenceCompat>("consent_pin_auto_lock_instantly")?.apply {
                isChecked = prefs.autoLockInstantly
                // This should also depend on auto_lock being enabled
                isEnabled = prefs.autoLock && prefs.isPinSet()
                
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.autoLockInstantly = newValue as Boolean
                    true
                }
            }
        }
        
        private fun updatePinSummary(preference: SwitchPreferenceCompat) {
            preference.summary = if (prefs.isPinSet()) {
                getString(R.string.pref_consent_pin_unlock_summary_pin, prefs.getFormattedPin())
            } else {
                getString(R.string.pref_consent_pin_unlock_summary)
            }
        }
        
        private fun showPinSetupDialog() {
            // First step: Enter new PIN
            val layout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(50, 40, 50, 10)
            }
            
            val pinInput = TextInputLayout(requireContext()).apply {
                hint = getString(R.string.enter_pin)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            val pinEditText = TextInputEditText(requireContext()).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                maxLines = 1
            }
            pinInput.addView(pinEditText)
            layout.addView(pinInput)
            
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.setup_pin_title)
                .setMessage(R.string.setup_pin_message)
                .setView(layout)
                .setPositiveButton(R.string.next) { _, _ ->
                    val pin = pinEditText.text.toString()
                    if (validatePinFormat(pin)) {
                        showConfirmPinDialog(pin)
                    } else {
                        Toast.makeText(requireContext(), R.string.invalid_pin, Toast.LENGTH_SHORT).show()
                        findPreference<SwitchPreferenceCompat>("consent_pin_unlock")?.isChecked = false
                    }
                }
                .setNegativeButton(R.string.cancel) { _, _ ->
                    findPreference<SwitchPreferenceCompat>("consent_pin_unlock")?.isChecked = false
                }
                .setOnCancelListener {
                    findPreference<SwitchPreferenceCompat>("consent_pin_unlock")?.isChecked = false
                }
                .show()
        }
        
        private fun showConfirmPinDialog(originalPin: String) {
            val layout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(50, 40, 50, 10)
            }
            
            val confirmInput = TextInputLayout(requireContext()).apply {
                hint = getString(R.string.confirm_pin)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            val confirmEditText = TextInputEditText(requireContext()).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                maxLines = 1
            }
            confirmInput.addView(confirmEditText)
            layout.addView(confirmInput)
            
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.confirm_pin_title)
                .setMessage(R.string.confirm_pin_message)
                .setView(layout)
                .setPositiveButton(R.string.ok) { _, _ ->
                    val confirmPin = confirmEditText.text.toString()
                    if (confirmPin == originalPin) {
                        // PINs match, save it
                        prefs.pinUnlock = true
                        prefs.pin = originalPin.toInt()
                        findPreference<SwitchPreferenceCompat>("consent_pin_unlock")?.apply {
                            isChecked = true
                            updatePinSummary(this)
                        }
                        // Update dependent preference states
                        findPreference<SwitchPreferenceCompat>("consent_pin_auto_lock_instantly")?.isEnabled = true
                        Toast.makeText(requireContext(), R.string.pin_set, Toast.LENGTH_SHORT).show()
                    } else {
                        // PINs don't match
                        Toast.makeText(requireContext(), R.string.pins_dont_match, Toast.LENGTH_SHORT).show()
                        findPreference<SwitchPreferenceCompat>("consent_pin_unlock")?.isChecked = false
                    }
                }
                .setNegativeButton(R.string.cancel) { _, _ ->
                    findPreference<SwitchPreferenceCompat>("consent_pin_unlock")?.isChecked = false
                }
                .setOnCancelListener {
                    findPreference<SwitchPreferenceCompat>("consent_pin_unlock")?.isChecked = false
                }
                .show()
        }
        
        private fun showVerifyPinDialog(onSuccess: () -> Unit) {
            val layout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(50, 40, 50, 10)
            }
            
            val pinInput = TextInputLayout(requireContext()).apply {
                hint = getString(R.string.enter_current_pin)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            val pinEditText = TextInputEditText(requireContext()).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                maxLines = 1
            }
            pinInput.addView(pinEditText)
            layout.addView(pinInput)
            
            var attempts = 0
            val maxAttempts = 3
            
            val dialog = MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.verify_pin_title)
                .setMessage(R.string.verify_pin_message)
                .setView(layout)
                .setPositiveButton(R.string.ok, null) // Set to null, we'll override below
                .setNegativeButton(R.string.cancel, null)
                .create()
            
            dialog.setOnShowListener {
                val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                positiveButton.setOnClickListener {
                    val enteredPin = pinEditText.text.toString()
                    if (enteredPin.length == 4 && enteredPin.toIntOrNull() == prefs.pin) {
                        dialog.dismiss()
                        onSuccess()
                    } else {
                        attempts++
                        if (attempts >= maxAttempts) {
                            dialog.dismiss()
                            Toast.makeText(
                                requireContext(),
                                R.string.too_many_pin_attempts,
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            pinEditText.text?.clear()
                            Toast.makeText(
                                requireContext(),
                                getString(R.string.wrong_pin_attempts, maxAttempts - attempts),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
            
            dialog.show()
        }
        
        private fun validatePinFormat(pin: String): Boolean {
            return pin.length == 4 && pin.all { it.isDigit() }
        }
    }
    
    /**
     * Following settings fragment - Full implementation
     */
    class FollowingSettingsFragment : PreferenceFragmentCompat() {
        
        private val prefs by lazy { E621Application.instance.userPreferences }
        
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences_following, rootKey)
            
            setupFollowingEnabled()
            setupManageTags()
            setupOnlyWifi()
            setupPeriod()
            setupDisplayTag()
            setupDisplayInSavedSearch()
            setupViewLog()
        }
        
        private fun setupFollowingEnabled() {
            findPreference<SwitchPreferenceCompat>("following_enabled")?.apply {
                isChecked = prefs.followedTagsNotificationsEnabled
                setOnPreferenceChangeListener { _, newValue ->
                    val enabled = newValue as Boolean
                    prefs.followedTagsNotificationsEnabled = enabled
                    
                    // Reschedule or cancel worker
                    E621Application.instance.rescheduleFollowedTagsWorker()
                    
                    if (enabled) {
                        Toast.makeText(
                            requireContext(),
                            R.string.following_enabled_message,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    true
                }
            }
        }
        
        private fun setupManageTags() {
            findPreference<Preference>("following_manage")?.apply {
                // Update summary with count
                updateManageTagsSummary(this)
                
                setOnPreferenceClickListener {
                    showManageTagsDialog()
                    true
                }
            }
        }
        
        private fun updateManageTagsSummary(pref: Preference) {
            val count = prefs.followedTags.size
            pref.summary = if (count == 0) {
                getString(R.string.following_no_tags)
            } else {
                getString(R.string.following_tags_count, count)
            }
        }
        
        private fun showManageTagsDialog() {
            val layout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(50, 40, 50, 10)
            }
            
            val editText = EditText(requireContext()).apply {
                hint = getString(R.string.following_edit_hint)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                minLines = 5
                maxLines = 10
                setText(prefs.followingTags)
                gravity = android.view.Gravity.TOP or android.view.Gravity.START
            }
            layout.addView(editText)
            
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.following_edit_title)
                .setMessage(R.string.following_edit_message)
                .setView(layout)
                .setPositiveButton(R.string.save) { _, _ ->
                    prefs.followingTags = editText.text.toString()
                    
                    // Update summary
                    findPreference<Preference>("following_manage")?.let {
                        updateManageTagsSummary(it)
                    }
                    
                    // Reschedule worker with new tags
                    if (prefs.followedTagsNotificationsEnabled) {
                        E621Application.instance.rescheduleFollowedTagsWorker()
                    }
                    
                    Toast.makeText(
                        requireContext(),
                        R.string.following_tags_saved,
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
        
        private fun setupOnlyWifi() {
            findPreference<SwitchPreferenceCompat>("following_only_wifi")?.apply {
                isChecked = prefs.followingOnlyWifi
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.followingOnlyWifi = newValue as Boolean
                    // Reschedule worker with new constraint
                    E621Application.instance.rescheduleFollowedTagsWorker()
                    true
                }
            }
        }
        
        private fun setupPeriod() {
            findPreference<ListPreference>("following_period")?.apply {
                // Set current value based on saved interval
                val savedMinutes = prefs.followedTagsCheckInterval
                value = savedMinutes.toString()
                
                setOnPreferenceChangeListener { _, newValue ->
                    val minutes = (newValue as String).toIntOrNull() ?: 60
                    // Store in minutes
                    prefs.followedTagsCheckInterval = minutes
                    // Reschedule worker with new interval
                    E621Application.instance.rescheduleFollowedTagsWorker()
                    true
                }
            }
        }
        
        private fun setupDisplayTag() {
            findPreference<SwitchPreferenceCompat>("following_display_tag")?.apply {
                isChecked = prefs.followingDisplayTag
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.followingDisplayTag = newValue as Boolean
                    true
                }
            }
        }
        
        private fun setupDisplayInSavedSearch() {
            findPreference<SwitchPreferenceCompat>("following_display_in_saved_search")?.apply {
                isChecked = prefs.followingDisplayInSavedSearch
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.followingDisplayInSavedSearch = newValue as Boolean
                    true
                }
            }
        }
        
        private fun setupViewLog() {
            findPreference<Preference>("following_view_log")?.apply {
                setOnPreferenceClickListener {
                    startActivity(Intent(requireContext(), FollowingLogActivity::class.java))
                    true
                }
            }
        }
    }
    
    /**
     * Searching settings fragment
     */
    class SearchingSettingsFragment : PreferenceFragmentCompat() {
        
        private val prefs by lazy { E621Application.instance.userPreferences }
        
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences_searching, rootKey)
            
            setupSearchHistory()
            setupSearchSuggestions()
            setupSavedNewWindow()
            setupLastOnStart()
            setupInNewTask()
            setupFavOrder()
            setupNewestFirst()
            setupIncludeFlash()
        }
        
        private fun setupSearchHistory() {
            findPreference<SwitchPreferenceCompat>("search_history_enabled")?.apply {
                isChecked = prefs.searchHistoryEnabled
                setOnPreferenceChangeListener { _, newValue ->
                    val enabled = newValue as Boolean
                    prefs.searchHistoryEnabled = enabled
                    
                    // Clear history when disabled
                    if (!enabled) {
                        prefs.clearSearchHistory()
                        Toast.makeText(
                            requireContext(),
                            R.string.search_history_cleared,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    true
                }
            }
        }
        
        private fun setupSearchSuggestions() {
            findPreference<SwitchPreferenceCompat>("search_suggestions")?.apply {
                isChecked = prefs.searchSuggestions
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.searchSuggestions = newValue as Boolean
                    true
                }
            }
        }
        
        private fun setupSavedNewWindow() {
            findPreference<SwitchPreferenceCompat>("search_saved_new_window")?.apply {
                isChecked = prefs.searchSavedNewWindow
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.searchSavedNewWindow = newValue as Boolean
                    true
                }
            }
        }
        
        private fun setupLastOnStart() {
            findPreference<SwitchPreferenceCompat>("search_last_on_start")?.apply {
                isChecked = prefs.searchLastOnStart
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.searchLastOnStart = newValue as Boolean
                    true
                }
            }
        }
        
        private fun setupInNewTask() {
            findPreference<SwitchPreferenceCompat>("search_in_new_task")?.apply {
                isChecked = prefs.searchInNewTask
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.searchInNewTask = newValue as Boolean
                    true
                }
            }
        }
        
        private fun setupFavOrder() {
            findPreference<SwitchPreferenceCompat>("grid_fav_order")?.apply {
                isChecked = prefs.searchFavOrder
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.searchFavOrder = newValue as Boolean
                    true
                }
            }
        }
        
        private fun setupNewestFirst() {
            findPreference<SwitchPreferenceCompat>("search_newest_first")?.apply {
                isChecked = prefs.searchNewestFirst
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.searchNewestFirst = newValue as Boolean
                    true
                }
            }
        }
        
        private fun setupIncludeFlash() {
            findPreference<SwitchPreferenceCompat>("search_include_flash")?.apply {
                isChecked = prefs.searchIncludeFlash
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.searchIncludeFlash = newValue as Boolean
                    true
                }
            }
        }
    }
    
    /**
     * Grid view settings fragment
     */
    class GridSettingsFragment : PreferenceFragmentCompat() {
        
        private val prefs by lazy { E621Application.instance.userPreferences }
        private var viewedPostsCount = 0
        
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences_grid, rootKey)
            
            setupGridStats()
            setupGridInfo()
            setupGridNewLabel()
            setupGridDarkenSeen()
            setupGridHideSeen()
            setupClearViewed()
            setupGridColours()
            setupGridRefresh()
            setupGridHeight()
            setupGridWidth()
            setupPostsCount()
            setupGridGifs()
            setupGridNavigate()
        }
        
        private fun setupGridStats() {
            findPreference<SwitchPreferenceCompat>("grid_stats")?.apply {
                isChecked = prefs.gridStats
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.gridStats = newValue as Boolean
                    true
                }
            }
        }
        
        private fun setupGridInfo() {
            findPreference<SwitchPreferenceCompat>("grid_info")?.apply {
                isChecked = prefs.gridInfo
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.gridInfo = newValue as Boolean
                    true
                }
            }
        }
        
        private fun setupGridNewLabel() {
            findPreference<SwitchPreferenceCompat>("grid_new_label")?.apply {
                isChecked = prefs.gridNewLabel
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.gridNewLabel = newValue as Boolean
                    true
                }
            }
        }
        
        private fun setupGridDarkenSeen() {
            findPreference<SwitchPreferenceCompat>("grid_darken_seen")?.apply {
                isChecked = prefs.gridDarkenSeen
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.gridDarkenSeen = newValue as Boolean
                    true
                }
            }
        }
        
        private fun setupGridHideSeen() {
            findPreference<SwitchPreferenceCompat>("grid_hide_seen")?.apply {
                isChecked = prefs.gridHideSeen
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.gridHideSeen = newValue as Boolean
                    true
                }
            }
        }
        
        private fun setupClearViewed() {
            findPreference<Preference>("grid_darken_clear")?.apply {
                // Load viewed count
                viewedPostsCount = prefs.getViewedPostsCount()
                updateClearViewedSummary(this)
                
                setOnPreferenceClickListener {
                    // Clear viewed posts
                    prefs.clearViewedPosts()
                    viewedPostsCount = 0
                    updateClearViewedSummary(this)
                    Toast.makeText(
                        requireContext(),
                        R.string.grid_viewed_cleared,
                        Toast.LENGTH_SHORT
                    ).show()
                    true
                }
            }
        }
        
        private fun updateClearViewedSummary(pref: Preference) {
            pref.summary = if (viewedPostsCount > 0) {
                getString(R.string.pref_grid_darken_clear_count_summary, viewedPostsCount)
            } else {
                getString(R.string.pref_grid_darken_clear_summary)
            }
        }
        
        private fun setupGridColours() {
            findPreference<SwitchPreferenceCompat>("grid_colours")?.apply {
                isChecked = prefs.gridColours
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.gridColours = newValue as Boolean
                    true
                }
            }
        }
        
        private fun setupGridRefresh() {
            findPreference<SwitchPreferenceCompat>("grid_refresh")?.apply {
                isChecked = prefs.gridRefresh
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.gridRefresh = newValue as Boolean
                    true
                }
            }
        }
        
        private fun setupGridHeight() {
            findPreference<SeekBarPreference>("grid_height")?.apply {
                value = prefs.gridHeight
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.gridHeight = newValue as Int
                    true
                }
                // Click to reset to default
                setOnPreferenceClickListener {
                    value = 110
                    prefs.gridHeight = 110
                    Toast.makeText(requireContext(), R.string.grid_height_reset, Toast.LENGTH_SHORT).show()
                    true
                }
            }
        }
        
        private fun setupGridWidth() {
            findPreference<SeekBarPreference>("grid_width")?.apply {
                value = prefs.gridWidth
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.gridWidth = newValue as Int
                    true
                }
            }
        }
        
        private fun setupPostsCount() {
            findPreference<SeekBarPreference>("search_posts_count")?.apply {
                value = prefs.gridPostsCount
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.gridPostsCount = newValue as Int
                    true
                }
                // Click to reset to default
                setOnPreferenceClickListener {
                    value = 75
                    prefs.gridPostsCount = 75
                    Toast.makeText(requireContext(), R.string.grid_posts_reset, Toast.LENGTH_SHORT).show()
                    true
                }
            }
        }
        
        private fun setupGridGifs() {
            findPreference<SwitchPreferenceCompat>("grid_gifs")?.apply {
                isChecked = prefs.gridGifs
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.gridGifs = newValue as Boolean
                    true
                }
            }
        }
        
        private fun setupGridNavigate() {
            findPreference<SwitchPreferenceCompat>("grid_navigate")?.apply {
                isChecked = prefs.gridNavigate
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.gridNavigate = newValue as Boolean
                    true
                }
            }
        }
    }
    
    /**
     * Post view settings fragment
     */
    class PostSettingsFragment : PreferenceFragmentCompat() {
        
        private val prefs by lazy { E621Application.instance.userPreferences }
        
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences_post, rootKey)
            
            setupExpandTags()
            setupExpandDescription()
            setupBackButton()
            setupLoadHQ()
            setupEdgeNavigation()
            setupBackButtonLocation()
            setupKeepScreenAwake()
            setupHideStatusBar()
            setupHideNavBar()
            setupDefaultVideoQuality()
            setupDefaultVideoFormat()
            setupMuteVideos()
            setupAutoplayVideos()
            setupLandscapeVideos()
            setupFullscreenVideos()
            setupActionUpvoteOnFav()
            setupActionUpvoteOnDownload()
            setupActionFavOnDownload()
            setupLongClickToUnfav()
            setupHideScore()
            setupTopPreview()
            setupControlsFullscreen()
            setupHideComments()
            setupPullToClose()
            setupDataSaver()
            setupDisableShare()
        }
        
        private fun setupExpandTags() {
            findPreference<SwitchPreferenceCompat>("post_expand_tags")?.apply {
                isChecked = prefs.postExpandTags
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.postExpandTags = newValue as Boolean
                    true
                }
            }
        }
        
        private fun setupExpandDescription() {
            findPreference<SwitchPreferenceCompat>("post_expand_description")?.apply {
                isChecked = prefs.postExpandDescription
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.postExpandDescription = newValue as Boolean
                    true
                }
            }
        }
        
        private fun setupBackButton() {
            findPreference<SwitchPreferenceCompat>("post_back_button")?.apply {
                isChecked = prefs.postBackButton
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.postBackButton = newValue as Boolean
                    true
                }
            }
        }
        
        private fun setupLoadHQ() {
            findPreference<SwitchPreferenceCompat>("post_load_hq")?.apply {
                isChecked = prefs.postLoadHQ
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.postLoadHQ = newValue as Boolean
                    true
                }
            }
        }
        
        private fun setupEdgeNavigation() {
            findPreference<SwitchPreferenceCompat>("post_edge_navigation")?.apply {
                isChecked = prefs.postEdgeNavigation
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.postEdgeNavigation = newValue as Boolean
                    true
                }
            }
        }
        
        private fun setupBackButtonLocation() {
            findPreference<ListPreference>("post_back_button_location")?.apply {
                value = prefs.postBackButtonLocation
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.postBackButtonLocation = newValue as String
                    true
                }
            }
        }
        
        private fun setupKeepScreenAwake() {
            findPreference<SwitchPreferenceCompat>("post_keep_screen_awake")?.apply {
                isChecked = prefs.postKeepScreenAwake
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.postKeepScreenAwake = newValue as Boolean
                    true
                }
            }
        }
        
        private fun setupHideStatusBar() {
            findPreference<SwitchPreferenceCompat>("post_hide_status_bar")?.apply {
                isChecked = prefs.postHideStatusBar
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.postHideStatusBar = newValue as Boolean
                    true
                }
            }
        }
        
        private fun setupHideNavBar() {
            findPreference<SwitchPreferenceCompat>("post_hide_nav_bar")?.apply {
                isChecked = prefs.postHideNavBar
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.postHideNavBar = newValue as Boolean
                    true
                }
            }
        }
        
        private fun setupDefaultVideoQuality() {
            findPreference<ListPreference>("post_default_video_quality")?.apply {
                value = prefs.postDefaultVideoQuality
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.postDefaultVideoQuality = newValue as String
                    true
                }
            }
        }
        
        private fun setupDefaultVideoFormat() {
            findPreference<ListPreference>("post_default_video_format")?.apply {
                value = prefs.postDefaultVideoFormat
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.postDefaultVideoFormat = newValue as String
                    true
                }
            }
        }
        
        private fun setupMuteVideos() {
            findPreference<SwitchPreferenceCompat>("post_mute_videos")?.apply {
                isChecked = prefs.postMuteVideos
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.postMuteVideos = newValue as Boolean
                    true
                }
            }
        }
        
        private fun setupAutoplayVideos() {
            findPreference<SwitchPreferenceCompat>("post_autoplay_videos")?.apply {
                isChecked = prefs.postAutoplayVideos
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.postAutoplayVideos = newValue as Boolean
                    true
                }
            }
        }
        
        private fun setupLandscapeVideos() {
            findPreference<SwitchPreferenceCompat>("post_landscape_videos")?.apply {
                isChecked = prefs.postLandscapeVideos
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.postLandscapeVideos = newValue as Boolean
                    true
                }
            }
        }
        
        private fun setupFullscreenVideos() {
            findPreference<SwitchPreferenceCompat>("post_fullscreen_videos")?.apply {
                isChecked = prefs.postFullscreenVideos
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.postFullscreenVideos = newValue as Boolean
                    true
                }
            }
        }
        
        private fun setupActionUpvoteOnFav() {
            findPreference<SwitchPreferenceCompat>("post_action_upvote_on_fav")?.apply {
                isChecked = prefs.postActionUpvoteOnFav
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.postActionUpvoteOnFav = newValue as Boolean
                    true
                }
            }
        }
        
        private fun setupActionUpvoteOnDownload() {
            findPreference<SwitchPreferenceCompat>("post_action_upvote_on_download")?.apply {
                isChecked = prefs.postActionUpvoteOnDownload
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.postActionUpvoteOnDownload = newValue as Boolean
                    true
                }
            }
        }
        
        private fun setupActionFavOnDownload() {
            findPreference<SwitchPreferenceCompat>("post_action_fav_on_download")?.apply {
                isChecked = prefs.postActionFavOnDownload
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.postActionFavOnDownload = newValue as Boolean
                    true
                }
            }
        }
        
        private fun setupLongClickToUnfav() {
            findPreference<SwitchPreferenceCompat>("post_long_click_to_unfav")?.apply {
                isChecked = prefs.postLongClickToUnfav
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.postLongClickToUnfav = newValue as Boolean
                    true
                }
            }
        }
        
        private fun setupHideScore() {
            findPreference<SwitchPreferenceCompat>("post_hide_score")?.apply {
                isChecked = prefs.postHideScore
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.postHideScore = newValue as Boolean
                    true
                }
            }
        }
        
        private fun setupTopPreview() {
            findPreference<SwitchPreferenceCompat>("post_top_preview")?.apply {
                isChecked = prefs.postTopPreview
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.postTopPreview = newValue as Boolean
                    true
                }
            }
        }
        
        private fun setupControlsFullscreen() {
            findPreference<SwitchPreferenceCompat>("post_controls_fullscreen")?.apply {
                isChecked = prefs.postControlsFullscreen
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.postControlsFullscreen = newValue as Boolean
                    true
                }
            }
        }
        
        private fun setupHideComments() {
            findPreference<SwitchPreferenceCompat>("post_hide_comments")?.apply {
                isChecked = prefs.postHideComments
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.postHideComments = newValue as Boolean
                    true
                }
            }
        }
        
        private fun setupPullToClose() {
            findPreference<SwitchPreferenceCompat>("post_pull_to_close")?.apply {
                isChecked = prefs.postPullToClose
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.postPullToClose = newValue as Boolean
                    true
                }
            }
        }
        
        private fun setupDataSaver() {
            findPreference<SwitchPreferenceCompat>("post_data_saver")?.apply {
                isChecked = prefs.postDataSaver
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.postDataSaver = newValue as Boolean
                    true
                }
            }
        }
        
        private fun setupDisableShare() {
            findPreference<SwitchPreferenceCompat>("post_disable_share")?.apply {
                isChecked = prefs.postDisableShare
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.postDisableShare = newValue as Boolean
                    true
                }
            }
        }
    }
    
    /**
     * Storage settings fragment - Full implementation based on decompiled app
     */
    class StorageSettingsFragment : PreferenceFragmentCompat() {
        
        private val prefs by lazy { E621Application.instance.userPreferences }
        
        private val folderPickerLauncher = registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
        ) { uri ->
            uri?.let { handleFolderSelected(it) }
        }
        
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences_storage, rootKey)
            
            setupCustomFolderEnabled()
            setupCustomFolder()
            setupFileNameMask()
            setupOverwrite()
            setupHide()
            setupMaxCache()
            setupMaxCacheSlider()
            setupClearCache()
        }
        
        private fun setupCustomFolderEnabled() {
            findPreference<SwitchPreferenceCompat>("storage_custom_folder_enabled")?.apply {
                isChecked = prefs.storageCustomFolderEnabled
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.storageCustomFolderEnabled = newValue as Boolean
                    true
                }
            }
        }
        
        private fun setupCustomFolder() {
            findPreference<Preference>("storage_custom_folder")?.apply {
                // Update summary with current folder
                val currentFolder = prefs.storageCustomFolder
                if (currentFolder != null) {
                    try {
                        val uri = android.net.Uri.parse(currentFolder)
                        val path = uri.lastPathSegment?.replace("primary:", "/storage/emulated/0/")
                            ?: currentFolder
                        summary = path
                    } catch (e: Exception) {
                        summary = getString(R.string.pref_storage_custom_folder_summary)
                    }
                } else {
                    summary = getString(R.string.pref_storage_custom_folder_summary)
                }
                
                setOnPreferenceClickListener {
                    openFolderPicker()
                    true
                }
            }
        }
        
        private fun openFolderPicker() {
            try {
                folderPickerLauncher.launch(null)
            } catch (e: android.content.ActivityNotFoundException) {
                e.printStackTrace()
                Toast.makeText(
                    requireContext(),
                    R.string.pref_storage_no_app_found,
                    Toast.LENGTH_LONG
                ).show()
                prefs.storageCustomFolderEnabled = false
                findPreference<SwitchPreferenceCompat>("storage_custom_folder_enabled")?.isChecked = false
            }
        }
        
        private fun handleFolderSelected(uri: android.net.Uri) {
            // Take persistable permission
            try {
                requireContext().contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            prefs.storageCustomFolder = uri.toString()
            
            // Update preference summary
            findPreference<Preference>("storage_custom_folder")?.apply {
                val path = uri.lastPathSegment?.replace("primary:", "/storage/emulated/0/")
                    ?: uri.toString()
                summary = path
            }
            
            Toast.makeText(
                requireContext(),
                R.string.pref_storage_folder_selected,
                Toast.LENGTH_SHORT
            ).show()
        }
        
        private fun setupFileNameMask() {
            findPreference<Preference>("storage_file_name_mask")?.apply {
                summary = prefs.storageFileNameMask
                
                setOnPreferenceClickListener {
                    showFileNameMaskDialog()
                    true
                }
            }
        }
        
        private fun showFileNameMaskDialog() {
            val context = requireContext()
            val editText = EditText(context).apply {
                setText(prefs.storageFileNameMask)
                inputType = InputType.TYPE_CLASS_TEXT
                maxLines = 1
                setPadding(48, 32, 48, 16)
            }
            
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.pref_storage_file_name_dialog_title)
                .setMessage(R.string.pref_storage_file_name_dialog_message)
                .setView(editText)
                .setPositiveButton(R.string.save) { _, _ ->
                    val newMask = editText.text.toString()
                    if (newMask.isNotBlank()) {
                        prefs.storageFileNameMask = newMask
                        findPreference<Preference>("storage_file_name_mask")?.summary = newMask
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
        
        private fun setupOverwrite() {
            findPreference<SwitchPreferenceCompat>("storage_overwrite")?.apply {
                isChecked = prefs.storageOverwrite
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.storageOverwrite = newValue as Boolean
                    true
                }
            }
        }
        
        private fun setupHide() {
            findPreference<SwitchPreferenceCompat>("storage_hide")?.apply {
                isChecked = prefs.storageHide
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.storageHide = newValue as Boolean
                    true
                }
            }
        }
        
        private fun setupMaxCache() {
            findPreference<SwitchPreferenceCompat>("storage_max_cache")?.apply {
                isChecked = prefs.storageMaxCache
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.storageMaxCache = newValue as Boolean
                    true
                }
            }
        }
        
        private fun setupMaxCacheSlider() {
            findPreference<SeekBarPreference>("storage_max_cache_slider")?.apply {
                value = prefs.storageMaxCacheSize
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.storageMaxCacheSize = newValue as Int
                    true
                }
            }
        }
        
        private fun setupClearCache() {
            findPreference<Preference>("storage_clear_cache")?.apply {
                // Update summary with current cache size
                updateCacheSizeSummary()
                
                setOnPreferenceClickListener {
                    showClearCacheConfirmation()
                    true
                }
            }
        }
        
        private fun updateCacheSizeSummary() {
            findPreference<Preference>("storage_clear_cache")?.apply {
                try {
                    val cacheSize = getCacheSize()
                    val formattedSize = formatFileSize(cacheSize)
                    summary = getString(R.string.pref_storage_cache_size, formattedSize)
                } catch (e: Exception) {
                    summary = getString(R.string.pref_storage_clear_cache_summary)
                }
            }
        }
        
        private fun getCacheSize(): Long {
            var size: Long = 0
            
            // App cache directory
            requireContext().cacheDir?.let { cacheDir ->
                size += calculateDirSize(cacheDir)
            }
            
            // External cache directory (if available)
            requireContext().externalCacheDir?.let { externalCacheDir ->
                size += calculateDirSize(externalCacheDir)
            }
            
            return size
        }
        
        private fun calculateDirSize(dir: java.io.File): Long {
            var size: Long = 0
            if (dir.isDirectory) {
                dir.listFiles()?.forEach { file ->
                    size += if (file.isDirectory) {
                        calculateDirSize(file)
                    } else {
                        file.length()
                    }
                }
            }
            return size
        }
        
        private fun formatFileSize(bytes: Long): String {
            return when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> "${bytes / 1024} KB"
                bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
                else -> String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
            }
        }
        
        private fun showClearCacheConfirmation() {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.pref_storage_clear_cache_title)
                .setMessage(R.string.pref_storage_clear_cache_summary)
                .setPositiveButton(R.string.ok) { _, _ ->
                    clearCache()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
        
        private fun clearCache() {
            try {
                // Clear app cache
                requireContext().cacheDir?.deleteRecursively()
                
                // Clear external cache
                requireContext().externalCacheDir?.deleteRecursively()
                
                // Clear Glide image cache
                Thread {
                    try {
                        com.bumptech.glide.Glide.get(requireContext()).clearDiskCache()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }.start()
                
                Toast.makeText(
                    requireContext(),
                    R.string.pref_storage_cache_cleared,
                    Toast.LENGTH_SHORT
                ).show()
                
                // Update cache size summary
                updateCacheSizeSummary()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

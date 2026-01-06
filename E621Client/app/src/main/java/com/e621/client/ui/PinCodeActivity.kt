package com.e621.client.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.e621.client.E621Application
import com.e621.client.R
import com.e621.client.ui.saved.SavedSearchesActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * PIN Code Activity - Verifies PIN before granting access to the app
 * Supports biometric authentication if enabled
 */
class PinCodeActivity : AppCompatActivity() {

    private val prefs by lazy { E621Application.instance.userPreferences }

    private lateinit var etPin: EditText
    private lateinit var imgCircle1: ImageView
    private lateinit var imgCircle2: ImageView
    private lateinit var imgCircle3: ImageView
    private lateinit var imgCircle4: ImageView
    private lateinit var llPin: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var txtWrongPin: TextView
    private lateinit var txtForgot: TextView
    private lateinit var btnBio: Button

    private var wrongAttempts = 0
    private val maxAttempts = 5

    override fun onCreate(savedInstanceState: Bundle?) {
        // Check if application is properly initialized
        if (!E621Application.isInitialized) {
            restartApp()
            return
        }
        
        super.onCreate(savedInstanceState)
        
        // Apply FLAG_SECURE if needed
        if (prefs.hideInTasks) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }

        // Check if PIN is actually set
        if (!prefs.isPinSet()) {
            goToMainScreen()
            return
        }

        setContentView(R.layout.activity_pin_code)
        setupViews()
        setupBiometrics()
    }

    private fun setupViews() {
        etPin = findViewById(R.id.etPin)
        imgCircle1 = findViewById(R.id.imgCircle1)
        imgCircle2 = findViewById(R.id.imgCircle2)
        imgCircle3 = findViewById(R.id.imgCircle3)
        imgCircle4 = findViewById(R.id.imgCircle4)
        llPin = findViewById(R.id.llPin)
        progressBar = findViewById(R.id.progressBar)
        txtWrongPin = findViewById(R.id.txtWrongPin)
        txtForgot = findViewById(R.id.txtForgot)
        btnBio = findViewById(R.id.btnBio)

        progressBar.visibility = View.INVISIBLE
        txtWrongPin.visibility = View.INVISIBLE

        // Focus and show keyboard
        etPin.requestFocus()
        etPin.postDelayed({
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(etPin, InputMethodManager.SHOW_IMPLICIT)
        }, 150)

        // Text watcher for PIN entry
        etPin.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val length = s?.length ?: 0
                updateCircles(length)
                
                if (length == 4) {
                    verifyPin(s.toString())
                }
            }
        })

        // Forgot PIN
        txtForgot.setOnClickListener {
            showForgotPinDialog()
        }

        // Biometrics button
        btnBio.setOnClickListener {
            showBiometricPrompt()
        }
    }

    private fun updateCircles(filledCount: Int) {
        val filled = R.drawable.ic_pin_filled
        val empty = R.drawable.ic_pin_empty
        
        imgCircle1.setImageResource(if (filledCount >= 1) filled else empty)
        imgCircle2.setImageResource(if (filledCount >= 2) filled else empty)
        imgCircle3.setImageResource(if (filledCount >= 3) filled else empty)
        imgCircle4.setImageResource(if (filledCount >= 4) filled else empty)
    }

    private fun verifyPin(enteredPin: String) {
        val correctPin = prefs.pin
        val enteredPinInt = enteredPin.toIntOrNull() ?: -1

        if (enteredPinInt == correctPin) {
            // Correct PIN
            goToMainScreen()
        } else {
            // Wrong PIN
            wrongAttempts++
            
            if (wrongAttempts >= maxAttempts) {
                // Too many attempts - logout
                showTooManyAttemptsDialog()
            } else {
                // Show error and reset
                showWrongPinError()
            }
        }
    }

    private fun showWrongPinError() {
        txtWrongPin.text = getString(R.string.wrong_pin_attempts, maxAttempts - wrongAttempts)
        txtWrongPin.visibility = View.VISIBLE
        
        // Shake animation on circles
        val shake = AnimationUtils.loadAnimation(this, R.anim.shake)
        llPin.startAnimation(shake)
        
        // Clear input
        etPin.text?.clear()
        updateCircles(0)
    }

    private fun showTooManyAttemptsDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.too_many_attempts_title)
            .setMessage(R.string.too_many_attempts_message)
            .setPositiveButton(R.string.ok) { _, _ ->
                // Clear all credentials and go to login
                prefs.clearCredentials()
                prefs.pinUnlock = false
                prefs.pin = -1
                goToMainScreen()
            }
            .setCancelable(false)
            .show()
    }

    private fun showForgotPinDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.forgot_pin_title)
            .setMessage(R.string.forgot_pin_message)
            .setPositiveButton(R.string.logout) { _, _ ->
                // Clear all credentials
                prefs.clearCredentials()
                prefs.pinUnlock = false
                prefs.pin = -1
                goToMainScreen()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun setupBiometrics() {
        val biometricManager = BiometricManager.from(this)
        val canAuthenticate = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        )

        if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS && prefs.biometricsEnabled) {
            btnBio.visibility = View.VISIBLE
            // Auto-show biometric prompt on launch
            showBiometricPrompt()
        } else {
            btnBio.visibility = View.GONE
        }
    }

    private fun showBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    goToMainScreen()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    // User can still use PIN
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(this@PinCodeActivity, R.string.biometric_failed, Toast.LENGTH_SHORT).show()
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_title))
            .setSubtitle(getString(R.string.biometric_subtitle))
            .setNegativeButtonText(getString(R.string.use_pin))
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun goToMainScreen() {
        val intent = if (prefs.startInSaved) {
            Intent(this, SavedSearchesActivity::class.java)
        } else {
            Intent(this, MainActivity::class.java)
        }
        startActivity(intent)
        finish()
        overridePendingTransition(0, 0)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Don't allow back - user must enter PIN
        finishAffinity()
    }
    
    private fun restartApp() {
        val intent = Intent(this, LauncherActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }
}

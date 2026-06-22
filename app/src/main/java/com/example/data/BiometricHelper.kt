package com.example.data

import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle

object BiometricHelper {
    fun triggerBiometricPrompt(
        activity: FragmentActivity,
        prefs: LockPreferences,
        onSuccess: () -> Unit
    ) {
        if (activity.isFinishing || activity.isDestroyed) return
        
        activity.window.decorView.post {
            if (activity.isFinishing || activity.isDestroyed) return@post
            if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                android.util.Log.w("BiometricHelper", "Activity is not at least STARTED. State: ${activity.lifecycle.currentState}")
                return@post
            }
            
            try {
                val biometricManager = BiometricManager.from(activity)
                val allowedAuthenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
                val canAuthenticate = biometricManager.canAuthenticate(allowedAuthenticators)

                if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
                    val executor = ContextCompat.getMainExecutor(activity)
                    val biometricPrompt = BiometricPrompt(activity, executor,
                        object : BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                super.onAuthenticationError(errorCode, errString)
                                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                                    Toast.makeText(activity, "Biometric error: $errString", Toast.LENGTH_SHORT).show()
                                }
                            }

                            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                super.onAuthenticationSucceeded(result)
                                onSuccess()
                            }

                            override fun onAuthenticationFailed() {
                                super.onAuthenticationFailed()
                                Toast.makeText(activity, "Biometric authentication failed", Toast.LENGTH_SHORT).show()
                            }
                        })

                    val fallbackLabel = when (prefs.lockType) {
                        "pin" -> "Enter PIN"
                        "pattern" -> "Draw Pattern"
                        "password" -> "Enter Password"
                        else -> "Enter PIN"
                    }

                    val promptInfo = BiometricPrompt.PromptInfo.Builder()
                        .setTitle("Unlock App")
                        .setSubtitle("Authenticate using Face or Fingerprint")
                        .setAllowedAuthenticators(allowedAuthenticators)
                        .setNegativeButtonText(fallbackLabel)
                        .setConfirmationRequired(false)
                        .build()

                    biometricPrompt.authenticate(promptInfo)
                } else {
                    val statusMessage = when (canAuthenticate) {
                        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "Biometric hardware is currently unavailable."
                        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "No biometrics enrolled (fingerprint or face setup required in system settings)."
                        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "This device does not support biometric features."
                        BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> "Biometric security update required."
                        else -> "Biometric features are not active or configured on this device."
                    }
                    android.util.Log.w("BiometricHelper", "Biometric not ready: $statusMessage (canAuthenticate=$canAuthenticate)")
                    Toast.makeText(activity, statusMessage, Toast.LENGTH_LONG).show()
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                android.util.Log.e("BiometricHelper", "Exception during biometric launch", e)
            }
        }
    }
}

package com.example.data

import android.content.Context
import android.content.SharedPreferences

class LockPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "app_locker_preferences",
        Context.MODE_PRIVATE
    )

    companion object {
        private const val KEY_PATTERN = "lock_pattern_passcode"
        private const val KEY_LOCK_TYPE = "lock_type" // "pattern", "pin", "password"
        private const val KEY_PASSCODE = "lock_passcode" // Store PIN/Password string
        private const val KEY_BIOMETRIC_ENABLED = "biometric_auth_enabled"
        private const val KEY_SERVICE_ACTIVE = "locker_service_active"
        private const val KEY_INTRUDER_DETECTION_ENABLED = "intruder_detection"
    }

    var isIntruderDetectionEnabled: Boolean
        get() = prefs.getBoolean(KEY_INTRUDER_DETECTION_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_INTRUDER_DETECTION_ENABLED, value).apply()

    var lockType: String
        get() = prefs.getString(KEY_LOCK_TYPE, "pattern") ?: "pattern"
        set(value) = prefs.edit().putString(KEY_LOCK_TYPE, value).apply()

    var savedPattern: String?
        get() = prefs.getString(KEY_PATTERN, null)
        set(value) = prefs.edit().putString(KEY_PATTERN, value).apply()

    var savedPasscode: String?
        get() = prefs.getString(KEY_PASSCODE, null)
        set(value) = prefs.edit().putString(KEY_PASSCODE, value).apply()

    var isBiometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, value).apply()

    var lockoutEndTimestamp: Long
        get() = prefs.getLong("lockout_end_timestamp", 0L)
        set(value) = prefs.edit().putLong("lockout_end_timestamp", value).apply()

    fun getLockoutEndTimestamp(packageName: String): Long {
        if (packageName.isEmpty()) {
            return lockoutEndTimestamp
        }
        return prefs.getLong("lockout_end_timestamp_$packageName", 0L)
    }

    fun setLockoutEndTimestamp(packageName: String, value: Long) {
        if (packageName.isEmpty()) {
            lockoutEndTimestamp = value
        } else {
            prefs.edit().putLong("lockout_end_timestamp_$packageName", value).apply()
        }
    }

    var isServiceActive: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_ACTIVE, true)
        set(value) = prefs.edit().putBoolean(KEY_SERVICE_ACTIVE, value).apply()

    var isDarkMode: Boolean
        get() = prefs.getBoolean("dark_mode_enabled", true)
        set(value) = prefs.edit().putBoolean("dark_mode_enabled", value).apply()

    fun hasPatternSet(): Boolean {
        return when (lockType) {
            "pattern" -> !savedPattern.isNullOrEmpty()
            "pin", "password" -> !savedPasscode.isNullOrEmpty()
            else -> false
        }
    }

    fun clearPattern() {
        prefs.edit().remove(KEY_PATTERN).remove(KEY_PASSCODE).apply()
    }
}

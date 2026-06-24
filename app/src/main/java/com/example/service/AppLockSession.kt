package com.example.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object AppLockSession {
    // Stores currently unlocked app package names during the current screen-on session
    private val unlockedApps = mutableSetOf<String>()
    private val unlockTimes = mutableMapOf<String, Long>()
    
    // Track the package we are actively unlocking so we do not launch multiple overlay activities
    var activeUnlockingPackage: String? = null

    // Track state of locker service
    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning

    fun setServiceRunning(running: Boolean) {
        _isServiceRunning.value = running
    }

    fun isUnlocked(packageName: String): Boolean {
        synchronized(unlockedApps) {
            return packageName in unlockedApps
        }
    }

    fun getUnlockTime(packageName: String): Long {
        synchronized(unlockedApps) {
            return unlockTimes[packageName] ?: 0L
        }
    }

    fun unlockApp(packageName: String) {
        synchronized(unlockedApps) {
            unlockedApps.add(packageName)
            unlockTimes[packageName] = System.currentTimeMillis()
        }
        if (activeUnlockingPackage == packageName) {
            activeUnlockingPackage = null
        }
    }

    fun lockApp(packageName: String) {
        synchronized(unlockedApps) {
            unlockedApps.remove(packageName)
            unlockTimes.remove(packageName)
        }
    }

    fun clearSession() {
        synchronized(unlockedApps) {
            unlockedApps.clear()
            unlockTimes.clear()
        }
        activeUnlockingPackage = null
    }

    fun getUnlockedAppsCopy(): List<String> {
        synchronized(unlockedApps) {
            return unlockedApps.toList()
        }
    }
}

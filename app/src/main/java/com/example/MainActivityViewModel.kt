package com.example

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.AppRepository
import com.example.data.LockedApp
import com.example.data.LockPreferences
import com.example.data.IntruderAlert
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class GridAppInfo(
    val packageName: String,
    val appName: String,
    val isLocked: Boolean
)

sealed interface SetupState {
    object WelcomePatternRequired : SetupState
    object SelectLockType : SetupState
    object SetFirstPattern : SetupState
    data class ConfirmPattern(val firstAttempt: List<Int>) : SetupState
    object SetFirstPin : SetupState
    data class ConfirmPin(val firstAttempt: String) : SetupState
    object SetFirstPassword : SetupState
    data class ConfirmPassword(val firstAttempt: String) : SetupState
    object SetupSuccess : SetupState
    object SetupFinished : SetupState
}

class MainActivityViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val repository: AppRepository
    private val prefs = LockPreferences(context)

    // State flows
    private val _installedApps = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _isLoadingApps = MutableStateFlow(true)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps

    // Setup Wizard state flow
    private val _setupState = MutableStateFlow<SetupState>(
        if (prefs.hasPatternSet()) SetupState.SetupFinished else SetupState.WelcomePatternRequired
    )
    val setupState: StateFlow<SetupState> = _setupState

    // Track locked apps from Room DB
    val lockedAppsFlow: Flow<List<LockedApp>>

    // Combined stream of installed + search query + lock status
    val appGridState: StateFlow<List<GridAppInfo>>

    init {
        val database = AppDatabase.getInstance(context)
        repository = AppRepository(database.lockedAppDao(), database.intruderAlertDao())
        lockedAppsFlow = repository.allLockedAppsStateFlow

        appGridState = combine(_installedApps, lockedAppsFlow, _searchQuery) { installed, lockedList, query ->
            val lockedPackagesMap = lockedList.associateBy { it.packageName }
            val filtered = if (query.isEmpty()) {
                installed
            } else {
                installed.filter { it.second.contains(query, ignoreCase = true) }
            }

            filtered.map { (packageName, appName) ->
                GridAppInfo(
                    packageName = packageName,
                    appName = appName,
                    isLocked = lockedPackagesMap.containsKey(packageName)
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        loadInstalledApps()
    }

    private fun loadInstalledApps() {
        viewModelScope.launch {
            _isLoadingApps.value = true
            val appsList = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                val intent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val resolveInfos = pm.queryIntentActivities(intent, 0)
                resolveInfos.mapNotNull { resolveInfo ->
                    val packageName = resolveInfo.activityInfo.packageName
                    if (packageName == context.packageName) return@mapNotNull null // Don't lock ourselves
                    try {
                        val appLabel = resolveInfo.loadLabel(pm).toString()
                        Pair(packageName, appLabel)
                    } catch (e: Exception) {
                        null
                    }
                }.distinctBy { it.first }.sortedBy { it.second }
            }
            _installedApps.value = appsList
            _isLoadingApps.value = false
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleAppLock(packageName: String, appName: String, shouldLock: Boolean) {
        viewModelScope.launch {
            if (shouldLock) {
                repository.lockApp(packageName, appName)
            } else {
                repository.unlockApp(packageName)
            }
        }
    }

    // Pattern / PIN / Password wizard control
    fun resetSetupWizard() {
        prefs.clearPattern()
        _setupState.value = SetupState.SelectLockType
    }

    fun startWizard() {
        _setupState.value = SetupState.SelectLockType
    }

    fun selectLockType(type: String) {
        prefs.lockType = type
        when (type) {
            "pattern" -> _setupState.value = SetupState.SetFirstPattern
            "pin" -> _setupState.value = SetupState.SetFirstPin
            "password" -> _setupState.value = SetupState.SetFirstPassword
        }
    }

    fun getLockType(): String {
        return prefs.lockType
    }

    fun handlePatternDrawn(pattern: List<Int>) {
        val current = _setupState.value
        if (pattern.size < 4) {
            return
        }

        when (current) {
            is SetupState.SetFirstPattern -> {
                _setupState.value = SetupState.ConfirmPattern(pattern)
            }
            is SetupState.ConfirmPattern -> {
                if (current.firstAttempt == pattern) {
                    // Success! Store pattern sequence
                    val patternString = pattern.joinToString(",")
                    prefs.savedPattern = patternString
                    prefs.lockType = "pattern"
                    _setupState.value = SetupState.SetupSuccess
                } else {
                    // Mismatch, reset back to first drawing attempt
                    _setupState.value = SetupState.SetFirstPattern
                }
            }
            else -> {}
        }
    }

    fun handlePinEntered(pin: String) {
        val current = _setupState.value
        when (current) {
            is SetupState.SetFirstPin -> {
                _setupState.value = SetupState.ConfirmPin(pin)
            }
            is SetupState.ConfirmPin -> {
                if (current.firstAttempt == pin) {
                    prefs.savedPasscode = pin
                    prefs.lockType = "pin"
                    _setupState.value = SetupState.SetupSuccess
                } else {
                    _setupState.value = SetupState.SetFirstPin
                }
            }
            else -> {}
        }
    }

    fun handlePasswordEntered(password: String) {
        val current = _setupState.value
        when (current) {
            is SetupState.SetFirstPassword -> {
                _setupState.value = SetupState.ConfirmPassword(password)
            }
            is SetupState.ConfirmPassword -> {
                if (current.firstAttempt == password) {
                    prefs.savedPasscode = password
                    prefs.lockType = "password"
                    _setupState.value = SetupState.SetupSuccess
                } else {
                    _setupState.value = SetupState.SetFirstPassword
                }
            }
            else -> {}
        }
    }

    fun completeWizard() {
        _setupState.value = SetupState.SetupFinished
    }

    fun isServiceActive(): Boolean {
        return prefs.isServiceActive
    }

    fun setServiceActive(active: Boolean) {
        prefs.isServiceActive = active
    }

    fun isBiometricEnabled(): Boolean {
        return prefs.isBiometricEnabled
    }

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.isBiometricEnabled = enabled
    }

    fun isIntruderDetectionEnabled(): Boolean {
        return prefs.isIntruderDetectionEnabled
    }

    fun setIntruderDetectionEnabled(enabled: Boolean) {
        prefs.isIntruderDetectionEnabled = enabled
    }

    val intruderAlertsFlow: StateFlow<List<IntruderAlert>> = repository.allIntruderAlertsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun deleteIntruderAlert(alert: IntruderAlert) {
        viewModelScope.launch {
            repository.deleteIntruderAlert(alert)
        }
    }

    fun clearAllIntruderAlerts() {
        viewModelScope.launch {
            repository.deleteAllIntruderAlerts()
        }
    }
}

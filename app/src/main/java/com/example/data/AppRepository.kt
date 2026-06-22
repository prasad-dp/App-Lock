package com.example.data

import kotlinx.coroutines.flow.Flow

class AppRepository(
    private val lockedAppDao: LockedAppDao,
    private val intruderAlertDao: IntruderAlertDao
) {
    val allLockedAppsStateFlow: Flow<List<LockedApp>> = lockedAppDao.getAllLockedAppsFlow()
    val allIntruderAlertsFlow: Flow<List<IntruderAlert>> = intruderAlertDao.getAllAlertsFlow()

    suspend fun getAllLockedApps(): List<LockedApp> {
        return lockedAppDao.getAllLockedApps()
    }

    suspend fun isAppLocked(packageName: String): Boolean {
        return lockedAppDao.isAppLocked(packageName)
    }

    suspend fun lockApp(packageName: String, appName: String) {
        lockedAppDao.insertLockedApp(LockedApp(packageName = packageName, appName = appName, isLocked = true))
    }

    suspend fun unlockApp(packageName: String) {
        lockedAppDao.deleteLockedAppByPackage(packageName)
    }

    suspend fun insertIntruderAlert(alert: IntruderAlert) {
        intruderAlertDao.insertAlert(alert)
    }

    suspend fun deleteIntruderAlert(alert: IntruderAlert) {
        intruderAlertDao.deleteAlert(alert)
    }

    suspend fun deleteAllIntruderAlerts() {
        intruderAlertDao.deleteAllAlerts()
    }
}

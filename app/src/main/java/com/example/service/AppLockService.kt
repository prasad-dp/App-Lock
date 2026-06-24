package com.example.service

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.UnlockActivity
import com.example.data.AppDatabase
import com.example.data.AppRepository
import kotlinx.coroutines.*

class AppLockService : Service() {
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    
    private lateinit var repository: AppRepository
    private val lockedPackages = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val lastSeenForegroundTime = java.util.Collections.synchronizedMap(mutableMapOf<String, Long>())
    private var lastKnownForegroundPackage: String? = null
    
    private val screenLockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                AppLockSession.clearSession()
                Log.d("AppLockService", "Screen off logic: Sessions cleared, apps re-locked.")
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "app_lock_service_channel"
        private const val NOTIFICATION_ID = 101
        private const val TAG = "AppLockService"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service Created")
        AppLockSession.setServiceRunning(true)
        
        // Initialize Room Repo
        val database = AppDatabase.getInstance(this)
        repository = AppRepository(database.lockedAppDao(), database.intruderAlertDao())

        // Cache and dynamically track locked packages in real-time
        serviceScope.launch {
            repository.allLockedAppsStateFlow.collect { list ->
                val packages = list.filter { it.isLocked }.map { it.packageName }
                lockedPackages.clear()
                lockedPackages.addAll(packages)
                Log.d(TAG, "Sync: locked packages updated -> size: ${lockedPackages.size}")
            }
        }

        // Register screen off receiver to lock apps when phone is locked
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        registerReceiver(screenLockReceiver, filter)

        // Start Foreground Service with notification
        createNotificationChannel()
        val notification = createNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start service as foreground", e)
            stopSelf()
        }

        // Start background polling loop
        startCheckingLoop()
    }

    private var lastLaunchTime = 0L

    private fun startCheckingLoop() {
        serviceScope.launch {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            val powerManager = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            if (usm == null) {
                Log.e(TAG, "UsageStatsManager was not available")
                return@launch
            }

            while (isActive) {
                try {
                    // Battery Saver Optimization: Avoid active polling of usage statistics or locks when screen is off
                    if (powerManager != null && !powerManager.isInteractive) {
                        delay(1200) // Sleep peacefully while non-interactive
                        continue
                    }

                    val currentApp = getForegroundPackageName(usm)
                    if (currentApp != null) {
                        lastSeenForegroundTime[currentApp] = System.currentTimeMillis()
                    }

                    // Auto-relock any unlocked app that is no longer in the foreground
                    val currentUnlockedApps = AppLockSession.getUnlockedAppsCopy()
                    for (unlockedApp in currentUnlockedApps) {
                        if (unlockedApp != currentApp) {
                            val lastSeen = lastSeenForegroundTime[unlockedApp] ?: System.currentTimeMillis()
                            val outOfForegroundDuration = System.currentTimeMillis() - lastSeen
                            if (outOfForegroundDuration > 1500) { // 1.5 second grace period to prevent transient lock screens
                                AppLockSession.lockApp(unlockedApp)
                                Log.d(TAG, "Auto-relocked app: $unlockedApp because it was out of foreground for $outOfForegroundDuration ms")
                            }
                        }
                    }

                    if (currentApp != null && currentApp != packageName) {
                        // Check if this package is locked (extremely fast in-memory check)
                        val isLocked = lockedPackages.contains(currentApp)
                        if (isLocked) {
                            // Check if already unlocked in active session
                            val isUnlocked = AppLockSession.isUnlocked(currentApp)
                            val isUnlockingNow = AppLockSession.activeUnlockingPackage == currentApp

                            // If we already tried launching the unlock overlay for this app but after 1.5s the foreground 
                            // app is STILL the locked app (not our overlay), the launch was likely delayed or blocked by Android.
                            // We trigger a re-launch overlay to prevent bypassing.
                            val isLaunchBlocked = isUnlockingNow && (System.currentTimeMillis() - lastLaunchTime > 1500)

                            if (!isUnlocked && (!isUnlockingNow || isLaunchBlocked)) {
                                Log.d(TAG, "Locked app detected: $currentApp. Launching unlock screen. Blocked retry: $isLaunchBlocked")
                                launchUnlockScreen(currentApp)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in checking loop", e)
                }
                delay(50) // Poll every 50ms - ultra-responsive to prevent any target application screen leak
            }
        }
    }

    private fun getForegroundPackageName(usm: UsageStatsManager): String? {
        val endTime = System.currentTimeMillis()
        
        // Use a standard 15-second tracking window, which captures recent transitions flawlessly
        val startTime = endTime - 15000
        val usageEvents = try {
            usm.queryEvents(startTime, endTime)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query usage events", e)
            null
        } ?: return null

        val event = UsageEvents.Event()
        val packageStates = mutableMapOf<String, Int>()
        val packageLastTime = mutableMapOf<String, Long>()

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            val pkg = event.packageName ?: continue
            val type = event.eventType
            if (type == UsageEvents.Event.ACTIVITY_RESUMED || type == UsageEvents.Event.ACTIVITY_PAUSED) {
                packageStates[pkg] = type
                packageLastTime[pkg] = event.timeStamp
            }
        }

        // Filter and find apps whose absolute latest event in the log is a RESUME.
        // This is 100% correct, because any app that was paused cannot be in the foreground.
        val resumedPackages = packageStates.filter { it.value == UsageEvents.Event.ACTIVITY_RESUMED }
        if (resumedPackages.isNotEmpty()) {
            val topPkg = resumedPackages.keys.maxByOrNull { packageLastTime[it] ?: 0L }
            if (topPkg != null) {
                lastKnownForegroundPackage = topPkg
                return topPkg
            }
        }

        // If there were events but none are currently in the RESUMED state, it means the foreground element 
        // is likely the system launcher, an unlogged dialogue, or the lock screen.
        if (packageStates.isNotEmpty()) {
            lastKnownForegroundPackage = null
            return null
        }

        // Fallback: If there was absolutely ZERO usage event in the last 15 seconds, query active stats,
        // but only if the highest used app was touched in the last 8 seconds to prevent stale data recurrence.
        try {
            val fallbackStart = endTime - 10000
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, fallbackStart, endTime)
            if (!stats.isNullOrEmpty()) {
                val sortedStats = stats.sortedByDescending { it.lastTimeUsed }
                val top = sortedStats.firstOrNull()
                if (top != null && (endTime - top.lastTimeUsed) < 8000) {
                    lastKnownForegroundPackage = top.packageName
                    return top.packageName
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed fallback usage stats query", e)
        }

        return null
    }

    @Suppress("DEPRECATION")
    private fun launchUnlockScreen(targetPackage: String) {
        AppLockSession.activeUnlockingPackage = targetPackage
        lastLaunchTime = System.currentTimeMillis()
        val intent = Intent(this, UnlockActivity::class.java).apply {
            putExtra("EXTRA_PACKAGE_NAME", targetPackage)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        // Pass background activity start authority mode on modern Android 14+ targets
        val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ActivityOptions.makeBasic().apply {
                setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            }.toBundle()
        } else {
            null
        }

        try {
            startActivity(intent, options)
        } catch (e: Exception) {
            Log.e(TAG, "Failed standard startActivity overlay", e)
            try {
                startActivity(intent)
            } catch (ex: Exception) {
                Log.e(TAG, "Failed fallback overlay launch", ex)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service Destroyed")
        AppLockSession.setServiceRunning(false)
        unregisterReceiver(screenLockReceiver)
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("App Locker Active")
            .setContentText("Your selected apps are currently locked and protected.")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "App Shield Running Services",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }
}

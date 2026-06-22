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
            if (usm == null) {
                Log.e(TAG, "UsageStatsManager was not available")
                return@launch
            }

            while (isActive) {
                try {
                    val currentApp = getForegroundPackageName(usm)
                    if (currentApp != null && currentApp != packageName) {
                        // Auto-relock any unlocked app that is no longer in the foreground
                        val currentUnlockedApps = AppLockSession.getUnlockedAppsCopy()
                        for (unlockedApp in currentUnlockedApps) {
                            if (unlockedApp != currentApp) {
                                AppLockSession.lockApp(unlockedApp)
                                Log.d(TAG, "Auto-relocked app: $unlockedApp because user navigated to $currentApp")
                            }
                        }

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
                delay(350) // Poll every 350ms - highly responsive yet gentle on the battery
            }
        }
    }

    private fun getForegroundPackageName(usm: UsageStatsManager): String? {
        val endTime = System.currentTimeMillis()
        
        // Performance optimization: check the last 3 seconds first to handle current foreground
        var startTime = endTime - 3000
        var usageEvents = usm.queryEvents(startTime, endTime)
        
        // Fallback to larger windows if no event is found (covers time offset issues or inactive periods)
        if (!usageEvents.hasNextEvent()) {
            startTime = endTime - 15000
            usageEvents = usm.queryEvents(startTime, endTime)
        }
        if (!usageEvents.hasNextEvent()) {
            startTime = endTime - 60000
            usageEvents = usm.queryEvents(startTime, endTime)
        }
        
        val event = UsageEvents.Event()
        var lastResumedPackage: String? = null

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                lastResumedPackage = event.packageName
            }
        }

        // Robust fallback: if no event found, check queryUsageStats
        if (lastResumedPackage == null) {
            try {
                // Ensure fallback query window spans a full 1 minute
                val fallbackStart = endTime - 60000
                val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, fallbackStart, endTime)
                if (!stats.isNullOrEmpty()) {
                    val sortedStats = stats.sortedByDescending { it.lastTimeUsed }
                    lastResumedPackage = sortedStats.firstOrNull()?.packageName
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed fallback usage stats query", e)
            }
        }
        return lastResumedPackage
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

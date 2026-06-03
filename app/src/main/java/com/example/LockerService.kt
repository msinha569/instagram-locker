package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LockerService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var trackingJob: Job? = null
    
    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private var overlayLifecycleOwner: OverlayLifecycleOwner? = null

    companion object {
        private const val TAG = "InstagramLockerService"
        private const val NOTIFICATION_ID = 2026
        private const val INSTAGRAM_PACKAGE = "com.instagram.android"
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "LockerService Created")
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        InstagramLockerManager.init(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "LockerService Started")
        
        // Start Foreground immediately to fulfill system demands
        startForegroundServiceNotification()
        InstagramLockerManager.setServiceRunning(true)

        startTracking()

        return START_STICKY
    }

    private fun startForegroundServiceNotification() {
        val channelId = "instagram_locker_channel"
        val channelName = "Instagram Usage Locker"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors Instagram screen time to improve digital wellbeing."
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            pendingIntentFlags
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Digital Wellbeing Lock Active")
            .setContentText("Monitoring Instagram usage to support focus.")
            .setSmallIcon(applicationInfo.icon)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, 
                notification, 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startTracking() {
        trackingJob?.cancel()
        trackingJob = serviceScope.launch {
            while (isActive) {
                try {
                    val foregroundApp = getForegroundApp(this@LockerService)
                    val isInstagramForeground = (foregroundApp == INSTAGRAM_PACKAGE)

                    // Read configurations
                    val isTest = InstagramLockerManager.isTestMode.value
                    
                    val maxUsage = if (isTest) {
                        10 * 1000L // 10 seconds for Test Mode
                    } else {
                        InstagramLockerManager.usageLimitConfigMs.value
                    }

                    val maxCooldown = if (isTest) {
                        20 * 1000L // 20 seconds for Test Mode
                    } else {
                        InstagramLockerManager.cooldownConfigMs.value
                    }

                    var currentUsage = InstagramLockerManager.accumulatedUsageMs.value
                    var currentInactive = InstagramLockerManager.inactiveDurationMs.value
                    var currentStatus = InstagramLockerManager.lockStatus.value

                    Log.d(TAG, "Poll - App: $foregroundApp, Usage: $currentUsage, Inactive: $currentInactive, Status: $currentStatus")

                    if (currentStatus == LockStatus.UNLOCKED) {
                        if (isInstagramForeground) {
                            currentUsage += 1000L
                            currentInactive = 0L // Reset inactive count, since they just opened it
                            
                            if (currentUsage >= maxUsage) {
                                currentStatus = LockStatus.LOCKED
                                currentInactive = 0L // Cooldown starts running immediately
                            }
                        } else {
                            // Instagram is not active
                            currentInactive += 1000L
                            if (currentInactive >= maxCooldown) {
                                // Left inactive long enough, restore fully to fresh status
                                currentUsage = 0L
                                currentInactive = 0L
                            }
                        }
                    } else { // LOCKED State
                        // Instagram is locked. Cooldown ticks up continuously
                        currentInactive += 1000L
                        
                        if (currentInactive >= maxCooldown) {
                            currentStatus = LockStatus.UNLOCKED
                            currentUsage = 0L
                            currentInactive = 0L
                        }
                    }

                    InstagramLockerManager.updateRealtimeState(
                        context = this@LockerService,
                        detectedApp = foregroundApp,
                        usageMs = currentUsage,
                        inactiveMs = currentInactive,
                        status = currentStatus
                    )

                    // Render or remove block overlay
                    if (currentStatus == LockStatus.LOCKED && isInstagramForeground && Settings.canDrawOverlays(this@LockerService)) {
                        showOverlay()
                    } else {
                        hideOverlay()
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error in tracking loop", e)
                }

                delay(1000L) // Wait exactly 1 second
            }
        }
    }

    private fun showOverlay() {
        if (overlayView != null) return
        
        Log.i(TAG, "Presenting Locker overlay")
        val localWindowManager = windowManager ?: return
        
        val lifecycleOwner = OverlayLifecycleOwner()
        lifecycleOwner.start()
        overlayLifecycleOwner = lifecycleOwner

        overlayView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setContent {
                MyApplicationTheme {
                    LockOverlayScreen(
                        onGoBackHome = {
                            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                                addCategory(Intent.CATEGORY_HOME)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            startActivity(homeIntent)
                        }
                    )
                }
            }
        }

        val typeParam = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            typeParam,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            screenOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        try {
            localWindowManager.addView(overlayView, params)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to render Lock overlay window", e)
            overlayView = null
            overlayLifecycleOwner?.stop()
            overlayLifecycleOwner = null
        }
    }

    private fun hideOverlay() {
        val view = overlayView ?: return
        Log.i(TAG, "Dismissing Locker overlay")
        val localWindowManager = windowManager ?: return
        try {
            localWindowManager.removeView(view)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanly remove overlay view", e)
        }
        overlayView = null
        overlayLifecycleOwner?.stop()
        overlayLifecycleOwner = null
    }

    private fun getForegroundApp(context: Context): String? {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 15 * 1000 // 15 seconds window
        
        val events = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()
        var lastResumedApp: String? = null
        var lastResumedTime = 0L

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                if (event.timeStamp > lastResumedTime) {
                    lastResumedApp = event.packageName
                    lastResumedTime = event.timeStamp
                }
            }
        }

        if (lastResumedApp != null) {
            return lastResumedApp
        }

        // Method B: Fallback daily analytics sorting
        val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, endTime - 3600 * 1000, endTime)
        if (!stats.isNullOrEmpty()) {
            val latest = stats.maxByOrNull { it.lastTimeUsed }
            if (latest != null && (endTime - latest.lastTimeUsed < 5000)) {
                return latest.packageName
            }
        }

        return null
    }

    override fun onDestroy() {
        Log.i(TAG, "LockerService Destroyed")
        trackingJob?.cancel()
        hideOverlay()
        InstagramLockerManager.setServiceRunning(false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    // Custom Lifecycle container wrapper for full Jetpack Compose integration within non-Activity windows
    private class OverlayLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val myViewModelStore = ViewModelStore()
        private val savedStateController = SavedStateRegistryController.create(this)

        override val lifecycle: Lifecycle = lifecycleRegistry
        override val viewModelStore: ViewModelStore = myViewModelStore
        override val savedStateRegistry: SavedStateRegistry = savedStateController.savedStateRegistry

        init {
            savedStateController.performRestore(null)
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
        }

        fun start() {
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        }

        fun stop() {
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        }
    }
}

package com.example

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LockStatus {
    UNLOCKED,
    LOCKED
}

object InstagramLockerManager {
    // Settings & Service States
    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _lockStatus = MutableStateFlow(LockStatus.UNLOCKED)
    val lockStatus: StateFlow<LockStatus> = _lockStatus.asStateFlow()

    // Test mode allows seconds instead of minutes for quick user evaluation
    private val _isTestMode = MutableStateFlow(false)
    val isTestMode: StateFlow<Boolean> = _isTestMode.asStateFlow()

    // Config durations (in Milliseconds)
    private val _usageLimitConfigMs = MutableStateFlow(10 * 60 * 1000L) // Default 10 mins
    val usageLimitConfigMs: StateFlow<Long> = _usageLimitConfigMs.asStateFlow()

    private val _cooldownConfigMs = MutableStateFlow(20 * 60 * 1000L) // Default 20 mins
    val cooldownConfigMs: StateFlow<Long> = _cooldownConfigMs.asStateFlow()

    // Runtime state tracking (live values updated by the monitoring service)
    private val _accumulatedUsageMs = MutableStateFlow(0L)
    val accumulatedUsageMs: StateFlow<Long> = _accumulatedUsageMs.asStateFlow()

    private val _inactiveDurationMs = MutableStateFlow(0L)
    val inactiveDurationMs: StateFlow<Long> = _inactiveDurationMs.asStateFlow()

    private val _lastDetectedApp = MutableStateFlow<String?>(null)
    val lastDetectedApp: StateFlow<String?> = _lastDetectedApp.asStateFlow()

    private val _totalLocksCount = MutableStateFlow(0)
    val totalLocksCount: StateFlow<Int> = _totalLocksCount.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("instagram_locker_prefs", Context.MODE_PRIVATE)
        _isTestMode.value = prefs.getBoolean("is_test_mode", false)
        _usageLimitConfigMs.value = prefs.getLong("usage_limit_ms", 10 * 60 * 1000L)
        _cooldownConfigMs.value = prefs.getLong("cooldown_ms", 20 * 60 * 1000L)
        _totalLocksCount.value = prefs.getInt("total_locks", 0)
        _accumulatedUsageMs.value = prefs.getLong("accumulated_usage", 0L)
        _inactiveDurationMs.value = prefs.getLong("inactive_duration", 0L)
        _lockStatus.value = LockStatus.valueOf(
            prefs.getString("lock_status", LockStatus.UNLOCKED.name) ?: LockStatus.UNLOCKED.name
        )
    }

    fun setServiceRunning(running: Boolean) {
        _isServiceRunning.value = running
    }

    fun setTestMode(context: Context, enabled: Boolean) {
        _isTestMode.value = enabled
        saveSetting(context, "is_test_mode", enabled)
        resetTimers(context)
    }

    fun setUsageLimitConfig(context: Context, minutes: Int) {
        val ms = minutes * 60 * 1000L
        _usageLimitConfigMs.value = ms
        saveSetting(context, "usage_limit_ms", ms)
    }

    fun setCooldownConfig(context: Context, minutes: Int) {
        val ms = minutes * 60 * 1000L
        _cooldownConfigMs.value = ms
        saveSetting(context, "cooldown_ms", ms)
    }

    private fun incrementLocksCount(context: Context) {
        _totalLocksCount.value += 1
        saveSetting(context, "total_locks", _totalLocksCount.value)
    }

    fun updateRealtimeState(
        context: Context,
        detectedApp: String?,
        usageMs: Long,
        inactiveMs: Long,
        status: LockStatus
    ) {
        _lastDetectedApp.value = detectedApp
        _accumulatedUsageMs.value = usageMs
        _inactiveDurationMs.value = inactiveMs
        
        if (_lockStatus.value != status) {
            _lockStatus.value = status
            saveSetting(context, "lock_status", status.name)
            if (status == LockStatus.LOCKED) {
                incrementLocksCount(context)
            }
        }
        
        saveSetting(context, "accumulated_usage", usageMs)
        saveSetting(context, "inactive_duration", inactiveMs)
    }

    fun resetTimers(context: Context) {
        _accumulatedUsageMs.value = 0L
        _inactiveDurationMs.value = 0L
        _lockStatus.value = LockStatus.UNLOCKED
        _lastDetectedApp.value = null
        saveSetting(context, "accumulated_usage", 0L)
        saveSetting(context, "inactive_duration", 0L)
        saveSetting(context, "lock_status", LockStatus.UNLOCKED.name)
    }

    private fun saveSetting(context: Context, key: String, value: Any) {
        val prefs = context.getSharedPreferences("instagram_locker_prefs", Context.MODE_PRIVATE)
        with(prefs.edit()) {
            when (value) {
                is Boolean -> putBoolean(key, value)
                is Long -> putLong(key, value)
                is Int -> putInt(key, value)
                is String -> putString(key, value)
            }
            apply()
        }
    }
}

package com.example

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize our global thread-safe Preference storage
        InstagramLockerManager.init(this)

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFFEF7FF))
                ) { innerPadding ->
                    LockerDashboardScreen(
                        modifier = Modifier.padding(innerPadding),
                        onRequestPermissions = { checkPermissionsAndRequest() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Force state update to reflect granted permissions instantly
        // when coming back from System Settings redirects.
        triggerPermissionPoll()
    }

    private fun triggerPermissionPoll() {
        // Simple trick to force Composable recomposition
    }

    private fun checkPermissionsAndRequest() {
        // Request notification permission if Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }
    }
}

@Composable
fun LockerDashboardScreen(
    modifier: Modifier = Modifier,
    onRequestPermissions: () -> Unit
) {
    val context = LocalContext.current
    
    // Read state flows in real-time
    val isRunning by InstagramLockerManager.isServiceRunning.collectAsState()
    val lockStatus by InstagramLockerManager.lockStatus.collectAsState()
    val isTestMode by InstagramLockerManager.isTestMode.collectAsState()
    val usageConfig by InstagramLockerManager.usageLimitConfigMs.collectAsState()
    val cooldownConfig by InstagramLockerManager.cooldownConfigMs.collectAsState()
    val currentUsage by InstagramLockerManager.accumulatedUsageMs.collectAsState()
    val currentInactive by InstagramLockerManager.inactiveDurationMs.collectAsState()
    val lastAppDetected by InstagramLockerManager.lastDetectedApp.collectAsState()
    val countLocks by InstagramLockerManager.totalLocksCount.collectAsState()

    // Config conversions (ms to minutes representation)
    val usageMinutes = (usageConfig / (60 * 1000)).toInt().coerceIn(1, 60)
    val cooldownMinutes = (cooldownConfig / (60 * 1000)).toInt().coerceIn(1, 120)

    // Check states of native permissions
    var hasNotificationPermission by remember { mutableStateOf(false) }
    var hasUsageStatsPermission by remember { mutableStateOf(false) }
    var hasOverlayPermission by remember { mutableStateOf(false) }

    // Periodically poll permissions to ensure UI state stays synchronized
    LaunchedEffect(Unit) {
        while (true) {
            hasNotificationPermission = checkNotificationPermission(context)
            hasUsageStatsPermission = checkUsageStatsPermission(context)
            hasOverlayPermission = Settings.canDrawOverlays(context)
            kotlinx.coroutines.delay(1000L)
        }
    }

    val areAllPermissionsGranted = hasUsageStatsPermission && hasOverlayPermission

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFEF7FF)) // Light lavender background
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Elegant Cosmic Header
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF6750A4))
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Instagram Control",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Text(
                            text = "ZenLock",
                            color = Color(0xFF1D1B20),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                    Text(
                        text = "Mindful, automated screen-time boundaries",
                        color = Color(0xFF49454F),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Real-time Wellness State Card
            item {
                LiveStateConsole(
                    isRunning = isRunning,
                    lockStatus = lockStatus,
                    isTestMode = isTestMode,
                    usageConfig = usageConfig,
                    cooldownConfig = cooldownConfig,
                    currentUsage = currentUsage,
                    currentInactive = currentInactive
                )
            }

            // Foreground Service Action Control Button
            item {
                ServiceControlButton(
                    isRunning = isRunning,
                    permissionsGranted = areAllPermissionsGranted,
                    onStart = {
                        val intent = Intent(context, LockerService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(intent)
                        } else {
                            context.startService(intent)
                        }
                    },
                    onStop = {
                        val intent = Intent(context, LockerService::class.java)
                        context.stopService(intent)
                    }
                )
            }

            // Active Rules Settings Slider Card
            item {
                RulesConfigurationCard(
                    usageMinutes = usageMinutes,
                    cooldownMinutes = cooldownMinutes,
                    isTestMode = isTestMode,
                    onUsageChange = { InstagramLockerManager.setUsageLimitConfig(context, it) },
                    onCooldownChange = { InstagramLockerManager.setCooldownConfig(context, it) }
                )
            }

            // Demonstration and Demo Config
            item {
                DemoTestModeCard(
                    isTestMode = isTestMode,
                    onToggle = { InstagramLockerManager.setTestMode(context, it) }
                )
            }

            // Mandatory System permissions card
            item {
                SystemPermissionsGroup(
                    context = context,
                    hasUsage = hasUsageStatsPermission,
                    hasOverlay = hasOverlayPermission,
                    hasNotify = hasNotificationPermission,
                    onTriggerNotificationRequest = onRequestPermissions
                )
            }

            // Statistics Logs Panel
            item {
                AnalyticsDashboardCard(
                    lastDetectedApp = lastAppDetected,
                    locksCount = countLocks,
                    onReset = { InstagramLockerManager.resetTimers(context) }
                )
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun LiveStateConsole(
    isRunning: Boolean,
    lockStatus: LockStatus,
    isTestMode: Boolean,
    usageConfig: Long,
    cooldownConfig: Long,
    currentUsage: Long,
    currentInactive: Long
) {
    val maxUsage = if (isTestMode) 10 * 1000L else usageConfig
    val maxCooldown = if (isTestMode) 20 * 1000L else cooldownConfig

    val formattedUsageLimit = if (isTestMode) "10s (Test)" else "${maxUsage / (60 * 1000)}m"
    val formattedCooldownLimit = if (isTestMode) "20s (Test)" else "${maxCooldown / (60 * 1000)}m"

    val isLocked = lockStatus == LockStatus.LOCKED

    val usageRemainingMs = (maxUsage - currentUsage).coerceAtLeast(0L)
    val cooldownRemainingMs = (maxCooldown - currentInactive).coerceAtLeast(0L)

    val usageMinutes = (usageRemainingMs / 1000) / 60
    val usageSeconds = (usageRemainingMs / 1000) % 60
    
    val cooldownMinutes = (cooldownRemainingMs / 1000) / 60
    val cooldownSeconds = (cooldownRemainingMs / 1000) % 60

    val visualBrush = if (!isRunning) {
        Brush.verticalGradient(
            colors = listOf(Color(0xFFF7F2FA), Color(0xFFF1EDF5))
        )
    } else if (isLocked) {
        Brush.verticalGradient(
            colors = listOf(Color(0xFFEADDFF), Color(0xFFD6C8F2))
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(Color(0xFFF7F2FA), Color(0xFFE8DEF8))
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(28.dp))
            .border(
                1.dp,
                Color(0xFFCAC4D0),
                RoundedCornerShape(28.dp)
            ),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(visualBrush)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Live Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (!isRunning) Color(0xFFE8DEF8) else if (isLocked) Color(0xFFF9DEDC) else Color(0xFFE8DEF8)
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (!isRunning) Color(0xFF49454F) else if (isLocked) Color(0xFFB3261E) else Color(0xFF6750A4)
                            )
                    )
                    Text(
                        text = if (!isRunning) "MONITOR INACTIVE" else if (isLocked) "INSTAGRAM LOCKED" else "MONITORING ACTIVE",
                        color = if (!isRunning) Color(0xFF49454F) else if (isLocked) Color(0xFF410E0B) else Color(0xFF21005D),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Massive Time Center Display
            if (!isRunning) {
                Text(
                    text = "-- : --",
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF49454F),
                    letterSpacing = 2.sp,
                    fontFamily = FontFamily.SansSerif
                )
                Text(
                    text = "Press start to initiate digital boundaries",
                    fontSize = 13.sp,
                    color = Color(0xFF49454F),
                    modifier = Modifier.padding(top = 8.dp),
                    textAlign = TextAlign.Center
                )
            } else if (isLocked) {
                Text(
                    text = String.format("%02d:%02d", cooldownMinutes, cooldownSeconds),
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF21005D),
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.SansSerif
                )
                Text(
                    text = "Instagram Blocked • Cooldown Cooldown",
                    fontSize = 13.sp,
                    color = Color(0xFF21005D),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else {
                Text(
                    text = String.format("%02d:%02d", usageMinutes, usageSeconds),
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF6750A4),
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.SansSerif
                )
                Text(
                    text = "Usage Time Remaining",
                    fontSize = 13.sp,
                    color = Color(0xFF49454F),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Limit details grids
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                RuleMiniWidget(
                    title = "SCREEN ALLOWED",
                    value = formattedUsageLimit,
                    icon = Icons.Default.Notifications,
                    color = Color(0xFF6750A4)
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(36.dp)
                        .background(Color(0xFFCAC4D0))
                )
                RuleMiniWidget(
                    title = "LOCK DURATION",
                    value = formattedCooldownLimit,
                    icon = Icons.Default.Lock,
                    color = Color(0xFF6750A4)
                )
            }
        }
    }
}

@Composable
fun RuleMiniWidget(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = title,
                color = Color(0xFF49454F),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
        Text(
            text = value,
            color = Color(0xFF1D1B20),
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun ServiceControlButton(
    isRunning: Boolean,
    permissionsGranted: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val buttonBg = if (!isRunning) {
        if (permissionsGranted) Color(0xFF6750A4) else Color(0xFFE8DEF8)
    } else {
        Color(0xFFB3261E)
    }

    Button(
        onClick = { if (isRunning) onStop() else onStart() },
        enabled = permissionsGranted || isRunning,
        colors = ButtonDefaults.buttonColors(
            containerColor = buttonBg,
            contentColor = Color.White,
            disabledContainerColor = Color(0xFFF3EDF7),
            disabledContentColor = Color(0xFF938F99)
        ),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .testTag("action_service_btn")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isRunning) Icons.Default.Close else Icons.Default.PlayArrow,
                contentDescription = if (isRunning) "Stop" else "Start",
                tint = if (isRunning) Color.White else if (permissionsGranted) Color.White else Color(0xFF938F99),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = if (isRunning) "STOP INTUITIVE LOCKER" else "START INTUITIVE LOCKER",
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun RulesConfigurationCard(
    usageMinutes: Int,
    cooldownMinutes: Int,
    isTestMode: Boolean,
    onUsageChange: (Int) -> Unit,
    onCooldownChange: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F2FA)),
        border = BorderStroke(1.dp, Color(0xFFCAC4D0))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = Color(0xFF6750A4),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Control Thresholds",
                    color = Color(0xFF1D1B20),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Animated Warning overlay when sliders are locked out during Test Mode
            AnimatedVisibility(
                visible = isTestMode,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF9DEDC))
                        .border(1.dp, Color(0xFFB3261E).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Sliders are inactive while 'Test Mode' runs. Limits are accelerated to 10s Screen / 20s Break.",
                        color = Color(0xFF410E0B),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        lineHeight = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Screen Lock limit
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Instagram Usage Limit",
                        color = Color(0xFF49454F),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "$usageMinutes min",
                        color = if (isTestMode) Color(0xFF938F99) else Color(0xFF6750A4),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Slider(
                    value = usageMinutes.toFloat(),
                    onValueChange = { onUsageChange(it.toInt()) },
                    valueRange = 1f..60f,
                    enabled = !isTestMode,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF6750A4),
                        activeTrackColor = Color(0xFF6750A4),
                        inactiveTrackColor = Color(0xFFE8DEF8),
                        disabledThumbColor = Color(0xFF938F99),
                        disabledActiveTrackColor = Color(0xFFE8DEF8)
                    ),
                    modifier = Modifier.testTag("usage_slider")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Cool-off break limit
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Break Duration",
                        color = Color(0xFF49454F),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "$cooldownMinutes min",
                        color = if (isTestMode) Color(0xFF938F99) else Color(0xFF6750A4),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Slider(
                    value = cooldownMinutes.toFloat(),
                    onValueChange = { onCooldownChange(it.toInt()) },
                    valueRange = 5f..120f,
                    enabled = !isTestMode,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF6750A4),
                        activeTrackColor = Color(0xFF6750A4),
                        inactiveTrackColor = Color(0xFFE8DEF8),
                        disabledThumbColor = Color(0xFF938F99),
                        disabledActiveTrackColor = Color(0xFFE8DEF8)
                    ),
                    modifier = Modifier.testTag("cooldown_slider")
                )
            }
        }
    }
}

@Composable
fun DemoTestModeCard(
    isTestMode: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F2FA)),
        border = BorderStroke(1.dp, Color(0xFFCAC4D0))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Accelerated Demo Mode",
                        color = Color(0xFF1D1B20),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFEADDFF))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "TEST",
                            color = Color(0xFF21005D),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
                Text(
                    text = "Switches minutes to seconds! Tests lock cycle instantly: 10s usage transforms into 20s lock break.",
                    color = Color(0xFF49454F),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))

            Switch(
                checked = isTestMode,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF6750A4),
                    checkedTrackColor = Color(0xFFE8DEF8),
                    uncheckedThumbColor = Color(0xFF938F99),
                    uncheckedTrackColor = Color(0xFFF3EDF7)
                ),
                modifier = Modifier.testTag("test_switch")
            )
        }
    }
}

@Composable
fun SystemPermissionsGroup(
    context: Context,
    hasUsage: Boolean,
    hasOverlay: Boolean,
    hasNotify: Boolean,
    onTriggerNotificationRequest: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F2FA)),
        border = BorderStroke(1.dp, Color(0xFFCAC4D0))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color(0xFF6750A4),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Mandatory Authorizations",
                    color = Color(0xFF1D1B20),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Sub-indicator
            Text(
                text = "These system authorizations allow the Locker to monitor active processes and overlay lock sheets securely.",
                color = Color(0xFF49454F),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            // Permission widget A (Usage Access Stats)
            PermissionItemWidget(
                title = "Usage Statistics Access",
                description = "Lets us monitor when Instagram switches to foreground.",
                isGranted = hasUsage,
                buttonLabel = "Authorize",
                onAuthorize = {
                    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                },
                testTag = "perm_usage_btn"
            )

            // Permission widget B (Draw Overlays)
            PermissionItemWidget(
                title = "Display Over Other Apps",
                description = "Required to render the lock sheet block overlay safely.",
                isGranted = hasOverlay,
                buttonLabel = "Allow",
                onAuthorize = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    ).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                },
                testTag = "perm_overlay_btn"
            )

            // Permission widget C (Notification)
            PermissionItemWidget(
                title = "Notification Controls",
                description = "Keeps the background service persistent per Android rules.",
                isGranted = hasNotify,
                buttonLabel = "Grant",
                onAuthorize = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        onTriggerNotificationRequest()
                    } else {
                        // Forward older versions to notification config
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    }
                },
                testTag = "perm_notify_btn"
            )
        }
    }
}

@Composable
fun PermissionItemWidget(
    title: String,
    description: String,
    isGranted: Boolean,
    buttonLabel: String,
    onAuthorize: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFFEF7FF))
            .border(1.dp, Color(0x22CAC4D0), RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isGranted) Color(0xFF008000) else Color(0xFFB3261E))
                )
                Text(
                    text = title,
                    color = Color(0xFF1D1B20),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = description,
                color = Color(0xFF49454F),
                fontSize = 11.sp,
                lineHeight = 14.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        if (isGranted) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFFD1F4D3))
                    .border(1.dp, Color(0xFF008000).copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "ACTIVE",
                    color = Color(0xFF0A320F),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        } else {
            Button(
                onClick = onAuthorize,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6750A4),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .height(36.dp)
                    .testTag(testTag),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 0.dp)
            ) {
                Text(
                    text = buttonLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun AnalyticsDashboardCard(
    lastDetectedApp: String?,
    locksCount: Int,
    onReset: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F2FA)),
        border = BorderStroke(1.dp, Color(0xFFCAC4D0))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFF6750A4),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Session Statistics",
                        color = Color(0xFF1D1B20),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset stats",
                    tint = Color(0xFF6750A4),
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { onReset() }
                        .testTag("reset_analytics_btn")
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Stat units
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatPanelItem(
                    title = "Instagram Locks",
                    value = "$locksCount",
                    modifier = Modifier.weight(1f)
                )
                StatPanelItem(
                    title = "Foreground Pack",
                    value = if (lastDetectedApp != null) {
                        if (lastDetectedApp.contains("instagram")) "com.instagram" else {
                            val parts = lastDetectedApp.split(".")
                            parts.lastOrNull() ?: lastDetectedApp
                        }
                    } else "None",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun StatPanelItem(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFFEF7FF))
            .border(1.dp, Color(0x22CAC4D0), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Text(
            text = title,
            color = Color(0xFF49454F),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
        Text(
            text = value,
            color = Color(0xFF1D1B20),
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(top = 4.dp),
            maxLines = 1
        )
    }
}

private fun checkNotificationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

private fun checkUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

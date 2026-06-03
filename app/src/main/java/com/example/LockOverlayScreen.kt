package com.example

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LockOverlayScreen(
    onGoBackHome: () -> Unit
) {
    val isTestMode by InstagramLockerManager.isTestMode.collectAsState()
    val cooldownConfig by InstagramLockerManager.cooldownConfigMs.collectAsState()
    val inactiveDuration by InstagramLockerManager.inactiveDurationMs.collectAsState()

    val maxCooldown = if (isTestMode) 20 * 1000L else cooldownConfig
    val remainingMs = (maxCooldown - inactiveDuration).coerceAtLeast(0L)

    val minutes = (remainingMs / 1000) / 60
    val seconds = (remainingMs / 1000) % 60
    val formattedTime = String.format("%02d:%02d", minutes, seconds)

    val progress = if (maxCooldown > 0) {
        (inactiveDuration.toFloat() / maxCooldown.toFloat()).coerceIn(0f, 1f)
    } else {
        1f
    }

    // Sleek background gradient matching the Professional Polish theme
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFFEF7FF), // Primary light lavender surface
            Color(0xFFF3EDF7)  // Slanted darker lavender depth
        )
    )

    // Sleek rotating visual transition for the icon
    val infiniteTransition = rememberInfiniteTransition(label = "lock_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing)
        ),
        label = "rotation"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Warning badge header
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFFEADDFF))
                        .border(1.dp, Color(0xFF6750A4), RoundedCornerShape(50))
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "DIGITAL WELLBEING LOCK",
                        color = Color(0xFF21005D),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Beautiful glowing ring containing rotating/floating elements
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(160.dp)
                ) {
                    // Outer rotating accent track
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .rotate(rotation)
                            .border(
                                width = 3.dp,
                                brush = Brush.sweepGradient(
                                    colors = listOf(
                                        Color(0xFF6750A4),
                                        Color(0x336750A4),
                                        Color(0xFF6750A4).copy(alpha = 0.8f)
                                    )
                                ),
                                shape = CircleShape
                            )
                    )

                    // Inner circle
                    Box(
                        modifier = Modifier
                            .size(130.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF7F2FA))
                            .border(1.dp, Color(0x33CAC4D0), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Locked",
                                tint = Color(0xFF6750A4),
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Heading title
                Text(
                    text = "Instagram is Locked",
                    color = Color(0xFF1D1B20),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = FontFamily.SansSerif,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Captivating description
                Text(
                    text = "You've crushed your 10 minutes limit! Step away for a brief digital reset.",
                    color = Color(0xFF49454F),
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(40.dp))

                // Countdown Timer Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(0xFFEADDFF))
                        .border(1.dp, Color(0xFFCAC4D0), RoundedCornerShape(28.dp))
                        .padding(vertical = 20.dp, horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "UNLOCKING IN",
                            color = Color(0xFF21005D),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Text(
                            text = formattedTime,
                            color = Color(0xFF6750A4),
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = FontFamily.SansSerif,
                            letterSpacing = 2.sp
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Linear visual completion track
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0x3321005D))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                Color(0xFF6750A4),
                                                Color(0xFFEADDFF)
                                            )
                                        )
                                    )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))

                // Primary exit button to prevent stuck loop
                Button(
                    onClick = onGoBackHome,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6750A4),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(54.dp)
                        .testTag("exit_locked_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "Exit to Launcher",
                        tint = Color.White,
                        modifier = Modifier.padding(end = 8.dp).size(18.dp)
                    )
                    Text(
                        text = "Take A Deep Breath & Go Home",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.session.SessionState
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.GlowEmerald
import com.example.ui.theme.NeonPink
import com.example.ui.theme.ObsidianDark
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun FuturisticOrb(
    sessionState: SessionState,
    amplitude: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_transition")

    // Rotation angle for orbital energy ring
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Breathing pulse for idle state
    val breathingPulse by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing"
    )

    // Smooth amplitude scale
    val animatedAmp = remember { Animatable(0f) }
    LaunchedEffect(amplitude) {
        animatedAmp.animateTo(
            targetValue = amplitude.coerceIn(0f, 1f),
            animationSpec = tween(durationMillis = 60)
        )
    }

    val primaryAuraColor = when (sessionState) {
        is SessionState.Disconnected -> Color(0xFF334155)
        is SessionState.Connecting -> ElectricPurple
        is SessionState.Listening -> CyberCyan
        is SessionState.Speaking -> NeonPink
        is SessionState.Error -> Color(0xFFFF4D4F)
    }

    val secondaryAuraColor = when (sessionState) {
        is SessionState.Disconnected -> Color(0xFF1E293B)
        is SessionState.Connecting -> CyberCyan
        is SessionState.Listening -> GlowEmerald
        is SessionState.Speaking -> ElectricPurple
        is SessionState.Error -> Color(0xFF991B1B)
    }

    Box(
        modifier = modifier
            .size(240.dp)
            .testTag("central_mic_button")
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = primaryAuraColor),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerOffset = Offset(size.width / 2, size.height / 2)
            val baseRadius = size.width * 0.32f

            val dynamicMultiplier = if (sessionState is SessionState.Listening || sessionState is SessionState.Speaking) {
                1f + animatedAmp.value * 0.45f
            } else {
                breathingPulse
            }

            // 1. Outer ambient glow ring
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryAuraColor.copy(alpha = 0.35f * dynamicMultiplier),
                        secondaryAuraColor.copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    center = centerOffset,
                    radius = baseRadius * 1.55f * dynamicMultiplier
                ),
                radius = baseRadius * 1.55f * dynamicMultiplier,
                center = centerOffset
            )

            // 2. Secondary ripple wave ring for live voice states
            if (sessionState is SessionState.Listening || sessionState is SessionState.Speaking) {
                val waveRadius = baseRadius * (1.15f + animatedAmp.value * 0.5f)
                drawCircle(
                    color = primaryAuraColor.copy(alpha = 0.4f - (animatedAmp.value * 0.2f)),
                    radius = waveRadius,
                    center = centerOffset,
                    style = Stroke(width = 2.5f)
                )

                val waveRadius2 = baseRadius * (1.3f + animatedAmp.value * 0.35f)
                drawCircle(
                    color = secondaryAuraColor.copy(alpha = 0.25f),
                    radius = waveRadius2,
                    center = centerOffset,
                    style = Stroke(width = 1.5f)
                )
            }

            // 3. Orbital Particles / Arc Segment
            val orbitRadius = baseRadius * 1.08f
            for (i in 0 until 6) {
                val angleRad = Math.toRadians((rotationAngle + i * 60).toDouble())
                val particleX = centerOffset.x + (orbitRadius * cos(angleRad)).toFloat()
                val particleY = centerOffset.y + (orbitRadius * sin(angleRad)).toFloat()
                drawCircle(
                    color = if (i % 2 == 0) primaryAuraColor else secondaryAuraColor,
                    radius = 3.5f,
                    center = Offset(particleX, particleY)
                )
            }

            // 4. Central Solid Cyber Core
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(
                        ObsidianDark,
                        Color(0xFF13172A),
                        ObsidianDark
                    )
                ),
                radius = baseRadius,
                center = centerOffset
            )

            // 5. High-tech core border
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        primaryAuraColor,
                        secondaryAuraColor,
                        primaryAuraColor
                    ),
                    center = centerOffset
                ),
                radius = baseRadius,
                center = centerOffset,
                style = Stroke(width = 3.5f)
            )
        }

        // Center Icon Indicator
        val iconModifier = Modifier.size(52.dp)
        when (sessionState) {
            is SessionState.Disconnected -> {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = "Tap to connect Mahi",
                    tint = primaryAuraColor,
                    modifier = iconModifier
                )
            }
            is SessionState.Connecting -> {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = "Connecting to Mahi",
                    tint = CyberCyan,
                    modifier = iconModifier
                )
            }
            is SessionState.Listening -> {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Listening to you",
                    tint = CyberCyan,
                    modifier = iconModifier
                )
            }
            is SessionState.Speaking -> {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Mahi is speaking",
                    tint = NeonPink,
                    modifier = iconModifier
                )
            }
            is SessionState.Error -> {
                Icon(
                    imageVector = Icons.Default.MicOff,
                    contentDescription = "Error",
                    tint = Color(0xFFFF4D4F),
                    modifier = iconModifier
                )
            }
        }
    }
}

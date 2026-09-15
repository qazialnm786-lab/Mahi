package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.session.SessionState
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.NeonPink
import kotlin.math.sin

@Composable
fun WaveformVisualizer(
    sessionState: SessionState,
    amplitude: Float,
    modifier: Modifier = Modifier
) {
    val barCount = 28
    val infiniteTransition = rememberInfiniteTransition(label = "wave_anim")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val animatedAmp = remember { Animatable(0.08f) }
    LaunchedEffect(amplitude, sessionState) {
        val target = when (sessionState) {
            is SessionState.Listening, is SessionState.Speaking -> amplitude.coerceIn(0.12f, 1f)
            is SessionState.Connecting -> 0.25f
            else -> 0.05f
        }
        animatedAmp.animateTo(
            targetValue = target,
            animationSpec = tween(durationMillis = 80)
        )
    }

    val primaryColor = when (sessionState) {
        is SessionState.Speaking -> NeonPink
        is SessionState.Listening -> CyberCyan
        is SessionState.Connecting -> ElectricPurple
        else -> Color(0xFF334155)
    }

    val secondaryColor = when (sessionState) {
        is SessionState.Speaking -> ElectricPurple
        is SessionState.Listening -> ElectricPurple
        is SessionState.Connecting -> CyberCyan
        else -> Color(0xFF1E293B)
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        val width = size.width
        val height = size.height
        val barWidth = width / (barCount * 1.6f)
        val spacing = (width - (barCount * barWidth)) / (barCount - 1)
        val centerY = height / 2

        for (i in 0 until barCount) {
            val normalizedX = i.toFloat() / (barCount - 1)
            val envelope = sin(normalizedX * Math.PI).toFloat() // Bell curve distribution
            val wave = (sin(phase + i * 0.4f) * 0.5f + 0.5f)

            val barHeight = ((height * 0.85f * animatedAmp.value * envelope * (0.4f + 0.6f * wave)) + 4f)
                .coerceIn(4f, height * 0.95f)

            val x = i * (barWidth + spacing)
            val y = centerY - (barHeight / 2)

            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(primaryColor, secondaryColor)
                ),
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
            )
        }
    }
}

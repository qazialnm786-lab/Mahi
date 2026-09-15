package com.example.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.session.SessionState
import com.example.ui.components.FuturisticOrb
import com.example.ui.components.SubtitlesHud
import com.example.ui.components.WaveformVisualizer
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.GlowEmerald
import com.example.ui.theme.NeonPink
import com.example.ui.theme.ObsidianDark
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun MahiScreen(
    viewModel: MahiViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sessionState by viewModel.sessionState.collectAsState()
    val subtitle by viewModel.currentSubtitle.collectAsState()
    val amplitude by viewModel.amplitude.collectAsState()
    val lastToolExecuted by viewModel.lastToolExecuted.collectAsState()

    var showInfoDialog by remember { mutableStateOf(false) }

    // Google Speech-to-Text Recognition Launcher
    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenList = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val recognizedText = spokenList?.firstOrNull()
            if (!recognizedText.isNullOrBlank()) {
                viewModel.sendPrompt(recognizedText)
            }
        }
    }

    fun launchVoiceRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Mahi se kuch bhi poocho...")
        }
        try {
            speechRecognizerLauncher.launch(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Voice recognition unavailable on this device. Please type your question!", Toast.LENGTH_SHORT).show()
        }
    }

    // Audio Permission Launcher
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.setMicPermissionGranted(isGranted)
        if (isGranted) {
            viewModel.toggleSession()
        }
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.setMicPermissionGranted(granted)
    }

    fun handleOrbClick() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            if (sessionState is SessionState.Disconnected) {
                viewModel.toggleSession()
            } else {
                // If already active, trigger voice recognition or toggle
                launchVoiceRecognition()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        ObsidianDark,
                        Color(0xFF090B14),
                        Color(0xFF100E20),
                        ObsidianDark
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar
            TopBar(
                sessionState = sessionState,
                onVoiceDemo = { viewModel.testVoiceDemo() },
                onInfoClick = { showInfoDialog = true }
            )

            // Center: Status Banner, Futuristic Orb & Reactive Waveform
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                // Interactive State Label
                Text(
                    text = when (sessionState) {
                        is SessionState.Disconnected -> "TAP ORB YA MIC TO TALK"
                        is SessionState.Connecting -> "INITIALIZING NEURAL LINK"
                        is SessionState.Listening -> "MAHI SUN RAHI HAI... BOLO!"
                        is SessionState.Speaking -> "MAHI JAWAAB DE RAHI HAI"
                        is SessionState.Error -> "CONNECTION ERROR"
                    },
                    color = when (sessionState) {
                        is SessionState.Speaking -> NeonPink
                        is SessionState.Listening -> CyberCyan
                        is SessionState.Connecting -> ElectricPurple
                        is SessionState.Error -> Color(0xFFFF4D4F)
                        else -> TextSecondary
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                // Central Futuristic Glowing Orb
                FuturisticOrb(
                    sessionState = sessionState,
                    amplitude = amplitude,
                    onClick = { handleOrbClick() }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Reactive Waveform Visualizer
                WaveformVisualizer(
                    sessionState = sessionState,
                    amplitude = amplitude,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }

            // Bottom: Subtitles HUD, Question Input & Quick Prompts
            SubtitlesHud(
                sessionState = sessionState,
                subtitle = subtitle,
                lastToolExecuted = lastToolExecuted,
                onInterrupt = { viewModel.interruptMahi() },
                onVoiceDemo = { viewModel.testVoiceDemo() },
                onStartVoiceInput = { launchVoiceRecognition() },
                onPromptSelected = { prompt ->
                    viewModel.sendPrompt(prompt)
                }
            )
        }

        // Info Dialog
        if (showInfoDialog) {
            AlertDialog(
                onDismissRequest = { showInfoDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Meet Mahi",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                text = {
                    Column {
                        Text(
                            text = "Mahi is your young, confident, witty, and sassy AI assistant. Kuch bhi poochho, woh bolke jawab degi in her sweet, realistic girl voice!",
                            color = TextSecondary,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "• Real-time Spoken Answers (Girl's Voice)\n" +
                                   "• Hindi & English conversational fluency\n" +
                                   "• Type ya Voice input se kuch bhi poochho\n" +
                                   "• Browser control: YouTube/Google khol sakti hai",
                            color = CyberCyan,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 18.sp
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showInfoDialog = false }) {
                        Text(text = "Got It", color = CyberCyan)
                    }
                },
                containerColor = SurfaceDark,
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

@Composable
private fun TopBar(
    sessionState: SessionState,
    onVoiceDemo: () -> Unit,
    onInfoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App Identity
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "MAHI",
                    color = CyberCyan,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(NeonPink.copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "LIVE AI",
                        color = NeonPink,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            Text(
                text = "Voice Assistant • Sassy Girl",
                color = TextSecondary,
                fontSize = 11.sp
            )
        }

        // Live Connection Indicator & Voice/Info Buttons
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        when (sessionState) {
                            is SessionState.Listening, is SessionState.Speaking -> GlowEmerald.copy(alpha = 0.15f)
                            is SessionState.Connecting -> ElectricPurple.copy(alpha = 0.15f)
                            else -> Color(0xFF1E293B)
                        }
                    )
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (sessionState is SessionState.Listening || sessionState is SessionState.Speaking) Icons.Default.Wifi else Icons.Default.WifiOff,
                        contentDescription = "Connection Status",
                        tint = when (sessionState) {
                            is SessionState.Listening, is SessionState.Speaking -> GlowEmerald
                            is SessionState.Connecting -> ElectricPurple
                            else -> TextSecondary
                        },
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = when (sessionState) {
                            is SessionState.Listening, is SessionState.Speaking -> "ONLINE"
                            is SessionState.Connecting -> "LINKING"
                            else -> "READY"
                        },
                        color = when (sessionState) {
                            is SessionState.Listening, is SessionState.Speaking -> GlowEmerald
                            is SessionState.Connecting -> ElectricPurple
                            else -> TextSecondary
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(
                onClick = onVoiceDemo,
                modifier = Modifier.testTag("voice_demo_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Hear Mahi's Voice",
                    tint = NeonPink,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(
                onClick = onInfoClick,
                modifier = Modifier.testTag("info_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "About Mahi",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

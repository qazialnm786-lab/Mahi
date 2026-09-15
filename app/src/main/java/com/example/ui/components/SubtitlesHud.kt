package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.MahiPersonality
import com.example.session.SessionState
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.NeonPink
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun SubtitlesHud(
    sessionState: SessionState,
    subtitle: String,
    lastToolExecuted: String?,
    onInterrupt: () -> Unit,
    onVoiceDemo: () -> Unit,
    onPromptSelected: (String) -> Unit,
    onStartVoiceInput: () -> Unit,
    modifier: Modifier = Modifier
) {
    var customQuestion by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Tool Execution Badge
        AnimatedVisibility(
            visible = lastToolExecuted != null,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut()
        ) {
            if (lastToolExecuted != null) {
                Box(
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(CyberCyan.copy(alpha = 0.2f), ElectricPurple.copy(alpha = 0.2f))
                            )
                        )
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.OpenInBrowser,
                            contentDescription = "Tool action",
                            tint = CyberCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "⚡ Action: $lastToolExecuted",
                            color = CyberCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // Subtitle / Live Response HUD Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("subtitle_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = SurfaceDark.copy(alpha = 0.9f)
            ),
            border = BorderStroke(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    listOf(
                        if (sessionState is SessionState.Speaking) NeonPink.copy(alpha = 0.6f) else CyberCyan.copy(alpha = 0.4f),
                        ElectricPurple.copy(alpha = 0.3f),
                        if (sessionState is SessionState.Speaking) NeonPink.copy(alpha = 0.6f) else CyberCyan.copy(alpha = 0.4f)
                    )
                )
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                when (sessionState) {
                                    is SessionState.Speaking -> NeonPink
                                    is SessionState.Listening -> CyberCyan
                                    is SessionState.Connecting -> ElectricPurple
                                    else -> Color.Gray
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (sessionState) {
                            is SessionState.Speaking -> "MAHI • BOL RAHI HAI"
                            is SessionState.Listening -> "MAHI • SUN RAHI HAI"
                            is SessionState.Connecting -> "CONNECTING..."
                            is SessionState.Disconnected -> "STANDBY MODE"
                            is SessionState.Error -> "SYSTEM STATUS"
                        },
                        color = when (sessionState) {
                            is SessionState.Speaking -> NeonPink
                            is SessionState.Listening -> CyberCyan
                            else -> TextSecondary
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = subtitle.ifEmpty { "Kuchh bhi poocho Mahi se, woh bolke jawab degi!" },
                    color = TextPrimary,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth()
                )

                // Interrupt button when Mahi is speaking
                AnimatedVisibility(
                    visible = sessionState is SessionState.Speaking,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    OutlinedButton(
                        onClick = onInterrupt,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = NeonPink
                        ),
                        border = BorderStroke(1.dp, NeonPink.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .testTag("interrupt_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Interrupt",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Interrupt Mahi", fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Ask Any Question Input Bar (Type ya Speak)
        OutlinedTextField(
            value = customQuestion,
            onValueChange = { customQuestion = it },
            placeholder = {
                Text(
                    text = "Kuch bhi poochho... (Type ya Bolo 🎙️)",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            leadingIcon = {
                IconButton(
                    onClick = onStartVoiceInput,
                    modifier = Modifier.testTag("voice_input_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice Input",
                        tint = CyberCyan,
                        modifier = Modifier.size(22.dp)
                    )
                }
            },
            trailingIcon = {
                IconButton(
                    onClick = {
                        val q = customQuestion.trim()
                        if (q.isNotEmpty()) {
                            onPromptSelected(q)
                            customQuestion = ""
                        }
                    },
                    modifier = Modifier.testTag("send_question_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send Question",
                        tint = if (customQuestion.isNotBlank()) NeonPink else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(
                onSend = {
                    val q = customQuestion.trim()
                    if (q.isNotEmpty()) {
                        onPromptSelected(q)
                        customQuestion = ""
                    }
                }
            ),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SurfaceDark.copy(alpha = 0.7f),
                unfocusedContainerColor = SurfaceDark.copy(alpha = 0.5f),
                focusedBorderColor = CyberCyan,
                unfocusedBorderColor = GlassBorder,
                cursorColor = NeonPink
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("question_input_field")
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Quick Conversation Prompt Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Voice Demo Chip
            SuggestionChip(
                onClick = onVoiceDemo,
                label = {
                    Text(
                        text = "🔊 Mahi ki Awaz Suno",
                        fontSize = 12.sp,
                        color = NeonPink,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                shape = RoundedCornerShape(16.dp),
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = NeonPink.copy(alpha = 0.15f)
                ),
                border = BorderStroke(1.dp, NeonPink.copy(alpha = 0.5f)),
                modifier = Modifier.testTag("voice_demo_chip")
            )

            MahiPersonality.SAMPLE_PROMPTS.forEach { prompt ->
                SuggestionChip(
                    onClick = { onPromptSelected(prompt) },
                    label = {
                        Text(
                            text = prompt,
                            fontSize = 12.sp,
                            color = TextPrimary
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = SurfaceDark.copy(alpha = 0.6f)
                    ),
                    border = BorderStroke(1.dp, GlassBorder)
                )
            }
        }
    }
}

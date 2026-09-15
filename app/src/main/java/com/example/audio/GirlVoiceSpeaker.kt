package com.example.audio

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

class GirlVoiceSpeaker(
    context: Context,
    private val scope: CoroutineScope
) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "GirlVoiceSpeaker"
    }

    private var tts: TextToSpeech? = null
    @Volatile private var isReady = false
    private var amplitudeJob: Job? = null

    var onAmplitude: ((Float) -> Unit)? = null
    var onSpeakingChanged: ((Boolean) -> Unit)? = null

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TextToSpeech: ${e.message}")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isReady = true
            configureGirlVoice()
        } else {
            Log.e(TAG, "TextToSpeech initialization failed with code: $status")
        }
    }

    private fun configureGirlVoice() {
        val engine = tts ?: return
        try {
            // Set locale to device or English/Hindi
            val currentLocale = Locale.getDefault()
            val res = engine.setLanguage(currentLocale)
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.language = Locale.US
            }

            // Set higher pitch for a sweet, energetic, natural female voice
            engine.setPitch(1.22f)
            engine.setSpeechRate(1.02f)

            // Attempt to select an explicit female voice if available
            val voices: Set<Voice>? = engine.voices
            if (voices != null) {
                val femaleVoice = voices.firstOrNull { v ->
                    val name = v.name.lowercase()
                    (name.contains("female") || name.contains("girl") || name.contains("woman") ||
                            name.contains("hi-in-x-hie") || name.contains("hi-in-x-cfc") ||
                            name.contains("en-us-x-sfg") || name.contains("en-us-x-iom")) &&
                            !name.contains("male")
                } ?: voices.firstOrNull { it.name.lowercase().contains("female") }

                if (femaleVoice != null) {
                    engine.voice = femaleVoice
                    Log.d(TAG, "Applied female voice: ${femaleVoice.name}")
                }
            }

            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    scope.launch(Dispatchers.Main) {
                        onSpeakingChanged?.invoke(true)
                    }
                    startWaveAnimation()
                }

                override fun onDone(utteranceId: String?) {
                    stopWaveAnimation()
                    scope.launch(Dispatchers.Main) {
                        onSpeakingChanged?.invoke(false)
                    }
                }

                override fun onError(utteranceId: String?) {
                    stopWaveAnimation()
                    scope.launch(Dispatchers.Main) {
                        onSpeakingChanged?.invoke(false)
                    }
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "Error configuring female voice: ${e.message}")
        }
    }

    fun speak(text: String, onComplete: (() -> Unit)? = null) {
        if (!isReady || text.isBlank()) return
        stop()

        val cleanText = text
            .replace("*", "")
            .replace("#", "")
            .replace("`", "")
            .trim()

        val utteranceId = "mahi_voice_${System.currentTimeMillis()}"
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }

        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    fun stop() {
        stopWaveAnimation()
        try {
            tts?.stop()
        } catch (_: Exception) {}
        onSpeakingChanged?.invoke(false)
    }

    private fun startWaveAnimation() {
        amplitudeJob?.cancel()
        amplitudeJob = scope.launch(Dispatchers.Default) {
            var step = 0
            while (isActive) {
                // Generates dynamic realistic speech waveform amplitudes
                val base = 0.35f
                val mod = (kotlin.math.sin(step * 0.4) * 0.3 + kotlin.math.cos(step * 0.2) * 0.2).toFloat()
                val amp = (base + mod).coerceIn(0.15f, 0.9f)
                onAmplitude?.invoke(amp)
                step++
                delay(60)
            }
        }
    }

    private fun stopWaveAnimation() {
        amplitudeJob?.cancel()
        amplitudeJob = null
        onAmplitude?.invoke(0f)
    }

    fun release() {
        stop()
        try {
            tts?.shutdown()
        } catch (_: Exception) {}
        tts = null
    }
}

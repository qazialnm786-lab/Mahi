package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.audio.AudioStreamer
import com.example.session.LiveSessionManager
import com.example.session.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MahiViewModel(application: Application) : AndroidViewModel(application) {

    private val audioStreamer = AudioStreamer(viewModelScope)

    private val liveSessionManager = LiveSessionManager(
        context = application.applicationContext,
        scope = viewModelScope,
        audioStreamer = audioStreamer,
        apiKey = BuildConfig.GEMINI_API_KEY
    )

    val sessionState: StateFlow<SessionState> = liveSessionManager.state
    val currentSubtitle: StateFlow<String> = liveSessionManager.currentSubtitle
    val lastToolExecuted: StateFlow<String?> = liveSessionManager.lastToolExecuted

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val _hasMicPermission = MutableStateFlow(false)
    val hasMicPermission: StateFlow<Boolean> = _hasMicPermission.asStateFlow()

    init {
        // Set up amplitude listeners for dynamic waveforms
        audioStreamer.onInputAmplitude = { amp ->
            if (sessionState.value is SessionState.Listening) {
                _amplitude.value = amp
            }
        }
        audioStreamer.onOutputAmplitude = { amp ->
            if (sessionState.value is SessionState.Speaking) {
                _amplitude.value = amp
            }
        }
    }

    fun setMicPermissionGranted(granted: Boolean) {
        _hasMicPermission.value = granted
    }

    fun toggleSession() {
        when (sessionState.value) {
            is SessionState.Disconnected, is SessionState.Error -> {
                liveSessionManager.connect()
            }
            is SessionState.Connecting, is SessionState.Listening, is SessionState.Speaking -> {
                liveSessionManager.disconnect()
            }
        }
    }

    fun interruptMahi() {
        liveSessionManager.interrupt()
    }

    fun sendPrompt(prompt: String) {
        liveSessionManager.sendUserPrompt(prompt)
    }

    fun testVoiceDemo() {
        liveSessionManager.speakCustom("Haanji, main Mahi hoon! Meri awaz ekdam sweet aur sassy ladki jaisi hai na? Poochho jo bhi poochna hai, main bolke jawaab doongi!")
    }

    fun dismissToolNotification() {
        viewModelScope.launch {
            // No-op or clear if desired
        }
    }

    override fun onCleared() {
        super.onCleared()
        liveSessionManager.release()
    }
}

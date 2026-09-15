package com.example.session

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.audio.AudioStreamer
import com.example.audio.GirlVoiceSpeaker
import com.example.data.LiveJsonHelper
import com.example.data.MahiPersonality
import com.example.data.ToolCall
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class LiveSessionManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val audioStreamer: AudioStreamer,
    private val apiKey: String
) {
    companion object {
        private const val TAG = "LiveSessionManager"
        private const val WS_URL =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"
        private const val REST_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"
    }

    private val girlVoiceSpeaker = GirlVoiceSpeaker(context, scope)

    private val _state = MutableStateFlow<SessionState>(SessionState.Disconnected)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _currentSubtitle = MutableStateFlow("")
    val currentSubtitle: StateFlow<String> = _currentSubtitle.asStateFlow()

    private val _lastToolExecuted = MutableStateFlow<String?>(null)
    val lastToolExecuted: StateFlow<String?> = _lastToolExecuted.asStateFlow()

    private var webSocket: WebSocket? = null
    private var completionCheckJob: Job? = null
    private var usingRestFallback = false
    @Volatile private var receivedPcmInTurn = false

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    init {
        girlVoiceSpeaker.onAmplitude = { amp ->
            audioStreamer.onOutputAmplitude?.invoke(amp)
        }
        girlVoiceSpeaker.onSpeakingChanged = { isSpeaking ->
            if (isSpeaking) {
                _state.value = SessionState.Speaking
            } else {
                if (_state.value is SessionState.Speaking && audioStreamer.isPlaybackQueueEmpty()) {
                    _state.value = SessionState.Listening
                }
            }
        }
    }

    fun connect() {
        if (_state.value is SessionState.Connecting || _state.value is SessionState.Listening || _state.value is SessionState.Speaking) {
            return
        }

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            _state.value = SessionState.Error("Gemini API key is not configured. Please set it in AI Studio secrets.")
            return
        }

        _state.value = SessionState.Connecting
        _currentSubtitle.value = "Connecting to Mahi..."

        val wsUrl = "$WS_URL?key=$apiKey"
        val request = Request.Builder().url(wsUrl).build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket opened successfully")
                usingRestFallback = false
                val setupJson = LiveJsonHelper.buildSetupMessage()
                webSocket.send(setupJson)

                scope.launch(Dispatchers.Main) {
                    _state.value = SessionState.Listening
                    val greeting = MahiPersonality.WITTY_GREETINGS.random()
                    _currentSubtitle.value = greeting
                    girlVoiceSpeaker.speak(greeting)
                    startMicStreaming()
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleWebSocketMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code / $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code / $reason")
                scope.launch(Dispatchers.Main) {
                    if (_state.value !is SessionState.Disconnected) {
                        _state.value = SessionState.Disconnected
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "WebSocket connection failed: ${t.message}. Falling back to REST mode.")
                scope.launch(Dispatchers.Main) {
                    usingRestFallback = true
                    _state.value = SessionState.Listening
                    val greeting = "Hey darling! Main online aa gayi hoon. Puchho jo puchhna hai, I will answer in my voice!"
                    _currentSubtitle.value = greeting
                    girlVoiceSpeaker.speak(greeting)
                    startMicStreaming()
                }
            }
        })
    }

    private fun startMicStreaming() {
        audioStreamer.startRecording { pcmChunk ->
            if (_state.value is SessionState.Listening) {
                if (!usingRestFallback) {
                    val base64 = Base64.encodeToString(pcmChunk, Base64.NO_WRAP)
                    val chunkJson = LiveJsonHelper.buildRealtimeAudioChunkMessage(base64)
                    webSocket?.send(chunkJson)
                }
            }
        }
    }

    private fun handleWebSocketMessage(text: String) {
        try {
            val json = JSONObject(text)

            // 1. Check Server Content
            if (json.has("serverContent")) {
                val serverContent = json.getJSONObject("serverContent")

                // Check interruption
                if (serverContent.optBoolean("interrupted", false)) {
                    Log.d(TAG, "Mahi was interrupted by user speech")
                    interrupt()
                    return
                }

                // Check model turn (audio & transcript)
                if (serverContent.has("modelTurn")) {
                    val modelTurn = serverContent.getJSONObject("modelTurn")
                    if (modelTurn.has("parts")) {
                        val parts = modelTurn.getJSONArray("parts")
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)

                            // Audio part from Gemini Live
                            if (part.has("inlineData")) {
                                val inline = part.getJSONObject("inlineData")
                                val dataBase64 = inline.optString("data", "")
                                if (dataBase64.isNotEmpty()) {
                                    receivedPcmInTurn = true
                                    val pcmBytes = Base64.decode(dataBase64, Base64.DEFAULT)
                                    scope.launch(Dispatchers.Main) {
                                        _state.value = SessionState.Speaking
                                    }
                                    audioStreamer.playPcmChunk(pcmBytes, AudioStreamer.OUTPUT_SAMPLE_RATE)
                                }
                            }

                            // Text part
                            if (part.has("text")) {
                                val partText = part.getString("text")
                                scope.launch(Dispatchers.Main) {
                                    _currentSubtitle.value = partText
                                    // If raw PCM wasn't streamed for this chunk, speak it out loud with girl's voice
                                    if (!receivedPcmInTurn) {
                                        girlVoiceSpeaker.speak(partText)
                                    }
                                }
                            }
                        }
                    }
                }

                // Check turn complete
                if (serverContent.optBoolean("turnComplete", false)) {
                    monitorAudioCompletion()
                    receivedPcmInTurn = false
                }
            }

            // 2. Check Tool Call
            if (json.has("toolCall")) {
                val toolCallObj = json.getJSONObject("toolCall")
                val functionCalls = toolCallObj.optJSONArray("functionCalls")
                if (functionCalls != null && functionCalls.length() > 0) {
                    for (i in 0 until functionCalls.length()) {
                        val func = functionCalls.getJSONObject(i)
                        val id = func.optString("id", "call_${System.currentTimeMillis()}")
                        val name = func.optString("name")
                        val argsObj = func.optJSONObject("args")
                        val argsMap = mutableMapOf<String, Any?>()
                        if (argsObj != null) {
                            val keys = argsObj.keys()
                            while (keys.hasNext()) {
                                val k = keys.next()
                                argsMap[k] = argsObj.opt(k)
                            }
                        }
                        handleToolExecution(ToolCall(id, name, argsMap))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing server message: ${e.message}", e)
        }
    }

    private fun handleToolExecution(toolCall: ToolCall) {
        scope.launch(Dispatchers.Main) {
            when (toolCall.name) {
                "openWebsite" -> {
                    var url = toolCall.args["url"] as? String ?: "https://google.com"
                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        url = "https://$url"
                    }
                    _lastToolExecuted.value = "Opened $url"
                    val speech = "Opening $url for you, darling!"
                    _currentSubtitle.value = speech
                    girlVoiceSpeaker.speak(speech)

                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error opening URL: ${e.message}")
                    }

                    val responseJson = LiveJsonHelper.buildToolResponseMessage(
                        callId = toolCall.id,
                        name = toolCall.name,
                        responseContent = "Successfully opened website $url in user browser"
                    )
                    webSocket?.send(responseJson)
                }
                else -> {
                    val responseJson = LiveJsonHelper.buildToolResponseMessage(
                        callId = toolCall.id,
                        name = toolCall.name,
                        responseContent = "Action completed"
                    )
                    webSocket?.send(responseJson)
                }
            }
        }
    }

    private fun monitorAudioCompletion() {
        completionCheckJob?.cancel()
        completionCheckJob = scope.launch(Dispatchers.Default) {
            while (!audioStreamer.isPlaybackQueueEmpty()) {
                delay(100)
            }
            delay(200)
            withContext(Dispatchers.Main) {
                if (_state.value is SessionState.Speaking) {
                    _state.value = SessionState.Listening
                }
            }
        }
    }

    /**
     * Send a prompt/question to Mahi.
     * She gives an answer and speaks it aloud in her sweet girl's voice!
     */
    fun sendUserPrompt(promptText: String) {
        interrupt()

        _currentSubtitle.value = "You: \"$promptText\""

        val lower = promptText.lowercase()
        if (lower.contains("youtube") && (lower.contains("open") || lower.contains("kholo") || lower.contains("chalao") || lower.contains("dekho"))) {
            handleToolExecution(ToolCall("call_yt", "openWebsite", mapOf("url" to "https://www.youtube.com")))
            return
        } else if (lower.contains("google") && (lower.contains("open") || lower.contains("kholo") || lower.contains("search"))) {
            handleToolExecution(ToolCall("call_g", "openWebsite", mapOf("url" to "https://www.google.com")))
            return
        }

        executeRestAudioTurn(promptText)
    }

    private fun executeRestAudioTurn(promptText: String) {
        scope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                _state.value = SessionState.Speaking
                _currentSubtitle.value = "Mahi soch rahi hai..."
            }
            try {
                val root = JSONObject()
                val contents = JSONArray()
                val contentObj = JSONObject().apply {
                    put("role", "user")
                    val parts = JSONArray().apply {
                        put(JSONObject().apply { put("text", promptText) })
                    }
                    put("parts", parts)
                }
                contents.put(contentObj)
                root.put("contents", contents)

                val sysObj = JSONObject().apply {
                    val parts = JSONArray().apply {
                        put(JSONObject().apply { put("text", MahiPersonality.SYSTEM_PROMPT) })
                    }
                    put("parts", parts)
                }
                root.put("systemInstruction", sysObj)

                val requestBody = root.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("$REST_URL?key=$apiKey")
                    .post(requestBody)
                    .build()

                val response = okHttpClient.newCall(request).execute()
                val bodyString = response.body?.string()

                if (response.isSuccessful && !bodyString.isNullOrEmpty()) {
                    val resJson = JSONObject(bodyString)
                    val candidates = resJson.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val firstCand = candidates.getJSONObject(0)
                        val candContent = firstCand.optJSONObject("content")
                        val parts = candContent?.optJSONArray("parts")
                        var answerText = ""
                        if (parts != null) {
                            for (i in 0 until parts.length()) {
                                val p = parts.getJSONObject(i)
                                if (p.has("text")) {
                                    answerText += p.getString("text") + " "
                                }
                            }
                        }
                        val cleanAnswer = answerText.trim()
                        if (cleanAnswer.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                _currentSubtitle.value = cleanAnswer
                                // Speak answer out loud in female voice
                                girlVoiceSpeaker.speak(cleanAnswer)
                            }
                        } else {
                            speakFallback()
                        }
                    } else {
                        speakFallback()
                    }
                } else {
                    speakFallback()
                }
            } catch (e: Exception) {
                Log.e(TAG, "REST call error: ${e.message}", e)
                speakFallback()
            }
        }
    }

    private suspend fun speakFallback() {
        withContext(Dispatchers.Main) {
            val fallback = MahiPersonality.WITTY_FALLBACKS.random()
            _currentSubtitle.value = fallback
            girlVoiceSpeaker.speak(fallback)
        }
    }

    fun speakCustom(text: String) {
        _currentSubtitle.value = text
        girlVoiceSpeaker.speak(text)
    }

    fun interrupt() {
        girlVoiceSpeaker.stop()
        audioStreamer.interruptPlayback()
        if (_state.value is SessionState.Speaking) {
            _state.value = SessionState.Listening
            _currentSubtitle.value = "Listening to you..."
        }
    }

    fun disconnect() {
        completionCheckJob?.cancel()
        girlVoiceSpeaker.stop()
        audioStreamer.stopRecording()
        audioStreamer.interruptPlayback()
        try {
            webSocket?.close(1000, "User disconnected")
        } catch (_: Exception) {}
        webSocket = null
        _state.value = SessionState.Disconnected
        _currentSubtitle.value = "Mahi standby mode mein hai. Tap the orb to connect!"
    }

    fun release() {
        disconnect()
        audioStreamer.release()
        girlVoiceSpeaker.release()
    }
}

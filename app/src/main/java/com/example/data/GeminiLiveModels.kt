package com.example.data

import org.json.JSONArray
import org.json.JSONObject

data class ToolCall(
    val id: String,
    val name: String,
    val args: Map<String, Any?>
)

sealed class ServerEvent {
    data class AudioChunk(val pcmData: ByteArray, val sampleRate: Int = 24000) : ServerEvent()
    data class Transcript(val text: String, val isUser: Boolean = false) : ServerEvent()
    data class ToolCallReceived(val toolCall: ToolCall) : ServerEvent()
    data object TurnComplete : ServerEvent()
    data object Interrupted : ServerEvent()
    data class Error(val message: String) : ServerEvent()
}

object LiveJsonHelper {

    fun buildSetupMessage(
        modelName: String = "models/gemini-2.5-flash-native-audio-preview-12-2025",
        systemInstruction: String = MahiPersonality.SYSTEM_PROMPT,
        voiceName: String = "Aoede"
    ): String {
        val root = JSONObject()
        val setup = JSONObject()

        setup.put("model", modelName)

        val genConfig = JSONObject()
        val modalities = JSONArray().apply { put("AUDIO") }
        genConfig.put("responseModalities", modalities)

        val speechConfig = JSONObject()
        val voiceConfig = JSONObject()
        val prebuilt = JSONObject()
        prebuilt.put("voiceName", voiceName)
        voiceConfig.put("prebuiltVoiceConfig", prebuilt)
        speechConfig.put("voiceConfig", voiceConfig)
        genConfig.put("speechConfig", speechConfig)

        setup.put("generationConfig", genConfig)

        val sysInstObj = JSONObject()
        val sysParts = JSONArray()
        val sysPart = JSONObject().apply { put("text", systemInstruction) }
        sysParts.put(sysPart)
        sysInstObj.put("parts", sysParts)
        setup.put("systemInstruction", sysInstObj)

        // Tools
        val tools = JSONArray()
        val toolObj = JSONObject()
        val funcDecls = JSONArray()

        val openWebDecl = JSONObject().apply {
            put("name", "openWebsite")
            put("description", "Opens a website or URL in the Android web browser")
            val params = JSONObject().apply {
                put("type", "OBJECT")
                val props = JSONObject().apply {
                    val urlProp = JSONObject().apply {
                        put("type", "STRING")
                        put("description", "The website URL to open, e.g. https://youtube.com")
                    }
                    put("url", urlProp)
                }
                put("properties", props)
                val req = JSONArray().apply { put("url") }
                put("required", req)
            }
            put("parameters", params)
        }
        funcDecls.put(openWebDecl)
        toolObj.put("functionDeclarations", funcDecls)
        tools.put(toolObj)
        setup.put("tools", tools)

        root.put("setup", setup)
        return root.toString()
    }

    fun buildRealtimeAudioChunkMessage(base64Pcm: String): String {
        val root = JSONObject()
        val realtime = JSONObject()
        val mediaChunks = JSONArray()
        val chunk = JSONObject().apply {
            put("mimeType", "audio/pcm;rate=16000")
            put("data", base64Pcm)
        }
        mediaChunks.put(chunk)
        realtime.put("mediaChunks", mediaChunks)
        root.put("realtimeInput", realtime)
        return root.toString()
    }

    fun buildRealtimeTextMessage(text: String): String {
        val root = JSONObject()
        val clientContent = JSONObject()
        val turns = JSONArray()
        val turn = JSONObject().apply {
            put("role", "user")
            val parts = JSONArray().apply {
                put(JSONObject().apply { put("text", text) })
            }
            put("parts", parts)
        }
        turns.put(turn)
        clientContent.put("turns", turns)
        clientContent.put("turnComplete", true)
        root.put("clientContent", clientContent)
        return root.toString()
    }

    fun buildToolResponseMessage(callId: String, name: String, responseContent: String): String {
        val root = JSONObject()
        val toolResponse = JSONObject()
        val funcResponses = JSONArray()
        val respObj = JSONObject().apply {
            put("id", callId)
            val output = JSONObject().apply {
                put("status", "success")
                put("result", responseContent)
            }
            put("response", JSONObject().apply { put("output", output) })
        }
        funcResponses.put(respObj)
        toolResponse.put("functionResponses", funcResponses)
        root.put("toolResponse", toolResponse)
        return root.toString()
    }
}

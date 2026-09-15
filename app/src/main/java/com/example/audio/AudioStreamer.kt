package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.sin
import kotlin.math.sqrt

class AudioStreamer(
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "AudioStreamer"
        const val INPUT_SAMPLE_RATE = 16000
        const val OUTPUT_SAMPLE_RATE = 24000
        private const val CHUNK_SIZE = 1600 // 50ms of 16-bit mono at 16kHz
    }

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    @Volatile private var isRecording = false

    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    private val audioQueue = ConcurrentLinkedQueue<ByteArray>()
    @Volatile private var isPlaying = false

    var onOutputAmplitude: ((Float) -> Unit)? = null
    var onInputAmplitude: ((Float) -> Unit)? = null

    @SuppressLint("MissingPermission")
    fun startRecording(onPcmChunk: (ByteArray) -> Unit) {
        if (isRecording) return

        val minBufSize = AudioRecord.getMinBufferSize(
            INPUT_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        if (minBufSize <= 0) {
            Log.e(TAG, "Invalid minBufSize for AudioRecord: $minBufSize")
            return
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                INPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufSize.coerceAtLeast(CHUNK_SIZE * 4)
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                audioRecord?.release()
                audioRecord = null
                return
            }

            audioRecord?.startRecording()
            isRecording = true

            recordingJob = scope.launch(Dispatchers.IO) {
                val buffer = ByteArray(CHUNK_SIZE)
                while (isActive && isRecording) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (read > 0) {
                        val chunk = buffer.copyOf(read)
                        // Calculate RMS amplitude
                        val amp = calculateRms(chunk, read)
                        onInputAmplitude?.invoke(amp)
                        onPcmChunk(chunk)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording: ${e.message}", e)
            isRecording = false
        }
    }

    fun stopRecording() {
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioRecord: ${e.message}")
        }
        audioRecord = null
        onInputAmplitude?.invoke(0f)
    }

    private fun initAudioTrack(sampleRate: Int) {
        if (audioTrack != null && audioTrack?.sampleRate == sampleRate) return

        try {
            audioTrack?.release()
        } catch (_: Exception) {}

        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val format = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build()

        audioTrack = AudioTrack(
            attributes,
            format,
            minBuf.coerceAtLeast(4096),
            AudioTrack.MODE_STREAM,
            android.media.AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        audioTrack?.play()
        startPlaybackLoop()
    }

    private fun startPlaybackLoop() {
        if (playbackJob?.isActive == true) return

        playbackJob = scope.launch(Dispatchers.IO) {
            isPlaying = true
            while (isActive) {
                val chunk = audioQueue.poll()
                if (chunk != null) {
                    val track = audioTrack
                    if (track != null && track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        val amp = calculateRms(chunk, chunk.size)
                        onOutputAmplitude?.invoke(amp)
                        track.write(chunk, 0, chunk.size)
                    }
                } else {
                    onOutputAmplitude?.invoke(0f)
                    kotlinx.coroutines.delay(10)
                }
            }
        }
    }

    fun playPcmChunk(pcmData: ByteArray, sampleRate: Int = OUTPUT_SAMPLE_RATE) {
        initAudioTrack(sampleRate)
        audioQueue.offer(pcmData)
    }

    /**
     * Instantly flushes audio buffers and stops talking when interrupted.
     */
    fun interruptPlayback() {
        audioQueue.clear()
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.play()
        } catch (e: Exception) {
            Log.w(TAG, "Error flushing AudioTrack: ${e.message}")
        }
        onOutputAmplitude?.invoke(0f)
    }

    fun isPlaybackQueueEmpty(): Boolean = audioQueue.isEmpty()

    private fun calculateRms(buffer: ByteArray, length: Int): Float {
        if (length < 2) return 0f
        var sum = 0.0
        val sampleCount = length / 2
        for (i in 0 until length - 1 step 2) {
            val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
            val sampleShort = sample.toShort()
            sum += sampleShort * sampleShort
        }
        val rms = sqrt(sum / sampleCount)
        return (rms / 32768.0).coerceIn(0.0, 1.0).toFloat()
    }

    /**
     * Generates a short playful sound tone for auditory cues
     */
    fun playChime(frequencies: List<Double> = listOf(523.25, 659.25, 783.99), durationMs: Int = 120) {
        scope.launch(Dispatchers.Default) {
            val sampleRate = OUTPUT_SAMPLE_RATE
            for (freq in frequencies) {
                val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
                val buffer = ByteArray(numSamples * 2)
                for (i in 0 until numSamples) {
                    val angle = 2.0 * Math.PI * i * freq / sampleRate
                    val sample = (sin(angle) * 12000).toInt().toShort()
                    buffer[i * 2] = (sample.toInt() and 0xFF).toByte()
                    buffer[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
                }
                playPcmChunk(buffer, sampleRate)
            }
        }
    }

    fun release() {
        stopRecording()
        playbackJob?.cancel()
        playbackJob = null
        audioQueue.clear()
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
    }
}

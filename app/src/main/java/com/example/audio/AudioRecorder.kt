package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * AudioRecorder handles recording 16kHz, 16-bit Mono PCM audio,
 * converting it to standard WAV format, and chunking into segments
 * (<= 30 seconds) required by Gemma 4 E2B ASR.
 */
class AudioRecorder(private val context: Context) {

    companion object {
        const val SAMPLE_RATE = 16000 // 16 kHz
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val BYTES_PER_SAMPLE = 2 // 16-bit = 2 bytes
        const val BYTES_PER_SECOND = SAMPLE_RATE * BYTES_PER_SAMPLE // 32,000 bytes/sec
        const val MAX_CHUNK_DURATION_SEC = 28 // Gemma 4 max 30s; chunking at 28s gives safe margin
        const val MAX_CHUNK_BYTES = MAX_CHUNK_DURATION_SEC * BYTES_PER_SECOND // 896,000 bytes
    }

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingDurationSec = MutableStateFlow(0)
    val recordingDurationSec: StateFlow<Int> = _recordingDurationSec.asStateFlow()

    private val _currentAmplitude = MutableStateFlow(0f)
    val currentAmplitude: StateFlow<Float> = _currentAmplitude.asStateFlow()

    private val pcmOutputStream = ByteArrayOutputStream()

    @SuppressLint("MissingPermission")
    fun startRecording(): Boolean {
        if (_isRecording.value) return false

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )
        val bufferSize = (minBufferSize * 2).coerceAtLeast(4096)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord?.release()
                audioRecord = null
                return false
            }

            pcmOutputStream.reset()
            audioRecord?.startRecording()
            _isRecording.value = true
            _recordingDurationSec.value = 0

            recordingJob = scope.launch {
                val buffer = ByteArray(bufferSize)
                var totalBytesRead = 0L

                while (isActive && _isRecording.value) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (read > 0) {
                        pcmOutputStream.write(buffer, 0, read)
                        totalBytesRead += read

                        // Compute normalized amplitude for visualization
                        var maxAmp = 0
                        var i = 0
                        while (i < read - 1) {
                            val sample = (buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)
                            val absSample = abs(sample.toShort().toInt())
                            if (absSample > maxAmp) maxAmp = absSample
                            i += 2
                        }
                        _currentAmplitude.value = (maxAmp / 32768f).coerceIn(0f, 1f)

                        // Update duration in seconds
                        _recordingDurationSec.value = (totalBytesRead / BYTES_PER_SECOND).toInt()
                    }
                }
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            stopRecording()
            return false
        }
    }

    fun stopRecording(): List<File> {
        if (!_isRecording.value && pcmOutputStream.size() == 0) return emptyList()

        _isRecording.value = false
        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            audioRecord = null
        }

        _currentAmplitude.value = 0f
        val pcmData = pcmOutputStream.toByteArray()
        pcmOutputStream.reset()

        if (pcmData.isEmpty()) return emptyList()

        return splitAndExportToWav(pcmData)
    }

    /**
     * Splits raw PCM byte data into chunks <= MAX_CHUNK_BYTES (28-30s),
     * and wraps each chunk in a proper 16kHz mono 16-bit WAV file.
     */
    private fun splitAndExportToWav(pcmData: ByteArray): List<File> {
        val chunkFiles = mutableListOf<File>()
        val outputDir = File(context.cacheDir, "audio_chunks").apply { mkdirs() }
        outputDir.listFiles()?.forEach { it.delete() } // clean previous chunks

        val totalBytes = pcmData.size
        var offset = 0
        var chunkIndex = 1

        while (offset < totalBytes) {
            val remaining = totalBytes - offset
            var chunkSize = remaining.coerceAtMost(MAX_CHUNK_BYTES)

            // Ensure alignment to 2-byte sample boundary
            if (chunkSize % 2 != 0) {
                chunkSize--
            }

            if (chunkSize <= 0) break

            val chunkPcm = ByteArray(chunkSize)
            System.arraycopy(pcmData, offset, chunkPcm, 0, chunkSize)

            val wavFile = File(outputDir, "chunk_${chunkIndex}_16k.wav")
            writeWavFile(wavFile, chunkPcm)
            chunkFiles.add(wavFile)

            offset += chunkSize
            chunkIndex++
        }

        return chunkFiles
    }

    /**
     * Writes 16-bit 16kHz Mono PCM data with a 44-byte RIFF WAV header.
     */
    private fun writeWavFile(outputFile: File, pcmData: ByteArray) {
        val totalAudioLen = pcmData.size.toLong()
        val totalDataLen = totalAudioLen + 36
        val longSampleRate = SAMPLE_RATE.toLong()
        val channels = 1
        val byteRate = (SAMPLE_RATE * channels * BYTES_PER_SAMPLE).toLong()

        FileOutputStream(outputFile).use { out ->
            val header = ByteArray(44)
            val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

            // RIFF header
            header[0] = 'R'.code.toByte()
            header[1] = 'I'.code.toByte()
            header[2] = 'F'.code.toByte()
            header[3] = 'F'.code.toByte()
            bb.putInt(4, totalDataLen.toInt())
            header[8] = 'W'.code.toByte()
            header[9] = 'A'.code.toByte()
            header[10] = 'V'.code.toByte()
            header[11] = 'E'.code.toByte()

            // "fmt " subchunk
            header[12] = 'f'.code.toByte()
            header[13] = 'm'.code.toByte()
            header[14] = 't'.code.toByte()
            header[15] = ' '.code.toByte()
            bb.putInt(16, 16) // Subchunk1Size for PCM
            bb.putShort(20, 1.toShort()) // AudioFormat 1 = PCM
            bb.putShort(22, channels.toShort()) // NumChannels
            bb.putInt(24, longSampleRate.toInt()) // SampleRate
            bb.putInt(28, byteRate.toInt()) // ByteRate
            bb.putShort(32, (channels * BYTES_PER_SAMPLE).toShort()) // BlockAlign
            bb.putShort(34, 16.toShort()) // BitsPerSample

            // "data" subchunk
            header[36] = 'd'.code.toByte()
            header[37] = 'a'.code.toByte()
            header[38] = 't'.code.toByte()
            header[39] = 'a'.code.toByte()
            bb.putInt(40, totalAudioLen.toInt())

            out.write(header, 0, 44)
            out.write(pcmData)
        }
    }
}

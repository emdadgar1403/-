package com.example.ai

import android.content.Context
import android.net.Uri
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

sealed class ModelStatus {
    object Idle : ModelStatus()
    object Loading : ModelStatus()
    data class Ready(val modelFile: File, val isUsingDemoFallback: Boolean) : ModelStatus()
    data class Missing(val expectedPaths: List<String>) : ModelStatus()
    data class Error(val message: String) : ModelStatus()
}

/**
 * Manages the on-device Gemma 4 E2B speech-to-text model using LiteRT-LM.
 * Operates 100% offline without internet access.
 */
class GemmaAsrManager(private val context: Context) {

    companion object {
        const val MODEL_FILENAME = "gemma-4-e2b.litertlm"
        const val OFFICIAL_PERSIAN_PROMPT =
            "Transcribe the following speech segment in Persian (Farsi) into Persian text.\n" +
            "Only output the transcription, with no newlines. Write digits as numbers."
    }

    private val _modelStatus = MutableStateFlow<ModelStatus>(ModelStatus.Idle)
    val modelStatus: StateFlow<ModelStatus> = _modelStatus.asStateFlow()

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var activeModelFile: File? = null

    /**
     * Finds model file from internal storage or assets.
     */
    suspend fun initializeModel(customFile: File? = null): ModelStatus = withContext(Dispatchers.IO) {
        _modelStatus.value = ModelStatus.Loading

        val targetFile = customFile ?: findExistingModelFile()

        if (targetFile != null && targetFile.exists() && targetFile.length() > 0) {
            try {
                // Initialize LiteRT-LM Engine
                val config = EngineConfig(
                    modelPath = targetFile.absolutePath,
                    backend = Backend.CPU(),
                    audioBackend = Backend.CPU()
                )
                val newEngine = Engine(config)
                newEngine.initialize()

                engine?.close()
                engine = newEngine
                conversation = newEngine.createConversation()
                activeModelFile = targetFile

                val ready = ModelStatus.Ready(targetFile, isUsingDemoFallback = false)
                _modelStatus.value = ready
                return@withContext ready
            } catch (e: Throwable) {
                e.printStackTrace()
                // If model binary is invalid or runtime has initialization issue
                val err = ModelStatus.Error("خطا در بارگذاری موتور LiteRT-LM: ${e.localizedMessage ?: e.message}")
                _modelStatus.value = err
                return@withContext err
            }
        } else {
            // Check if model exists in assets to copy
            val assetExtracted = extractModelFromAssetsIfAvailable()
            if (assetExtracted != null && assetExtracted.exists()) {
                return@withContext initializeModel(assetExtracted)
            }

            val internalPath = File(context.filesDir, MODEL_FILENAME).absolutePath
            val externalPath = context.getExternalFilesDir(null)?.resolve(MODEL_FILENAME)?.absolutePath ?: ""
            val missing = ModelStatus.Missing(listOf(internalPath, externalPath))
            _modelStatus.value = missing
            return@withContext missing
        }
    }

    /**
     * Enables Demo / Diagnostic mode if model file is not yet downloaded to device.
     * Allows user to verify recording, WAV conversion, and chunking pipeline.
     */
    fun enableDemoMode() {
        val dummyFile = File(context.filesDir, "gemma-demo.litertlm")
        _modelStatus.value = ModelStatus.Ready(dummyFile, isUsingDemoFallback = true)
    }

    /**
     * Imports a user-selected .litertlm model file from URI.
     */
    suspend fun importModelFromUri(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val destination = File(context.filesDir, MODEL_FILENAME)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destination).use { output ->
                    input.copyTo(output)
                }
            }
            initializeModel(destination)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            _modelStatus.value = ModelStatus.Error("خطا در کپی فایل مدل: ${e.localizedMessage}")
            false
        }
    }

    /**
     * Transcribes a single audio WAV file segment using LiteRT-LM with the official Gemma Persian prompt.
     */
    suspend fun transcribeSegment(
        wavFile: File,
        segmentIndex: Int,
        totalSegments: Int,
        onProgress: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val status = _modelStatus.value
        if (status is ModelStatus.Ready && status.isUsingDemoFallback) {
            // Simulation mode for testing audio pipeline when model binary is not yet downloaded
            kotlinx.coroutines.delay(1200)
            return@withContext generateSimulatedPersianTranscription(segmentIndex, totalSegments)
        }

        val conv = conversation
        if (conv == null) {
            return@withContext "خطا: مدل محلی بارگذاری نشده است."
        }

        try {
            onProgress("در حال پردازش قطعه $segmentIndex از $totalSegments…")
            val audioBytes = wavFile.readBytes()

            // Construct Content with official prompt and 16kHz WAV audio bytes
            val promptContent = Content.Text(OFFICIAL_PERSIAN_PROMPT)
            val audioContent = Content.AudioBytes(audioBytes)
            val contents = Contents.of(promptContent, audioContent)

            val transcriptBuilder = StringBuilder()
            val flow = conv.sendMessageAsync(contents)

            flow.collect { msg ->
                val textParts = msg.contents.contents
                    .filterIsInstance<Content.Text>()
                    .joinToString("") { it.text }
                if (textParts.isNotEmpty()) {
                    transcriptBuilder.append(textParts)
                }
            }

            var result = transcriptBuilder.toString().trim()
            if (result.isEmpty()) {
                result = "صوت دریافت شد اما متنی تولید نشد."
            }
            result
        } catch (e: Throwable) {
            e.printStackTrace()
            "خطا در رونویسی صوت: ${e.localizedMessage ?: e.message}"
        }
    }

    private fun findExistingModelFile(): File? {
        val candidates = listOf(
            File(context.filesDir, MODEL_FILENAME),
            context.getExternalFilesDir(null)?.resolve(MODEL_FILENAME)
        )
        for (candidate in candidates) {
            if (candidate != null && candidate.exists() && candidate.length() > 1024) {
                return candidate
            }
        }
        return null
    }

    private fun extractModelFromAssetsIfAvailable(): File? {
        return try {
            val assetsList = context.assets.list("") ?: return null
            if (MODEL_FILENAME in assetsList) {
                val outFile = File(context.filesDir, MODEL_FILENAME)
                context.assets.open(MODEL_FILENAME).use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                }
                outFile
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun generateSimulatedPersianTranscription(segmentIndex: Int, totalSegments: Int): String {
        val phrases = listOf(
            "سلام، به سیستم تبدیل صوت به متن آفلاین خوش آمدید.",
            "این سیستم با استفاده از مدل Gemma 4 به صورت کاملاً آفلاین کار می‌کند.",
            "صوت ورودی با نرخ ۱۶ کیلوهرتز و فرمت مونو پردازش شد.",
            "در صورت نیاز می‌توانید متن خروجی را ویرایش یا کپی کنید."
        )
        return phrases.getOrElse(segmentIndex - 1) {
            "قطعه صوتی شماره $segmentIndex با موفقیت تبدیل شد."
        }
    }

    fun close() {
        try {
            engine?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            engine = null
            conversation = null
        }
    }
}

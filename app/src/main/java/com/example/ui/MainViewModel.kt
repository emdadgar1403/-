package com.example.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.GemmaAsrManager
import com.example.ai.ModelStatus
import com.example.audio.AudioRecorder
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class UiState(
    val isRecording: Boolean = false,
    val recordingDurationSec: Int = 0,
    val amplitude: Float = 0f,
    val isProcessing: Boolean = false,
    val processingMessage: String = "",
    val processingProgress: Float = 0f,
    val transcriptionText: String = "",
    val modelStatus: ModelStatus = ModelStatus.Idle,
    val lastChunksCount: Int = 0,
    val infoMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val audioRecorder = AudioRecorder(application)
    private val asrManager = GemmaAsrManager(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _eventFlow = MutableSharedFlow<String>()
    val eventFlow: SharedFlow<String> = _eventFlow.asSharedFlow()

    init {
        // Collect recorder states
        viewModelScope.launch {
            audioRecorder.isRecording.collect { rec ->
                _uiState.update { it.copy(isRecording = rec) }
            }
        }
        viewModelScope.launch {
            audioRecorder.recordingDurationSec.collect { sec ->
                _uiState.update { it.copy(recordingDurationSec = sec) }
            }
        }
        viewModelScope.launch {
            audioRecorder.currentAmplitude.collect { amp ->
                _uiState.update { it.copy(amplitude = amp) }
            }
        }
        viewModelScope.launch {
            asrManager.modelStatus.collect { status ->
                _uiState.update { it.copy(modelStatus = status) }
            }
        }

        // Initialize model on startup
        viewModelScope.launch {
            asrManager.initializeModel()
        }
    }

    fun startRecording(): Boolean {
        if (_uiState.value.isProcessing) return false
        val started = audioRecorder.startRecording()
        if (started) {
            _uiState.update {
                it.copy(
                    processingMessage = "",
                    lastChunksCount = 0
                )
            }
        }
        return started
    }

    fun stopRecording() {
        if (!_uiState.value.isRecording) return

        val chunkFiles = audioRecorder.stopRecording()
        if (chunkFiles.isEmpty()) {
            emitToast("هیچ صوتی ضبط نشد.")
            return
        }

        _uiState.update {
            it.copy(
                isProcessing = true,
                processingProgress = 0f,
                lastChunksCount = chunkFiles.size,
                processingMessage = if (chunkFiles.size > 1) {
                    "صوت به ${chunkFiles.size} قطعه تقسیم شد. شروع پردازش…"
                } else {
                    "در حال آماده‌سازی صوت برای پردازش…"
                }
            )
        }

        processAudioChunks(chunkFiles)
    }

    private fun processAudioChunks(chunks: List<File>) {
        viewModelScope.launch {
            val total = chunks.size
            val fullTranscript = StringBuilder()

            for (index in chunks.indices) {
                val chunk = chunks[index]
                val currentNum = index + 1
                val progress = currentNum.toFloat() / total.toFloat()

                _uiState.update {
                    it.copy(
                        processingProgress = progress,
                        processingMessage = "در حال پردازش قطعه $currentNum از $total با مدل Gemma…"
                    )
                }

                val segmentResult = asrManager.transcribeSegment(
                    wavFile = chunk,
                    segmentIndex = currentNum,
                    totalSegments = total,
                    onProgress = { msg ->
                        _uiState.update { it.copy(processingMessage = msg) }
                    }
                )

                if (fullTranscript.isNotEmpty() && segmentResult.isNotEmpty()) {
                    fullTranscript.append(" ")
                }
                fullTranscript.append(segmentResult)
            }

            val finalResult = fullTranscript.toString().trim()
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    processingProgress = 1f,
                    processingMessage = "",
                    transcriptionText = if (it.transcriptionText.isNotEmpty()) {
                        "${it.transcriptionText}\n\n$finalResult"
                    } else {
                        finalResult
                    }
                )
            }
            emitToast("تبدیل صوت به متن با موفقیت انجام شد.")
        }
    }

    fun updateTranscription(newText: String) {
        _uiState.update { it.copy(transcriptionText = newText) }
    }

    fun clearTranscription() {
        _uiState.update { it.copy(transcriptionText = "") }
        emitToast("متن پاک شد.")
    }

    fun copyToClipboard() {
        val text = _uiState.value.transcriptionText
        if (text.isEmpty()) return

        val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Transcribed Text", text)
        clipboard.setPrimaryClip(clip)
        emitToast("متن در حافظه کپی شد.")
    }

    fun enableDemoMode() {
        asrManager.enableDemoMode()
        emitToast("حالت تست فعال شد.")
    }

    fun importModel(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processingMessage = "در حال وارد کردن فایل مدل…") }
            val ok = asrManager.importModelFromUri(uri)
            _uiState.update { it.copy(isProcessing = false, processingMessage = "") }
            if (ok) {
                emitToast("مدل با موفقیت بارگذاری شد.")
            } else {
                emitToast("خطا در بارگذاری فایل مدل.")
            }
        }
    }

    fun retryModelLoad() {
        viewModelScope.launch {
            asrManager.initializeModel()
        }
    }

    private fun emitToast(msg: String) {
        viewModelScope.launch {
            _eventFlow.emit(msg)
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioRecorder.stopRecording()
        asrManager.close()
    }
}

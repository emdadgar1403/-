package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.ai.ModelStatus
import com.example.ui.MainViewModel
import com.example.ui.theme.RecordingRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningOrange
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AsrScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    var showModelDialog by remember { mutableStateOf(false) }
    var showPermissionRationale by remember { mutableStateOf(false) }

    // Event listener for toast notifications
    LaunchedEffect(Unit) {
        viewModel.eventFlow.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    // Model file picker launcher (.litertlm)
    val modelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importModel(it) }
    }

    // Permission launcher for RECORD_AUDIO
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startRecording()
        } else {
            showPermissionRationale = true
        }
    }

    fun handleRecordClick() {
        if (uiState.isRecording) {
            viewModel.stopRecording()
        } else {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                viewModel.startRecording()
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    // Persian RTL layout support
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "آوانگار آفلاین",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                // Offline security badge
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Security,
                                            contentDescription = "بدون اینترنت",
                                            modifier = Modifier.size(12.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "۱۰۰٪ آفلاین",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "تبدیل صوت به متن با مدل محلی Gemma 4",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showModelDialog = true },
                            modifier = Modifier.testTag("open_model_dialog")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "تنظیمات مدل",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 640.dp)
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Spacer(modifier = Modifier.height(4.dp))

                    // Model Status Banner
                    ModelStatusBanner(
                        status = uiState.modelStatus,
                        onOpenSettings = { showModelDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Recording Hub
                    RecordingCard(
                        isRecording = uiState.isRecording,
                        durationSec = uiState.recordingDurationSec,
                        amplitude = uiState.amplitude,
                        isProcessing = uiState.isProcessing,
                        onToggleRecording = { handleRecordClick() },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Processing Status Indicator
                    AnimatedVisibility(
                        visible = uiState.isProcessing,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        ProcessingCard(
                            message = uiState.processingMessage,
                            progress = uiState.processingProgress,
                            chunksCount = uiState.lastChunksCount,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Transcription Result Box (Editable & Copyable)
                    TranscriptionCard(
                        text = uiState.transcriptionText,
                        onTextChanged = { viewModel.updateTranscription(it) },
                        onCopy = { viewModel.copyToClipboard() },
                        onClear = { viewModel.clearTranscription() },
                        onShare = {
                            if (uiState.transcriptionText.isNotEmpty()) {
                                shareText(context, uiState.transcriptionText)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    // Model Management Dialog
    if (showModelDialog) {
        ModelManagerDialog(
            status = uiState.modelStatus,
            onDismiss = { showModelDialog = false },
            onPickModel = {
                modelPickerLauncher.launch(arrayOf("*/*"))
            },
            onEnableDemoMode = {
                viewModel.enableDemoMode()
                showModelDialog = false
            },
            onRetry = {
                viewModel.retryModelLoad()
            }
        )
    }

    // Permission Rationale Dialog
    if (showPermissionRationale) {
        AlertDialog(
            onDismissRequest = { showPermissionRationale = false },
            title = { Text("مجوز دسترسی به میکروفون") },
            text = {
                Text("برای ضبط صدا و تبدیل آن به متن با مدل محلی Gemma، برنامه به مجوز ضبط صدا نیاز دارد. اطلاعات ضبط شده هرگز به اینترنت ارسال نمی‌شود و کاملاً آفلاین روی دستگاه پردازش می‌شود.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionRationale = false
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                ) {
                    Text("تلاش مجدد")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionRationale = false }) {
                    Text("انصراف")
                }
            }
        )
    }
}

@Composable
private fun ModelStatusBanner(
    status: ModelStatus,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable { onOpenSettings() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (status) {
                is ModelStatus.Ready -> if (status.isUsingDemoFallback) WarningOrange.copy(alpha = 0.12f) else SuccessGreen.copy(alpha = 0.12f)
                is ModelStatus.Loading -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (status) {
                is ModelStatus.Ready -> {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (status.isUsingDemoFallback) WarningOrange else SuccessGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (status.isUsingDemoFallback) "حالت آزمایشی (Demo) فعال است" else "مدل محلی Gemma 4 آماده است",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (status.isUsingDemoFallback) "برای تست بدون فایل مدل ۲ گیگابایتی" else status.modelFile.name,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is ModelStatus.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "در حال بارگذاری موتور LiteRT-LM…",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                is ModelStatus.Missing -> {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "فایل مدل gemma-4-e2b.litertlm یافت نشد",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "برای راهنمای نصب یا فعال‌سازی حالت آزمایشی لمس کنید",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is ModelStatus.Error -> {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = status.message,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun RecordingCard(
    isRecording: Boolean,
    durationSec: Int,
    amplitude: Float,
    isProcessing: Boolean,
    onToggleRecording: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 1.25f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Duration Display
            val minutes = durationSec / 60
            val seconds = durationSec % 60
            val formattedTime = String.format(Locale.US, "%02d:%02d", minutes, seconds)

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isRecording) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(RecordingRed)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = formattedTime,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isRecording) RecordingRed else MaterialTheme.colorScheme.onSurface
                )
            }

            // Audio chunking note
            val currentChunk = (durationSec / 28) + 1
            if (isRecording && durationSec >= 28) {
                Text(
                    text = "در حال ضبط قطعه شماره $currentChunk (تقسیم خودکار ۳۰ ثانیه‌ای)",
                    fontSize = 12.sp,
                    color = WarningOrange,
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else {
                Text(
                    text = if (isRecording) "در حال ضبط صدا با کیفیت ۱۶ کیلوهرتز…" else "صوت با نرخ ۱۶kHz مونو برای پردازش آماده می‌شود",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Animated waveform bars
            WaveformDisplay(
                isRecording = isRecording,
                amplitude = amplitude,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Big Record Button with Pulse Glow
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(100.dp)
            ) {
                if (isRecording) {
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(RecordingRed.copy(alpha = 0.25f))
                    )
                }

                val buttonColor by animateColorAsState(
                    targetValue = if (isRecording) RecordingRed else MaterialTheme.colorScheme.primary,
                    label = "btn_color"
                )

                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(buttonColor, buttonColor.copy(alpha = 0.85f))
                            )
                        )
                        .clickable(enabled = !isProcessing) { onToggleRecording() }
                        .testTag("record_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = if (isRecording) "توقف ضبط" else "شروع ضبط",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (isRecording) "برای پایان ضبط لمس کنید" else "برای شروع ضبط لمس کنید",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun WaveformDisplay(
    isRecording: Boolean,
    amplitude: Float,
    modifier: Modifier = Modifier
) {
    val barCount = 21
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until barCount) {
            val factor = kotlin.math.sin((i.toDouble() / barCount) * Math.PI).toFloat()
            val heightFraction = if (isRecording) {
                ((amplitude * factor) + 0.15f).coerceIn(0.1f, 1f)
            } else {
                0.15f
            }

            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxSize(fraction = heightFraction)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (isRecording) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    )
            )
        }
    }
}

@Composable
private fun ProcessingCard(
    message: String,
    progress: Float,
    chunksCount: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "تبدیل صوت به متن فارسی",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                if (chunksCount > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "$chunksCount قطعه",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = message,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun TranscriptionCard(
    text: String,
    onTextChanged: (String) -> Unit,
    onCopy: () -> Unit,
    onClear: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.transcription_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (text.isNotEmpty()) {
                    val wordCount = text.trim().split("\\s+".toRegex()).size
                    Text(
                        text = "$wordCount کلمه",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Editable Text Area
            OutlinedTextField(
                value = text,
                onValueChange = onTextChanged,
                placeholder = {
                    Text(
                        text = "متن پیاده‌سازی شده در اینجا نمایش داده می‌شود و می‌توانید آن را ویرایش یا کپی کنید…",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .testTag("transcription_input"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onCopy,
                    enabled = text.isNotEmpty(),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("copy_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "کپی", fontSize = 13.sp)
                }

                OutlinedButton(
                    onClick = onShare,
                    enabled = text.isNotEmpty(),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("share_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "اشتراک", fontSize = 13.sp)
                }

                IconButton(
                    onClick = onClear,
                    enabled = text.isNotEmpty(),
                    modifier = Modifier.testTag("clear_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "پاک کردن",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelManagerDialog(
    status: ModelStatus,
    onDismiss: () -> Unit,
    onPickModel: () -> Unit,
    onEnableDemoMode: () -> Unit,
    onRetry: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "مدیریت مدل آفلاین Gemma 4 E2B")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "این برنامه برای تبدیل صوت به متن فارسی از مدل محلی Gemma 4 E2B (.litertlm) استفاده می‌کند و هیچ ارتباط اینترنتی ندارد.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "مسیر مورد انتظار فایل مدل:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "gemma-4-e2b.litertlm در پوشه داخلی یا assets",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = "گزینه‌های راه‌اندازی:",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )

                // Pick file button
                Button(
                    onClick = onPickModel,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("انتخاب فایل مدل (.litertlm) از دستگاه")
                }

                // Demo mode button
                OutlinedButton(
                    onClick = onEnableDemoMode,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("فعال‌سازی حالت آزمایشی (Demo Mode)")
                }

                Text(
                    text = "نکته: حالت آزمایشی به شما اجازه می‌دهد جریان ضبط صدا، نرخ ۱۶kHz و قطعه‌بندی ۳۰ ثانیه‌ای را حتی قبل از دانلود مدل ۲ گیگابایتی آزمایش کنید.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("بستن")
            }
        }
    )
}

private fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "اشتراک‌گذاری متن"))
}

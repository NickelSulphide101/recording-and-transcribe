package com.example.recordingandtranscribe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.recordingandtranscribe.core.FileExporter
import com.example.recordingandtranscribe.core.GeminiNanoTranscriber
import com.example.recordingandtranscribe.core.GeminiTranscriber
import com.example.recordingandtranscribe.core.MetadataManager
import com.example.recordingandtranscribe.core.zh
import com.example.recordingandtranscribe.core.RecordingMetadata
import com.example.recordingandtranscribe.core.SettingsRepository
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptionScreen(
    navController: NavController,
    file: File,
    settingsRepository: SettingsRepository
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val apiKey by settingsRepository.apiKeyFlow.collectAsState(initial = null)
    val modelName by settingsRepository.modelNameFlow.collectAsState(initial = "gemini-1.5-flash")
    val useGeminiNano by settingsRepository.useGeminiNanoFlow.collectAsState(initial = false)
    
    var isProcessing by remember { mutableStateOf(false) }
    var downloadProgressMsg by remember { mutableStateOf<String?>(null) }
    var isAsking by remember { mutableStateOf(false) }
    var chatQuery by remember { mutableStateOf("") }
    var metadata by remember { mutableStateOf(MetadataManager.loadMetadata(file)) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }
    
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val clipboardManager = LocalClipboardManager.current
    
    // Separate scroll states for each tab
    val transcriptScroll = rememberScrollState()
    val summaryScroll = rememberScrollState()
    val taskScroll = rememberScrollState()
    val emotionScroll = rememberScrollState()
    val privacyScroll = rememberScrollState()
    val chatScroll = rememberScrollState()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(file.name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back".zh(context, "返回"))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val pdf = FileExporter.exportToPdf(context, file, metadata)
                        if (pdf != null) FileExporter.shareFile(context, pdf)
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Share".zh(context, "分享"))
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "Info",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("File Size".zh(context, "文件大小"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${file.length() / 1024} KB", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    if (metadata.tags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            metadata.tags.forEach { tag ->
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text("#$tag", style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                            }
                        }
                    } else if (metadata.keywords.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            metadata.keywords.take(3).forEach { kw ->
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(kw, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            // AI Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Button 1: Speech to Text
                Button(
                    onClick = {
                        val apiKeysList = (apiKey ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        if (!useGeminiNano && apiKeysList.isEmpty()) {
                            errorMessage = "Please set Gemini API Key in Settings.".zh(context, "请先在设置中配置 Gemini API 密钥。")
                            return@Button
                        }
                        isProcessing = true
                        errorMessage = null
                        downloadProgressMsg = if (useGeminiNano) "Downloading AI model on first use...".zh(context, "首次使用本地 AI 需下载模型，请耐心等待...") else null
                        coroutineScope.launch {
                            try {
                                val transcriber = if (apiKeysList.isNotEmpty()) {
                                    GeminiTranscriber(apiKeysList, modelName ?: "gemini-1.5-flash")
                                } else {
                                    null
                                }
                                val nanoTranscriber = GeminiNanoTranscriber(context)
                                
                                val transcriptResult = if (useGeminiNano) {
                                    nanoTranscriber.transcribeOnDevice(file)
                                } else {
                                    transcriber?.transcribeAudio(file) ?: Result.failure(Exception("API Key required"))
                                }

                                if (transcriptResult.isSuccess) {
                                    val newMetadata = metadata.copy(transcript = transcriptResult.getOrNull())
                                    metadata = newMetadata
                                    MetadataManager.saveMetadata(file, newMetadata)
                                } else {
                                    errorMessage = transcriptResult.exceptionOrNull()?.message ?: "Transcription failed.".zh(context, "转录失败。")
                                }
                            } finally {
                                isProcessing = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = MaterialTheme.shapes.large,
                    enabled = !isProcessing
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(downloadProgressMsg ?: "Processing...".zh(context, "处理中..."))
                    } else {
                        Icon(Icons.Default.Mic, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Transcribe".zh(context, "语音转文字"))
                    }
                }

                // Button 2: AI Analysis
                Button(
                    onClick = {
                        val apiKeysList = (apiKey ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        if (!useGeminiNano && apiKeysList.isEmpty()) {
                            errorMessage = "Please set Gemini API Key in Settings.".zh(context, "请先在设置中配置 Gemini API 密钥。")
                            return@Button
                        }
                        isProcessing = true
                        errorMessage = null
                        downloadProgressMsg = if (useGeminiNano) "Downloading AI model on first use...".zh(context, "首次使用本地 AI 需下载模型，请耐心等待...") else null
                        coroutineScope.launch {
                            try {
                                val transcriber = if (apiKeysList.isNotEmpty()) {
                                    GeminiTranscriber(apiKeysList, modelName ?: "gemini-1.5-flash")
                                } else {
                                    null
                                }
                                val nanoTranscriber = GeminiNanoTranscriber(context)

                                // Ensure we have transcription first
                                var currentTranscript = metadata.transcript ?: ""
                                if (currentTranscript.isEmpty()) {
                                    val transcriptResult = if (useGeminiNano) {
                                        nanoTranscriber.transcribeOnDevice(file)
                                    } else {
                                        transcriber?.transcribeAudio(file) ?: Result.failure(Exception("API Key required"))
                                    }
                                    currentTranscript = transcriptResult.getOrNull() ?: ""
                                    if (currentTranscript.isEmpty()) {
                                        errorMessage = "Please transcribe first or check API key.".zh(context, "请先进行转录或检查 API 密钥。")
                                        return@launch
                                    }
                                }
                                
                                val summaryResult = if (useGeminiNano) {
                                    nanoTranscriber.generateOnDeviceSummary(currentTranscript)
                                } else {
                                    transcriber?.generateSummary(currentTranscript) ?: Result.failure(Exception("API Key required"))
                                }
                                val actionItemsResult = if (useGeminiNano) Result.success(emptyList()) else transcriber?.generateActionItems(currentTranscript) ?: Result.failure(Exception("API Key required"))
                                val keywordsResult = if (useGeminiNano) Result.success(emptyList()) else transcriber?.generateKeywords(currentTranscript) ?: Result.failure(Exception("API Key required"))
                                val emotionResult = if (useGeminiNano) Result.success(null) else transcriber?.generateEmotionAnalysis(currentTranscript) ?: Result.failure(Exception("API Key required"))
                                val privacyResult = if (useGeminiNano) Result.success(currentTranscript) else transcriber?.generatePrivacyMaskedTranscript(currentTranscript) ?: Result.failure(Exception("API Key required"))
                                val tagsResult = if (useGeminiNano) Result.success(emptyList()) else transcriber?.generateSmartTags(currentTranscript) ?: Result.success(emptyList())

                                val newTranscript = if (privacyResult.isSuccess) {
                                    privacyResult.getOrNull() ?: currentTranscript
                                } else {
                                    currentTranscript
                                }

                                val newMetadata = metadata.copy(
                                    transcript = newTranscript,
                                    summary = summaryResult.getOrNull() ?: metadata.summary,
                                    actionItems = actionItemsResult.getOrNull() ?: metadata.actionItems,
                                    keywords = keywordsResult.getOrNull() ?: metadata.keywords,
                                    emotionAnalysis = emotionResult.getOrNull() ?: metadata.emotionAnalysis,
                                    isPrivacyMasked = privacyResult.isSuccess,
                                    tags = tagsResult.getOrNull() ?: metadata.tags
                                )
                                metadata = newMetadata
                                MetadataManager.saveMetadata(file, newMetadata)
                            } catch (e: Exception) {
                                errorMessage = e.message ?: "Analysis failed.".zh(context, "分析失败。")
                            } finally {
                                isProcessing = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = MaterialTheme.shapes.large,
                    enabled = !isProcessing,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(downloadProgressMsg ?: "Processing...".zh(context, "处理中..."))
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("AI Analysis".zh(context, "AI 总结/分析"))
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            if (errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Text(text = errorMessage!!, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(16.dp))
                }
            }

            // Tabs for different views
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                edgePadding = 16.dp,
                divider = {}
            ) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                    Text("Transcript".zh(context, "全文"), modifier = Modifier.padding(12.dp))
                }
                Tab(selected = selectedTab == 5, onClick = { selectedTab = 5 }) {
                    Text("Chat".zh(context, "AI 追问"), modifier = Modifier.padding(12.dp))
                }
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                    Text("Summary".zh(context, "摘要"), modifier = Modifier.padding(12.dp))
                }
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }) {
                    Text("Tasks".zh(context, "任务"), modifier = Modifier.padding(12.dp))
                }
                Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }) {
                    Text("Emotion".zh(context, "情感"), modifier = Modifier.padding(12.dp))
                }
                Tab(selected = selectedTab == 4, onClick = { selectedTab = 4 }) {
                    Text("Privacy".zh(context, "隐私"), modifier = Modifier.padding(12.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Photo Gallery
            if (metadata.photoUris.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(100.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    metadata.photoUris.forEach { uriString ->
                        AsyncImage(
                            model = uriString,
                            contentDescription = null,
                            modifier = Modifier.size(100.dp).clip(MaterialTheme.shapes.medium),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(bottom = 16.dp),
                shape = MaterialTheme.shapes.large,
                color = Color.Transparent,
                tonalElevation = 2.dp
            ) {
                Box(
                    modifier = Modifier.fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surfaceContainerLow,
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                            )
                        )
                        .padding(16.dp)
                ) {
                    when (selectedTab) {
                        0 -> {
                            Text(
                                text = metadata.transcript ?: "No transcript available.".zh(context, "暂无转录内容。"),
                                modifier = Modifier.verticalScroll(transcriptScroll),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        5 -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Column(
                                    modifier = Modifier.weight(1f).verticalScroll(chatScroll),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (metadata.chatHistory.isEmpty()) {
                                        Text(
                                            "Ask anything about this recording!".zh(context, "针对录音提问，我会为您解答。"),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    metadata.chatHistory.forEach { msg ->
                                        val isUser = msg.role == "user"
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                                        ) {
                                            Surface(
                                                color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                                                shape = MaterialTheme.shapes.medium
                                            ) {
                                                Text(msg.content, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                                            }
                                        }
                                    }
                                    if (isAsking) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp).align(Alignment.CenterHorizontally))
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                if (useGeminiNano) {
                                    Text(
                                        "Chat is disabled in Local AI mode to protect your privacy.".zh(context, "为保护隐私，本地 AI 模式下已禁用云端追问。"),
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                } else if (apiKey.isNullOrBlank()) {
                                    Text(
                                        "Chat function requires Cloud Gemini API Key. (AI 追问功能需要云端 API 密钥)",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = chatQuery,
                                        onValueChange = { chatQuery = it },
                                        modifier = Modifier.weight(1f),
                                        placeholder = { Text("Ask a question...".zh(context, "提问...")) },
                                        shape = MaterialTheme.shapes.medium,
                                        enabled = !isAsking && (metadata.transcript?.isNotEmpty() == true) && !useGeminiNano && !apiKey.isNullOrBlank()
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    FilledIconButton(
                                        onClick = {
                                            val query = chatQuery
                                            chatQuery = ""
                                            isAsking = true
                                            coroutineScope.launch {
                                                val transcriber = GeminiTranscriber(
                                                    (apiKey ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() },
                                                    modelName ?: "gemini-1.5-flash"
                                                )
                                                val result = transcriber.askQuestion(
                                                    metadata.transcript ?: "",
                                                    query,
                                                    metadata.chatHistory
                                                )
                                                if (result.isSuccess) {
                                                    val newHistory = metadata.chatHistory + 
                                                        com.example.recordingandtranscribe.core.ChatMessage("user", query) +
                                                        com.example.recordingandtranscribe.core.ChatMessage("model", result.getOrNull() ?: "")
                                                    val newMetadata = metadata.copy(chatHistory = newHistory)
                                                    metadata = newMetadata
                                                    MetadataManager.saveMetadata(file, newMetadata)
                                                } else {
                                                    chatQuery = query // Restore query on failure
                                                    android.widget.Toast.makeText(context, "Chat failed: ${result.exceptionOrNull()?.message}".zh(context, "追问失败"), android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                                isAsking = false
                                            }
                                        },
                                        enabled = chatQuery.isNotBlank() && !isAsking && !useGeminiNano && !apiKey.isNullOrBlank(),
                                        modifier = Modifier.size(52.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Send", modifier = Modifier.size(24.dp))
                                    }
                                }
                            }
                        }
                        1 -> {
                            Text(
                                text = metadata.summary ?: "No summary available.".zh(context, "暂无摘要。"),
                                modifier = Modifier.verticalScroll(summaryScroll),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        2 -> {
                            Column(modifier = Modifier.verticalScroll(taskScroll)) {
                                if (metadata.actionItems.isEmpty()) {
                                    Text("No action items found.".zh(context, "未发现行动事项。"))
                                } else {
                                    metadata.actionItems.forEach { item ->
                                        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 4.dp)) {
                                            Icon(Icons.AutoMirrored.Filled.Assignment, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(item, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                            IconButton(
                                                onClick = {
                                                    val intent = android.content.Intent(android.content.Intent.ACTION_INSERT)
                                                        .setData(android.provider.CalendarContract.Events.CONTENT_URI)
                                                        .putExtra(android.provider.CalendarContract.Events.TITLE, item)
                                                        .putExtra(android.provider.CalendarContract.Events.DESCRIPTION, "Added from Recording: ${file.name}")
                                                    context.startActivity(intent)
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Default.Event, contentDescription = "Add to Calendar".zh(context, "添加到日历"), modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        3 -> {
                             Text(
                                metadata.emotionAnalysis ?: "No emotion analysis yet.".zh(context, "暂无情感分析。"),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.verticalScroll(emotionScroll)
                             )
                        }
                        4 -> {
                             Column(modifier = Modifier.verticalScroll(privacyScroll)) {
                                 Text(
                                    text = "Privacy Masking (AI Redaction)".zh(context, "隐私脱敏 (AI 脱敏)"),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                 )
                                 Spacer(modifier = Modifier.height(8.dp))
                                 val statusColor = if (metadata.isPrivacyMasked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                 val statusText = if (metadata.isPrivacyMasked) 
                                     "Active: Sensitive info (Names, Phones, Addresses) redacted.".zh(context, "已聚合：敏感信息（姓名、电话、地址）已脱敏。") 
                                     else "Inactive: Transcript contains original data.".zh(context, "未激活：转录稿包含原始数据。")
                                 
                                 Text(statusText, style = MaterialTheme.typography.bodyMedium, color = statusColor)
                                 Spacer(modifier = Modifier.height(16.dp))
                                 Text(
                                     "To apply masking, toggle 'Privacy Masking' in Settings and run 'AI Insights' again.".zh(context, "如需脱敏，请在设置中开启“隐私脱敏”并重新运行“AI 洞察”。"),
                                     style = MaterialTheme.typography.bodySmall,
                                     color = MaterialTheme.colorScheme.onSurfaceVariant
                                 )
                             }
                        }
                        else -> {}
                    }
                    
                    if (selectedTab != 5) {
                        // Copy button floating in the bottom right of the container
                        FilledIconButton(
                            onClick = {
                                val textToCopy = when(selectedTab) {
                                    0 -> metadata.transcript
                                    1 -> metadata.summary
                                    2 -> metadata.actionItems.joinToString("\n")
                                    3 -> metadata.emotionAnalysis
                                    else -> ""
                                }
                                if (!textToCopy.isNullOrBlank()) {
                                    clipboardManager.setText(AnnotatedString(textToCopy))
                                }
                            },
                            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy".zh(context, "复制"))
                        }
                    }
                }
            }
        }
    }
}

package com.example.recordingandtranscribe.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.recordingandtranscribe.core.FileExporter
import com.example.recordingandtranscribe.core.GeminiTranscriber
import com.example.recordingandtranscribe.core.MetadataManager
import com.example.recordingandtranscribe.core.RecordingMetadata
import com.example.recordingandtranscribe.core.SettingsRepository
import com.example.recordingandtranscribe.core.zh
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
    
    var isProcessing by remember { mutableStateOf(false) }
    var metadata by remember { mutableStateOf(MetadataManager.loadMetadata(file)) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }
    
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val clipboardManager = LocalClipboardManager.current

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
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                    Spacer(modifier = Modifier.weight(1f))
                    if (metadata.keywords.isNotEmpty()) {
                        metadata.keywords.take(2).forEach { kw ->
                            SuggestionChip(
                                onClick = {},
                                label = { Text(kw, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.padding(start = 4.dp)
                            )
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
                Button(
                    onClick = {
                        val apiKeysList = (apiKey ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        if (apiKeysList.isEmpty()) {
                            errorMessage = "Please set Gemini API Key in Settings.".zh(context, "请先在设置中配置 Gemini API 密钥。")
                            return@Button
                        }
                        isProcessing = true
                        errorMessage = null
                        coroutineScope.launch {
                            val transcriber = GeminiTranscriber(apiKeysList, modelName ?: "gemini-1.5-flash")
                            val transcriptResult = transcriber.transcribeAudio(file)
                            val summaryResult = transcriber.generateSummary(file)
                            val actionItemsResult = transcriber.generateActionItems(file)
                            val keywordsResult = transcriber.generateKeywords(file)

                            val newMetadata = RecordingMetadata(
                                transcript = transcriptResult.getOrNull() ?: metadata.transcript,
                                summary = summaryResult.getOrNull() ?: metadata.summary,
                                actionItems = actionItemsResult.getOrNull() ?: metadata.actionItems,
                                keywords = keywordsResult.getOrNull() ?: metadata.keywords
                            )
                            metadata = newMetadata
                            MetadataManager.saveMetadata(file, newMetadata)
                            isProcessing = false
                            if (transcriptResult.isFailure) {
                                errorMessage = transcriptResult.exceptionOrNull()?.message ?: "AI processing failed.".zh(context, "AI 处理失败。")
                            }
                        }
                    },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = MaterialTheme.shapes.large,
                    enabled = !isProcessing
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("AI Insights".zh(context, "AI 洞察"))
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
            SecondaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                divider = {}
            ) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                    Text("Transcript".zh(context, "全文"), modifier = Modifier.padding(12.dp))
                }
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                    Text("Summary".zh(context, "摘要"), modifier = Modifier.padding(12.dp))
                }
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }) {
                    Text("Tasks".zh(context, "任务"), modifier = Modifier.padding(12.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

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
                                modifier = Modifier.verticalScroll(scrollState),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        1 -> {
                            Text(
                                text = metadata.summary ?: "No summary available.".zh(context, "暂无摘要。"),
                                modifier = Modifier.verticalScroll(scrollState),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        2 -> {
                            Column(modifier = Modifier.verticalScroll(scrollState)) {
                                if (metadata.actionItems.isEmpty()) {
                                    Text("No action items found.".zh(context, "未发现行动事项。"))
                                } else {
                                    metadata.actionItems.forEach { item ->
                                        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 4.dp)) {
                                            Icon(Icons.Default.Assignment, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(item, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    // Copy button floating in the bottom right of the container
                    FilledIconButton(
                        onClick = {
                            val textToCopy = when(selectedTab) {
                                0 -> metadata.transcript
                                1 -> metadata.summary
                                2 -> metadata.actionItems.joinToString("\n")
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

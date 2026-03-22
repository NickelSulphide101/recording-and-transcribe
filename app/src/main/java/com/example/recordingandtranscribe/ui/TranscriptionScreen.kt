package com.example.recordingandtranscribe.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.recordingandtranscribe.core.GeminiTranscriber
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
    val coroutineScope = rememberCoroutineScope()
    val apiKey by settingsRepository.apiKeyFlow.collectAsState(initial = null)
    val modelName by settingsRepository.modelNameFlow.collectAsState(initial = "gemini-1.5-flash")
    
    var isTranscribing by remember { mutableStateOf(false) }
    var transcriptionText by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val clipboardManager = LocalClipboardManager.current
    val txtFile = remember(file) { File(file.parentFile, "${file.nameWithoutExtension}.txt") }

    LaunchedEffect(file) {
        if (txtFile.exists()) {
            transcriptionText = txtFile.readText()
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(file.name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                        Text("File Size", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${file.length() / 1024} KB", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = {
                    val apiKeysStr = apiKey ?: ""
                    val apiKeysList = apiKeysStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    
                    if (apiKeysList.isEmpty()) {
                        errorMessage = "Please set your Gemini API Key(s) in Settings first."
                        return@Button
                    }
                    
                    isTranscribing = true
                    errorMessage = null
                    
                    coroutineScope.launch {
                        val actualModel = if (modelName.isNullOrBlank()) "gemini-1.5-flash" else modelName!!
                        val transcriber = GeminiTranscriber(apiKeysList, actualModel)
                        val result = transcriber.transcribeAudio(file)
                        isTranscribing = false
                        
                        result.onSuccess {
                            transcriptionText = it
                            try {
                                txtFile.writeText(it)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }.onFailure {
                            errorMessage = it.message ?: "All keys failed to transcribe the audio."
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.extraLarge,
                enabled = !isTranscribing
            ) {
                if (isTranscribing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Transcribing Audio...", style = MaterialTheme.typography.titleMedium)
                } else {
                    Text(if (txtFile.exists()) "Re-Transcribe Audio" else "Transcribe Audio", style = MaterialTheme.typography.titleMedium)
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            if (errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            
            if (transcriptionText != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Result",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    FilledIconButton(onClick = {
                        clipboardManager.setText(AnnotatedString(transcriptionText!!))
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy text")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(bottom = 16.dp),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 4.dp
                ) {
                    Text(
                        text = transcriptionText!!,
                        modifier = Modifier
                            .padding(20.dp)
                            .verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

package com.example.recordingandtranscribe.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(file.name) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("File details", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Name: ${file.name}")
                    Text("Size: ${file.length() / 1024} KB")
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
                        }.onFailure {
                            errorMessage = it.message ?: "All keys failed to transcribe the audio."
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isTranscribing
            ) {
                if (isTranscribing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Transcribing...")
                } else {
                    Text("Transcribe with Gemini")
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            if (transcriptionText != null) {
                Text(
                    text = "Transcription:",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = transcriptionText!!,
                        modifier = Modifier
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

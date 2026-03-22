package com.example.recordingandtranscribe.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.recordingandtranscribe.core.SettingsRepository
import com.example.recordingandtranscribe.core.zh
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    settingsRepository: SettingsRepository
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val apiKey by settingsRepository.apiKeyFlow.collectAsState(initial = "")
    val modelName by settingsRepository.modelNameFlow.collectAsState(initial = "gemini-1.5-flash")
    val bitrate by settingsRepository.bitrateFlow.collectAsState(initial = 16000)
    val skipSilence by settingsRepository.skipSilenceFlow.collectAsState(initial = false)

    var currentApiKey by remember(apiKey) { mutableStateOf(apiKey ?: "") }
    var currentModelName by remember(modelName) { mutableStateOf(modelName ?: "gemini-1.5-flash") }
    var currentBitrate by remember(bitrate) { mutableIntStateOf(bitrate) }
    var currentSkipSilence by remember(skipSilence) { mutableStateOf(skipSilence) }
    
    var isSaved by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Settings".zh(context, "设置")) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back".zh(context, "返回"))
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
                .verticalScroll(rememberScrollState())
        ) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Google Gemini Configuration".zh(context, "Google Gemini 配置"),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = currentApiKey,
                        onValueChange = {
                            currentApiKey = it
                            isSaved = false
                        },
                        label = { Text("Gemini API Keys (comma separated)".zh(context, "Gemini API 密钥 (多个用英文逗号分隔)")) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = currentModelName,
                        onValueChange = {
                            currentModelName = it
                            isSaved = false
                        },
                        label = { Text("Model Name".zh(context, "模型名称")) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Recording Settings".zh(context, "录音设置"),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text("Audio Bitrate".zh(context, "音频比特率") + ": ${currentBitrate / 1000} kbps")
                    Slider(
                        value = currentBitrate.toFloat(),
                        onValueChange = { 
                            currentBitrate = it.toInt() 
                            isSaved = false
                        },
                        valueRange = 8000f..128000f,
                        steps = 14
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Skip Silence".zh(context, "自动跳过静音"), style = MaterialTheme.typography.bodyLarge)
                            Text("Auto-pause when no sound is detected".zh(context, "未检测到声音时自动暂停"), style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = currentSkipSilence,
                            onCheckedChange = { 
                                currentSkipSilence = it 
                                isSaved = false
                            }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            FilledTonalButton(
                onClick = {
                    coroutineScope.launch {
                        settingsRepository.saveSettings(currentApiKey, currentModelName, currentBitrate, currentSkipSilence)
                        isSaved = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Text("Save Configuration".zh(context, "保存设置"), style = MaterialTheme.typography.titleMedium)
            }
            if (isSaved) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Settings saved successfully!".zh(context, "设置已成功保存！"),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

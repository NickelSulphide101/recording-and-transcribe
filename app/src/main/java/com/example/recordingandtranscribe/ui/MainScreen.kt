package com.example.recordingandtranscribe.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.recordingandtranscribe.core.AudioRecorder
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(navController: NavController, audioRecorder: AudioRecorder) {
    var isRecording by remember { mutableStateOf(false) }
    var recordings by remember { mutableStateOf(audioRecorder.getRecordings()) }
    val context = LocalContext.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted -> hasPermission = isGranted }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recordings") },
                actions = {
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (!hasPermission) {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        if (isRecording) {
                            audioRecorder.stopRecording()
                            isRecording = false
                            recordings = audioRecorder.getRecordings()
                        } else {
                            val file = audioRecorder.startRecording()
                            if (file != null) {
                                isRecording = true
                            }
                        }
                    }
                },
                containerColor = if (isRecording) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = if (isRecording) "Stop Recording" else "Start Recording"
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (recordings.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No recordings yet. Tap the mic to start.")
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(recordings) { file ->
                        ListItem(
                            headlineContent = { Text(file.name, fontWeight = FontWeight.Bold) },
                            supportingContent = { Text("Size: ${file.length() / 1024} KB") },
                            modifier = Modifier.clickable {
                                navController.navigate("transcribe/${file.name}")
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

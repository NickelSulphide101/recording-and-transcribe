package com.example.recordingandtranscribe.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.recordingandtranscribe.core.AudioRecorder
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.recordingandtranscribe.core.AudioPlayer
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(navController: NavController, audioRecorder: AudioRecorder) {
    var isRecording by remember { mutableStateOf(false) }
    var recordings by remember { mutableStateOf(audioRecorder.getRecordings()) }
    val context = LocalContext.current

    val audioPlayer = remember { AudioPlayer() }
    
    val isPlaying by audioPlayer.isPlaying.collectAsState()
    val progress by audioPlayer.progress.collectAsState()
    val duration by audioPlayer.duration.collectAsState()
    val currentFile = audioPlayer.currentFile

    var fileToRename by remember { mutableStateOf<File?>(null) }
    var newFileName by remember { mutableStateOf("") }

    // When navigating away, stop playing.
    DisposableEffect(Unit) {
        onDispose {
            audioPlayer.stop()
        }
    }

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

    if (fileToRename != null) {
        AlertDialog(
            onDismissRequest = { fileToRename = null },
            title = { Text("Rename Recording") },
            text = {
                OutlinedTextField(
                    value = newFileName,
                    onValueChange = { newFileName = it },
                    label = { Text("New file name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val currentF = fileToRename!!
                    val finalName = if (newFileName.endsWith(".m4a")) newFileName else "$newFileName.m4a"
                    val newFile = File(currentF.parentFile, finalName)
                    if (currentF.renameTo(newFile)) {
                        recordings = audioRecorder.getRecordings()
                        // Stop playback if we renamed the currently playing file, as path changes
                        if (currentFile == currentF) {
                            audioPlayer.stop()
                        }
                    }
                    fileToRename = null
                }) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }

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
                            audioPlayer.stop()
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
        },
        bottomBar = {
            if (currentFile != null) {
                BottomAppBar {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = currentFile.name,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(formatTime(progress), style = MaterialTheme.typography.bodySmall)
                            Slider(
                                value = if (duration > 0) progress.toFloat() / duration else 0f,
                                onValueChange = { percent ->
                                    val newPos = (percent * duration).toInt()
                                    audioPlayer.seekTo(newPos)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp)
                            )
                            Text(formatTime(duration), style = MaterialTheme.typography.bodySmall)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val currentIndex = recordings.indexOf(currentFile)
                            
                            IconButton(
                                onClick = {
                                    if (currentIndex > 0) {
                                        audioPlayer.playFile(recordings[currentIndex - 1])
                                    }
                                },
                                enabled = currentIndex > 0
                            ) {
                                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous Track")
                            }
                            
                            IconButton(onClick = { audioPlayer.seekRelative(-10000) }) {
                                Icon(Icons.Default.FastRewind, contentDescription = "Rewind 10s")
                            }
                            
                            FilledIconButton(onClick = { audioPlayer.togglePlayPause() }) {
                                Icon(
                                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause"
                                )
                            }
                            
                            IconButton(onClick = { audioPlayer.seekRelative(10000) }) {
                                Icon(Icons.Default.FastForward, contentDescription = "Forward 10s")
                            }
                            
                            IconButton(
                                onClick = {
                                    if (currentIndex in 0 until recordings.size - 1) {
                                        audioPlayer.playFile(recordings[currentIndex + 1])
                                    }
                                },
                                enabled = currentIndex in 0 until recordings.size - 1
                            ) {
                                Icon(Icons.Default.SkipNext, contentDescription = "Next Track")
                            }
                        }
                    }
                }
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
                        var expanded by remember { mutableStateOf(false) }

                        ListItem(
                            headlineContent = { Text(file.name, fontWeight = FontWeight.Bold) },
                            supportingContent = { Text("Size: ${file.length() / 1024} KB") },
                            modifier = Modifier.clickable {
                                navController.navigate("transcribe/${file.name}")
                            },
                            leadingContent = {
                                if (currentFile == file && isPlaying) {
                                    Icon(Icons.Default.SurroundSound, contentDescription = "Playing", tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = {
                                        if (currentFile == file) {
                                            audioPlayer.togglePlayPause()
                                        } else {
                                            audioPlayer.playFile(file)
                                        }
                                    }) {
                                        Icon(
                                            if (currentFile == file && isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = "Play/Pause"
                                        )
                                    }
                                    Box {
                                        IconButton(onClick = { expanded = true }) {
                                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                                        }
                                        DropdownMenu(
                                            expanded = expanded,
                                            onDismissRequest = { expanded = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Rename") },
                                                onClick = {
                                                    expanded = false
                                                    newFileName = file.nameWithoutExtension
                                                    fileToRename = file
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Delete") },
                                                onClick = {
                                                    expanded = false
                                                    if (currentFile == file) {
                                                        audioPlayer.stop()
                                                    }
                                                    file.delete()
                                                    recordings = audioRecorder.getRecordings()
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

private fun formatTime(millis: Int): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

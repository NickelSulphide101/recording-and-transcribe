package com.example.recordingandtranscribe.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.example.recordingandtranscribe.core.AudioPlayer
import com.example.recordingandtranscribe.core.AudioRecorder
import com.example.recordingandtranscribe.core.AudioRecorderService
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(navController: NavController, audioRecorder: AudioRecorder) {
    var recordings by remember { mutableStateOf(audioRecorder.getRecordings()) }
    val context = LocalContext.current

    val audioPlayer = remember { AudioPlayer() }
    
    val isPlaying by audioPlayer.isPlaying.collectAsState()
    val progress by audioPlayer.progress.collectAsState()
    val duration by audioPlayer.duration.collectAsState()
    val currentFile by audioPlayer.currentFile.collectAsState()
    
    val isRecording by AudioRecorderService.isRecording.collectAsState()
    val isPaused by AudioRecorderService.isPaused.collectAsState()

    var fileToRename by remember { mutableStateOf<File?>(null) }
    var newFileName by remember { mutableStateOf("") }
    
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    DisposableEffect(Unit) {
        onDispose {
            audioPlayer.stop()
        }
    }
    
    LaunchedEffect(isRecording) {
        if (!isRecording) {
            recordings = audioRecorder.getRecordings()
        }
    }

    val permissionsToRequest = remember {
        listOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS
        )
    }

    var hasPermissions by remember {
        mutableStateOf(
            permissionsToRequest.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions -> 
            hasPermissions = permissions.values.all { it == true }
        }
    )

    if (fileToRename != null) {
        AlertDialog(
            onDismissRequest = { fileToRename = null },
            icon = { Icon(Icons.Default.Edit, contentDescription = null) },
            title = { Text("Rename Recording") },
            text = {
                OutlinedTextField(
                    value = newFileName,
                    onValueChange = { newFileName = it },
                    label = { Text("New file name") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                FilledTonalButton(onClick = {
                    val currentF = fileToRename!!
                    val extension = if (currentF.name.endsWith(".m4a")) ".m4a" else ".ogg"
                    val finalName = if (newFileName.endsWith(extension)) newFileName else "$newFileName$extension"
                    val newFile = File(currentF.parentFile, finalName)
                    if (currentF.renameTo(newFile)) {
                        val oldTxtFile = File(currentF.parentFile, "${currentF.nameWithoutExtension}.txt")
                        if (oldTxtFile.exists()) {
                            oldTxtFile.renameTo(File(currentF.parentFile, "${newFile.nameWithoutExtension}.txt"))
                        }
                        recordings = audioRecorder.getRecordings()
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
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Recordings", fontWeight = FontWeight.Bold) },
                actions = {
                    FilledIconButton(onClick = { navController.navigate("settings") }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            if (isRecording) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            if (isPaused) audioRecorder.resumeRecording() else audioRecorder.pauseRecording()
                        },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        icon = { Icon(if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, contentDescription = null) },
                        text = { Text(if (isPaused) "Resume" else "Pause") }
                    )
                    ExtendedFloatingActionButton(
                        onClick = {
                            audioRecorder.stopRecording()
                        },
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        icon = { Icon(Icons.Default.Stop, contentDescription = null) },
                        text = { Text("Stop") }
                    )
                }
            } else {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (!hasPermissions) {
                            permissionLauncher.launch(permissionsToRequest.toTypedArray())
                        } else {
                            audioPlayer.stop()
                            audioRecorder.startRecording()
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    icon = { Icon(Icons.Default.Mic, contentDescription = null) },
                    text = { Text("Record Audio") },
                    expanded = true
                )
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
        bottomBar = {
            if (currentFile != null) {
                BottomAppBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = currentFile!!.name,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(formatTime(progress), style = MaterialTheme.typography.labelMedium)
                            Slider(
                                value = if (duration > 0) progress.toFloat() / duration else 0f,
                                onValueChange = { percent ->
                                    val newPos = (percent * duration).toInt()
                                    audioPlayer.seekTo(newPos)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                            Text(formatTime(duration), style = MaterialTheme.typography.labelMedium)
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
                            
                            FilledIconButton(
                                onClick = { audioPlayer.togglePlayPause() },
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(
                                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    modifier = Modifier.size(32.dp)
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
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No recordings found",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Tap the record button below to begin.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 88.dp) // Leave space for FAB
                ) {
                    items(recordings) { file ->
                        var expanded by remember { mutableStateOf(false) }

                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            onClick = { navController.navigate("transcribe/${file.name}") },
                            shape = MaterialTheme.shapes.large,
                            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
                        ) {
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                                headlineContent = { Text(file.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = { Text("${file.length() / 1024} KB", style = MaterialTheme.typography.bodySmall) },
                                leadingContent = {
                                    Box(
                                        modifier = Modifier.size(48.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (currentFile == file && isPlaying) {
                                            Icon(Icons.Default.GraphicEq, contentDescription = "Playing", tint = MaterialTheme.colorScheme.primary)
                                        } else {
                                            Icon(Icons.Default.AudioFile, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
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
                                                if (currentFile == file && isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                                contentDescription = "Play/Pause",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(32.dp)
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
                                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                                    onClick = {
                                                        expanded = false
                                                        newFileName = file.nameWithoutExtension
                                                        fileToRename = file
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                                    onClick = {
                                                        expanded = false
                                                        if (currentFile == file) {
                                                            audioPlayer.stop()
                                                        }
                                                        val txtFile = File(file.parentFile, "${file.nameWithoutExtension}.txt")
                                                        if (txtFile.exists()) txtFile.delete()
                                                        file.delete()
                                                        recordings = audioRecorder.getRecordings()
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            )
                        }
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

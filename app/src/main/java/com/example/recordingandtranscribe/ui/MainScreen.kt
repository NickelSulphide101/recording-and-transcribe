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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.foundation.Canvas
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.example.recordingandtranscribe.core.AudioPlayer
import com.example.recordingandtranscribe.core.AudioRecorder
import com.example.recordingandtranscribe.core.AudioRecorderService
import com.example.recordingandtranscribe.core.AudioTrimmer
import com.example.recordingandtranscribe.core.FileExporter
import com.example.recordingandtranscribe.core.MetadataManager
import com.example.recordingandtranscribe.core.zh
import java.io.File
import java.util.Locale
import kotlin.random.Random

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
    val amplitude by AudioRecorderService.amplitude.collectAsState()

    var capturedPhotoUris by remember { mutableStateOf(listOf<String>()) }
    var tempPhotoUri by remember { mutableStateOf<android.net.Uri?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var filterFavorite by remember { mutableStateOf(false) }
    var selectedTag by remember { mutableStateOf<String?>(null) }

    var fileToRename by remember { mutableStateOf<File?>(null) }
    var fileToTag by remember { mutableStateOf<File?>(null) }
    var fileToTrim by remember { mutableStateOf<File?>(null) }
    var trimStart by remember { mutableStateOf("0") }
    var trimEnd by remember { mutableStateOf("10") }
    var tagToAdd by remember { mutableStateOf("") }
    var newFileName by remember { mutableStateOf("") }
    
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    DisposableEffect(Unit) {
        onDispose {
            audioPlayer.stop()
        }
    }
    
    LaunchedEffect(isRecording) {
        if (!isRecording) {
            // Save captured photos to metadata of the just-finished recording
            val lastFile = AudioRecorderService.currentFile
            if (lastFile != null && capturedPhotoUris.isNotEmpty()) {
                val metadata = MetadataManager.loadMetadata(lastFile)
                MetadataManager.saveMetadata(lastFile, metadata.copy(photoUris = capturedPhotoUris))
            }
            capturedPhotoUris = emptyList() // Reset for next session
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

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            if (success) {
                tempPhotoUri?.let { capturedPhotoUris = capturedPhotoUris + it.toString() }
            }
        }
    )

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            uri?.let { capturedPhotoUris = capturedPhotoUris + it.toString() }
        }
    )

    val importAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            uri?.let {
                val inputStream = context.contentResolver.openInputStream(it)
                val fileName = "IMPORT_${System.currentTimeMillis()}.m4a"
                val destFile = File(context.getExternalFilesDir(null), fileName)
                inputStream?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                recordings = audioRecorder.getRecordings()
            }
        }
    )

    if (fileToRename != null) {
        AlertDialog(
            onDismissRequest = { fileToRename = null },
            icon = { Icon(Icons.Default.Edit, contentDescription = null) },
            title = { Text("Rename Recording".zh(context, "重命名录音")) },
            text = {
                OutlinedTextField(
                    value = newFileName,
                    onValueChange = { newFileName = it },
                    label = { Text("New file name".zh(context, "新文件名")) },
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
                    Text("Rename".zh(context, "重命名"))
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToRename = null }) {
                    Text("Cancel".zh(context, "取消"))
                }
            }
        )
    }

    if (fileToTrim != null) {
        AlertDialog(
            onDismissRequest = { fileToTrim = null },
            icon = { Icon(Icons.Default.ContentCut, contentDescription = null) },
            title = { Text("Trim Recording".zh(context, "裁剪录音")) },
            text = {
                Column {
                    Text("Enter start/end time in seconds".zh(context, "输入开始/结束时间(秒)"), style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = trimStart,
                        onValueChange = { trimStart = it },
                        label = { Text("Start (sec)".zh(context, "开始(秒)")) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = trimEnd,
                        onValueChange = { trimEnd = it },
                        label = { Text("End (sec)".zh(context, "结束(秒)")) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val startUs = (trimStart.toLongOrNull() ?: 0L) * 1_000_000L
                    val endUs = (trimEnd.toLongOrNull() ?: 10L) * 1_000_000L
                    val inputFile = fileToTrim!!
                    val outputFile = File(inputFile.parentFile, "TRIM_${inputFile.name}")
                    coroutineScope.launch {
                        val success = AudioTrimmer.trimAudio(inputFile, outputFile, startUs, endUs)
                        if (success) {
                            recordings = audioRecorder.getRecordings()
                        }
                        fileToTrim = null
                    }
                }) {
                    Text("Trim".zh(context, "裁剪"))
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToTrim = null }) {
                    Text("Cancel".zh(context, "取消"))
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column {
                LargeTopAppBar(
                    title = { 
                        Column {
                            Text("Recordings".zh(context, "录音列表"), fontWeight = FontWeight.Bold)
                            if (isRecording) {
                                Text(
                                    if (isPaused) "Recording Paused".zh(context, "录音已暂停") else "Recording...".zh(context, "正在录音..."),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { importAudioLauncher.launch("audio/*") }) {
                            Icon(Icons.Default.Upload, contentDescription = "Import".zh(context, "导入"))
                        }
                        FilledIconButton(onClick = { navController.navigate("settings") }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings".zh(context, "设置"))
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
                
                // Search Bar
                AnimatedVisibility(
                    visible = !isRecording,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text("Search recordings...".zh(context, "搜索录音...")) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = if (searchQuery.isNotEmpty()) {
                            { IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Clear, contentDescription = null) } }
                        } else null,
                        shape = MaterialTheme.shapes.extraLarge,
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    )
                }

                // Filter Chips
                AnimatedVisibility(
                    visible = !isRecording,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = filterFavorite,
                            onClick = { filterFavorite = !filterFavorite },
                            label = { Text("Favorites".zh(context, "已收藏")) },
                            leadingIcon = if (filterFavorite) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            } else null
                        )

                        val allTags = recordings.flatMap { MetadataManager.loadMetadata(it).tags }.distinct()
                        allTags.forEach { tag ->
                            FilterChip(
                                selected = selectedTag == tag,
                                onClick = { selectedTag = if (selectedTag == tag) null else tag },
                                label = { Text(tag) }
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.animateContentSize()
            ) {
                if (isRecording) {
                    WaveformVisualizer(amplitude = amplitude, isPaused = isPaused)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        // Photo Capture Button
                        AssistChip(
                            onClick = {
                                val file = File(context.cacheDir, "photo_${System.currentTimeMillis()}.jpg")
                                val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                tempPhotoUri = uri
                                takePictureLauncher.launch(uri)
                            },
                            label = { Text("Photo".zh(context, "拍照片")) },
                            leadingIcon = { Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                        AssistChip(
                            onClick = { pickImageLauncher.launch("image/*") },
                            label = { Text("Pick".zh(context, "选图片")) },
                            leadingIcon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    }
                    
                    if (capturedPhotoUris.isNotEmpty()) {
                        Text(
                            "${capturedPhotoUris.size} photos attached".zh(context, "已附带 ${capturedPhotoUris.size} 张照片"),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ExtendedFloatingActionButton(
                            onClick = {
                                if (isPaused) audioRecorder.resumeRecording() else audioRecorder.pauseRecording()
                            },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            icon = { Icon(if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, contentDescription = null) },
                            text = { Text(if (isPaused) "Resume".zh(context, "继续") else "Pause".zh(context, "暂停")) }
                        )
                        ExtendedFloatingActionButton(
                            onClick = {
                                audioRecorder.stopRecording()
                            },
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            icon = { Icon(Icons.Default.Stop, contentDescription = null) },
                            text = { Text("Stop".zh(context, "停止")) }
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
                        text = { Text("Record Audio".zh(context, "录制音频")) },
                        expanded = true
                    )
                }
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
                                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous Track".zh(context, "上一首"))
                            }
                            
                            IconButton(onClick = { audioPlayer.seekRelative(-10000) }) {
                                Icon(Icons.Default.FastRewind, contentDescription = "Rewind 10s".zh(context, "快退 10 秒"))
                            }
                            
                            FilledIconButton(
                                onClick = { audioPlayer.togglePlayPause() },
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(
                                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause".zh(context, "播放/暂停"),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            
                            IconButton(onClick = { audioPlayer.seekRelative(10000) }) {
                                Icon(Icons.Default.FastForward, contentDescription = "Forward 10s".zh(context, "快进 10 秒"))
                            }
                            
                            IconButton(
                                onClick = {
                                    if (currentIndex in 0 until recordings.size - 1) {
                                        audioPlayer.playFile(recordings[currentIndex + 1])
                                    }
                                },
                                enabled = currentIndex in 0 until recordings.size - 1
                            ) {
                                Icon(Icons.Default.SkipNext, contentDescription = "Next Track".zh(context, "下一首"))
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
                            "No recordings found".zh(context, "暂无录音文件"),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Tap the record button below to begin.".zh(context, "点击下方麦克风按钮开始录音"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                val filteredRecordings = recordings.filter { file ->
                    val matchesSearch = file.name.contains(searchQuery, ignoreCase = true)
                    val metadata = MetadataManager.loadMetadata(file)
                    val matchesFavorite = !filterFavorite || metadata.isFavorite
                    val matchesTag = selectedTag == null || metadata.tags.contains(selectedTag)
                    matchesSearch && matchesFavorite && matchesTag
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 88.dp) // Leave space for FAB
                ) {
                    items(filteredRecordings) { file ->
                        var expanded by remember { mutableStateOf(false) }
                        var mData by remember { mutableStateOf(MetadataManager.loadMetadata(file)) }

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
                                supportingContent = { 
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("${file.length() / 1024} KB", style = MaterialTheme.typography.bodySmall)
                                            if (mData.isFavorite) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                        if (mData.tags.isNotEmpty()) {
                                            Row(modifier = Modifier.padding(top = 4.dp)) {
                                                mData.tags.take(3).forEach { tag ->
                                                    Text(
                                                        "#$tag", 
                                                        style = MaterialTheme.typography.labelSmall, 
                                                        color = MaterialTheme.colorScheme.secondary,
                                                        modifier = Modifier.padding(end = 4.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                },
                                leadingContent = {
                                    Box(
                                        modifier = Modifier.size(48.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (currentFile == file && isPlaying) {
                                            Icon(Icons.Default.GraphicEq, contentDescription = "Playing".zh(context, "正在播放"), tint = MaterialTheme.colorScheme.primary)
                                        } else {
                                            Icon(Icons.Default.AudioFile, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                },
                                trailingContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = {
                                            val newMeta = mData.copy(isFavorite = !mData.isFavorite)
                                            MetadataManager.saveMetadata(file, newMeta)
                                            mData = newMeta
                                        }) {
                                            Icon(
                                                if (mData.isFavorite) Icons.Default.Star else Icons.Default.StarOutline,
                                                contentDescription = "Favorite".zh(context, "收藏"),
                                                tint = if (mData.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(onClick = {
                                            if (currentFile == file) {
                                                audioPlayer.togglePlayPause()
                                            } else {
                                                audioPlayer.playFile(file)
                                            }
                                        }) {
                                            Icon(
                                                if (currentFile == file && isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                                contentDescription = "Play/Pause".zh(context, "播放/暂停"),
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                        Box {
                                            IconButton(onClick = { expanded = true }) {
                                                Icon(Icons.Default.MoreVert, contentDescription = "More options".zh(context, "更多选项"))
                                            }
                                            DropdownMenu(
                                                expanded = expanded,
                                                onDismissRequest = { expanded = false }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("Rename".zh(context, "重命名")) },
                                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                                    onClick = {
                                                        expanded = false
                                                        newFileName = file.nameWithoutExtension
                                                        fileToRename = file
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Export".zh(context, "导出")) },
                                                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                                    onClick = {
                                                        expanded = false
                                                        val m = MetadataManager.loadMetadata(file)
                                                        val pdf = FileExporter.exportToPdf(context, file, m)
                                                        if (pdf != null) FileExporter.shareFile(context, pdf)
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Trim".zh(context, "裁剪")) },
                                                    leadingIcon = { Icon(Icons.Default.ContentCut, contentDescription = null) },
                                                    onClick = {
                                                        expanded = false
                                                        fileToTrim = file
                                                        trimStart = "0"
                                                        trimEnd = "10"
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Tags".zh(context, "标签")) },
                                                    leadingIcon = { Icon(Icons.Default.Label, contentDescription = null) },
                                                    onClick = {
                                                        expanded = false
                                                        fileToTag = file
                                                        tagToAdd = ""
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Delete".zh(context, "删除"), color = MaterialTheme.colorScheme.error) },
                                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                                    onClick = {
                                                        expanded = false
                                                        if (currentFile == file) {
                                                            audioPlayer.stop()
                                                        }
                                                        val txtFile = File(file.parentFile, "${file.nameWithoutExtension}.txt")
                                                        if (txtFile.exists()) txtFile.delete()
                                                        val jsonFile = File(file.parentFile, "${file.nameWithoutExtension}.json")
                                                        if (jsonFile.exists()) jsonFile.delete()
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

    if (fileToTag != null) {
        AlertDialog(
            onDismissRequest = { fileToTag = null },
            icon = { Icon(Icons.Default.Label, contentDescription = null) },
            title = { Text("Manage Tags".zh(context, "管理标签")) },
            text = {
                val currentMeta = MetadataManager.loadMetadata(fileToTag!!)
                Column {
                    FlowRow(modifier = Modifier.fillMaxWidth()) {
                        currentMeta.tags.forEach { tag ->
                            InputChip(
                                selected = false,
                                onClick = { 
                                    val newTags = currentMeta.tags.filter { it != tag }
                                    MetadataManager.saveMetadata(fileToTag!!, currentMeta.copy(tags = newTags))
                                    recordings = audioRecorder.getRecordings() // Force refresh
                                },
                                label = { Text(tag) },
                                trailingIcon = { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = tagToAdd,
                        onValueChange = { tagToAdd = it },
                        label = { Text("Add Tag".zh(context, "添加新标签")) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (tagToAdd.isNotBlank()) {
                        val currentMeta = MetadataManager.loadMetadata(fileToTag!!)
                        if (!currentMeta.tags.contains(tagToAdd)) {
                            val newTags = currentMeta.tags + tagToAdd
                            MetadataManager.saveMetadata(fileToTag!!, currentMeta.copy(tags = newTags))
                            recordings = audioRecorder.getRecordings()
                        }
                    }
                    fileToTag = null
                }) {
                    Text("Done".zh(context, "完成"))
                }
            }
        )
    }
}

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.FlowRow

private fun formatTime(millis: Int): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

@Composable
fun WaveformVisualizer(amplitude: Float, isPaused: Boolean) {
    val barCount = 32
    val bars = remember { mutableStateListOf<Float>().apply { repeat(barCount) { add(0.1f) } } }
    
    LaunchedEffect(amplitude) {
        if (!isPaused) {
            bars.removeAt(0)
            val normalized = (amplitude / 32768f).coerceIn(0.1f, 1f)
            bars.add(normalized)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth(0.8f)
            .height(64.dp)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        bars.forEach { barHeight ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(barHeight)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(
                        if (isPaused) MaterialTheme.colorScheme.outlineVariant 
                        else MaterialTheme.colorScheme.primary
                    )
            )
        }
    }
}

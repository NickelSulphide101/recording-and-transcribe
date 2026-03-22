package com.example.recordingandtranscribe.core

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import java.io.File

class AudioRecorder(private val context: Context) {

    fun startRecording() {
        val intent = Intent(context, AudioRecorderService::class.java).apply {
            action = AudioRecorderService.ACTION_START
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun stopRecording() {
        val intent = Intent(context, AudioRecorderService::class.java).apply {
            action = AudioRecorderService.ACTION_STOP
        }
        context.startService(intent)
    }
    
    fun pauseRecording() {
        val intent = Intent(context, AudioRecorderService::class.java).apply {
            action = AudioRecorderService.ACTION_PAUSE
        }
        context.startService(intent)
    }
    
    fun resumeRecording() {
        val intent = Intent(context, AudioRecorderService::class.java).apply {
            action = AudioRecorderService.ACTION_RESUME
        }
        context.startService(intent)
    }

    fun getRecordings(): List<File> {
        val files = context.filesDir.listFiles { _, name -> name.endsWith(".ogg") || name.endsWith(".m4a") }
        return files?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
}

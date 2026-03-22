package com.example.recordingandtranscribe.core

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    var currentOutputFile: File? = null
        private set

    fun startRecording(): File? {
        val fileName = "REC_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.ogg"
        val outputFile = File(context.filesDir, fileName)
        currentOutputFile = outputFile

        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.OGG)
            setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
            setAudioEncodingBitRate(16000)
            setAudioSamplingRate(16000)
            setOutputFile(outputFile.absolutePath)
            
            try {
                prepare()
                start()
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        }
        return outputFile
    }

    fun stopRecording() {
        try {
            recorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            recorder = null
        }
    }

    fun getRecordings(): List<File> {
        val files = context.filesDir.listFiles { _, name -> name.endsWith(".ogg") || name.endsWith(".m4a") }
        return files?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
}

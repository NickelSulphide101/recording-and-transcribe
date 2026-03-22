package com.example.recordingandtranscribe.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.recordingandtranscribe.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AudioRecorderService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val ACTION_STOP = "ACTION_STOP"

        private const val CHANNEL_ID = "audio_record_channel"
        private const val NOTIFICATION_ID = 1

        private val _isRecording = MutableStateFlow(false)
        val isRecording = _isRecording.asStateFlow()

        private val _isPaused = MutableStateFlow(false)
        val isPaused = _isPaused.asStateFlow()

        var currentFile: File? = null
            private set
    }

    private var recorder: MediaRecorder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_PAUSE -> pauseRecording()
            ACTION_RESUME -> resumeRecording()
            ACTION_STOP -> stopRecording()
        }
        return START_NOT_STICKY
    }

    private fun startRecording() {
        if (_isRecording.value) return

        val fileName = "REC_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.ogg"
        val outputFile = File(filesDir, fileName)
        currentFile = outputFile

        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
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
                _isRecording.value = true
                _isPaused.value = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, buildNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                } else {
                    startForeground(NOTIFICATION_ID, buildNotification())
                }
            } catch (e: Exception) {
                e.printStackTrace()
                stopSelf()
            }
        }
    }

    private fun pauseRecording() {
        if (_isRecording.value && !_isPaused.value) {
            try {
                recorder?.pause()
                _isPaused.value = true
                updateNotification()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun resumeRecording() {
        if (_isRecording.value && _isPaused.value) {
            try {
                recorder?.resume()
                _isPaused.value = false
                updateNotification()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun stopRecording() {
        try {
            recorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            recorder = null
            _isRecording.value = false
            _isPaused.value = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun updateNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingMainIntent = PendingIntent.getActivity(
            this, 0, mainIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, AudioRecorderService::class.java).apply { action = ACTION_STOP }
        val pendingStopIntent = PendingIntent.getService(
            this, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseResumeIntent = Intent(this, AudioRecorderService::class.java).apply {
            action = if (_isPaused.value) ACTION_RESUME else ACTION_PAUSE
        }
        val pendingPauseResumeIntent = PendingIntent.getService(
            this, 2, pauseResumeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseResumeAction = NotificationCompat.Action(
            if (_isPaused.value) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
            if (_isPaused.value) "Resume" else "Pause",
            pendingPauseResumeIntent
        )

        val stopAction = NotificationCompat.Action(
            android.R.drawable.ic_media_stop,
            "Stop",
            pendingStopIntent
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (_isPaused.value) "Recording Paused" else "Recording Audio...")
            .setContentText("Tap to open app")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingMainIntent)
            .addAction(pauseResumeAction)
            .addAction(stopAction)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Recording Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows recording controls in the notification area"
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

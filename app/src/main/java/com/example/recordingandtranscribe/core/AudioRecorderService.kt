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
import androidx.media.app.NotificationCompat.MediaStyle
import com.example.recordingandtranscribe.MainActivity
import com.example.recordingandtranscribe.core.zh
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Timer
import kotlin.concurrent.timerTask

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

        private val _amplitude = MutableStateFlow(0f)
        val amplitude = _amplitude.asStateFlow()

        private val _liveTranscript = MutableStateFlow("")
        val liveTranscript = _liveTranscript.asStateFlow()

        var currentFile: File? = null
            private set
    }

    private var recorder: MediaRecorder? = null
    private var speechRecognizer: android.speech.SpeechRecognizer? = null
    private var amplitudeTimer: Timer? = null
    private var silenceStartTime: Long = 0
    private var skipSilenceEnabled = false
    private var isAutoPaused = false
    private var isLocalMode = false
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_PAUSE -> {
                isAutoPaused = false
                pauseRecording()
            }
            ACTION_RESUME -> {
                isAutoPaused = false
                resumeRecording()
            }
            ACTION_STOP -> stopRecording()
        }
        return START_NOT_STICKY
    }

    private fun startRecording() {
        if (_isRecording.value) return

        startForeground(NOTIFICATION_ID, buildNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)

        val settings = SettingsRepository(this)
        
        serviceScope.launch(Dispatchers.IO) {
            val bitrate = settings.bitrateFlow.first()
            skipSilenceEnabled = settings.skipSilenceFlow.first()
            isLocalMode = settings.useGeminiNanoFlow.first()
            
            val fileName = "REC_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.ogg"
            val outputFile = File(filesDir, fileName)
            currentFile = outputFile

            launch(Dispatchers.Main) {
                _liveTranscript.value = ""
                finalizedTranscript = ""
                startSpeechRecognition()
            }
                
            recorder = MediaRecorder(this@AudioRecorderService).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.OGG)
                setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
                setAudioEncodingBitRate(bitrate)
                setAudioSamplingRate(16000)
                setOutputFile(outputFile.absolutePath)

                try {
                    prepare()
                    start()
                    launch(Dispatchers.Main) {
                        _isRecording.value = true
                        _isPaused.value = false
                        isAutoPaused = false
                        startAmplitudeMonitoring()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    stopSelf()
                }
            }
        }
    }

    private fun pauseRecording() {
        if (_isRecording.value && !_isPaused.value) {
            try {
                recorder?.pause()
                _isPaused.value = true
                speechRecognizer?.stopListening()
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
                speechRecognizer?.startListening(getSpeechRecognizerIntent())
                updateNotification()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun startAmplitudeMonitoring() {
        amplitudeTimer?.cancel()
        amplitudeTimer = Timer()
        amplitudeTimer?.scheduleAtFixedRate(timerTask {
            if (_isRecording.value) {
                try {
                    // We can't get amplitude when paused on some Android versions, 
                    // but on many we still can. If not, this is a best-effort.
                    val amp = recorder?.maxAmplitude?.toFloat() ?: 0f
                    _amplitude.value = amp

                    if (!_isPaused.value) {
                        // Basic Silence Skipping
                        if (amp < 500f) {
                            if (silenceStartTime == 0L) silenceStartTime = System.currentTimeMillis()
                            if (skipSilenceEnabled && System.currentTimeMillis() - silenceStartTime > 3000) {
                                // Automatically pause if silent for > 3 seconds
                                serviceScope.launch(Dispatchers.Main) {
                                    isAutoPaused = true
                                    pauseRecording()
                                }
                                silenceStartTime = 0
                            }
                        } else {
                            silenceStartTime = 0
                        }
                    } else if (isAutoPaused && amp > 1000f) {
                        // Auto-resume if sound detected
                        serviceScope.launch(Dispatchers.Main) {
                            isAutoPaused = false
                            resumeRecording()
                        }
                    }
                } catch (e: Exception) {}
            }
        }, 0, 100)
    }

    private fun stopAmplitudeMonitoring() {
        amplitudeTimer?.cancel()
        amplitudeTimer = null
        _amplitude.value = 0f
        silenceStartTime = 0
    }

    private fun stopRecording() {
        if (!_isRecording.value) return
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
            speechRecognizer = null
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
            stopAmplitudeMonitoring()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private var finalizedTranscript = ""

    private fun getSpeechRecognizerIntent(): Intent {
        return Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            if (isLocalMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                putExtra(android.speech.RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }
    }

    private fun startSpeechRecognition() {
        if (!android.speech.SpeechRecognizer.isRecognitionAvailable(this)) return
        
        speechRecognizer = android.speech.SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : android.speech.RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    if (error == android.speech.SpeechRecognizer.ERROR_NO_MATCH || error == android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        // Restart if it times out, but with a delay to optimize performance during prolonged silence
                        if (_isRecording.value && !_isPaused.value) {
                            serviceScope.launch {
                                kotlinx.coroutines.delay(1000)
                                if (_isRecording.value && !_isPaused.value) {
                                    startListening(getSpeechRecognizerIntent())
                                }
                            }
                        }
                    }
                }
                override fun onResults(results: android.os.Bundle?) {
                    val matches = results?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        finalizedTranscript += (if (finalizedTranscript.isEmpty()) "" else " ") + matches[0]
                        _liveTranscript.value = finalizedTranscript
                    }
                    // Restart listening for continuous flow
                    if (_isRecording.value && !_isPaused.value) {
                        startListening(getSpeechRecognizerIntent())
                    }
                }
                override fun onPartialResults(partialResults: android.os.Bundle?) {
                    val matches = partialResults?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        _liveTranscript.value = finalizedTranscript + (if (finalizedTranscript.isEmpty()) "" else " ") + matches[0]
                    }
                }
                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            })
            
            startListening(getSpeechRecognizerIntent())
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

        val pauseResumeIcon = if (_isPaused.value) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause
        val pauseResumeTitle = if (_isPaused.value) "Resume".zh(this, "继续") else "Pause".zh(this, "暂停")

        val pauseResumeAction = NotificationCompat.Action.Builder(
            pauseResumeIcon,
            pauseResumeTitle,
            pendingPauseResumeIntent
        ).build()

        val stopAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Stop".zh(this, "停止并保存"),
            pendingStopIntent
        ).build()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (_isPaused.value) "Recording Paused".zh(this, "录音已暂停") else "Recording Audio...".zh(this, "正在录制音频..."))
            .setContentText("Tap to open app".zh(this, "点击打开应用"))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingMainIntent)
            .addAction(pauseResumeAction)
            .addAction(stopAction)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setStyle(MediaStyle().setShowActionsInCompactView(0, 1))
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

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

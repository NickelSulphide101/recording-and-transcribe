package com.example.recordingandtranscribe.core

import android.media.MediaPlayer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class AudioPlayer {
    private var mediaPlayer: MediaPlayer? = null
    var currentFile: File? = null
        private set

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _progress = MutableStateFlow(0)
    val progress = _progress.asStateFlow()

    private val _duration = MutableStateFlow(0)
    val duration = _duration.asStateFlow()

    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    fun playFile(file: File, onCompletion: () -> Unit = {}) {
        stop()
        currentFile = file
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    _isPlaying.value = false
                    stopProgressUpdate()
                    onCompletion()
                }
                prepare()
                _duration.value = duration
                start()
                _isPlaying.value = true
                startProgressUpdate()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun pause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                _isPlaying.value = false
                stopProgressUpdate()
            }
        }
    }

    fun resume() {
        mediaPlayer?.let {
            if (!it.isPlaying) {
                it.start()
                _isPlaying.value = true
                startProgressUpdate()
            }
        }
    }

    fun togglePlayPause() {
        if (_isPlaying.value) pause() else resume()
    }

    fun seekRelative(millis: Int) {
        mediaPlayer?.let {
            val current = it.currentPosition
            val newPos = (current + millis).coerceIn(0, it.duration)
            it.seekTo(newPos)
            _progress.value = newPos
        }
    }

    fun seekTo(position: Int) {
        mediaPlayer?.let {
            val newPos = position.coerceIn(0, it.duration)
            it.seekTo(newPos)
            _progress.value = newPos
        }
    }

    fun stop() {
        mediaPlayer?.release()
        mediaPlayer = null
        currentFile = null
        _isPlaying.value = false
        _progress.value = 0
        _duration.value = 0
        stopProgressUpdate()
    }

    private fun startProgressUpdate() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive && _isPlaying.value) {
                mediaPlayer?.let {
                    _progress.value = it.currentPosition
                }
                delay(100)
            }
        }
    }

    private fun stopProgressUpdate() {
        progressJob?.cancel()
    }
}

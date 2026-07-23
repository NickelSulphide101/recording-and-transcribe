package com.example.recordingandtranscribe.core

import android.media.MediaPlayer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class AudioPlayer {
    private var mediaPlayer: MediaPlayer? = null
    private val _currentFile = MutableStateFlow<File?>(null)
    val currentFile = _currentFile.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _progress = MutableStateFlow(0)
    val progress = _progress.asStateFlow()

    private val _duration = MutableStateFlow(0)
    val duration = _duration.asStateFlow()

    private var progressJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun playFile(file: File, onCompletion: () -> Unit = {}) {
        stop()
        _currentFile.value = file
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    _isPlaying.value = false
                    stopProgressUpdate()
                    onCompletion()
                }
                setOnPreparedListener { mp ->
                    try {
                        _duration.value = mp.duration
                        mp.start()
                        _isPlaying.value = true
                        startProgressUpdate()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        stop()
                    }
                }
                setOnErrorListener { _, _, _ ->
                    stop()
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            stop()
        }
    }

    fun pause() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.pause()
                    _isPlaying.value = false
                    stopProgressUpdate()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _isPlaying.value = false
            stopProgressUpdate()
        }
    }

    fun resume() {
        try {
            mediaPlayer?.let {
                if (!it.isPlaying) {
                    it.start()
                    _isPlaying.value = true
                    startProgressUpdate()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun togglePlayPause() {
        if (_isPlaying.value) pause() else resume()
    }

    fun seekRelative(millis: Int) {
        try {
            mediaPlayer?.let {
                val current = it.currentPosition
                val newPos = (current + millis).coerceIn(0, it.duration)
                it.seekTo(newPos)
                _progress.value = newPos
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun seekTo(position: Int) {
        try {
            mediaPlayer?.let {
                val newPos = position.coerceIn(0, it.duration)
                it.seekTo(newPos)
                _progress.value = newPos
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stop() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaPlayer = null
            _currentFile.value = null
            _isPlaying.value = false
            _progress.value = 0
            _duration.value = 0
            stopProgressUpdate()
        }
    }

    private fun startProgressUpdate() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive && _isPlaying.value) {
                try {
                    mediaPlayer?.let {
                        if (it.isPlaying) {
                            _progress.value = it.currentPosition
                        }
                    }
                } catch (e: Exception) {
                    // Ignore transient exceptions if player is stopping/releasing
                }
                delay(100)
            }
        }
    }

    private fun stopProgressUpdate() {
        progressJob?.cancel()
    }

    fun release() {
        stop()
        scope.coroutineContext.cancelChildren()
    }
}

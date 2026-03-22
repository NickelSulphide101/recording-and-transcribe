package com.example.recordingandtranscribe.core

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class RecordingTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val isRecording = AudioRecorderService.isRecording.value
        
        tile.state = if (isRecording) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = if (isRecording) "Stop Recording".zh(this, "停止录音") else "Start Recording".zh(this, "开始录音")
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val isRecording = AudioRecorderService.isRecording.value
        val intent = Intent(this, AudioRecorderService::class.java).apply {
            action = if (isRecording) AudioRecorderService.ACTION_STOP else AudioRecorderService.ACTION_START
        }
        
        if (isRecording) {
            startService(intent)
        } else {
            // Check permissions or just start (Service will handle if it can)
            startForegroundService(intent)
        }
        
        // Give it a moment to update state
        CoroutineScope(Dispatchers.Main).launch {
            kotlinx.coroutines.delay(500)
            updateTileState()
        }
    }
}

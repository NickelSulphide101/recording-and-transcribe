package com.example.recordingandtranscribe.core

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.recordingandtranscribe.R

class RecordingWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // Refresh all widgets when recording state changes
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, RecordingWidgetProvider::class.java))
        for (id in ids) {
            updateAppWidget(context, manager, id)
        }
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.recording_widget)
        val isRecording = AudioRecorderService.isRecording.value

        val intent = Intent(context, AudioRecorderService::class.java).apply {
            action = if (isRecording) AudioRecorderService.ACTION_STOP else AudioRecorderService.ACTION_START
        }
        val pendingIntent = if (!isRecording && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(
                context, appWidgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getService(
                context, appWidgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        views.setOnClickPendingIntent(R.id.widget_icon, pendingIntent)
        views.setOnClickPendingIntent(R.id.widget_text, pendingIntent)
        
        views.setTextViewText(R.id.widget_text, if (isRecording) "Stop Recording".zh(context, "停止录音") else "Start Recording".zh(context, "开始录制"))
        
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}

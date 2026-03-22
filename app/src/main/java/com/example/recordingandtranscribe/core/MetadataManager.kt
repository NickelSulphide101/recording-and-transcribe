package com.example.recordingandtranscribe.core

import android.util.Log
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

object MetadataManager {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun saveMetadata(audioFile: File, metadata: RecordingMetadata) {
        try {
            val jsonFile = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.json")
            val content = json.encodeToString(metadata)
            jsonFile.writeText(content)
        } catch (e: Exception) {
            Log.e("MetadataManager", "Error saving metadata: ${e.message}")
        }
    }

    fun loadMetadata(audioFile: File): RecordingMetadata {
        return try {
            val jsonFile = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.json")
            if (jsonFile.exists()) {
                json.decodeFromString<RecordingMetadata>(jsonFile.readText())
            } else {
                RecordingMetadata()
            }
        } catch (e: Exception) {
            Log.e("MetadataManager", "Error loading metadata: ${e.message}")
            RecordingMetadata()
        }
    }
}

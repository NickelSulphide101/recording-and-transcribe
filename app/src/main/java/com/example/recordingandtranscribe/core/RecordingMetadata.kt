package com.example.recordingandtranscribe.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class RecordingMetadata(
    val transcript: String? = null,
    val summary: String? = null,
    val actionItems: List<String> = emptyList(),
    val speakerLabels: String? = null,
    val keywords: List<String> = emptyList(),
    val isFavorite: Boolean = false,
    val tags: List<String> = emptyList()
)

object MetadataManager {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun saveMetadata(audioFile: File, metadata: RecordingMetadata) {
        val metaFile = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.json")
        metaFile.writeText(json.encodeToString(RecordingMetadata.serializer(), metadata))
    }

    fun loadMetadata(audioFile: File): RecordingMetadata {
        val metaFile = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.json")
        if (!metaFile.exists()) {
            // Check for legacy .txt file
            val txtFile = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.txt")
            if (txtFile.exists()) {
                return RecordingMetadata(transcript = txtFile.readText())
            }
            return RecordingMetadata()
        }
        return try {
            json.decodeFromString(RecordingMetadata.serializer(), metaFile.readText())
        } catch (e: Exception) {
            RecordingMetadata()
        }
    }
}

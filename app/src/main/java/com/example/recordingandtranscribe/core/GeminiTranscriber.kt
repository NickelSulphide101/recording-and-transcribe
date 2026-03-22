package com.example.recordingandtranscribe.core

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.BlobPart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class GeminiTranscriber(private val apiKeys: List<String>, private val modelName: String = "gemini-1.5-flash") {

    suspend fun transcribeAudio(audioFile: File): Result<String> = withContext(Dispatchers.IO) {
        val keysToTry = apiKeys.shuffled()
        var lastError: Exception? = null
        val audioBytes = audioFile.readBytes()
        val mimeType = if (audioFile.name.endsWith(".m4a")) "audio/mp4" else "audio/ogg"
        val prompt = "Please transcribe the following audio exactly as spoken. If there are multiple speakers, label them if possible. Just return the transcription."

        for (key in keysToTry) {
            try {
                val generativeModel = GenerativeModel(
                    modelName = modelName,
                    apiKey = key
                )
                val response = generativeModel.generateContent(
                    content {
                        blob(mimeType, audioBytes)
                        text(prompt)
                    }
                )
                val output = response.text
                if (output != null) {
                    return@withContext Result.success(output)
                }
            } catch (e: Exception) {
                lastError = e
            }
        }
        
        Result.failure(lastError ?: Exception("All provided API keys failed."))
    }
}

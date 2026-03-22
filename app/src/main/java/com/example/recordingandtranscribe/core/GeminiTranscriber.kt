package com.example.recordingandtranscribe.core

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.BlobPart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class GeminiTranscriber(private val apiKey: String) {
    private val generativeModel = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = apiKey
    )

    suspend fun transcribeAudio(audioFile: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            val audioBytes = audioFile.readBytes()
            // MIME type for m4a (MPEG-4 Audio) is audio/mp4
            val prompt = "Please transcribe the following audio exactly as spoken. If there are multiple speakers, label them if possible. Just return the transcription."
            
            val response = generativeModel.generateContent(
                content {
                    blob("audio/mp4", audioBytes)
                    text(prompt)
                }
            )
            val output = response.text
            if (output != null) {
                Result.success(output)
            } else {
                Result.failure(Exception("No output from Gemini"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

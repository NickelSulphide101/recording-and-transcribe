package com.example.recordingandtranscribe.core

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class GeminiTranscriber(private val apiKeys: List<String>, private val modelName: String = "gemini-1.5-flash") {

    /**
     * Core method to process audio with a specific prompt.
     * Rotates through API keys to handle rate limits or failures.
     */
    private suspend fun processAudio(audioFile: File, prompt: String): Result<String> = withContext(Dispatchers.IO) {
        val keysToTry = apiKeys.shuffled()
        var lastError: Exception? = null
        val audioBytes = audioFile.readBytes()
        val mimeType = if (audioFile.name.endsWith(".m4a")) "audio/mp4" else "audio/ogg"

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

    suspend fun transcribeAudio(audioFile: File): Result<String> {
        val prompt = "Please transcribe the following audio exactly as spoken. If there are multiple speakers, label them if possible (e.g., Speaker 1: ...). Just return the transcription."
        return processAudio(audioFile, prompt)
    }

    suspend fun generateSummary(audioFile: File): Result<String> {
        val prompt = "Please provide a concise summary of the following audio. Focus on the main topics and key points."
        return processAudio(audioFile, prompt)
    }

    suspend fun generateActionItems(audioFile: File): Result<List<String>> {
        val prompt = "Please identify any action items, tasks, or follow-ups mentioned in this audio. Format them as a simple list, one per line."
        return processAudio(audioFile, prompt).map { resultText ->
            resultText.lines().filter { it.trim().isNotEmpty() }.map { it.trim().removePrefix("- ").removePrefix("* ").trim() }
        }
    }

    suspend fun generateKeywords(audioFile: File): Result<List<String>> {
        val prompt = "Please list the top 5-10 important keywords or topics mentioned in this audio. Return them separated by commas."
        return processAudio(audioFile, prompt).map { resultText ->
            resultText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
    }
}

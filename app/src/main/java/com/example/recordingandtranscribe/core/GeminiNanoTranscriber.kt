package com.example.recordingandtranscribe.core

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.audio.AudioSource
import com.google.mlkit.genai.speechrecognition.SpeechRecognition
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerOptions
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerRequest
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerResponse
import com.google.mlkit.genai.speechrecognition.speechRecognizerOptions
import com.google.mlkit.genai.speechrecognition.speechRecognizerRequest
import com.google.mlkit.genai.summarization.Summarization
import com.google.mlkit.genai.summarization.SummarizationRequest
import com.google.mlkit.genai.summarization.SummarizationResult
import com.google.mlkit.genai.summarization.SummarizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Android 16 Gemini Nano (On-Device SLM) Integration.
 * Uses ML Kit Summarization and GenAI Speech Recognition APIs.
 */
class GeminiNanoTranscriber(private val context: Context) {
    
    private fun getSummarizerOptions() = SummarizerOptions.builder(context)
        .setOutputType(SummarizerOptions.OutputType.THREE_BULLETS)
        .build()

    /**
     * Generates a summary of the provided text using the on-device Gemini Nano model.
     */
    suspend fun generateOnDeviceSummary(text: String): Result<String> = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext Result.failure(Exception("Input text is empty"))
        
        return@withContext try {
            val summarizer = Summarization.getClient(getSummarizerOptions())
            
            // Check summarization feature status
            val status = summarizer.checkFeatureStatus().await()
            if (status != FeatureStatus.AVAILABLE) {
                Log.d("GeminiNano", "Summarization model not available (status: $status). Starting download...")
                summarizer.download().first {
                    it is DownloadStatus.DownloadCompleted || it is DownloadStatus.DownloadFailed
                }
                // Re-check after download
                val postDownloadStatus = summarizer.checkFeatureStatus().await()
                if (postDownloadStatus != FeatureStatus.AVAILABLE) {
                    return@withContext Result.failure(Exception("Summarization model download failed or model still not available (Status: $postDownloadStatus)."))
                }
            }
            
            val request = SummarizationRequest.builder(text).build()
            val result = summarizer.runInference(request).await()
            Result.success(result.summary)
        } catch (e: Exception) {
            Log.e("GeminiNano", "On-device summarization error: ${e.message}")
            Result.failure(Exception("Summarization error: ${e.message}"))
        }
    }
    
    /**
     * Transcribes an audio file on-device using the GenAI-powered Speech Recognition API.
     */
    suspend fun transcribeOnDevice(audioFile: File): Result<String> = withContext(Dispatchers.IO) {
        return@withContext try {
            val options = speechRecognizerOptions {
                locale = Locale.getDefault()
                preferredMode = SpeechRecognizerOptions.Mode.MODE_ADVANCED
            }
            val recognizer = SpeechRecognition.getClient(options)
            
            // Check speech recognition status
            val status = recognizer.checkStatus()
            if (status != FeatureStatus.AVAILABLE) {
                Log.d("GeminiNano", "Speech GenAI model not available (status: $status). Starting download...")
                
                // Wait for terminal state in download flow
                val terminalStatus = recognizer.download().first {
                    it is DownloadStatus.DownloadCompleted || it is DownloadStatus.DownloadFailed
                }

                if (terminalStatus is DownloadStatus.DownloadFailed) {
                    return@withContext Result.failure(Exception("Failed to download Speech GenAI model: ${terminalStatus.e.message}. Please ensure you have a stable internet connection."))
                }
                
                // Double check final status
                val finalStatus = recognizer.checkStatus()
                if (finalStatus != FeatureStatus.AVAILABLE) {
                    return@withContext Result.failure(Exception("Speech model download finished but status is still $finalStatus."))
                }
            }

            val pfd = ParcelFileDescriptor.open(audioFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val request = speechRecognizerRequest {
                audioSource = AudioSource.fromPfd(pfd)
            }
            
            var finalTranscript = ""
            var error: Exception? = null
            
            // Start recognition flow
            recognizer.startRecognition(request)
                .takeWhile { response ->
                    when (response) {
                        is SpeechRecognizerResponse.ErrorResponse -> {
                            error = response.e
                            false
                        }
                        is SpeechRecognizerResponse.CompletedResponse -> false
                        else -> true
                    }
                }
                .collect { response ->
                    when (response) {
                        is SpeechRecognizerResponse.FinalTextResponse -> finalTranscript = response.text
                        is SpeechRecognizerResponse.PartialTextResponse -> finalTranscript = response.text
                        else -> {}
                    }
                }
            
            recognizer.stopRecognition()
            recognizer.close()
            
            if (error != null) {
                Result.failure(error!!)
            } else if (finalTranscript.isNotEmpty()) {
                Result.success(finalTranscript)
            } else {
                Result.failure(Exception("No transcription result received from on-device model."))
            }
        } catch (e: Exception) {
            Log.e("GeminiNano", "On-device transcription error: ${e.message}")
            Result.failure(Exception("Transcription error: ${e.message}"))
        }
    }
}

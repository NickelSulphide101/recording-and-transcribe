package com.example.recordingandtranscribe.core

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.genai.common.DownloadCallback
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
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
                summarizer.downloadFeature(object : DownloadCallback {
                    override fun onDownloadStarted(bytesToDownload: Long) {}
                    override fun onDownloadProgress(totalBytesDownloaded: Long) {}
                    override fun onDownloadCompleted() {}
                    override fun onDownloadFailed(e: GenAiException) {}
                }).await()
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
     * Implements a fallback mechanism: Advanced (Gemini Nano) -> Basic (Traditional On-Device).
     */
    suspend fun transcribeOnDevice(audioFile: File): Result<String> = withContext(Dispatchers.IO) {
        val configsToTry = listOf(
            // 1. Try Advanced mode with system locale (The premium Gemini Nano experience)
            speechRecognizerOptions {
                locale = Locale.getDefault()
                preferredMode = SpeechRecognizerOptions.Mode.MODE_ADVANCED
            },
            // 2. Fallback to Basic mode with system locale (High quality, broader device support)
            speechRecognizerOptions {
                locale = Locale.getDefault()
                preferredMode = SpeechRecognizerOptions.Mode.MODE_BASIC
            },
            // 3. Last resort: Basic mode with English (US)
            speechRecognizerOptions {
                locale = Locale.US
                preferredMode = SpeechRecognizerOptions.Mode.MODE_BASIC
            }
        )

        var lastError: String? = null

        for (options in configsToTry) {
            try {
                val recognizer = SpeechRecognition.getClient(options)
                val status = recognizer.checkStatus()
                
                if (status == FeatureStatus.UNAVAILABLE) {
                    Log.d("GeminiNano", "Mode ${options.preferredMode} with ${options.locale} is unavailable. Trying next config...")
                    continue
                }

                if (status != FeatureStatus.AVAILABLE) {
                    Log.d("GeminiNano", "Model download needed for ${options.preferredMode}. Starting...")
                    val terminalStatus = recognizer.download().first {
                        it is DownloadStatus.DownloadCompleted || it is DownloadStatus.DownloadFailed
                    }

                    if (terminalStatus is DownloadStatus.DownloadFailed) {
                        lastError = "Download failed: ${terminalStatus.e.message}"
                        Log.w("GeminiNano", "Download failed for ${options.preferredMode}: $lastError")
                        continue
                    }
                }

                // If we reach here, model is available or downloaded successfully
                val pfd = ParcelFileDescriptor.open(audioFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val request = speechRecognizerRequest {
                    audioSource = AudioSource.fromPfd(pfd)
                }
                
                var finalTranscript = ""
                var recognitionError: Exception? = null
                
                recognizer.startRecognition(request)
                    .takeWhile { response ->
                        when (response) {
                            is SpeechRecognizerResponse.ErrorResponse -> {
                                recognitionError = response.e
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
                
                if (recognitionError != null) {
                    lastError = recognitionError?.message
                    continue
                }

                if (finalTranscript.isNotEmpty()) {
                    Log.d("GeminiNano", "Transcription successful using ${options.preferredMode} mode.")
                    return@withContext Result.success(finalTranscript)
                } else {
                    lastError = "Empty result"
                }

            } catch (e: Exception) {
                lastError = e.message
                Log.e("GeminiNano", "Error with config ${options.preferredMode}: $lastError")
            }
        }

        Result.failure(Exception("On-device transcription failed even after fallbacks. Last error: $lastError. This device might not support the required AI models."))
    }
}

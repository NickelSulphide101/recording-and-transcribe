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
        
        val summarizer = Summarization.getClient(getSummarizerOptions())
        return@withContext try {
            // Check summarization feature status
            val status = summarizer.checkFeatureStatus().await()
            if (status != FeatureStatus.AVAILABLE) {
                Log.d("GeminiNano", "Summarization model not available (status: $status). Starting download...")
                val conditions = DownloadConditions.Builder().requireWifi().build()
                summarizer.downloadFeature(conditions, object : DownloadCallback {
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
        } finally {
            summarizer.close()
        }
    }
    
    /**
     * Transcribes an audio file on-device using the GenAI-powered Speech Recognition API.
     * Implements a fallback mechanism: Advanced (Gemini Nano) -> Basic (Traditional On-Device).
     */
    suspend fun transcribeOnDevice(audioFile: File): Result<String> = withContext(Dispatchers.IO) {
        if (!audioFile.exists() || !audioFile.canRead()) {
            return@withContext Result.failure(Exception("Audio file is missing or unreadable."))
        }
        val configsToTry = listOf(
            // 1. Try Advanced mode with system locale
            SpeechRecognizerOptions.Mode.MODE_ADVANCED to Locale.getDefault(),
            // 2. Try Advanced mode with explicit Simplified Chinese (cmn-Hans-CN)
            SpeechRecognizerOptions.Mode.MODE_ADVANCED to Locale.SIMPLIFIED_CHINESE,
            SpeechRecognizerOptions.Mode.MODE_ADVANCED to Locale.forLanguageTag("zh-CN"),
            SpeechRecognizerOptions.Mode.MODE_ADVANCED to Locale.forLanguageTag("zh-Hans-CN"),
            
            // 3. Fallback to Basic mode with system locale
            SpeechRecognizerOptions.Mode.MODE_BASIC to Locale.getDefault(),
            // 4. Fallback to Basic mode with Simplified Chinese
            SpeechRecognizerOptions.Mode.MODE_BASIC to Locale.SIMPLIFIED_CHINESE,
            
            // 5. Last resort: Basic mode with English (US)
            SpeechRecognizerOptions.Mode.MODE_BASIC to Locale.US
        )

        var lastError: String? = null
        
        continue_loop@for ((mode, locale) in configsToTry) {
            try {
                val options = speechRecognizerOptions {
                    this.locale = locale
                    this.preferredMode = mode
                }
                val recognizer = SpeechRecognition.getClient(options)
                try {
                    val status = recognizer.checkStatus()
                    
                    if (status == FeatureStatus.UNAVAILABLE) {
                        Log.d("GeminiNano", "Mode $mode with $locale is unavailable. Trying next config...")
                        continue
                    }

                    if (status != FeatureStatus.AVAILABLE) {
                        Log.d("GeminiNano", "Model download needed for $mode. Starting...")
                        val conditions = DownloadConditions.Builder().requireWifi().build()
                        val terminalStatus = recognizer.download(conditions).first {
                            it is DownloadStatus.DownloadCompleted || it is DownloadStatus.DownloadFailed
                        }

                        if (terminalStatus is DownloadStatus.DownloadFailed) {
                            lastError = "Download failed: ${terminalStatus.e.message}"
                            Log.w("GeminiNano", "Download failed for $mode: $lastError")
                            continue
                        }
                    }

                    // If we reach here, model is available or downloaded successfully
                    ParcelFileDescriptor.open(audioFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                        val request = speechRecognizerRequest {
                            audioSource = AudioSource.fromPfd(pfd)
                        }
                        
                        var finalTranscript = ""
                        var accumulatedText = ""
                        var currentPartial = ""
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
                                    is SpeechRecognizerResponse.FinalTextResponse -> {
                                        accumulatedText += response.text + " "
                                        currentPartial = ""
                                        finalTranscript = accumulatedText
                                    }
                                    is SpeechRecognizerResponse.PartialTextResponse -> {
                                        currentPartial = response.text
                                        finalTranscript = accumulatedText + currentPartial
                                    }
                                    else -> {}
                                }
                            }
                        
                        recognizer.stopRecognition()
                        
                        if (recognitionError != null) {
                            lastError = recognitionError?.message
                        } else {
                            Log.d("GeminiNano", "Transcription successful using $mode mode.")
                            return@withContext Result.success(finalTranscript.trim())
                        }
                    }
                } finally {
                    recognizer.close()
                }

            } catch (e: Exception) {
                lastError = e.message
                Log.e("GeminiNano", "Error with config $mode: $lastError")
            }
        }

        Result.failure(Exception("On-device transcription failed even after fallbacks. Last error: $lastError. This device might not support the required AI models."))
    }
}

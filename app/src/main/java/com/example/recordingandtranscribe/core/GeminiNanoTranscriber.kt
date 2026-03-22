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
            
            // checkFeatureStatus returns ListenableFuture<Int> in GenAI Summarization
            val status = summarizer.checkFeatureStatus().await()
            if (status != FeatureStatus.AVAILABLE) {
                // Simplified handle
            }
            
            val request = SummarizationRequest.builder(text).build()
            val result = summarizer.runInference(request).await()
            Result.success(result.summary)
        } catch (e: Exception) {
            Log.e("GeminiNano", "On-device summarization error: ${e.message}")
            Result.failure(e)
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
            
            // Check status (suspend fun, NO .await())
            val status = recognizer.checkStatus()
            if (status == FeatureStatus.DOWNLOADABLE || status == FeatureStatus.UNAVAILABLE) {
                var downloadSuccess = false
                recognizer.download().collect { downloadStatus ->
                    if (downloadStatus is DownloadStatus.DownloadCompleted) {
                        downloadSuccess = true
                    }
                }
                if (!downloadSuccess) return@withContext Result.failure(Exception("Failed to download Speech GenAI model"))
            }

            val pfd = ParcelFileDescriptor.open(audioFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val request = speechRecognizerRequest {
                audioSource = AudioSource.fromPfd(pfd)
            }
            
            var finalTranscript = ""
            recognizer.startRecognition(request).collect { response ->
                when (response) {
                    is SpeechRecognizerResponse.FinalTextResponse -> {
                        finalTranscript = response.text
                    }
                    is SpeechRecognizerResponse.PartialTextResponse -> {
                        // Intermediate results
                        finalTranscript = response.text
                    }
                    is SpeechRecognizerResponse.CompletedResponse -> {
                        Log.i("GeminiNano", "Recognition completed")
                    }
                    is SpeechRecognizerResponse.ErrorResponse -> {
                        Log.e("GeminiNano", "Recognition error: ${response.e.message}")
                    }
                    else -> {
                        // Handle other possible responses
                    }
                }
            }
            
            recognizer.stopRecognition()
            recognizer.close()
            
            if (finalTranscript.isNotEmpty()) {
                Result.success(finalTranscript)
            } else {
                Result.failure(Exception("No transcription result received"))
            }
        } catch (e: Exception) {
            Log.e("GeminiNano", "On-device transcription error: ${e.message}")
            Result.failure(e)
        }
    }
}

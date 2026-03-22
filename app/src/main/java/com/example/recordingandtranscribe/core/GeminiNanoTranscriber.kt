import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.genai.speechrecognition.AudioSource
import com.google.mlkit.genai.speechrecognition.DownloadStatus
import com.google.mlkit.genai.speechrecognition.FeatureStatus
import com.google.mlkit.genai.speechrecognition.SpeechRecognition
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerOptions
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerRequest
import com.google.mlkit.genai.speechrecognition.speechRecognizerOptions
import com.google.mlkit.genai.speechrecognition.speechRecognizerRequest
import com.google.mlkit.nl.summarization.Summarization
import com.google.mlkit.nl.summarization.SummarizationRequest
import com.google.mlkit.nl.summarization.SummarizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Android 16 Gemini Nano (On-Device SLM) Integration.
 * Uses ML Kit Summarization and GenAI Speech Recognition APIs.
 */
class GeminiNanoTranscriber(private val context: Context) {
    
    private val summarizerOptions = SummarizerOptions.Builder()
        .setSummaryStyle(SummarizerOptions.Style.BULLET_POINTS)
        .setSummaryLength(SummarizerOptions.Length.MEDIUM)
        .build()

    /**
     * Generates a summary of the provided text using the on-device Gemini Nano model.
     */
    suspend fun generateOnDeviceSummary(text: String): Result<String> = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext Result.failure(Exception("Input text is empty"))
        
        return@withContext try {
            val summarizer = Summarization.getClient(summarizerOptions)
            
            // Ensure model is downloaded
            val conditions = DownloadConditions.Builder()
                .requireWifi()
                .build()
            
            summarizer.downloadModelIfNeeded(conditions).await()
            
            val request = SummarizationRequest.Builder(text).build()
            val result = summarizer.runInference(request).await()
            Result.success(result)
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
            
            // Check and download model if needed
            val status = recognizer.checkStatus().await()
            if (status == FeatureStatus.DOWNLOADABLE || status == FeatureStatus.UNAVAILABLE) {
                // In a production app, we might want to track progress, but for simplicity we'll just wait for completion.
                // Note: download is a Flow in ML Kit
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
                finalTranscript = response.text ?: ""
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

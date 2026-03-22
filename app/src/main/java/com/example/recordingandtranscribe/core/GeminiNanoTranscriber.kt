import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.summarization.SummarizationService
import com.google.mlkit.nl.summarization.Summarizer
import com.google.mlkit.nl.summarization.SummarizerOptions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

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
            val summarizer = SummarizationService.getClient(summarizerOptions)
            
            // Ensure model is downloaded
            val conditions = DownloadConditions.Builder()
                .requireWifi()
                .build()
            
            summarizer.downloadModelIfNeeded(conditions).await()
            
            val result = summarizer.summarize(text).await()
            Result.success(result)
        } catch (e: Exception) {
            Log.e("GeminiNano", "On-device summarization error: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Transcribes an audio file on-device using the GenAI-powered Speech Recognition API.
     * Note: This uses the Android SpeechRecognizer with GenAI mode enabled.
     */
    suspend fun transcribeOnDevice(audioFile: File): Result<String> = withContext(Dispatchers.Main) {
        val deferred = CompletableDeferred<Result<String>>()
        
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            // Enable GenAI mode if supported (Android 16+ convention)
            putExtra("android.speech.extra.GENAI_ENABLED", true)
            // Path to audio file for offline processing
            putExtra("android.speech.extra.AUDIO_SOURCE_URI", android.net.Uri.fromFile(audioFile).toString())
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                deferred.complete(Result.failure(Exception("Speech recognition error code: $error")))
                recognizer.destroy()
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    deferred.complete(Result.success(matches[0]))
                } else {
                    deferred.complete(Result.failure(Exception("No transcription results")))
                }
                recognizer.destroy()
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        recognizer.startListening(intent)
        
        try {
            deferred.await()
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

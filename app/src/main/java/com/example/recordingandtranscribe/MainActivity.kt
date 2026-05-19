package com.example.recordingandtranscribe

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.example.recordingandtranscribe.core.SettingsRepository
import com.example.recordingandtranscribe.core.zh
import com.example.recordingandtranscribe.ui.AppNavigation
import com.example.recordingandtranscribe.ui.theme.RecordingAndTranscribeTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainActivity : FragmentActivity() {

    private lateinit var settingsRepository: SettingsRepository
    private var isBiometricEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        settingsRepository = SettingsRepository(this)

        // Fetch biometric setting asynchronously and then decide
        lifecycleScope.launch {
            isBiometricEnabled = settingsRepository.isBiometricEnabledFlow.first()
            if (isBiometricEnabled) {
                val biometricManager = BiometricManager.from(this@MainActivity)
                val canAuthenticate = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
                    showBiometricPrompt()
                } else {
                    Toast.makeText(
                        applicationContext,
                        "Biometric lock not available".zh(this@MainActivity, "生物识别锁当前不可用"),
                        Toast.LENGTH_LONG
                    ).show()
                    startApp()
                }
            } else {
                startApp()
            }
        }
    }

    private fun showBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    startApp()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Toast.makeText(applicationContext, errString, Toast.LENGTH_SHORT).show()
                    finish() // Close app if auth fails or is cancelled
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Biometric Lock".zh(this, "生物识别锁"))
            .setSubtitle("Unlock to access your recordings".zh(this, "验证身份以访问录音"))
            .setNegativeButtonText("Exit".zh(this, "退出"))
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun startApp() {
        setContent {
            RecordingAndTranscribeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

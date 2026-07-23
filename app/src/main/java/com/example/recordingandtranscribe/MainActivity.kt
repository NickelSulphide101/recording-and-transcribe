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
    private var isAuthenticated = mutableStateOf(false)
    private var isAuthRequired = mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (savedInstanceState?.getBoolean("isAuthenticated") == true) {
            isAuthenticated.value = true
            isAuthRequired.value = false
        }

        settingsRepository = SettingsRepository(this)

        setContent {
            RecordingAndTranscribeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val authState by isAuthenticated
                    val isRequired by isAuthRequired

                    if (authState || !isRequired) {
                        AppNavigation()
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            androidx.compose.material3.Text(
                                "Authenticating...".zh(this@MainActivity, "身份验证中..."),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
        }

        if (!isAuthenticated.value) {
            lifecycleScope.launch {
                val enabled = settingsRepository.isBiometricEnabledFlow.first()
                if (enabled) {
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
                        isAuthRequired.value = false
                        isAuthenticated.value = true
                    }
                } else {
                    isAuthRequired.value = false
                    isAuthenticated.value = true
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("isAuthenticated", isAuthenticated.value)
    }

    private fun showBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    isAuthenticated.value = true
                    isAuthRequired.value = false
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode != BiometricPrompt.ERROR_CANCELED && errorCode != BiometricPrompt.ERROR_USER_CANCELED) {
                        Toast.makeText(applicationContext, errString, Toast.LENGTH_SHORT).show()
                    }
                    if (!isAuthenticated.value) {
                        finish()
                    }
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
}

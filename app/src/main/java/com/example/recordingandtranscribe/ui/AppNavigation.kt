package com.example.recordingandtranscribe.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.recordingandtranscribe.core.AudioRecorder
import com.example.recordingandtranscribe.core.SettingsRepository
import java.io.File

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val settingsRepository = remember { SettingsRepository(context) }
    val audioRecorder = remember { AudioRecorder(context) }
    
    val isOnboardingCompleted by settingsRepository.isOnboardingCompletedFlow.collectAsState(initial = null)

    // Show default surface background until we know if onboarding is completed
    if (isOnboardingCompleted == null) {
        Box(
            modifier = Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        )
        return
    }

    NavHost(
        navController = navController, 
        startDestination = if (isOnboardingCompleted == true) "main" else "onboarding"
    ) {
        composable("onboarding") {
            OnboardingScreen(
                onFinished = {
                    navController.navigate("main") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                },
                settingsRepository = settingsRepository
            )
        }
        composable("main") {
            MainScreen(
                navController = navController,
                audioRecorder = audioRecorder
            )
        }
        composable("settings") {
            SettingsScreen(
                navController = navController,
                settingsRepository = settingsRepository
            )
        }
        composable(
            route = "transcribe/{fileName}",
            arguments = listOf(navArgument("fileName") { type = NavType.StringType })
        ) { backStackEntry ->
            val fileName = backStackEntry.arguments?.getString("fileName") ?: return@composable
            val file = File(context.filesDir, fileName)
            TranscriptionScreen(
                navController = navController,
                file = file,
                settingsRepository = settingsRepository
            )
        }
    }
}

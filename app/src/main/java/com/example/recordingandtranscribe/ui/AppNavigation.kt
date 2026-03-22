package com.example.recordingandtranscribe.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
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

    NavHost(navController = navController, startDestination = "main") {
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

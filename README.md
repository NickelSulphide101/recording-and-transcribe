# Recording and Transcribe

A modern, native Android application built with Jetpack Compose that allows users to record voice notes and transcribe them into text using Google's **Gemini 1.5 Flash** AI model.

## Features

* **Voice Recording**: One-tap recording with the built-in Android `MediaRecorder` API. The audio recordings are saved locally as `.m4a` files in app-specific storage.
* **Recording History**: Browse all your saved recordings chronologically.
* **AI Transcription**: Select any recording and use the Google Gemini API to seamlessly transcribe the voice audio directly to text instantly.
* **Custom API Key**: Securely input and save your own Google Gemini API key via Jetpack DataStore in the app's Settings menu.
* **Automatic CI/CD Pipeline**: GitHub Actions are pre-configured to build a new Android Debug APK on every push to the `main` branch, automatically creating a GitHub Release.

## Requirements

* Android Device (Target SDK 35, Min SDK 26) - Highly optimized for the latest Android devices, including Pixel.
* Microphone Permissions. 
* A valid Google Gemini API Key from [Google AI Studio](https://aistudio.google.com/).

## CI/CD details

The project does not require a local Gradle build environment. The repository includes a GitHub Action `.github/workflows/android.yml` which does the following:

1. Triggers dynamically on every push/PR to the `main` branch.
2. Sets up JDK 17 and Gradle 8.8 environments.
3. Builds the `app-debug.apk`.
4. Creates a designated GitHub Release under the "Releases" panel on the right side of this repository.

## Setup Instructions

1. Simply **Commit and Push** this codebase to the `main` branch of your GitHub repository.
2. Allow GitHub Actions about 2-3 minutes to successfully complete its task.
3. Go to the **Releases** tab on the repository's page to download the latest `.apk` installation package.
4. Sideload/Install the `.apk` on your Android device.
5. In the app, open Settings (gear icon in the top right).
6. Enter your Gemini API Key and tap Save.
7. Tap the microphone button to start recording and let Gemini transcribe it!

## Tech Stack

* Kotlin
* Jetpack Compose & Material 3
* Jetpack Navigation Compose
* Jetpack DataStore
* Google Generative AI Android SDK

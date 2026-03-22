# Recording and Transcribe

A robust, modern native Android application built with Jetpack Compose. It allows users to record highly-compressed voice notes, manage them, and seamlessly transcribe them into text using Google's **Gemini AI models** (like Gemini 1.5 Flash or Pro).

## ✨ Key Features

* **Advanced Voice Recording**: One-tap recording using Android's `MediaRecorder` API. Audio is encoded in **HE-AAC (High-Efficiency AAC)** at 16kHz/24Kbps, guaranteeing crisp human voice legibility while drastically reducing `.m4a` file sizes to save storage and upload bandwidth.
* **Full-Featured Audio Player**: Click on any recording in the history list to reveal a pristine playback console at the bottom of the screen. Includes play/pause, seek slider, ±10s skip, and previous/next track functionality.
* **Recording Management**: Long-press or click the "more" (⋮) icon on any recording to cleanly **Rename** or **Delete** the audio.
* **AI Transcription & Local Caching**: Send your audio to Gemini for an exact text transcription. Once transcribed, the result is **cached locally** as a hidden `.txt` file, ensuring instantaneous re-loads in the future without wasting API quota or waiting for the network.
* **One-Click Copy**: Easily copy the transcribed text to your Android system clipboard using the built-in copy button.
* **API Key Rotation & Custom Models**:
  * Input multiple Gemini API Keys (comma-separated) in Settings. If one fails or hits a rate limit, the app automatically and silently rotates to the next key.
  * Define exactly which AI Model you want to use (defaults to `gemini-1.5-flash`).
* **Automatic CI/CD Pipeline**: GitHub Actions are pre-configured to build a new Android Debug APK on every single push to the `main` branch, automatically publishing it as a GitHub Release.

## 📱 Requirements

* Android Device (Target SDK 35, Min SDK 26) - Highly optimized for the latest Android devices, including Pixel.
* Microphone Permissions. 
* A valid Google Gemini API Key from [Google AI Studio](https://aistudio.google.com/).

## ⚙️ CI/CD details

The project does not require a local Gradle build environment. The repository includes a GitHub Action `.github/workflows/android.yml` which does the following:

1. Triggers dynamically on every push/PR to the `main` branch.
2. Sets up the environment with Node 24 variables, JDK 17, and standard `setup-gradle`.
3. Builds the `app-debug.apk`.
4. Creates a designated GitHub Release strictly appended to your commit under the "Releases" panel.

## 🚀 Setup Instructions

1. Simply **Commit and Push** this codebase to the `main` branch of your GitHub repository.
2. Allow GitHub Actions about 2-3 minutes to successfully complete its task.
3. Go to the **Releases** tab on the repository's page to download the latest `.apk` installation package.
4. Sideload/Install the `.apk` on your Android device.
5. In the app, open Settings (gear icon in the top right).
6. Enter your Gemini API Key(s) (comma-separated) and your preferred Model Name, then tap Save.
7. Tap the microphone button to start recording and let Gemini transcribe it!

## 🛠️ Tech Stack

* Kotlin
* Jetpack Compose & Material 3
* Jetpack Navigation Compose
* Jetpack DataStore Preferences
* Google Generative AI Android SDK

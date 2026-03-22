# Audio Recording and Transcription Application

A modern Android application for recording audio and transcribing it using Google's Gemini AI.

## Features

- **High-Quality Recording**: Uses Opus encoding in OGG format for superior compression and voice quality.
- **Background Recording**: Persistent recording through a foreground service, allowing you to record even when the app is in the background or the screen is locked.
- **Notification Controls**: Pause, Resume, and Stop recording directly from the notification bar.
- **Advanced AI Transcription**: Powered by Google Gemini. Supports multiple API keys with automatic rotation and fallback.
- **Local Caching**: Transcription results are saved locally for instant access.
- **Bilingual Interface**: Supporting both English and Chinese based on system preferences, using the latest Android 16 Locale APIs.
- **Material 3 Design**: Modern, clean UI with dynamic color support (Material You).
- **Targeting Android 16**: Built with the latest Android 16 APIs (API 36), featuring edge-to-edge layout, predictive back gestures, and MediaStyle notifications.

## Requirements

- **Android Version**: Android 16 (API 36) or higher.
- **Permissions**:
  - `RECORD_AUDIO`: Required for recording voice.
  - `POST_NOTIFICATIONS`: Required for background recording notification.
  - `FOREGROUND_SERVICE_MICROPHONE`: Required for persistent recording.
  - `INTERNET`: Required for communicating with Google Gemini API.

## Setup

1. Get one or more Gemini API keys from [Google AI Studio](https://aistudio.google.com/).
2. Open the app and go to **Settings**.
3. Enter your API keys (separated by commas if you have multiple).
4. (Optional) Customize the Gemini model name (default is `gemini-1.5-flash`).
5. Save settings and start recording!

## Development

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose (Material 3)
- **Architecture**: MVVM / Service-based background processing
- **Build System**: Gradle 9.1.0+ with JDK 25/26
- **Dependencies**:
  - `androidx.core:core-ktx:1.15.0`
  - `com.google.ai.client.generativeai:generativeai:0.9.0`
  - `androidx.media:media:1.7.0`
  - `androidx.navigation:navigation-compose:2.8.4`
  - `androidx.datastore:datastore-preferences:1.1.1`

## UI
The UI is fully modernized for Android 16 with:
- Edge-to-edge immersive display.
- Predictive back gesture support.
- MediaStyle notification with playback-like controls for recording.
- Dynamic color themes that adapt to your wallpaper.

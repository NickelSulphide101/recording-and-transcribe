# Audio Recording and Transcription Application

A flagship Android 16 application for recording audio and transcribing it using Google's Gemini AI, featuring multimodal insights and professional-grade tools.

## Key Features

### 🎙️ Advanced Recording & Audio
- **High-Quality Recording**: Opus encoding in OGG format for superior compression and voice quality.
- **Background Recording**: Persistent foreground service with notification controls.
- **Audio Trimming**: Built-in precision tool for cropping recordings using `MediaMuxer`.
- **AI Denoising**: Intelligent noise reduction configuration for crystal-clear transcripts.
- **Universal Import**: Easily import and manage external audio files with automatic library integration.

### 🤖 Multimodal AI Insights
- **Gemini Powered**: Advanced transcription with support for multiple API keys and automatic rotation.
- **Photo Attachments**: Capture or attach photos during recording to provide visual context to your audio.
- **Emotion Analysis**: AI-driven sentiment tracking and emotional tone analysis.
- **Privacy Masking**: Automatic redaction of sensitive personal information (PII) from transcripts.
- **Smart Summarization**: Instant summaries, action items, and keyword extraction.
- **Android 16 AICore Ready**: Infrastructure prepared for Gemini Nano on-device AI integration.

### 🛡️ Security & Privacy
- **Biometric Lock**: Fingerprint and facial recognition protection on startup.
- **Local Control**: All metadata and transcripts stored locally with export options.
- **Android 16 Compliance**: Full support for the latest privacy sandbox and security standards.

### 🎨 Premium UI/UX
- **Material 3 (Material You)**: Modern, clean interface with dynamic color support and smooth animations.
- **Bilingual Support**: English and Chinese interfaces using Android 16's latest Locale APIs.
- **Adaptive Layout**: Fully responsive design for various screen sizes, including foldable and tablet optimization.

## Requirements

- **Android Version**: Android 16 (API 36) or higher.
- **Permissions**: `RECORD_AUDIO`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE_MICROPHONE`, `USE_BIOMETRIC`.

## Setup

1. Get Gemini API keys from [Google AI Studio](https://aistudio.google.com/).
2. In **Settings**, enter your keys (comma-separated) and select your preferred model.
3. (Optional) Enable **Biometric Lock** or **On-Device AI (Gemini Nano)** for a more private experience.
4. Tap the FAB to start recording, or use **Import** to bring in existing files!

## Development

- **Tech Stack**: Kotlin, Jetpack Compose, Coroutines, Flow, DataStore.
- **AI Backend**: Google Generative AI (Gemini SDK) + Android 16 AICore (Stubbed).
- **Media**: Android `MediaMuxer`, `MediaExtractor`, and `MediaStyle` notification.
- **Dependencies**: 
  - `androidx.biometric:biometric:1.2.0-alpha05`
  - `io.coil-kt:coil-compose:2.6.0`
  - `com.google.ai.client.generativeai:generativeai:0.9.0`

## UI/UX Optimizations
The UI is fully modernized for Android 16 with:
- Edge-to-edge immersive display.
- Predictive back gesture support.
- MediaStyle notification with playback-like controls for recording.
- Dynamic color themes that adapt to your wallpaper.

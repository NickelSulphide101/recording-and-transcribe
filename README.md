# 🎙️ Audio Recording & Transcription (Android 16 Flagship)

A premium Android application (API 36+) for high-fidelity audio recording and AI-driven transcription using Google's Gemini AI. Precision-engineered for Android 16 with a focus on multimodal context and edge-to-edge UI.

## ✨ Key Features

### 🎧 Professional Audio & Recording
- **High-Quality Audio**: OGG format with Opus encoding for perfect speech reproduction.
- **Background Recording**: Foreground service with `MediaStyle` notification controls for immersive recording.
- **Audio Trimmer**: Precision crop tool for editing recordings using low-level `MediaMuxer` and `MediaExtractor`.
- **Intelligent Denoising**: Filter out ambient noise for cleaner, more accurate AI transcripts.
- **Library Integration**: Seamlessly import external audio files via the Android Storage Access Framework.

### 🧠 Advanced Multimodal AI (Gemini Powered)
- **Transcription & Summary**: Fast, accurate transcription with instant AI summarization and keyword extraction.
- **Action Item Extraction**: Automatically identify tasks, calendar events, and follow-ups from your audio.
- **Emotion & Sentiment**: Track the emotional tone and sentiment shifts throughout the recording.
- **Privacy Redaction**: Optional AI-driven masking of sensitive personal info (names, phones, addresses) for total privacy.
- **Multimodal Context**: Attach photos or capture images during recording to provide visual context for AI analysis.
- **AICore Optimized**: Modular architecture prepared for Gemini Nano on-device AI integration.

### 🛡️ Security & Privacy
- **Biometric Guard**: Facial and fingerprint authentication to lock your private recordings and transcripts.
- **Total Local Storage**: All metadata and transcripts are encrypted and stored locally on your device.
- **Permission Transparency**: Compliant with Android 16's latest privacy standards and scoped storage.

### 🎨 State-of-the-Art UI/UX
- **Material 3 (Material You)**: Adaptive, dynamic color themes with ultra-smooth Compose transitions.
- **Bilingual (EN/ZH)**: Full professional localization for English and simplified Chinese.
- **Adaptive Design**: Fully optimized for phones, foldables, and tablets with a responsive multi-pane layout.

## 🛠️ Build & Development

### Target Environment
- **Minimum SDK**: API 36 (Android 16)
- **Kotlin**: 2.x+
- **Gradle**: 8.x+ with Kotlin DSL

### Dependencies
- `androidx.biometric:biometric:1.2.0-alpha05`
- `io.coil-kt:coil-compose:2.6.0`
- `com.google.ai.client.generativeai:generativeai:0.9.0`
- `kotlinx.serialization.json:1.6.3`

### Setup Instructions
1. Obtain Gemini API keys from [Google AI Studio](https://aistudio.google.com/).
2. In **Settings**, enter your keys (comma-separated for rotation) and select a model (e.g., `gemini-1.5-flash`).
3. (Recommended) Enable **Biometric Lock** for immediate data protection.
4. (Experimental) Toggle **Gemini Nano** for on-device summarization (requires AICore support).

## 🚀 Building the Project
The project uses the latest Kotlin Serialization and Parcelize plugins. To build via CLI:
```bash
./gradlew assembleDebug
```
Ensure your `local.properties` or environment variables are set up if you intend to sign production builds.

---
*Built with ❤️ for the Android 16 community.*

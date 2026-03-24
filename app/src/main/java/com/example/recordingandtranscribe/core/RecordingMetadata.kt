package com.example.recordingandtranscribe.core

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val role: String, // "user" or "model"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class RecordingMetadata(
    val transcript: String? = null,
    val summary: String? = null,
    val actionItems: List<String> = emptyList(),
    val keywords: List<String> = emptyList(),
    val isFavorite: Boolean = false,
    val tags: List<String> = emptyList(),
    val photoUris: List<String> = emptyList(),
    val emotionAnalysis: String? = null,
    val isPrivacyMasked: Boolean = false,
    val chatHistory: List<ChatMessage> = emptyList()
)

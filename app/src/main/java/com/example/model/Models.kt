package com.example.model

import java.util.UUID

data class User(
    val id: String = UUID.randomUUID().toString(),
    val username: String,
    val passwordHash: String,
    val role: String, // "admin", "master", "viewer"
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class Chat(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val lastMessage: String,
    val lastMessageTime: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false
)

data class Message(
    val id: String = UUID.randomUUID().toString(),
    val chatId: String,
    val role: String, // "user" or "ai"
    val type: String, // "text", "image", "guide"
    val text: String? = null,
    val imageUrl: String? = null,
    val guideData: GuideData? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isSaved: Boolean = false
)

data class GuideData(
    val device: String,
    val problem: String,
    val difficulty: String, // "Easy", "Medium", "Hard"
    val timeEstimate: String,
    val tools: List<String>,
    val causes: List<Cause>,
    val steps: List<Step>,
    val proTip: String? = null,
    val risks: String? = null,
    val links: List<String> = emptyList()
)

data class Cause(
    val description: String,
    val probability: Int, // percentage 0..100
    val checkMethod: String,
    val normalValue: String,
    val fixMethod: String
)

data class Step(
    val stepNumber: Int,
    val title: String,
    val description: String,
    val imageUrl: String? = null
)

data class KnowledgeBaseEntry(
    val id: String = UUID.randomUUID().toString(),
    val brand: String, // stored normalized lowercase
    val model: String, // stored normalized lowercase
    val problem: String,
    val guideData: GuideData,
    val addedBy: String,
    val addedDate: Long = System.currentTimeMillis(),
    val isSchematic: Boolean = false,
    val schematicImageUrl: String? = null
) {
    val displayBrand: String
        get() = brand.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    
    val displayModel: String
        get() = model.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

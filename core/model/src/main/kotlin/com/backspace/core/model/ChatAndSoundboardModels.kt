package com.backspace.core.model

import kotlinx.serialization.Serializable

@Serializable
data class MessageAttachment(
    val id: String = "",
    val filename: String = "",
    val url: String = "",
    val contentType: String? = null,
    val size: Long = 0L,
)

@Serializable
data class MessageReaction(
    val emoji: String,
    val count: Int = 1,
    val me: Boolean = false,
)

@Serializable
data class Message(
    val id: String,
    val channelId: String,
    val userId: String = "",
    val content: String = "",
    val createdAt: String = "",
    val editedAt: String? = null,
    val replyToId: String? = null,
    val user: User? = null,
    val attachments: List<MessageAttachment> = emptyList(),
    val reactions: List<MessageReaction> = emptyList(),
) {
    val authorLabel: String get() = user?.label ?: userId
}

@Serializable
data class SoundboardSound(
    val id: String,
    val name: String,
    val emoji: String = "🔊",
    val folder: String? = null,
    val scopeType: String = "space", // "instance" | "space"
    val scopeId: String = "",
    val fileName: String = "",
    val fileUrl: String = "",
    val durationMs: Int = 0,
    val volume: Double = 1.0,
    val hidden: Boolean = false,
) {
    val resolvedFileUrl: String
        get() = fileUrl.ifBlank {
            if (fileName.isNotBlank()) "/api/soundboard/files/$fileName" else ""
        }
}

@Serializable
data class SoundboardSpaceSettings(
    val soundboardEnabled: Boolean = true,
    val allowInstanceSounds: Boolean = true,
    val maxConcurrentSounds: Int? = null,
)

@Serializable
data class SoundboardListResponse(
    val instanceSounds: List<SoundboardSound> = emptyList(),
    val scopedSounds: List<SoundboardSound> = emptyList(),
    val canManageInstance: Boolean = false,
    val canManageScope: Boolean = false,
    val soundboardEnabled: Boolean = true,
    val maxSoundsPerScope: Int = 50,
    val maxConcurrentSounds: Int = 3,
    val instanceMaxConcurrentSounds: Int = 3,
    val spaceSettings: SoundboardSpaceSettings? = null,
)

@Serializable
data class SoundboardPlayPayload(
    val type: String = "soundboard_play",
    val soundId: String,
    val fileUrl: String,
    val name: String,
    val emoji: String = "🔊",
    val senderUserId: String,
    val durationMs: Int = 0,
    val volumeScale: Double = 1.0,
)

enum class ChosenUserStatus(val wireValue: String) {
    ONLINE("online"),
    IDLE("idle"),
    DND("dnd"),
}

enum class StatusRevertDuration(val minutes: Int?) {
    NONE(null),
    FIFTEEN_MIN(15),
    ONE_HOUR(60),
    FOUR_HOURS(240),
}

object SoundboardUrlValidator {
    private val safePathRegex = Regex("""^/api/soundboard/files/[a-zA-Z0-9._-]+$""")

    fun isSafeFileUrl(fileUrl: String): Boolean = safePathRegex.matches(fileUrl)
}

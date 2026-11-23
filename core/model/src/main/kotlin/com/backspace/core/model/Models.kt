package com.backspace.core.model

import kotlinx.serialization.Serializable

@Serializable
data class InstanceInfo(
    val name: String = "Backspace",
    val version: String = "",
    val registrationOpen: Boolean = false,
    val instanceId: String = "",
    val commit: String? = null,
)

@Serializable
data class InstanceStreamingLimits(
    val defaultAudioBitrateKbps: Int = 64,
    val maxAudioBitrateKbps: Int = 512,
    val allowMusicMode: Boolean = true,
    val allowStereoAudio: Boolean = true,
    val soundboardEnabled: Boolean = true,
    val maxSoundboardSoundsPerScope: Int = 50,
    val maxConcurrentSoundboardSounds: Int = 3,
)

@Serializable
data class User(
    val id: String,
    val username: String,
    val displayName: String? = null,
    val avatar: String? = null,
    val banner: String? = null,
    val accentColor: String? = null,
    val avatarColor: String? = null,
    val bio: String? = null,
    val status: String = "online",
    val customStatus: String? = null,
    val isAdmin: Boolean = false,
) {
    val label: String get() = displayName?.takeIf { it.isNotBlank() } ?: username
    fun avatarUrl(instanceUrl: String): String? =
        avatar?.takeIf { it.isNotBlank() }?.let { "$instanceUrl/api/uploads/$it" }
}

@Serializable
data class Space(
    val id: String,
    val name: String,
    val icon: String? = null,
    val description: String? = null,
    val ownerId: String = "",
    val channels: List<Channel> = emptyList(),
    val categories: List<ChannelCategory> = emptyList(),
    val members: List<MemberWithUser> = emptyList(),
    val myPermissions: String? = null,
    // Optional Opus overrides
    val audioBitrateKbps: Int? = null,
    val maxAudioBitrateKbps: Int? = null,
    val allowMusicMode: Boolean? = null,
    val allowStereoAudio: Boolean? = null,
) {
    fun iconUrl(instanceUrl: String): String? =
        icon?.takeIf { it.isNotBlank() }?.let { "$instanceUrl/api/uploads/$it" }
}

@Serializable
data class ChannelCategory(
    val id: String,
    val spaceId: String = "",
    val parentId: String? = null,
    val name: String,
    val position: Int = 0,
    val isPrivate: Boolean = false,
    // Optional Opus overrides
    val audioBitrateKbps: Int? = null,
    val maxAudioBitrateKbps: Int? = null,
    val allowMusicMode: Boolean? = null,
    val allowStereoAudio: Boolean? = null,
)

@Serializable
data class Channel(
    val id: String,
    val spaceId: String = "",
    val categoryId: String? = null,
    val name: String,
    val type: String = "text", // "text" | "voice"
    val position: Int = 0,
    val topic: String? = null,
    val isPrivate: Boolean = false,
    // Optional Opus overrides
    val audioBitrateKbps: Int? = null,
    val maxAudioBitrateKbps: Int? = null,
    val allowMusicMode: Boolean? = null,
    val allowStereoAudio: Boolean? = null,
) {
    val isVoice: Boolean get() = type == "voice"
}

@Serializable
data class Role(
    val id: String,
    val spaceId: String = "",
    val name: String = "",
    val color: String = "",
    val position: Int = 0,
    val isEveryone: Boolean = false,
)

@Serializable
data class MemberWithUser(
    val userId: String,
    val nickname: String? = null,
    val user: User? = null,
    val roles: List<Role> = emptyList(),
) {
    val label: String get() = nickname?.takeIf { it.isNotBlank() } ?: user?.label ?: userId

    /**
     * Cor hex do cargo mais alto com cor definida (ignorando @everyone / vazio).
     */
    val highestRoleColorHex: String?
        get() = roles
            .filter { !it.isEveryone && it.color.isNotBlank() && it.color != "#000000" && it.color != "transparent" }
            .maxByOrNull { it.position }
            ?.color
}

@Serializable
data class VoiceUserState(
    val isMuted: Boolean = false,
    val isDeafened: Boolean = false,
    val isCameraOn: Boolean = false,
    val isScreenSharing: Boolean = false,
)

@Serializable
data class SpaceVoiceState(
    val spaceMuted: Boolean = false,
    val spaceDeafened: Boolean = false,
    val permissionMuted: Boolean = false,
)

@Serializable
data class LiveKitTokenResponse(
    val token: String,
    val url: String,
)

enum class AudioMode {
    VOICE,
    MUSIC,
}

enum class AudioChannelsMode {
    MONO,
    STEREO,
    UNKNOWN,
}

enum class AudioOutputRoute {
    BLUETOOTH,
    WIRED_HEADSET,
    SPEAKER,
    EARPIECE,
}

data class UserAudioPreferences(
    val mode: AudioMode = AudioMode.VOICE,
    val stereo: Boolean = false,
    val bitrateAuto: Boolean = true,
    val manualBitrateKbps: Int = 64,
    val idleSuspendEnabled: Boolean = true,
    val idleSuspendTimeoutSeconds: Int = 60,
    val hideNonVideoParticipants: Boolean = false,
)

data class ResolvedAudioPolicy(
    val defaultBitrateKbps: Int,
    val maxBitrateKbps: Int,
    val allowMusicMode: Boolean,
    val allowStereoAudio: Boolean,
)

data class EffectiveAudioConfig(
    val bitrateKbps: Int,
    val mode: AudioMode,
    val stereo: Boolean,
    val dtx: Boolean,
    val red: Boolean,
    val policy: ResolvedAudioPolicy,
) {
    val maxBitrateBps: Int get() = bitrateKbps * 1000
}

data class VoiceTelemetry(
    val pingMs: Int? = null,
    val jitterMs: Double? = null,
    val packetLossPercent: Double? = null,
    val sendBitrateKbps: Int? = null,
    val recvBitrateKbps: Int? = null,
    val codec: String? = null,
    val channelsMode: AudioChannelsMode = AudioChannelsMode.UNKNOWN,
    val opusMode: AudioMode? = null,
)

package com.backspace.core.model

import kotlinx.serialization.Serializable

@Serializable
data class GifItem(
    val id: String = "",
    val title: String = "",
    val url: String = "",
    val previewUrl: String = "",
) {
    val bestPreview: String get() = previewUrl.ifBlank { url }
}

@Serializable
data class GifSearchResponse(
    val results: List<GifItem> = emptyList(),
)

@Serializable
data class StickerItem(
    val id: String,
    val name: String = "",
    val filename: String = "",
) {
    fun assetUrl(instanceUrl: String): String =
        "${instanceUrl.trimEnd('/')}/api/stickers/assets/$filename"
}

@Serializable
data class ExploreSpaceItem(
    val id: String,
    val name: String,
    val description: String? = null,
    val icon: String? = null,
    val memberCount: Int = 0,
    val isMember: Boolean = false,
) {
    fun iconUrl(instanceUrl: String): String? = UploadUrlResolver.resolve(instanceUrl, icon)
}

@Serializable
data class ExploreSpacesResponse(
    val spaces: List<ExploreSpaceItem> = emptyList(),
)

@Serializable
data class SpaceInviteResponse(
    val inviteCode: String = "",
)

@Serializable
data class FriendItem(
    val id: String = "",
    val userId: String = "",
    val user: User? = null,
) {
    val targetUserId: String get() = user?.id ?: userId.ifBlank { id }
    val label: String get() = user?.label ?: targetUserId
}

@Serializable
data class FriendRequestItem(
    val id: String,
    val fromUserId: String = "",
    val toUserId: String = "",
    val status: String = "pending",
    val direction: String = "incoming", // "incoming" | "outgoing"
    val user: User? = null,
) {
    val label: String get() = user?.label ?: fromUserId.ifBlank { toUserId }
}

@Serializable
data class SpaceBanItem(
    val userId: String = "",
    val reason: String? = null,
    val user: User? = null,
) {
    val label: String get() = user?.label ?: userId
}

object InviteCodeExtractor {
    fun extract(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        val idx = trimmed.lastIndexOf('/')
        return if (idx >= 0 && idx < trimmed.lastIndex) trimmed.substring(idx + 1) else trimmed
    }
}

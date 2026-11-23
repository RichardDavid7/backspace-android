package com.backspace.core.network

import android.util.Base64
import com.backspace.core.model.Channel
import com.backspace.core.model.ChannelCategory
import com.backspace.core.model.DmChannel
import com.backspace.core.model.DmMessage
import com.backspace.core.model.ExploreSpaceItem
import com.backspace.core.model.ExploreSpacesResponse
import com.backspace.core.model.FriendItem
import com.backspace.core.model.FriendRequestItem
import com.backspace.core.model.GifItem
import com.backspace.core.model.GifSearchResponse
import com.backspace.core.model.InstanceAdminSettings
import com.backspace.core.model.InstanceInfo
import com.backspace.core.model.InstanceStreamingLimits
import com.backspace.core.model.InviteCodeExtractor
import com.backspace.core.model.LiveKitTokenResponse
import com.backspace.core.model.Message
import com.backspace.core.model.MessageAttachment
import com.backspace.core.model.Role
import com.backspace.core.model.SoundboardListResponse
import com.backspace.core.model.Space
import com.backspace.core.model.SpaceBanItem
import com.backspace.core.model.SpaceInviteResponse
import com.backspace.core.model.StickerItem
import com.backspace.core.model.User
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class RegisterRequest(
    val username: String,
    val password: String,
    val displayName: String? = null,
    val inviteToken: String? = null,
)

@Serializable
data class EditMessageRequest(val content: String)

@Serializable
data class CreateGroupDmUser(val userId: String)

@Serializable
data class CreateGroupDmRequest(val users: List<CreateGroupDmUser>)

@Serializable
data class CreateRoleRequest(val name: String, val color: String = "#7C6CF6")

@Serializable
data class BanMemberRequest(val userId: String, val reason: String? = null)

@Serializable
data class UpdateChannelNameRequest(val name: String)

@Serializable
data class LoginResponse(val token: String, val user: User)

@Serializable
data class LiveKitTokenRequest(
    val channelId: String? = null,
    val dmChannelId: String? = null,
)

@Serializable
data class CreateDmRequest(val userId: String)

@Serializable
data class CreateSpaceRequest(val name: String, val description: String? = null)

@Serializable
data class JoinSpaceRequest(val inviteCode: String)

@Serializable
data class CreateChannelRequest(val name: String, val type: String = "text")

@Serializable
data class CreateCategoryRequest(val name: String)

@Serializable
data class SendFriendRequestBody(val username: String)

@Serializable
data class RespondFriendRequestBody(val action: String)

@Serializable
data class UpdateSpaceAdminRequest(
    val name: String? = null,
    val description: String? = null,
    val audioBitrateKbps: Int? = null,
    val maxAudioBitrateKbps: Int? = null,
    val allowMusicMode: Boolean? = null,
    val allowStereoAudio: Boolean? = null,
)

@Serializable
data class SendMessageRequest(
    val content: String? = null,
    val attachments: List<String>? = null,
    val replyToId: String? = null,
)

@Serializable
data class UpdateMeRequest(
    val displayName: String? = null,
    val bio: String? = null,
    val status: String? = null,
    val customStatus: String? = null,
)

@Serializable
data class ApiErrorResponse(
    val error: String = "Unknown error",
    val code: String? = null,
    val statusCode: Int = 0,
)

class BackspaceApiException(
    val statusCode: Int,
    val code: String?,
    override val message: String,
) : IOException(message)

class BackspaceRestClient(
    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build(),
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    },
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun getInstanceInfo(instanceUrl: String): InstanceInfo {
        val base = SecureSessionStorage.normalizeInstanceUrl(instanceUrl)
        return executeGet("$base/api/instance/info", token = null)
    }

    suspend fun login(instanceUrl: String, username: String, password: String): LoginResponse {
        val base = SecureSessionStorage.normalizeInstanceUrl(instanceUrl)
        return executePost(
            url = "$base/api/auth/login",
            body = json.encodeToString(LoginRequest.serializer(), LoginRequest(username, password)),
            token = null,
        )
    }

    suspend fun register(
        instanceUrl: String,
        username: String,
        password: String,
        displayName: String? = null,
        inviteToken: String? = null,
    ): LoginResponse {
        val base = SecureSessionStorage.normalizeInstanceUrl(instanceUrl)
        return executePost(
            url = "$base/api/auth/register",
            body = json.encodeToString(
                RegisterRequest.serializer(),
                RegisterRequest(
                    username = username,
                    password = password,
                    displayName = displayName?.ifBlank { null },
                    inviteToken = inviteToken?.ifBlank { null },
                ),
            ),
            token = null,
        )
    }

    suspend fun getStreamingLimits(instanceUrl: String, token: String): InstanceStreamingLimits {
        val base = SecureSessionStorage.normalizeInstanceUrl(instanceUrl)
        return executeGet("$base/api/settings/streaming", token = token)
    }

    suspend fun requestLiveKitToken(
        instanceUrl: String,
        token: String,
        channelId: String,
    ): LiveKitTokenResponse {
        val base = SecureSessionStorage.normalizeInstanceUrl(instanceUrl)
        return executePost(
            url = "$base/api/livekit/token",
            body = json.encodeToString(LiveKitTokenRequest.serializer(), LiveKitTokenRequest(channelId = channelId)),
            token = token,
        )
    }

    suspend fun requestLiveKitDmToken(
        instanceUrl: String,
        token: String,
        dmChannelId: String,
    ): LiveKitTokenResponse {
        val base = SecureSessionStorage.normalizeInstanceUrl(instanceUrl)
        return executePost(
            url = "$base/api/livekit/token",
            body = json.encodeToString(LiveKitTokenRequest.serializer(), LiveKitTokenRequest(dmChannelId = dmChannelId)),
            token = token,
        )
    }

    suspend fun getDmChannels(instanceUrl: String, token: String): List<DmChannel> {
        val base = SecureSessionStorage.normalizeInstanceUrl(instanceUrl)
        return executeGet("$base/api/dm", token = token)
    }

    suspend fun createOrOpenDm(instanceUrl: String, token: String, targetUserId: String): DmChannel {
        val base = SecureSessionStorage.normalizeInstanceUrl(instanceUrl)
        return executePost(
            url = "$base/api/dm",
            body = json.encodeToString(CreateDmRequest.serializer(), CreateDmRequest(targetUserId)),
            token = token,
        )
    }

    suspend fun getDmMessages(
        instanceUrl: String,
        token: String,
        dmChannelId: String,
        limit: Int = 50,
    ): List<DmMessage> {
        val base = SecureSessionStorage.normalizeInstanceUrl(instanceUrl)
        return executeGet("$base/api/dm/$dmChannelId/messages?limit=$limit", token = token)
    }

    suspend fun sendDmMessage(
        instanceUrl: String,
        token: String,
        dmChannelId: String,
        content: String?,
        attachmentIds: List<String>? = null,
        replyToId: String? = null,
    ): DmMessage {
        val base = SecureSessionStorage.normalizeInstanceUrl(instanceUrl)
        return executePost(
            url = "$base/api/dm/$dmChannelId/messages",
            body = json.encodeToString(
                SendMessageRequest.serializer(),
                SendMessageRequest(
                    content = content?.takeIf { it.isNotBlank() },
                    attachments = attachmentIds?.takeIf { it.isNotEmpty() },
                    replyToId = replyToId,
                ),
            ),
            token = token,
        )
    }

    suspend fun deleteDmMessage(
        instanceUrl: String,
        token: String,
        messageId: String,
    ) = withContext(Dispatchers.IO) {
        val base = SecureSessionStorage.normalizeInstanceUrl(instanceUrl)
        val req = Request.Builder()
            .url("$base/api/dm/messages/$messageId")
            .header("Authorization", "Bearer $token")
            .delete()
            .build()
        okHttpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw BackspaceApiException(resp.code, null, "Delete DM failed (HTTP ${resp.code})")
            }
        }
    }

    suspend fun updateSpaceSettings(
        instanceUrl: String,
        token: String,
        spaceId: String,
        request: UpdateSpaceAdminRequest,
    ): Space {
        val base = SecureSessionStorage.normalizeInstanceUrl(instanceUrl)
        return executePatch(
            url = "$base/api/spaces/$spaceId",
            body = json.encodeToString(UpdateSpaceAdminRequest.serializer(), request),
            token = token,
        )
    }

    suspend fun getInstanceAdminSettings(
        instanceUrl: String,
        token: String,
    ): InstanceAdminSettings {
        val base = SecureSessionStorage.normalizeInstanceUrl(instanceUrl)
        return executeGet("$base/api/settings/instance", token = token)
    }

    suspend fun updateInstanceAdminSettings(
        instanceUrl: String,
        token: String,
        settings: InstanceAdminSettings,
    ): InstanceAdminSettings {
        val base = SecureSessionStorage.normalizeInstanceUrl(instanceUrl)
        return executePatch(
            url = "$base/api/settings/instance",
            body = json.encodeToString(InstanceAdminSettings.serializer(), settings),
            token = token,
        )
    }

    suspend fun updateStreamingLimits(
        instanceUrl: String,
        token: String,
        limits: InstanceStreamingLimits,
    ): InstanceStreamingLimits {
        val base = SecureSessionStorage.normalizeInstanceUrl(instanceUrl)
        return executePatch(
            url = "$base/api/settings/streaming",
            body = json.encodeToString(InstanceStreamingLimits.serializer(), limits),
            token = token,
        )
    }

    // ── GIFs & Stickers ──
    suspend fun getTrendingGifs(instanceUrl: String, token: String, limit: Int = 24): List<GifItem> {
        val base = SecureSessionStorage.normalizeInstanceUrl(instanceUrl)
        return executeGet<GifSearchResponse>("$base/api/gif/trending?limit=$limit", token = token).results
    }

    suspend fun searchGifs(instanceUrl: String, token: String, query: String, limit: Int = 24): List<GifItem> {
        val base = SecureSessionStorage.normalizeInstanceUrl(instanceUrl)
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        return executeGet<GifSearchResponse>("$base/api/gif/search?q=$q&limit=$limit", token = token).results
    }

    suspend fun getStickers(instanceUrl: String, token: String): List<StickerItem> {
        val base = SecureSessionStorage.normalizeInstanceUrl(instanceUrl)
        return executeGet("$base/api/stickers", token = token)
    }

    // ── Espaços: Criar, Entrar por Convite, Descobrir, Gerar Convite, Canais/Categorias ──
    suspend fun createSpace(instanceUrl: String, token: String, name: String, description: String?): Space {
        val base = SecureSessionStorage.normalizeInstanceUrl(instanceUrl)
        return executePost(
            url = "$base/api/spaces",
            body = json.encodeToString(
                CreateSpaceRequest.serializer(),
                CreateSpaceRequest(name = name, description = description?.ifBlank { null }),
            ),
            token = token,
        )
    }

    suspend fun joinSpaceByInvite(instanceUrl: String, token: String, rawInvite: String): Space {
        val base = SecureSessionStorage.normalizeInstanceUrl(instanceUrl)
        val code = InviteCodeExtractor.extract(rawInvite)
        return executePost(
            url = "$base/api/spaces/join",
            body = json.encodeToString(JoinSpaceRequest.serializer(), JoinSpaceRequest(inviteCode = code)),
            token = token,
        )
    }

    suspend fun generateSpaceInvite(instanceUrl: String, token: String, spaceId: String): String {
        val base = SecureSessionStorage.normalizeInstanceUrl(instanceUrl)
        return executePost<SpaceInviteResponse>(
            url = "$base/api/spaces/$spaceId/invite",
            body = "{}",
            token = token,
        ).inviteCode
    }

    suspend fun exploreSpaces(instanceUrl: String, token: String, query: String = ""): List<ExploreSpaceItem> {
        val base = SecureSessionStorage.normalizeInstanceUrl(instanceUrl)
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        return executeGet<ExploreSpacesResponse>("$base/api/spaces/explore?q=$q&limit=30", token = token).spaces
    }

    suspend fun publicJoinSpace(instanceUrl: String, token: String, spaceId: String) {
        val base = SecureSessionStorage.normalizeInstanceUrl(instanceUrl)
        withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url("$base/api/spaces/$spaceId/public-join")
                .header("Authorization", "Bearer $token")
                .post("{}".toRequestBody(jsonMediaType))
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw BackspaceApiException(resp.code, null, "Join failed (HTTP ${resp.code})")
                }
            }
        }
    }

    suspend fun createChannel(
        instanceUrl: String,
        token: String,
        spaceId: String,
        name: String,
        type: String,
    ): Channel {
        val base = SecureSessionStorage.normalizeInstanceUrl(instanceUrl)
        return executePost(
            url = "$base/api/spaces/$spaceId/channels",
            body = json.encodeToString(CreateChannelRequest.serializer(), CreateChannelRequest(name, type)),
            token = token,
        )
    }

    suspend fun createCategory(
        instanceUrl: String,
        token: String,
        spaceId: String,
        name: String,
    ): ChannelCategory {
        val base = SecureSessionStorage.normalizeInstanceUrl(instanceUrl)
        return executePost(
            url = "$base/api/spaces/$spaceId/categories",
            body = json.encodeToString(CreateCategoryRequest.serializer(), CreateCategoryRequest(name)),
            token = token,
        )
    }

    suspend fun getSpaces(instanceUrl: String, token: String): List<Space> {
        val base = SecureSessionStorage.normalizeInstanceUrl(instanceUrl)
        return executeGet("$base/api/spaces", token = token)
    }

    suspend fun getSpaceDetail(instanceUrl: String, token: String, spaceId: String): Space {
        val base = SecureSessionStorage.normalizeInstanceUrl(instanceUrl)
        return executeGet("$base/api/spaces/$spaceId", token = token)
    }

    // ── Amigos (Social) ──
    suspend fun getFriends(instanceUrl: String, token: String): List<FriendItem> {
        val base = SecureSessionStorage.normalizeInstanceUrl(instanceUrl)
        return executeGet("$base/api/social/friends", token = token)
    }

    suspend fun getFriendRequests(instanceUrl: String, token: String): List<FriendRequestItem> {
        val base = SecureSessionStorage.normalizeInstanceUrl(instanceUrl)
        return executeGet("$base/api/social/requests", token = token)
    }

    suspend fun sendFriendRequest(instanceUrl: String, token: String, username: String) {
        val base = SecureSessionStorage.normalizeInstanceUrl(instanceUrl)
        withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url("$base/api/social/requests")
                .header("Authorization", "Bearer $token")
                .post(
                    json.encodeToString(
                        SendFriendRequestBody.serializer(),
                        SendFriendRequestBody(username.trim()),
                    ).toRequestBody(jsonMediaType),
                )
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val raw = resp.body?.string().orEmpty()
                    val parsed = runCatching { json.decodeFromString<ApiErrorResponse>(raw) }.getOrNull()
                    throw BackspaceApiException(resp.code, parsed?.code, parsed?.error ?: "HTTP ${resp.code}")
                }
            }
        }
    }

    suspend fun respondFriendRequest(instanceUrl: String, token: String, requestId: String, accept: Boolean) {
        val base = SecureSessionStorage.normalizeInstanceUrl(instanceUrl)
        withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url("$base/api/social/requests/$requestId")
                .header("Authorization", "Bearer $token")
                .patch(
                    json.encodeToString(
                        RespondFriendRequestBody.serializer(),
                        RespondFriendRequestBody(if (accept) "accept" else "decline"),
                    ).toRequestBody(jsonMediaType),
                )
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw BackspaceApiException(resp.code, null, "HTTP ${resp.code}")
                }
            }
        }
    }

    suspend fun getChannelMessages(
        instanceUrl: String,
        token: String,
        channelId: String,
        before: String? = null,
        limit: Int = 50,
    ): List<Message> {
        val base = SecureSessionStorage.normalizeInstanceUrl(instanceUrl)
        val query = buildString {
            append("limit=$limit")
            if (!before.isNullOrBlank()) append("&before=$before")
        }
        return executeGet("$base/api/channels/$channelId/messages?$query", token = token)
    }

    suspend fun sendMessage(
        instanceUrl: String,
        token: String,
        channelId: String,
        content: String?,
        attachmentIds: List<String>? = null,
        replyToId: String? = null,
    ): Message {
        val base = SecureSessionStorage.normalizeInstanceUrl(instanceUrl)
        return executePost(
            url = "$base/api/channels/$channelId/messages",
            body = json.encodeToString(
                SendMessageRequest.serializer(),
                SendMessageRequest(
                    content = content?.takeIf { it.isNotBlank() },
                    attachments = attachmentIds?.takeIf { it.isNotEmpty() },
                    replyToId = replyToId,
                ),
            ),
            token = token,
        )
    }

    suspend fun deleteMessage(
        instanceUrl: String,
        token: String,
        messageId: String,
    ) = withContext(Dispatchers.IO) {
        val base = SecureSessionStorage.normalizeInstanceUrl(instanceUrl)
        val req = Request.Builder()
            .url("$base/api/messages/$messageId")
            .header("Authorization", "Bearer $token")
            .delete()
            .build()
        okHttpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw BackspaceApiException(resp.code, null, "Delete failed (HTTP ${resp.code})")
            }
        }
    }

    suspend fun editMessage(
        instanceUrl: String,
        token: String,
        messageId: String,
        content: String,
    ): Message {
        val base = SecureSessionStorage.normalizeInstanceUrl(instanceUrl)
        return executePatch(
            url = "$base/api/messages/$messageId",
            body = json.encodeToString(EditMessageRequest.serializer(), EditMessageRequest(content)),
            token = token,
        )
    }

    suspend fun editDmMessage(
        instanceUrl: String,
        token: String,
        messageId: String,
        content: String,
    ): DmMessage {
        val base = SecureSessionStorage.normalizeInstanceUrl(instanceUrl)
        return executePatch(
            url = "$base/api/dm/messages/$messageId",
            body = json.encodeToString(EditMessageRequest.serializer(), EditMessageRequest(content)),
            token = token,
        )
    }

    suspend fun createGroupDm(
        instanceUrl: String,
        token: String,
        userIds: List<String>,
    ): DmChannel {
        val base = SecureSessionStorage.normalizeInstanceUrl(instanceUrl)
        return executePost(
            url = "$base/api/dm/group",
            body = json.encodeToString(
                CreateGroupDmRequest.serializer(),
                CreateGroupDmRequest(users = userIds.map { CreateGroupDmUser(it) }),
            ),
            token = token,
        )
    }

    suspend fun removeFriend(instanceUrl: String, token: String, friendUserId: String) =
        withContext(Dispatchers.IO) {
            val base = SecureSessionStorage.normalizeInstanceUrl(instanceUrl)
            val req = Request.Builder()
                .url("$base/api/social/friends/$friendUserId")
                .header("Authorization", "Bearer $token")
                .delete()
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw BackspaceApiException(resp.code, null, "HTTP ${resp.code}")
            }
        }

    suspend fun renameChannel(instanceUrl: String, token: String, channelId: String, newName: String): Channel {
        val base = SecureSessionStorage.normalizeInstanceUrl(instanceUrl)
        return executePatch(
            url = "$base/api/channels/$channelId",
            body = json.encodeToString(UpdateChannelNameRequest.serializer(), UpdateChannelNameRequest(newName)),
            token = token,
        )
    }

    suspend fun deleteChannel(instanceUrl: String, token: String, channelId: String) =
        withContext(Dispatchers.IO) {
            val base = SecureSessionStorage.normalizeInstanceUrl(instanceUrl)
            val req = Request.Builder()
                .url("$base/api/channels/$channelId")
                .header("Authorization", "Bearer $token")
                .delete()
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw BackspaceApiException(resp.code, null, "HTTP ${resp.code}")
            }
        }

    suspend fun createRole(instanceUrl: String, token: String, spaceId: String, name: String, color: String): Role {
        val base = SecureSessionStorage.normalizeInstanceUrl(instanceUrl)
        return executePost(
            url = "$base/api/spaces/$spaceId/roles",
            body = json.encodeToString(CreateRoleRequest.serializer(), CreateRoleRequest(name, color)),
            token = token,
        )
    }

    suspend fun deleteRole(instanceUrl: String, token: String, spaceId: String, roleId: String) =
        withContext(Dispatchers.IO) {
            val base = SecureSessionStorage.normalizeInstanceUrl(instanceUrl)
            val req = Request.Builder()
                .url("$base/api/spaces/$spaceId/roles/$roleId")
                .header("Authorization", "Bearer $token")
                .delete()
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw BackspaceApiException(resp.code, null, "HTTP ${resp.code}")
            }
        }

    suspend fun kickMember(instanceUrl: String, token: String, spaceId: String, userId: String) =
        withContext(Dispatchers.IO) {
            val base = SecureSessionStorage.normalizeInstanceUrl(instanceUrl)
            val req = Request.Builder()
                .url("$base/api/spaces/$spaceId/members/$userId")
                .header("Authorization", "Bearer $token")
                .delete()
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw BackspaceApiException(resp.code, null, "HTTP ${resp.code}")
            }
        }

    suspend fun getBans(instanceUrl: String, token: String, spaceId: String): List<SpaceBanItem> {
        val base = SecureSessionStorage.normalizeInstanceUrl(instanceUrl)
        return executeGet("$base/api/spaces/$spaceId/bans", token = token)
    }

    suspend fun banMember(instanceUrl: String, token: String, spaceId: String, userId: String, reason: String? = null) =
        withContext(Dispatchers.IO) {
            val base = SecureSessionStorage.normalizeInstanceUrl(instanceUrl)
            val req = Request.Builder()
                .url("$base/api/spaces/$spaceId/bans")
                .header("Authorization", "Bearer $token")
                .post(
                    json.encodeToString(
                        BanMemberRequest.serializer(),
                        BanMemberRequest(userId = userId, reason = reason?.ifBlank { null }),
                    ).toRequestBody(jsonMediaType),
                )
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw BackspaceApiException(resp.code, null, "HTTP ${resp.code}")
            }
        }

    suspend fun unbanMember(instanceUrl: String, token: String, spaceId: String, userId: String) =
        withContext(Dispatchers.IO) {
            val base = SecureSessionStorage.normalizeInstanceUrl(instanceUrl)
            val req = Request.Builder()
                .url("$base/api/spaces/$spaceId/bans/$userId")
                .header("Authorization", "Bearer $token")
                .delete()
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw BackspaceApiException(resp.code, null, "HTTP ${resp.code}")
            }
        }

    /**
     * Faz upload de anexo via protocolo tus (`/api/files`) usado pelo servidor Backspace.
     */
    suspend fun uploadFileTus(
        instanceUrl: String,
        token: String,
        originalName: String,
        mimeType: String,
        bytes: ByteArray,
    ): MessageAttachment = withContext(Dispatchers.IO) {
        val base = SecureSessionStorage.normalizeInstanceUrl(instanceUrl)
        val b64Name = Base64.encodeToString(originalName.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val b64Type = Base64.encodeToString(mimeType.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

        // 1. Criar sessão tus
        val createReq = Request.Builder()
            .url("$base/api/files")
            .header("Authorization", "Bearer $token")
            .header("Tus-Resumable", "1.0.0")
            .header("Upload-Length", bytes.size.toString())
            .header("Upload-Metadata", "filename $b64Name,filetype $b64Type")
            .post(ByteArray(0).toRequestBody(null))
            .build()

        val uploadLocation = okHttpClient.newCall(createReq).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw BackspaceApiException(resp.code, null, "Upload init failed (HTTP ${resp.code})")
            }
            resp.header("Location")
                ?: throw BackspaceApiException(500, null, "Missing tus Location header")
        }

        val patchUrl = if (uploadLocation.startsWith("http://") || uploadLocation.startsWith("https://")) {
            uploadLocation
        } else {
            "$base/${uploadLocation.trimStart('/')}"
        }

        // 2. Enviar bytes via PATCH
        val offsetMediaType = "application/offset+octet-stream".toMediaType()
        val patchReq = Request.Builder()
            .url(patchUrl)
            .header("Authorization", "Bearer $token")
            .header("Tus-Resumable", "1.0.0")
            .header("Upload-Offset", "0")
            .patch(bytes.toRequestBody(offsetMediaType))
            .build()

        okHttpClient.newCall(patchReq).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw BackspaceApiException(resp.code, null, "Upload failed (HTTP ${resp.code})")
            }
            json.decodeFromString(MessageAttachment.serializer(), raw)
        }
    }

    suspend fun updateMyStatus(
        instanceUrl: String,
        token: String,
        displayName: String? = null,
        bio: String? = null,
        status: String? = null,
        customStatus: String? = null,
    ): User {
        val base = SecureSessionStorage.normalizeInstanceUrl(instanceUrl)
        return executePatch(
            url = "$base/api/users/@me",
            body = json.encodeToString(
                UpdateMeRequest.serializer(),
                UpdateMeRequest(
                    displayName = displayName,
                    bio = bio,
                    status = status,
                    customStatus = customStatus,
                ),
            ),
            token = token,
        )
    }

    suspend fun getSoundboard(
        instanceUrl: String,
        token: String,
        spaceId: String,
    ): SoundboardListResponse {
        val base = SecureSessionStorage.normalizeInstanceUrl(instanceUrl)
        return executeGet("$base/api/soundboard?spaceId=$spaceId", token = token)
    }

    private suspend inline fun <reified T> executePatch(url: String, body: String, token: String?): T =
        withContext(Dispatchers.IO) {
            val reqBuilder = Request.Builder()
                .url(url)
                .patch(body.toRequestBody(jsonMediaType))
            if (!token.isNullOrBlank()) {
                reqBuilder.header("Authorization", "Bearer $token")
            }
            executeAndDecode(reqBuilder.build())
        }

    private suspend inline fun <reified T> executeGet(url: String, token: String?): T =
        withContext(Dispatchers.IO) {
            val reqBuilder = Request.Builder().url(url).get()
            if (!token.isNullOrBlank()) {
                reqBuilder.header("Authorization", "Bearer $token")
            }
            executeAndDecode(reqBuilder.build())
        }

    private suspend inline fun <reified T> executePost(url: String, body: String, token: String?): T =
        withContext(Dispatchers.IO) {
            val reqBuilder = Request.Builder()
                .url(url)
                .post(body.toRequestBody(jsonMediaType))
            if (!token.isNullOrBlank()) {
                reqBuilder.header("Authorization", "Bearer $token")
            }
            executeAndDecode(reqBuilder.build())
        }

    private inline fun <reified T> executeAndDecode(request: Request): T {
        okHttpClient.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val parsed = runCatching { json.decodeFromString<ApiErrorResponse>(raw) }.getOrNull()
                throw BackspaceApiException(
                    statusCode = response.code,
                    code = parsed?.code,
                    message = parsed?.error ?: "HTTP ${response.code}",
                )
            }
            return json.decodeFromString(raw)
        }
    }
}

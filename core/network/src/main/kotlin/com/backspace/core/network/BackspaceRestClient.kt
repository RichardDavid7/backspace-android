package com.backspace.core.network

import com.backspace.core.model.InstanceInfo
import com.backspace.core.model.InstanceStreamingLimits
import com.backspace.core.model.LiveKitTokenResponse
import com.backspace.core.model.Message
import com.backspace.core.model.SoundboardListResponse
import com.backspace.core.model.User
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
data class LoginResponse(val token: String, val user: User)

@Serializable
data class LiveKitTokenRequest(val channelId: String)

@Serializable
data class SendMessageRequest(
    val content: String,
    val replyToId: String? = null,
)

@Serializable
data class UpdateMeRequest(
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
            body = json.encodeToString(LiveKitTokenRequest.serializer(), LiveKitTokenRequest(channelId)),
            token = token,
        )
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
        content: String,
        replyToId: String? = null,
    ): Message {
        val base = SecureSessionStorage.normalizeInstanceUrl(instanceUrl)
        return executePost(
            url = "$base/api/channels/$channelId/messages",
            body = json.encodeToString(
                SendMessageRequest.serializer(),
                SendMessageRequest(content = content, replyToId = replyToId),
            ),
            token = token,
        )
    }

    suspend fun updateMyStatus(
        instanceUrl: String,
        token: String,
        status: String? = null,
        customStatus: String? = null,
    ): User {
        val base = SecureSessionStorage.normalizeInstanceUrl(instanceUrl)
        return executePatch(
            url = "$base/api/users/@me",
            body = json.encodeToString(
                UpdateMeRequest.serializer(),
                UpdateMeRequest(status = status, customStatus = customStatus),
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

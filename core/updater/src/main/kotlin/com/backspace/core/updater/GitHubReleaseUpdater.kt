package com.backspace.core.updater

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
data class GitHubAssetDto(
    val name: String = "",
    @SerialName("browser_download_url")
    val downloadUrl: String = "",
)

@Serializable
data class GitHubReleaseDto(
    @SerialName("tag_name")
    val tagName: String = "",
    val name: String = "",
    @SerialName("html_url")
    val htmlUrl: String = "",
    val assets: List<GitHubAssetDto> = emptyList(),
)

data class UpdateCheckResult(
    val currentVersion: String,
    val latestTag: String,
    val updateAvailable: Boolean,
    val downloadUrl: String,
)

class GitHubReleaseUpdater(
    private val okHttpClient: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun checkLatestRelease(
        repoSlug: String,
        currentVersion: String,
    ): UpdateCheckResult? = withContext(Dispatchers.IO) {
        if (repoSlug.isBlank()) return@withContext null
        val request = Request.Builder()
            .url("https://api.github.com/repos/$repoSlug/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .get()
            .build()

        runCatching {
            okHttpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val raw = resp.body?.string().orEmpty()
                val dto = json.decodeFromString(GitHubReleaseDto.serializer(), raw)
                val apkAsset = dto.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                val normalizedLatest = dto.tagName.removePrefix("v").trim()
                val normalizedCurrent = currentVersion.removePrefix("v").trim()

                UpdateCheckResult(
                    currentVersion = currentVersion,
                    latestTag = dto.tagName,
                    updateAvailable = normalizedLatest.isNotBlank() && normalizedLatest != normalizedCurrent,
                    downloadUrl = apkAsset?.downloadUrl?.ifBlank { dto.htmlUrl } ?: dto.htmlUrl,
                )
            }
        }.getOrNull()
    }
}

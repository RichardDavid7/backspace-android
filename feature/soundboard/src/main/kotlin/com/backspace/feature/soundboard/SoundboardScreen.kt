package com.backspace.feature.soundboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.backspace.core.designsystem.AetherPurpleSlider
import com.backspace.core.designsystem.AetherSurfaceCard
import com.backspace.core.designsystem.AetherTheme
import com.backspace.core.model.SoundboardListResponse
import com.backspace.core.network.BackspaceRestClient
import com.backspace.core.voice.BackspaceVoiceEngine

@Composable
fun SoundboardScreen(
    instanceUrl: String,
    jwtToken: String,
    spaceId: String,
    restClient: BackspaceRestClient,
    voiceEngine: BackspaceVoiceEngine,
    title: String,
    searchPlaceholder: String,
    volumeLabel: String,
    emptyText: String,
    onBack: () -> Unit,
) {
    val colors = AetherTheme.colors
    val voiceState by voiceEngine.state.collectAsStateWithLifecycle()

    var data by remember { mutableStateOf<SoundboardListResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(spaceId) {
        isLoading = true
        errorText = null
        runCatching {
            restClient.getSoundboard(instanceUrl, jwtToken, spaceId)
        }.onSuccess { resp ->
            data = resp
        }.onFailure { err ->
            errorText = err.message
        }
        isLoading = false
    }

    val allSounds = remember(data, query) {
        val combined = buildList {
            data?.scopedSounds?.filterNot { it.hidden }?.let(::addAll)
            if (data?.spaceSettings?.allowInstanceSounds != false) {
                data?.instanceSounds?.filterNot { it.hidden }?.let(::addAll)
            }
        }
        if (query.isBlank()) {
            combined
        } else {
            val q = query.trim().lowercase()
            combined.filter { it.name.lowercase().contains(q) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgBase)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = colors.textPrimary,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
            )
        }

        Spacer(Modifier.height(8.dp))

        AetherSurfaceCard {
            Text(
                text = "$volumeLabel: ${(voiceState.soundboardVolume * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            AetherPurpleSlider(
                value = voiceState.soundboardVolume,
                onValueChange = { voiceEngine.setSoundboardVolume(it) },
                valueRange = 0f..1f,
            )
            if (!voiceState.lastSoundboardEvent.isNullOrBlank()) {
                Text(
                    text = voiceState.lastSoundboardEvent!!,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.accentMint,
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(searchPlaceholder) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        when {
            isLoading -> {
                CircularProgressIndicator(
                    color = colors.accentPrimary,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
            !errorText.isNullOrBlank() -> {
                Text(text = errorText!!, color = colors.accentPeach)
            }
            allSounds.isEmpty() -> {
                AetherSurfaceCard {
                    Text(text = emptyText, color = colors.textSecondary)
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(allSounds, key = { it.id }) { sound ->
                        AetherSurfaceCard(
                            modifier = Modifier.clickable {
                                voiceEngine.playSoundboardSound(sound)
                            },
                        ) {
                            Text(
                                text = sound.emoji.ifBlank { "🔊" },
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = sound.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

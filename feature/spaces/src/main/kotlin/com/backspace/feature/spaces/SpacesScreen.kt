package com.backspace.feature.spaces

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.backspace.core.designsystem.AetherStatusDot
import com.backspace.core.designsystem.AetherSurfaceCard
import com.backspace.core.designsystem.AetherTheme
import com.backspace.core.model.Channel
import com.backspace.core.model.ChannelCategory
import com.backspace.core.model.PermissionBits
import com.backspace.core.model.Space
import com.backspace.core.network.BackspaceWebSocketClient
import com.backspace.core.voice.BackspaceVoiceEngine
import com.backspace.core.voice.VoiceConnectionStatus

@Composable
fun SpacesScreen(
    wsClient: BackspaceWebSocketClient,
    voiceEngine: BackspaceVoiceEngine,
    spacesTitle: String,
    voiceChannelsHeader: String,
    noSpacesText: String,
    activeVoiceBannerPrefix: String,
    onJoinVoiceChannel: (Space, ChannelCategory?, Channel) -> Unit,
    onOpenActiveVoice: () -> Unit,
    onLogout: () -> Unit,
) {
    val colors = AetherTheme.colors
    val snapshot by wsClient.snapshot.collectAsStateWithLifecycle()
    val voiceState by voiceEngine.state.collectAsStateWithLifecycle()

    var selectedSpaceId by remember(snapshot.spaces) {
        mutableStateOf(snapshot.spaces.firstOrNull()?.id)
    }
    val selectedSpace = snapshot.spaces.firstOrNull { it.id == selectedSpaceId }
        ?: snapshot.spaces.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgBase)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AetherStatusDot(status = snapshot.currentUser?.status ?: "offline")
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = spacesTitle,
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = snapshot.currentUser?.label ?: snapshot.connectionState.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }
            IconButton(onClick = onLogout) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = "Logout",
                    tint = colors.textSecondary,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (voiceState.status == VoiceConnectionStatus.CONNECTED || voiceState.status == VoiceConnectionStatus.CONNECTING) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenActiveVoice),
                shape = RoundedCornerShape(14.dp),
                color = colors.accentPrimary.copy(alpha = 0.2f),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = "$activeVoiceBannerPrefix · ${voiceState.channelName}",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.accentMint,
                        )
                        Text(
                            text = "${voiceState.spaceName} · Opus ${voiceState.effectiveAudio.bitrateKbps} kbps",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = colors.accentMint,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (snapshot.spaces.isEmpty()) {
            AetherSurfaceCard {
                Text(text = noSpacesText, color = colors.textSecondary)
            }
            return
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(snapshot.spaces, key = { it.id }) { space ->
                FilterChip(
                    selected = space.id == selectedSpace?.id,
                    onClick = { selectedSpaceId = space.id },
                    label = { Text(space.name) },
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        if (selectedSpace != null) {
            Text(
                text = voiceChannelsHeader,
                style = MaterialTheme.typography.labelLarge,
                color = colors.textSecondary,
            )
            Spacer(Modifier.height(8.dp))

            val canConnect = PermissionBits.hasPermission(selectedSpace.myPermissions, PermissionBits.CONNECT)
            val voiceChannels = selectedSpace.channels
                .filter { it.isVoice }
                .sortedBy { it.position }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(voiceChannels, key = { it.id }) { channel ->
                    val category = selectedSpace.categories.firstOrNull { it.id == channel.categoryId }
                    val userIds = snapshot.voiceStates[channel.id].orEmpty()
                    val isCurrent = voiceState.channelId == channel.id

                    AetherSurfaceCard(
                        modifier = Modifier.clickable(enabled = canConnect) {
                            onJoinVoiceChannel(selectedSpace, category, channel)
                        },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = null,
                                    tint = if (isCurrent) colors.accentMint else colors.accentPrimary,
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = channel.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = colors.textPrimary,
                                    )
                                    if (category != null) {
                                        Text(
                                            text = category.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.textTertiary,
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "${userIds.size}",
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.textSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

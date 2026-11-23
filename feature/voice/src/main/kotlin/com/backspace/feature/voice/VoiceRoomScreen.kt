package com.backspace.feature.voice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.HeadsetOff
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.backspace.core.designsystem.AetherPurpleSlider
import com.backspace.core.designsystem.AetherStatusDot
import com.backspace.core.designsystem.AetherSurfaceCard
import com.backspace.core.designsystem.AetherTheme
import com.backspace.core.model.AudioMode
import com.backspace.core.model.AudioOutputRoute
import com.backspace.core.voice.BackspaceVoiceEngine

@Composable
fun VoiceRoomScreen(
    voiceEngine: BackspaceVoiceEngine,
    opusTitle: String,
    telemetryTitle: String,
    voiceModeLabel: String,
    musicModeLabel: String,
    stereoLabel: String,
    autoBitrateLabel: String,
    idleSuspendLabel: String,
    onBack: () -> Unit,
) {
    val colors = AetherTheme.colors
    val state by voiceEngine.state.collectAsStateWithLifecycle()
    val policy = state.effectiveAudio.policy

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgBase)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.textPrimary,
                    )
                }
                Column {
                    Text(
                        text = state.channelName.ifBlank { "Voice" },
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = "${state.spaceName} · ${state.status.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Controles estilo Mumble (Mute / Deafen / Leave)
        AetherSurfaceCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                IconButton(onClick = { voiceEngine.toggleMute() }) {
                    Icon(
                        imageVector = if (state.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Mute",
                        tint = if (state.isMuted) colors.accentPeach else colors.accentMint,
                    )
                }
                IconButton(onClick = { voiceEngine.toggleDeafen() }) {
                    Icon(
                        imageVector = if (state.isDeafened) Icons.Default.HeadsetOff else Icons.Default.Headset,
                        contentDescription = "Deafen",
                        tint = if (state.isDeafened) colors.accentPeach else colors.textPrimary,
                    )
                }
                IconButton(
                    onClick = {
                        voiceEngine.leaveChannel()
                        onBack()
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "Leave",
                        tint = colors.accentPeach,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    AudioOutputRoute.BLUETOOTH to "BT",
                    AudioOutputRoute.SPEAKER to "Speaker",
                    AudioOutputRoute.EARPIECE to "Ear",
                ).forEach { (route, label) ->
                    FilterChip(
                        selected = state.outputRoute == route,
                        onClick = { voiceEngine.setOutputRoute(route) },
                        label = { Text(label) },
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Participantes
        AetherSurfaceCard {
            state.participants.forEach { p ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AetherStatusDot(status = if (p.isSpeaking) "online" else "offline")
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = if (p.isLocal) "${p.username} (You)" else p.username,
                            color = if (p.isSpeaking) colors.accentMint else colors.textPrimary,
                        )
                    }
                    if (p.isMuted) {
                        Icon(
                            imageVector = Icons.Default.MicOff,
                            contentDescription = null,
                            tint = colors.accentPeach,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Painel Opus Hierárquico
        AetherSurfaceCard {
            Text(
                text = opusTitle,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.effectiveAudio.mode == AudioMode.VOICE,
                    onClick = {
                        voiceEngine.updateUserAudioPreferences { it.copy(mode = AudioMode.VOICE) }
                    },
                    label = { Text(voiceModeLabel) },
                )
                FilterChip(
                    selected = state.effectiveAudio.mode == AudioMode.MUSIC,
                    enabled = policy.allowMusicMode,
                    onClick = {
                        voiceEngine.updateUserAudioPreferences { it.copy(mode = AudioMode.MUSIC) }
                    },
                    label = { Text(musicModeLabel) },
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = stereoLabel, color = colors.textPrimary)
                Switch(
                    checked = state.effectiveAudio.stereo,
                    enabled = policy.allowStereoAudio,
                    onCheckedChange = { checked ->
                        voiceEngine.updateUserAudioPreferences { it.copy(stereo = checked) }
                    },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = "$autoBitrateLabel (${policy.defaultBitrateKbps} kbps)", color = colors.textPrimary)
                Switch(
                    checked = state.userPrefs.bitrateAuto,
                    onCheckedChange = { checked ->
                        voiceEngine.updateUserAudioPreferences { it.copy(bitrateAuto = checked) }
                    },
                )
            }

            if (!state.userPrefs.bitrateAuto) {
                val maxRange = policy.maxBitrateKbps.coerceAtLeast(16).toFloat()
                Text(
                    text = "${state.effectiveAudio.bitrateKbps} kbps (max ${policy.maxBitrateKbps} kbps)",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
                AetherPurpleSlider(
                    value = state.effectiveAudio.bitrateKbps.toFloat().coerceIn(8f, maxRange),
                    onValueChange = { v ->
                        voiceEngine.updateUserAudioPreferences { it.copy(manualBitrateKbps = v.toInt()) }
                    },
                    valueRange = 8f..maxRange,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = idleSuspendLabel, color = colors.textPrimary)
                Switch(
                    checked = state.userPrefs.idleSuspendEnabled,
                    onCheckedChange = { checked ->
                        voiceEngine.updateUserAudioPreferences { it.copy(idleSuspendEnabled = checked) }
                    },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Telemetria Opus
        AetherSurfaceCard {
            Text(
                text = telemetryTitle,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Codec: ${state.telemetry.codec ?: "opus"} · ${state.telemetry.channelsMode.name} · " +
                    "TX: ${state.telemetry.sendBitrateKbps ?: 0} kbps · " +
                    "DTX: ${if (state.effectiveAudio.dtx) "ON" else "OFF"} · " +
                    "RED: ${if (state.effectiveAudio.red) "ON" else "OFF"}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            if (state.isIdleSuspended) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Idle audio suspended (battery saver)",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.accentAmber,
                )
            }
        }
    }
}

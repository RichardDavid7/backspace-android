package com.backspace.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.backspace.core.designsystem.AetherPrimaryButton
import com.backspace.core.designsystem.AetherPurpleSlider
import com.backspace.core.designsystem.AetherSurfaceCard
import com.backspace.core.designsystem.AetherTheme
import com.backspace.core.model.AudioMode
import com.backspace.core.network.BackspaceRestClient
import com.backspace.core.network.BackspaceWebSocketClient
import com.backspace.core.voice.BackspaceVoiceEngine
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    instanceUrl: String,
    jwtToken: String,
    username: String,
    appVersion: String,
    restClient: BackspaceRestClient,
    wsClient: BackspaceWebSocketClient,
    voiceEngine: BackspaceVoiceEngine,
    title: String,
    accountHeader: String,
    audioHeader: String,
    videoHeader: String,
    instanceAdminHeader: String,
    voiceModeLabel: String,
    musicModeLabel: String,
    stereoLabel: String,
    autoBitrateLabel: String,
    idleSuspendLabel: String,
    soundboardVolumeLabel: String,
    hideNonVideoLabel: String,
    checkUpdatesLabel: String,
    saveLabel: String,
    logoutLabel: String,
    onCheckUpdates: () -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = AetherTheme.colors
    val scope = rememberCoroutineScope()
    val voiceState by voiceEngine.state.collectAsStateWithLifecycle()
    val snapshot by wsClient.snapshot.collectAsStateWithLifecycle()
    val prefs = voiceState.userPrefs
    val isInstanceAdmin = snapshot.currentUser?.isAdmin == true
    val limits = snapshot.streamingLimits
    var adminStatusMsg by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgBase)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
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

        AetherSurfaceCard {
            Text(text = accountHeader, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            Spacer(Modifier.height(6.dp))
            Text(text = username, color = colors.accentMint, style = MaterialTheme.typography.bodyMedium)
            Text(text = instanceUrl, color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
            Text(text = "v$appVersion", color = colors.textTertiary, style = MaterialTheme.typography.labelSmall)
        }

        AetherSurfaceCard {
            Text(text = audioHeader, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = prefs.mode == AudioMode.VOICE,
                    onClick = { voiceEngine.updateUserAudioPreferences { it.copy(mode = AudioMode.VOICE) } },
                    label = { Text(voiceModeLabel) },
                )
                FilterChip(
                    selected = prefs.mode == AudioMode.MUSIC,
                    onClick = { voiceEngine.updateUserAudioPreferences { it.copy(mode = AudioMode.MUSIC) } },
                    label = { Text(musicModeLabel) },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = stereoLabel, color = colors.textPrimary)
                Switch(
                    checked = prefs.stereo,
                    onCheckedChange = { v -> voiceEngine.updateUserAudioPreferences { it.copy(stereo = v) } },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = autoBitrateLabel, color = colors.textPrimary)
                Switch(
                    checked = prefs.bitrateAuto,
                    onCheckedChange = { v -> voiceEngine.updateUserAudioPreferences { it.copy(bitrateAuto = v) } },
                )
            }

            if (!prefs.bitrateAuto) {
                Text(
                    text = "${prefs.manualBitrateKbps} kbps",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
                AetherPurpleSlider(
                    value = prefs.manualBitrateKbps.toFloat().coerceIn(8f, 512f),
                    onValueChange = { v ->
                        voiceEngine.updateUserAudioPreferences { it.copy(manualBitrateKbps = v.toInt()) }
                    },
                    valueRange = 8f..512f,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = idleSuspendLabel, color = colors.textPrimary)
                Switch(
                    checked = prefs.idleSuspendEnabled,
                    onCheckedChange = { v ->
                        voiceEngine.updateUserAudioPreferences { it.copy(idleSuspendEnabled = v) }
                    },
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = "$soundboardVolumeLabel: ${(voiceState.soundboardVolume * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            AetherPurpleSlider(
                value = voiceState.soundboardVolume,
                onValueChange = { voiceEngine.setSoundboardVolume(it) },
                valueRange = 0f..1f,
            )
        }

        AetherSurfaceCard {
            Text(text = videoHeader, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = hideNonVideoLabel, color = colors.textPrimary)
                Switch(
                    checked = prefs.hideNonVideoParticipants,
                    onCheckedChange = { v ->
                        voiceEngine.updateUserAudioPreferences { it.copy(hideNonVideoParticipants = v) }
                    },
                )
            }
        }

        if (isInstanceAdmin) {
            var instDefaultBr by remember(limits.defaultAudioBitrateKbps) { mutableStateOf(limits.defaultAudioBitrateKbps.toFloat()) }
            var instMaxBr by remember(limits.maxAudioBitrateKbps) { mutableStateOf(limits.maxAudioBitrateKbps.toFloat()) }
            var instMusic by remember(limits.allowMusicMode) { mutableStateOf(limits.allowMusicMode) }
            var instStereo by remember(limits.allowStereoAudio) { mutableStateOf(limits.allowStereoAudio) }
            var instSoundboard by remember(limits.soundboardEnabled) { mutableStateOf(limits.soundboardEnabled) }

            AetherSurfaceCard {
                Text(text = instanceAdminHeader, style = MaterialTheme.typography.titleMedium, color = colors.accentAmber)
                Spacer(Modifier.height(8.dp))
                Text("Opus default: ${instDefaultBr.toInt()} kbps", color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                AetherPurpleSlider(value = instDefaultBr, onValueChange = { instDefaultBr = it }, valueRange = 8f..512f)
                Text("Opus max: ${instMaxBr.toInt()} kbps", color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                AetherPurpleSlider(value = instMaxBr, onValueChange = { instMaxBr = it }, valueRange = 8f..512f)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(musicModeLabel, color = colors.textPrimary)
                    Switch(checked = instMusic, onCheckedChange = { instMusic = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stereoLabel, color = colors.textPrimary)
                    Switch(checked = instStereo, onCheckedChange = { instStereo = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Soundboard", color = colors.textPrimary)
                    Switch(checked = instSoundboard, onCheckedChange = { instSoundboard = it })
                }
                if (!adminStatusMsg.isNullOrBlank()) {
                    Text(adminStatusMsg!!, color = colors.accentMint, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(6.dp))
                AetherPrimaryButton(
                    text = saveLabel,
                    onClick = {
                        scope.launch {
                            val updatedLimits = limits.copy(
                                defaultAudioBitrateKbps = instDefaultBr.toInt(),
                                maxAudioBitrateKbps = instMaxBr.toInt().coerceAtLeast(instDefaultBr.toInt()),
                                allowMusicMode = instMusic,
                                allowStereoAudio = instStereo,
                                soundboardEnabled = instSoundboard,
                            )
                            runCatching {
                                restClient.updateStreamingLimits(instanceUrl, jwtToken, updatedLimits)
                            }.onSuccess { saved ->
                                wsClient.updateStreamingLimitsLocal(saved)
                                adminStatusMsg = "OK"
                            }.onFailure { err ->
                                adminStatusMsg = err.message
                            }
                        }
                    },
                )
            }
        }

        AetherPrimaryButton(text = checkUpdatesLabel, onClick = onCheckUpdates)

        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = logoutLabel, color = colors.accentPeach)
        }
    }
}

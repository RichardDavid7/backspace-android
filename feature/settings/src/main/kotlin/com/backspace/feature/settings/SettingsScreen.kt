package com.backspace.feature.settings

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.backspace.core.designsystem.AetherAvatar
import com.backspace.core.designsystem.AetherPrimaryButton
import com.backspace.core.designsystem.AetherPurpleSlider
import com.backspace.core.designsystem.AetherSurfaceCard
import com.backspace.core.designsystem.AetherTheme
import com.backspace.core.model.AudioMode
import com.backspace.core.model.InstanceAdminSettings
import com.backspace.core.model.PttHardwareKey
import com.backspace.core.model.TransmissionMode
import com.backspace.core.network.BackspaceRestClient
import com.backspace.core.network.BackspaceWebSocketClient
import com.backspace.core.voice.BackspaceVoiceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    userTabLabel: String,
    instanceTabLabel: String,
    accountHeader: String,
    audioHeader: String,
    videoHeader: String,
    instanceGeneralHeader: String,
    instanceFederationHeader: String,
    instanceStreamingHeader: String,
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
    modifier: Modifier = Modifier,
) {
    val colors = AetherTheme.colors
    val scope = rememberCoroutineScope()
    val voiceState by voiceEngine.state.collectAsStateWithLifecycle()
    val snapshot by wsClient.snapshot.collectAsStateWithLifecycle()
    val prefs = voiceState.userPrefs
    val isInstanceAdmin = snapshot.currentUser?.isAdmin == true
    val limits = snapshot.streamingLimits

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Usuário, 1 = Instância
    var adminSettings by remember { mutableStateOf<InstanceAdminSettings?>(null) }
    var adminStatusMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(isInstanceAdmin, selectedTab) {
        if (isInstanceAdmin && selectedTab == 1 && adminSettings == null) {
            runCatching { restClient.getInstanceAdminSettings(instanceUrl, jwtToken) }
                .onSuccess { adminSettings = it }
                .onFailure { adminStatusMsg = it.message }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgBase)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = colors.textPrimary,
        )

        if (isInstanceAdmin) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    label = { Text(userTabLabel) },
                )
                FilterChip(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    label = { Text(instanceTabLabel) },
                )
            }
        }

        if (selectedTab == 0 || !isInstanceAdmin) {
            // ── ABA USUÁRIO: Perfil + Microfone/Transmissão (amigável) + Áudio/Opus + Restaurar ──
            val context = LocalContext.current
            val me = snapshot.currentUser
            var displayName by remember(me?.displayName) { mutableStateOf(me?.displayName.orEmpty()) }
            var bio by remember(me?.bio) { mutableStateOf(me?.bio.orEmpty()) }
            var customStatus by remember(me?.customStatus) { mutableStateOf(me?.customStatus.orEmpty()) }
            var profileMsg by remember { mutableStateOf<String?>(null) }

            val avatarPicker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent(),
            ) { uri: Uri? ->
                if (uri == null) return@rememberLauncherForActivityResult
                scope.launch {
                    profileMsg = null
                    runCatching {
                        val (fileName, mimeType, bytes) = withContext(Dispatchers.IO) {
                            val resolver = context.contentResolver
                            var name = "avatar.png"
                            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                                if (c.moveToFirst()) {
                                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                    if (idx >= 0) name = c.getString(idx) ?: name
                                }
                            }
                            val type = resolver.getType(uri) ?: "image/png"
                            val data = resolver.openInputStream(uri)?.use { it.readBytes() }
                                ?: throw IllegalStateException("Cannot read image")
                            Triple(name, type, data)
                        }
                        val uploaded = restClient.uploadFileTus(instanceUrl, jwtToken, fileName, mimeType, bytes)
                        val updated = restClient.updateMyStatus(
                            instanceUrl = instanceUrl,
                            token = jwtToken,
                            avatar = uploaded.filename,
                        )
                        wsClient.updateCurrentUserLocal(updated)
                    }.onSuccess { profileMsg = "Foto atualizada!" }
                        .onFailure { profileMsg = it.message }
                }
            }

            AetherSurfaceCard {
                Text(text = accountHeader, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AetherAvatar(
                        imageUrl = me?.avatarUrl(instanceUrl),
                        fallbackLabel = username,
                        size = 52.dp,
                        status = me?.status ?: "online",
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = username, color = colors.textPrimary, style = MaterialTheme.typography.titleSmall)
                        Text(text = "$instanceUrl · v$appVersion", color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { avatarPicker.launch("image/*") },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Alterar foto de perfil", color = colors.accentSky)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Nome de exibição") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = customStatus,
                    onValueChange = { customStatus = it },
                    label = { Text("Status personalizado") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text("Biografia") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                )
                if (!profileMsg.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(profileMsg!!, color = colors.accentMint, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
                AetherPrimaryButton(
                    text = saveLabel,
                    onClick = {
                        scope.launch {
                            profileMsg = null
                            runCatching {
                                restClient.updateMyStatus(
                                    instanceUrl = instanceUrl,
                                    token = jwtToken,
                                    displayName = displayName.trim().ifBlank { null },
                                    bio = bio.trim().ifBlank { null },
                                    customStatus = customStatus.trim().ifBlank { null },
                                )
                            }.onSuccess { updated ->
                                wsClient.updateCurrentUserLocal(updated)
                                profileMsg = "Perfil salvo!"
                            }.onFailure { err ->
                                profileMsg = err.message
                            }
                        }
                    },
                )
            }

            // Card separado e amigável: Microfone e Modo de Transmissão
            AetherSurfaceCard {
                Text(text = "Microfone e Transmissão", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Escolha como sua voz é enviada nas chamadas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
                Spacer(Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = prefs.transmissionMode == TransmissionMode.VOICE_ACTIVITY,
                        onClick = { voiceEngine.updateUserAudioPreferences { it.copy(transmissionMode = TransmissionMode.VOICE_ACTIVITY) } },
                        label = { Text("Automático (Voz)") },
                    )
                    FilterChip(
                        selected = prefs.transmissionMode == TransmissionMode.PUSH_TO_TALK,
                        onClick = { voiceEngine.updateUserAudioPreferences { it.copy(transmissionMode = TransmissionMode.PUSH_TO_TALK) } },
                        label = { Text("Apertar p/ Falar (PTT)") },
                    )
                }

                if (prefs.transmissionMode == TransmissionMode.PUSH_TO_TALK) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Segure o botão na chamada ou a tecla física escolhida abaixo para falar (sem cortar início nem fim).",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            PttHardwareKey.NONE to "Só na tela",
                            PttHardwareKey.VOLUME_UP to "Botão Vol +",
                            PttHardwareKey.VOLUME_DOWN to "Botão Vol -",
                        ).forEach { (key, label) ->
                            FilterChip(
                                selected = prefs.pttHardwareKey == key,
                                onClick = { voiceEngine.updateUserAudioPreferences { it.copy(pttHardwareKey = key) } },
                                label = { Text(label) },
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Sensibilidade do microfone (${(prefs.vadThreshold * 100).toInt()}%) — abaixe se sua voz estiver cortando; aumente se estiver pegando ruído de fundo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                    AetherPurpleSlider(
                        value = prefs.vadThreshold,
                        onValueChange = { v -> voiceEngine.updateUserAudioPreferences { it.copy(vadThreshold = v) } },
                        valueRange = 0.02f..0.8f,
                    )
                    LinearProgressIndicator(
                        progress = { voiceState.micInputLevel.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = colors.accentMint,
                        trackColor = colors.bgBase,
                    )
                }
            }

            AetherSurfaceCard {
                Text(text = audioHeader, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                Spacer(Modifier.height(8.dp))

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

                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Volume geral da chamada: ${(prefs.masterOutputVolume * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
                AetherPurpleSlider(
                    value = prefs.masterOutputVolume,
                    onValueChange = { v -> voiceEngine.updateUserAudioPreferences { it.copy(masterOutputVolume = v) } },
                    valueRange = 0f..2f,
                )

                Text("Tamanho do pacote Opus (menor = menos atraso):", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(10, 20, 40).forEach { ms ->
                        FilterChip(
                            selected = prefs.opusFrameSizeMs == ms,
                            onClick = { voiceEngine.updateUserAudioPreferences { it.copy(opusFrameSizeMs = ms) } },
                            label = { Text("${ms}ms") },
                        )
                    }
                }

                SettingsToggleRow(
                    label = "Economia de dados móveis (24 kbps)",
                    checked = prefs.dataSaverMode,
                    onCheckedChange = { v -> voiceEngine.updateUserAudioPreferences { it.copy(dataSaverMode = v) } },
                )
                SettingsToggleRow(
                    label = "Abaixar volume da sala quando eu falar",
                    checked = prefs.audioDuckingEnabled,
                    onCheckedChange = { v -> voiceEngine.updateUserAudioPreferences { it.copy(audioDuckingEnabled = v) } },
                )
                SettingsToggleRow(
                    label = "Sons do app (mutar / entrar / sair)",
                    checked = prefs.uiSoundsEnabled,
                    onCheckedChange = { v -> voiceEngine.updateUserAudioPreferences { it.copy(uiSoundsEnabled = v) } },
                )
                SettingsToggleRow(
                    label = "Cancelamento de Eco (AEC)",
                    checked = prefs.echoCancellation,
                    onCheckedChange = { v -> voiceEngine.updateUserAudioPreferences { it.copy(echoCancellation = v) } },
                )
                SettingsToggleRow(
                    label = "Supressão de Ruído (NS)",
                    checked = prefs.noiseSuppression,
                    onCheckedChange = { v -> voiceEngine.updateUserAudioPreferences { it.copy(noiseSuppression = v) } },
                )
                SettingsToggleRow(
                    label = "Ganho Automático (AGC)",
                    checked = prefs.autoGainControl,
                    onCheckedChange = { v -> voiceEngine.updateUserAudioPreferences { it.copy(autoGainControl = v) } },
                )
                SettingsToggleRow(
                    label = stereoLabel,
                    checked = prefs.stereo,
                    onCheckedChange = { v -> voiceEngine.updateUserAudioPreferences { it.copy(stereo = v) } },
                )
                SettingsToggleRow(
                    label = autoBitrateLabel,
                    checked = prefs.bitrateAuto,
                    onCheckedChange = { v -> voiceEngine.updateUserAudioPreferences { it.copy(bitrateAuto = v) } },
                )

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

                SettingsToggleRow(
                    label = idleSuspendLabel,
                    checked = prefs.idleSuspendEnabled,
                    onCheckedChange = { v ->
                        voiceEngine.updateUserAudioPreferences { it.copy(idleSuspendEnabled = v) }
                    },
                )

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

                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = { voiceEngine.resetAudioPreferencesToDefault() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Restaurar padrões de áudio", color = colors.accentAmber)
                }
            }

            AetherSurfaceCard {
                Text(text = videoHeader, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                SettingsToggleRow(
                    label = hideNonVideoLabel,
                    checked = prefs.hideNonVideoParticipants,
                    onCheckedChange = { v ->
                        voiceEngine.updateUserAudioPreferences { it.copy(hideNonVideoParticipants = v) }
                    },
                )
            }

            AetherPrimaryButton(text = checkUpdatesLabel, onClick = onCheckUpdates)

            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = logoutLabel, color = colors.accentPeach)
            }
        } else {
            // ── ABA ADMINISTRAÇÃO COMPLETA DA INSTÂNCIA ──
            val currentAdmin = adminSettings ?: InstanceAdminSettings()
            var instanceName by remember(currentAdmin) { mutableStateOf(currentAdmin.instanceName) }
            var registrationOpen by remember(currentAdmin) { mutableStateOf(currentAdmin.registrationOpen) }
            var federatedReg by remember(currentAdmin) { mutableStateOf(currentAdmin.federatedRegistrationOpen) }
            var discoveryEnabled by remember(currentAdmin) { mutableStateOf(currentAdmin.discoveryEnabled) }
            var gifEnabled by remember(currentAdmin) { mutableStateOf(currentAdmin.gifEnabled) }
            var maxUploadMb by remember(currentAdmin) { mutableStateOf(currentAdmin.maxUploadSizeMb.toFloat()) }

            var relayEnabled by remember(currentAdmin) { mutableStateOf(currentAdmin.federationRelayEnabled) }
            var autoPeering by remember(currentAdmin) { mutableStateOf(currentAdmin.autoAcceptPeering) }
            var dirEnabled by remember(currentAdmin) { mutableStateOf(currentAdmin.directoryEnabled) }
            var dirBrowse by remember(currentAdmin) { mutableStateOf(currentAdmin.directoryBrowseEnabled) }

            var videoMaxBitrate by remember(limits) { mutableStateOf(limits.maxBitrateKbps.toFloat()) }
            var allowCustomVideoBr by remember(limits) { mutableStateOf(limits.allowCustomBitrate) }
            var instDefaultAudio by remember(limits) { mutableStateOf(limits.defaultAudioBitrateKbps.toFloat()) }
            var instMaxAudio by remember(limits) { mutableStateOf(limits.maxAudioBitrateKbps.toFloat()) }
            var instMusic by remember(limits) { mutableStateOf(limits.allowMusicMode) }
            var instStereo by remember(limits) { mutableStateOf(limits.allowStereoAudio) }
            var instSoundboard by remember(limits) { mutableStateOf(limits.soundboardEnabled) }
            var instMaxSbSounds by remember(limits) { mutableStateOf(limits.maxSoundboardSoundsPerScope.toFloat()) }
            var instMaxSbConc by remember(limits) { mutableStateOf(limits.maxConcurrentSoundboardSounds.toFloat()) }

            AetherSurfaceCard {
                Text(text = instanceGeneralHeader, style = MaterialTheme.typography.titleMedium, color = colors.accentAmber)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = instanceName,
                    onValueChange = { instanceName = it },
                    label = { Text("Instance name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                SettingsToggleRow("Registration open", registrationOpen) { registrationOpen = it }
                SettingsToggleRow("Federated registration", federatedReg) { federatedReg = it }
                SettingsToggleRow("Discovery enabled", discoveryEnabled) { discoveryEnabled = it }
                SettingsToggleRow("GIFs enabled", gifEnabled) { gifEnabled = it }
                Text("Max upload: ${maxUploadMb.toInt()} MB", color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                AetherPurpleSlider(value = maxUploadMb.coerceIn(5f, 500f), onValueChange = { maxUploadMb = it }, valueRange = 5f..500f)
            }

            AetherSurfaceCard {
                Text(text = instanceFederationHeader, style = MaterialTheme.typography.titleMedium, color = colors.accentSky)
                SettingsToggleRow("Federation relay", relayEnabled) { relayEnabled = it }
                SettingsToggleRow("Auto-accept peering", autoPeering) { autoPeering = it }
                SettingsToggleRow("Directory listing", dirEnabled) { dirEnabled = it }
                SettingsToggleRow("Browse directory", dirBrowse) { dirBrowse = it }
            }

            AetherSurfaceCard {
                Text(text = instanceStreamingHeader, style = MaterialTheme.typography.titleMedium, color = colors.accentPrimary)
                Spacer(Modifier.height(6.dp))
                Text("Video max bitrate: ${videoMaxBitrate.toInt()} kbps", color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                AetherPurpleSlider(value = videoMaxBitrate.coerceIn(500f, 20000f), onValueChange = { videoMaxBitrate = it }, valueRange = 500f..20000f)
                SettingsToggleRow("Allow custom video bitrate", allowCustomVideoBr) { allowCustomVideoBr = it }

                Spacer(Modifier.height(6.dp))
                Text("Opus default: ${instDefaultAudio.toInt()} kbps", color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                AetherPurpleSlider(value = instDefaultAudio.coerceIn(8f, 512f), onValueChange = { instDefaultAudio = it }, valueRange = 8f..512f)
                Text("Opus max: ${instMaxAudio.toInt()} kbps", color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                AetherPurpleSlider(value = instMaxAudio.coerceIn(8f, 512f), onValueChange = { instMaxAudio = it }, valueRange = 8f..512f)
                SettingsToggleRow(musicModeLabel, instMusic) { instMusic = it }
                SettingsToggleRow(stereoLabel, instStereo) { instStereo = it }

                Spacer(Modifier.height(6.dp))
                SettingsToggleRow("Soundboard", instSoundboard) { instSoundboard = it }
                Text("Max sounds / scope: ${instMaxSbSounds.toInt()}", color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                AetherPurpleSlider(value = instMaxSbSounds.coerceIn(1f, 200f), onValueChange = { instMaxSbSounds = it }, valueRange = 1f..200f)
                Text("Max concurrent sounds: ${instMaxSbConc.toInt()}", color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                AetherPurpleSlider(value = instMaxSbConc.coerceIn(1f, 10f), onValueChange = { instMaxSbConc = it }, valueRange = 1f..10f)
            }

            if (!adminStatusMsg.isNullOrBlank()) {
                Text(adminStatusMsg!!, color = colors.accentMint, style = MaterialTheme.typography.bodySmall)
            }

            AetherPrimaryButton(
                text = saveLabel,
                onClick = {
                    scope.launch {
                        adminStatusMsg = null
                        val newAdmin = currentAdmin.copy(
                            instanceName = instanceName.trim().ifBlank { "Backspace" },
                            registrationOpen = registrationOpen,
                            federatedRegistrationOpen = federatedReg,
                            discoveryEnabled = discoveryEnabled,
                            gifEnabled = gifEnabled,
                            maxUploadSizeMb = maxUploadMb.toInt(),
                            federationRelayEnabled = relayEnabled,
                            autoAcceptPeering = autoPeering,
                            directoryEnabled = dirEnabled,
                            directoryBrowseEnabled = dirBrowse,
                        )
                        val newLimits = limits.copy(
                            maxBitrateKbps = videoMaxBitrate.toInt(),
                            allowCustomBitrate = allowCustomVideoBr,
                            defaultAudioBitrateKbps = instDefaultAudio.toInt(),
                            maxAudioBitrateKbps = instMaxAudio.toInt().coerceAtLeast(instDefaultAudio.toInt()),
                            allowMusicMode = instMusic,
                            allowStereoAudio = instStereo,
                            soundboardEnabled = instSoundboard,
                            maxSoundboardSoundsPerScope = instMaxSbSounds.toInt(),
                            maxConcurrentSoundboardSounds = instMaxSbConc.toInt(),
                        )
                        runCatching {
                            val savedAdmin = restClient.updateInstanceAdminSettings(instanceUrl, jwtToken, newAdmin)
                            val savedLimits = restClient.updateStreamingLimits(instanceUrl, jwtToken, newLimits)
                            savedAdmin to savedLimits
                        }.onSuccess { (savedAdmin, savedLimits) ->
                            adminSettings = savedAdmin
                            wsClient.updateStreamingLimitsLocal(savedLimits)
                            adminStatusMsg = "OK"
                        }.onFailure { err ->
                            adminStatusMsg = err.message
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun SettingsToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = AetherTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, color = colors.textPrimary)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

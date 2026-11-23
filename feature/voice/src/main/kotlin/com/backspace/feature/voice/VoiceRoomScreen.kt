package com.backspace.feature.voice

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.HeadsetOff
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.StopScreenShare
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.backspace.core.designsystem.AetherPurpleSlider
import com.backspace.core.designsystem.AetherStatusDot
import com.backspace.core.designsystem.AetherSurfaceCard
import com.backspace.core.designsystem.AetherTheme
import com.backspace.core.model.AudioMode
import com.backspace.core.model.AudioOutputRoute
import com.backspace.core.voice.BackspaceVoiceEngine
import com.backspace.core.voice.VoiceVideoTile
import io.livekit.android.renderer.TextureViewRenderer
import livekit.org.webrtc.RendererCommon

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
    hideNonVideoLabel: String,
    screenShareBadge: String,
    onOpenSoundboard: (() -> Unit)? = null,
    onBack: () -> Unit,
) {
    val colors = AetherTheme.colors
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val state by voiceEngine.state.collectAsStateWithLifecycle()
    val policy = state.effectiveAudio.policy
    var fullscreenTileId by remember { mutableStateOf<String?>(null) }
    var isLandscapeForced by remember { mutableStateOf(false) }

    val screenCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            voiceEngine.startScreenShare(result.data!!)
        }
    }

    val fullscreenTile = state.videoTiles.firstOrNull { it.id == fullscreenTileId }

    DisposableEffect(fullscreenTile != null) {
        onDispose {
            if (isLandscapeForced) {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                isLandscapeForced = false
            }
        }
    }

    if (fullscreenTile != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            LiveKitVideoBox(
                tile = fullscreenTile,
                voiceEngine = voiceEngine,
                modifier = Modifier.fillMaxSize(),
            )
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                IconButton(
                    onClick = {
                        isLandscapeForced = !isLandscapeForced
                        activity?.requestedOrientation = if (isLandscapeForced) {
                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        } else {
                            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        }
                    },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f)),
                ) {
                    Icon(Icons.Default.ScreenRotation, contentDescription = "Rotate", tint = Color.White)
                }
                IconButton(
                    onClick = {
                        fullscreenTileId = null
                        if (isLandscapeForced) {
                            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            isLandscapeForced = false
                        }
                    },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f)),
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close fullscreen", tint = Color.White)
                }
            }
        }
        return
    }

    Scaffold(
        containerColor = colors.bgBase,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.bgBase)
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
        },
        bottomBar = {
            Surface(
                color = colors.bgChannel,
                tonalElevation = 4.dp,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DockControlButton(
                        icon = if (state.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        tint = if (state.isMuted) colors.accentPeach else colors.accentMint,
                        bg = if (state.isMuted) colors.accentPeach.copy(alpha = 0.16f) else colors.bgElevated,
                        onClick = { voiceEngine.toggleMute() },
                    )
                    DockControlButton(
                        icon = if (state.isDeafened) Icons.Default.HeadsetOff else Icons.Default.Headset,
                        tint = if (state.isDeafened) colors.accentPeach else colors.textPrimary,
                        bg = if (state.isDeafened) colors.accentPeach.copy(alpha = 0.16f) else colors.bgElevated,
                        onClick = { voiceEngine.toggleDeafen() },
                    )
                    DockControlButton(
                        icon = if (state.isCameraOn) Icons.Default.Videocam else Icons.Default.VideocamOff,
                        tint = if (state.isCameraOn) colors.accentMint else colors.textPrimary,
                        bg = if (state.isCameraOn) colors.accentMint.copy(alpha = 0.16f) else colors.bgElevated,
                        onClick = { voiceEngine.toggleCamera() },
                    )
                    DockControlButton(
                        icon = if (state.isScreenSharing) Icons.Default.StopScreenShare else Icons.Default.ScreenShare,
                        tint = if (state.isScreenSharing) colors.accentSky else colors.textPrimary,
                        bg = if (state.isScreenSharing) colors.accentSky.copy(alpha = 0.16f) else colors.bgElevated,
                        onClick = {
                            if (state.isScreenSharing) {
                                voiceEngine.stopScreenShare()
                            } else {
                                val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                                screenCaptureLauncher.launch(mgr.createScreenCaptureIntent())
                            }
                        },
                    )
                    if (onOpenSoundboard != null) {
                        DockControlButton(
                            icon = Icons.Default.MusicNote,
                            tint = colors.accentPrimary,
                            bg = colors.bgElevated,
                            onClick = onOpenSoundboard,
                        )
                    }
                    DockControlButton(
                        icon = Icons.Default.CallEnd,
                        tint = Color.White,
                        bg = Color(0xFFD94A5A),
                        onClick = {
                            voiceEngine.leaveChannel()
                            onBack()
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!state.errorMessage.isNullOrBlank()) {
                Text(
                    text = state.errorMessage!!,
                    color = colors.accentPeach,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

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

            // Grade adaptativa de vídeos/transmissões (1 coluna para 1 stream, 2 colunas para múltiplos)
            if (state.videoTiles.isNotEmpty()) {
                val sortedTiles = state.videoTiles.sortedByDescending { it.isScreenShare }
                if (sortedTiles.size == 1) {
                    VideoTileCard(
                        tile = sortedTiles.first(),
                        voiceEngine = voiceEngine,
                        screenShareBadge = screenShareBadge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f),
                        onClick = { fullscreenTileId = sortedTiles.first().id },
                    )
                } else {
                    sortedTiles.chunked(2).forEach { rowTiles ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            rowTiles.forEach { tile ->
                                VideoTileCard(
                                    tile = tile,
                                    voiceEngine = voiceEngine,
                                    screenShareBadge = screenShareBadge,
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(16f / 10f),
                                    onClick = { fullscreenTileId = tile.id },
                                )
                            }
                            if (rowTiles.size == 1) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            val visibleParticipants = if (state.userPrefs.hideNonVideoParticipants && state.videoTiles.isNotEmpty()) {
                state.participants.filter { it.hasVideo }
            } else {
                state.participants
            }

            if (visibleParticipants.isNotEmpty()) {
                AetherSurfaceCard {
                    visibleParticipants.forEach { p ->
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
            }

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

                Spacer(Modifier.height(8.dp))
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = hideNonVideoLabel, color = colors.textPrimary)
                    Switch(
                        checked = state.userPrefs.hideNonVideoParticipants,
                        onCheckedChange = { checked ->
                            voiceEngine.updateUserAudioPreferences { it.copy(hideNonVideoParticipants = checked) }
                        },
                    )
                }
            }

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
            }
        }
    }
}

@Composable
private fun VideoTileCard(
    tile: VoiceVideoTile,
    voiceEngine: BackspaceVoiceEngine,
    screenShareBadge: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black)
            .clickable(onClick = onClick),
    ) {
        LiveKitVideoBox(
            tile = tile,
            voiceEngine = voiceEngine,
            modifier = Modifier.fillMaxSize(),
        )
        Surface(
            color = Color.Black.copy(alpha = 0.65f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp),
        ) {
            val badge = if (tile.isScreenShare) " · $screenShareBadge" else ""
            Text(
                text = "${tile.participantName}$badge",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun DockControlButton(
    icon: ImageVector,
    tint: Color,
    bg: Color,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .clip(CircleShape)
            .background(bg),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint)
    }
}

@Composable
private fun LiveKitVideoBox(
    tile: VoiceVideoTile,
    voiceEngine: BackspaceVoiceEngine,
    modifier: Modifier = Modifier,
) {
    val rendererHolder = remember { arrayOfNulls<TextureViewRenderer>(1) }

    DisposableEffect(tile.videoTrack) {
        onDispose {
            rendererHolder[0]?.let { r ->
                runCatching { tile.videoTrack.removeRenderer(r) }
                runCatching { r.release() }
            }
            rendererHolder[0] = null
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextureViewRenderer(ctx).also { renderer ->
                voiceEngine.initVideoRenderer(renderer)
                renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                tile.videoTrack.addRenderer(renderer)
                rendererHolder[0] = renderer
            }
        },
        update = { renderer ->
            renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
            if (rendererHolder[0] !== renderer) {
                rendererHolder[0] = renderer
            }
        },
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

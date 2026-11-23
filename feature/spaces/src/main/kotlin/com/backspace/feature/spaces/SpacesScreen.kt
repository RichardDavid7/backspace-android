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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import com.backspace.core.model.ChannelTreeBuilder
import com.backspace.core.model.ChosenUserStatus
import com.backspace.core.model.PermissionBits
import com.backspace.core.model.Space
import com.backspace.core.model.SpaceTreeItem
import com.backspace.core.model.StatusRevertDuration
import com.backspace.core.network.BackspaceWebSocketClient
import com.backspace.core.voice.BackspaceVoiceEngine
import com.backspace.core.voice.VoiceConnectionStatus

@Composable
fun SpacesScreen(
    wsClient: BackspaceWebSocketClient,
    voiceEngine: BackspaceVoiceEngine,
    spacesTitle: String,
    noSpacesText: String,
    activeVoiceBannerPrefix: String,
    statusPickerTitle: String,
    statusOnlineLabel: String,
    statusIdleLabel: String,
    statusDndLabel: String,
    customStatusPlaceholder: String,
    revertTimerLabel: String,
    saveLabel: String,
    cancelLabel: String,
    onOpenTextChannel: (Channel) -> Unit,
    onJoinVoiceChannel: (Space, ChannelCategory?, Channel) -> Unit,
    onOpenActiveVoice: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = AetherTheme.colors
    val snapshot by wsClient.snapshot.collectAsStateWithLifecycle()
    val voiceState by voiceEngine.state.collectAsStateWithLifecycle()

    var selectedSpaceId by remember(snapshot.spaces) {
        mutableStateOf(snapshot.spaces.firstOrNull()?.id)
    }
    var showStatusDialog by remember { mutableStateOf(false) }
    val collapsedCategories = remember { mutableStateMapOf<String, Boolean>() }

    val selectedSpace = snapshot.spaces.firstOrNull { it.id == selectedSpaceId }
        ?: snapshot.spaces.firstOrNull()

    if (showStatusDialog) {
        StatusPickerDialog(
            currentStatus = snapshot.currentUser?.status ?: "online",
            currentCustomStatus = snapshot.currentUser?.customStatus.orEmpty(),
            title = statusPickerTitle,
            onlineLabel = statusOnlineLabel,
            idleLabel = statusIdleLabel,
            dndLabel = statusDndLabel,
            customStatusPlaceholder = customStatusPlaceholder,
            revertTimerLabel = revertTimerLabel,
            saveLabel = saveLabel,
            cancelLabel = cancelLabel,
            onDismiss = { showStatusDialog = false },
            onSave = { status, custom, revert ->
                wsClient.setStatusWithOptionalRevert(status, custom.ifBlank { null }, revert)
                showStatusDialog = false
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgBase)
            .systemBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { showStatusDialog = true },
            ) {
                AetherStatusDot(status = snapshot.currentUser?.status ?: "offline", size = 12.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = spacesTitle,
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.textPrimary,
                    )
                    val subtitle = buildString {
                        append(snapshot.currentUser?.label ?: snapshot.connectionState.name)
                        val custom = snapshot.currentUser?.customStatus
                        if (!custom.isNullOrBlank()) append(" · $custom")
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }
            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = colors.textSecondary,
                )
            }
        }

        Spacer(Modifier.height(10.dp))

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
            Spacer(Modifier.height(10.dp))
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

        Spacer(Modifier.height(10.dp))

        if (selectedSpace != null) {
            val canConnect = PermissionBits.hasPermission(selectedSpace.myPermissions, PermissionBits.CONNECT)
            val treeItems = remember(selectedSpace) { ChannelTreeBuilder.build(selectedSpace) }
            val memberById = remember(selectedSpace.members) { selectedSpace.members.associateBy { it.userId } }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(treeItems, key = { it.stableKey }) { item ->
                    when (item) {
                        is SpaceTreeItem.CategoryHeader -> {
                            val collapsed = collapsedCategories[item.category.id] == true
                            val indent = (item.depth * 14).dp
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { collapsedCategories[item.category.id] = !collapsed }
                                    .padding(start = indent, top = 8.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = if (collapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                                    contentDescription = null,
                                    tint = colors.textTertiary,
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = item.category.name.uppercase(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.textSecondary,
                                )
                            }
                        }

                        is SpaceTreeItem.ChannelRow -> {
                            val isHidden = item.category?.let { collapsedCategories[it.id] == true } == true
                            if (!isHidden) {
                                val indent = (item.depth * 10).dp
                                val ch = item.channel
                                if (ch.isVoice) {
                                    val userIds = snapshot.voiceStates[ch.id].orEmpty()
                                    val isCurrent = voiceState.channelId == ch.id
                                    AetherSurfaceCard(
                                        modifier = Modifier
                                            .padding(start = indent)
                                            .clickable(enabled = canConnect) {
                                                onJoinVoiceChannel(selectedSpace, item.category, ch)
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
                                                Text(
                                                    text = ch.name,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = colors.textPrimary,
                                                )
                                            }
                                            if (userIds.isNotEmpty()) {
                                                Text(
                                                    text = "${userIds.size}",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = colors.accentMint,
                                                )
                                            }
                                        }
                                        if (userIds.isNotEmpty()) {
                                            Spacer(Modifier.height(6.dp))
                                            val names = userIds.map { uid -> memberById[uid]?.label ?: uid }
                                            Text(
                                                text = names.joinToString(", "),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.textSecondary,
                                            )
                                        }
                                    }
                                } else {
                                    AetherSurfaceCard(
                                        modifier = Modifier
                                            .padding(start = indent)
                                            .clickable { onOpenTextChannel(ch) },
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Tag,
                                                contentDescription = null,
                                                tint = colors.textSecondary,
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Text(
                                                text = ch.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = colors.textPrimary,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPickerDialog(
    currentStatus: String,
    currentCustomStatus: String,
    title: String,
    onlineLabel: String,
    idleLabel: String,
    dndLabel: String,
    customStatusPlaceholder: String,
    revertTimerLabel: String,
    saveLabel: String,
    cancelLabel: String,
    onDismiss: () -> Unit,
    onSave: (ChosenUserStatus, String, StatusRevertDuration) -> Unit,
) {
    val colors = AetherTheme.colors
    var chosen by remember {
        mutableStateOf(
            when (currentStatus) {
                "idle" -> ChosenUserStatus.IDLE
                "dnd" -> ChosenUserStatus.DND
                else -> ChosenUserStatus.ONLINE
            },
        )
    }
    var customText by remember { mutableStateOf(currentCustomStatus) }
    var revert by remember { mutableStateOf(StatusRevertDuration.NONE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.bgChannel,
        title = { Text(title, color = colors.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        ChosenUserStatus.ONLINE to onlineLabel,
                        ChosenUserStatus.IDLE to idleLabel,
                        ChosenUserStatus.DND to dndLabel,
                    ).forEach { (status, label) ->
                        FilterChip(
                            selected = chosen == status,
                            onClick = { chosen = status },
                            label = { Text(label) },
                        )
                    }
                }

                OutlinedTextField(
                    value = customText,
                    onValueChange = { customText = it },
                    placeholder = { Text(customStatusPlaceholder) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = revertTimerLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        StatusRevertDuration.NONE to "Off",
                        StatusRevertDuration.FIFTEEN_MIN to "15m",
                        StatusRevertDuration.ONE_HOUR to "1h",
                        StatusRevertDuration.FOUR_HOURS to "4h",
                    ).forEach { (dur, label) ->
                        FilterChip(
                            selected = revert == dur,
                            onClick = { revert = dur },
                            label = { Text(label) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(chosen, customText, revert) }) {
                Text(saveLabel, color = colors.accentPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(cancelLabel, color = colors.textSecondary)
            }
        },
    )
}

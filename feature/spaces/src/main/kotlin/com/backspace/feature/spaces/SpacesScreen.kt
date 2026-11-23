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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.backspace.core.designsystem.AetherAvatar
import com.backspace.core.designsystem.AetherPrimaryButton
import com.backspace.core.designsystem.AetherPurpleSlider
import com.backspace.core.designsystem.AetherSurfaceCard
import com.backspace.core.designsystem.AetherTheme
import com.backspace.core.model.Channel
import com.backspace.core.model.ChannelCategory
import com.backspace.core.model.ChannelTreeBuilder
import com.backspace.core.model.ChosenUserStatus
import com.backspace.core.model.ExploreSpaceItem
import com.backspace.core.model.MemberWithUser
import com.backspace.core.model.PermissionBits
import com.backspace.core.model.Space
import com.backspace.core.model.SpaceTreeItem
import com.backspace.core.model.StatusRevertDuration
import com.backspace.core.network.BackspaceRestClient
import com.backspace.core.network.BackspaceWebSocketClient
import com.backspace.core.network.UpdateSpaceAdminRequest
import com.backspace.core.voice.BackspaceVoiceEngine
import com.backspace.core.voice.VoiceConnectionStatus
import kotlinx.coroutines.launch

@Composable
fun SpacesScreen(
    instanceUrl: String,
    jwtToken: String,
    restClient: BackspaceRestClient,
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
    membersTitle: String,
    sendDmLabel: String,
    spaceAdminTitle: String,
    saveLabel: String,
    cancelLabel: String,
    onOpenDirectDm: (dmChannelId: String, dmTitle: String) -> Unit,
    onOpenTextChannel: (Channel) -> Unit,
    onJoinVoiceChannel: (Space, ChannelCategory?, Channel) -> Unit,
    onOpenActiveVoice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AetherTheme.colors
    val scope = rememberCoroutineScope()
    val snapshot by wsClient.snapshot.collectAsStateWithLifecycle()
    val voiceState by voiceEngine.state.collectAsStateWithLifecycle()

    var selectedSpaceId by remember(snapshot.spaces) {
        mutableStateOf(snapshot.spaces.firstOrNull()?.id)
    }
    var showStatusDialog by remember { mutableStateOf(false) }
    var showMembersDialog by remember { mutableStateOf(false) }
    var selectedProfileMember by remember { mutableStateOf<MemberWithUser?>(null) }
    var showSpaceAdminDialog by remember { mutableStateOf(false) }
    var showAddOrExploreDialog by remember { mutableStateOf(false) }
    val collapsedCategories = remember { mutableStateMapOf<String, Boolean>() }

    val selectedSpace = snapshot.spaces.firstOrNull { it.id == selectedSpaceId }
        ?: snapshot.spaces.firstOrNull()
    val isSpaceAdmin = selectedSpace != null && (
        PermissionBits.hasPermission(selectedSpace.myPermissions, PermissionBits.ADMINISTRATOR) ||
            selectedSpace.ownerId == snapshot.currentUser?.id
        )

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

    if (showMembersDialog && selectedSpace != null) {
        MembersListDialog(
            instanceUrl = instanceUrl,
            title = "$membersTitle (${selectedSpace.members.size})",
            members = selectedSpace.members,
            onSelectMember = { member ->
                showMembersDialog = false
                selectedProfileMember = member
            },
            onDismiss = { showMembersDialog = false },
        )
    }

    selectedProfileMember?.let { member ->
        UserProfileDialog(
            instanceUrl = instanceUrl,
            member = member,
            isMe = member.userId == snapshot.currentUser?.id,
            sendDmLabel = sendDmLabel,
            onSendDm = {
                selectedProfileMember = null
                scope.launch {
                    runCatching {
                        val dm = restClient.createOrOpenDm(instanceUrl, jwtToken, member.userId)
                        wsClient.addOrUpdateDmChannel(dm)
                        dm
                    }.onSuccess { dm ->
                        onOpenDirectDm(dm.id, dm.displayTitle(snapshot.currentUser?.id))
                    }
                }
            },
            onDismiss = { selectedProfileMember = null },
        )
    }

    if (showAddOrExploreDialog) {
        AddOrExploreSpaceDialog(
            instanceUrl = instanceUrl,
            jwtToken = jwtToken,
            restClient = restClient,
            wsClient = wsClient,
            onSpaceJoined = { spaceId ->
                selectedSpaceId = spaceId
                showAddOrExploreDialog = false
            },
            onDismiss = { showAddOrExploreDialog = false },
        )
    }

    if (showSpaceAdminDialog && selectedSpace != null) {
        SpaceAdminDialog(
            instanceUrl = instanceUrl,
            jwtToken = jwtToken,
            restClient = restClient,
            wsClient = wsClient,
            space = selectedSpace,
            title = spaceAdminTitle,
            saveLabel = saveLabel,
            cancelLabel = cancelLabel,
            onDismiss = { showSpaceAdminDialog = false },
            onSave = { req ->
                showSpaceAdminDialog = false
                scope.launch {
                    runCatching {
                        restClient.updateSpaceSettings(instanceUrl, jwtToken, selectedSpace.id, req)
                        wsClient.refreshSpaceDetail(selectedSpace.id)
                    }
                }
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgBase)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clickable { showStatusDialog = true },
            ) {
                AetherAvatar(
                    imageUrl = snapshot.currentUser?.avatarUrl(instanceUrl),
                    fallbackLabel = snapshot.currentUser?.label ?: "?",
                    size = 38.dp,
                    status = snapshot.currentUser?.status ?: "offline",
                )
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
            Row {
                IconButton(onClick = { showAddOrExploreDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Create / Join / Explore Space",
                        tint = colors.accentPrimary,
                    )
                }
                if (selectedSpace != null) {
                    IconButton(onClick = { showMembersDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = "Members",
                            tint = colors.textSecondary,
                        )
                    }
                }
                if (isSpaceAdmin) {
                    IconButton(onClick = { showSpaceAdminDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.AdminPanelSettings,
                            contentDescription = "Space Admin",
                            tint = colors.accentAmber,
                        )
                    }
                }
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

        // Lista horizontal de Espaços com ícone oficial de cada Espaço
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(snapshot.spaces, key = { it.id }) { space ->
                FilterChip(
                    selected = space.id == selectedSpace?.id,
                    onClick = { selectedSpaceId = space.id },
                    leadingIcon = {
                        AetherAvatar(
                            imageUrl = space.iconUrl(instanceUrl),
                            fallbackLabel = space.name,
                            size = 22.dp,
                        )
                    },
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
private fun MembersListDialog(
    instanceUrl: String,
    title: String,
    members: List<MemberWithUser>,
    onSelectMember: (MemberWithUser) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AetherTheme.colors
    val sorted = remember(members) {
        members.sortedWith(
            compareBy<MemberWithUser> { if ((it.user?.status ?: "offline") == "offline") 1 else 0 }
                .thenBy { it.label.lowercase() },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.bgChannel,
        title = { Text(title, color = colors.textPrimary) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(sorted, key = { it.userId }) { member ->
                    val roleColor = member.highestRoleColorHex?.let {
                        runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull()
                    } ?: colors.textPrimary

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectMember(member) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AetherAvatar(
                            imageUrl = member.user?.avatarUrl(instanceUrl),
                            fallbackLabel = member.label,
                            size = 34.dp,
                            status = member.user?.status ?: "offline",
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(text = member.label, color = roleColor, style = MaterialTheme.typography.bodyMedium)
                            val custom = member.user?.customStatus
                            if (!custom.isNullOrBlank()) {
                                Text(text = custom, color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK", color = colors.accentPrimary)
            }
        },
    )
}

@Composable
private fun UserProfileDialog(
    instanceUrl: String,
    member: MemberWithUser,
    isMe: Boolean,
    sendDmLabel: String,
    onSendDm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AetherTheme.colors
    val user = member.user
    val roleColor = member.highestRoleColorHex?.let {
        runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull()
    } ?: colors.textPrimary

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.bgChannel,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AetherAvatar(
                    imageUrl = user?.avatarUrl(instanceUrl),
                    fallbackLabel = member.label,
                    size = 48.dp,
                    status = user?.status ?: "offline",
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(text = member.label, color = roleColor, style = MaterialTheme.typography.titleMedium)
                    Text(text = "@${user?.username ?: member.userId}", color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!user?.customStatus.isNullOrBlank()) {
                    Text(text = user?.customStatus!!, color = colors.accentMint, style = MaterialTheme.typography.bodySmall)
                }
                if (!user?.bio.isNullOrBlank()) {
                    Text(text = user?.bio!!, color = colors.textPrimary, style = MaterialTheme.typography.bodyMedium)
                }
                val visibleRoles = member.roles.filter { !it.isEveryone && it.name.isNotBlank() }
                if (visibleRoles.isNotEmpty()) {
                    Text(
                        text = visibleRoles.joinToString(" · ") { it.name },
                        color = colors.accentSky,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                if (!isMe) {
                    Spacer(Modifier.height(4.dp))
                    AetherPrimaryButton(text = sendDmLabel, onClick = onSendDm)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK", color = colors.textSecondary)
            }
        },
    )
}

@Composable
private fun AddOrExploreSpaceDialog(
    instanceUrl: String,
    jwtToken: String,
    restClient: BackspaceRestClient,
    wsClient: BackspaceWebSocketClient,
    onSpaceJoined: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AetherTheme.colors
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) } // 0 = Criar, 1 = Convite, 2 = Descobrir
    var newSpaceName by remember { mutableStateOf("") }
    var newSpaceDesc by remember { mutableStateOf("") }
    var inviteInput by remember { mutableStateOf("") }
    var exploreQuery by remember { mutableStateOf("") }
    var exploreList by remember { mutableStateOf<List<ExploreSpaceItem>>(emptyList()) }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(tab, exploreQuery) {
        if (tab == 2) {
            runCatching { restClient.exploreSpaces(instanceUrl, jwtToken, exploreQuery) }
                .onSuccess { exploreList = it }
                .onFailure { statusMsg = it.message }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.bgChannel,
        title = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Criar") })
                FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("Convite") })
                FilterChip(selected = tab == 2, onClick = { tab = 2 }, label = { Text("Descobrir") })
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!statusMsg.isNullOrBlank()) {
                    Text(statusMsg!!, color = colors.accentPeach, style = MaterialTheme.typography.bodySmall)
                }
                when (tab) {
                    0 -> {
                        OutlinedTextField(
                            value = newSpaceName,
                            onValueChange = { newSpaceName = it },
                            label = { Text("Nome do Espaço") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = newSpaceDesc,
                            onValueChange = { newSpaceDesc = it },
                            label = { Text("Descrição") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        AetherPrimaryButton(
                            text = "Criar Espaço",
                            enabled = newSpaceName.isNotBlank(),
                            onClick = {
                                scope.launch {
                                    statusMsg = null
                                    runCatching {
                                        val created = restClient.createSpace(instanceUrl, jwtToken, newSpaceName.trim(), newSpaceDesc)
                                        wsClient.refreshSpaceDetail(created.id)
                                        created.id
                                    }.onSuccess(onSpaceJoined)
                                        .onFailure { statusMsg = it.message }
                                }
                            },
                        )
                    }

                    1 -> {
                        OutlinedTextField(
                            value = inviteInput,
                            onValueChange = { inviteInput = it },
                            label = { Text("Link ou código de convite") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        AetherPrimaryButton(
                            text = "Entrar no Espaço",
                            enabled = inviteInput.isNotBlank(),
                            onClick = {
                                scope.launch {
                                    statusMsg = null
                                    runCatching {
                                        val joined = restClient.joinSpaceByInvite(instanceUrl, jwtToken, inviteInput)
                                        wsClient.refreshSpaceDetail(joined.id)
                                        joined.id
                                    }.onSuccess(onSpaceJoined)
                                        .onFailure { statusMsg = it.message }
                                }
                            },
                        )
                    }

                    else -> {
                        OutlinedTextField(
                            value = exploreQuery,
                            onValueChange = { exploreQuery = it },
                            placeholder = { Text("Buscar Espaços...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 260.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(exploreList, key = { it.id }) { item ->
                                AetherSurfaceCard {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            AetherAvatar(
                                                imageUrl = item.iconUrl(instanceUrl),
                                                fallbackLabel = item.name,
                                                size = 32.dp,
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Column {
                                                Text(item.name, color = colors.textPrimary, style = MaterialTheme.typography.titleSmall)
                                                Text("${item.memberCount} membros", color = colors.textSecondary, style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                        if (!item.isMember) {
                                            TextButton(
                                                onClick = {
                                                    scope.launch {
                                                        runCatching {
                                                            restClient.publicJoinSpace(instanceUrl, jwtToken, item.id)
                                                            wsClient.refreshSpaceDetail(item.id)
                                                        }.onSuccess { onSpaceJoined(item.id) }
                                                            .onFailure { statusMsg = it.message }
                                                    }
                                                },
                                            ) {
                                                Text("Entrar", color = colors.accentMint)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK", color = colors.textSecondary)
            }
        },
    )
}

@Composable
private fun SpaceAdminDialog(
    instanceUrl: String,
    jwtToken: String,
    restClient: BackspaceRestClient,
    wsClient: BackspaceWebSocketClient,
    space: Space,
    title: String,
    saveLabel: String,
    cancelLabel: String,
    onDismiss: () -> Unit,
    onSave: (UpdateSpaceAdminRequest) -> Unit,
) {
    val colors = AetherTheme.colors
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var tab by remember { mutableIntStateOf(0) } // 0 = Geral, 1 = Canais/Convite, 2 = Opus
    var name by remember { mutableStateOf(space.name) }
    var description by remember { mutableStateOf(space.description.orEmpty()) }
    var newChannelName by remember { mutableStateOf("") }
    var newChannelType by remember { mutableStateOf("text") }
    var newCategoryName by remember { mutableStateOf("") }
    var generatedInvite by remember { mutableStateOf<String?>(null) }
    var actionFeedback by remember { mutableStateOf<String?>(null) }
    var bitrate by remember { mutableStateOf((space.audioBitrateKbps ?: 64).toFloat()) }
    var maxBitrate by remember { mutableStateOf((space.maxAudioBitrateKbps ?: 256).toFloat()) }
    var allowMusic by remember { mutableStateOf(space.allowMusicMode ?: true) }
    var allowStereo by remember { mutableStateOf(space.allowStereoAudio ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.bgChannel,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("$title · ${space.name}", color = colors.textPrimary, style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Geral") })
                    FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("Canais") })
                    FilterChip(selected = tab == 2, onClick = { tab = 2 }, label = { Text("Opus") })
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!actionFeedback.isNullOrBlank()) {
                    Text(actionFeedback!!, color = colors.accentMint, style = MaterialTheme.typography.bodySmall)
                }
                when (tab) {
                    0 -> {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Nome") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Descrição") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    runCatching { restClient.generateSpaceInvite(instanceUrl, jwtToken, space.id) }
                                        .onSuccess { code ->
                                            val link = "${instanceUrl.trimEnd('/')}/invite/$code"
                                            generatedInvite = link
                                            clipboard.setText(AnnotatedString(link))
                                            actionFeedback = "Link copiado: $link"
                                        }
                                        .onFailure { actionFeedback = it.message }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Gerar e copiar link de convite", color = colors.accentSky)
                        }
                        if (!generatedInvite.isNullOrBlank()) {
                            Text(generatedInvite!!, color = colors.textSecondary, style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    1 -> {
                        OutlinedTextField(
                            value = newChannelName,
                            onValueChange = { newChannelName = it },
                            label = { Text("Novo canal") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = newChannelType == "text",
                                onClick = { newChannelType = "text" },
                                label = { Text("# Texto") },
                            )
                            FilterChip(
                                selected = newChannelType == "voice",
                                onClick = { newChannelType = "voice" },
                                label = { Text("🔊 Voz") },
                            )
                        }
                        AetherPrimaryButton(
                            text = "Criar canal",
                            enabled = newChannelName.isNotBlank(),
                            onClick = {
                                scope.launch {
                                    runCatching {
                                        restClient.createChannel(instanceUrl, jwtToken, space.id, newChannelName.trim(), newChannelType)
                                        wsClient.refreshSpaceDetail(space.id)
                                    }.onSuccess {
                                        newChannelName = ""
                                        actionFeedback = "Canal criado!"
                                    }.onFailure { actionFeedback = it.message }
                                }
                            },
                        )
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = newCategoryName,
                            onValueChange = { newCategoryName = it },
                            label = { Text("Nova categoria") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedButton(
                            enabled = newCategoryName.isNotBlank(),
                            onClick = {
                                scope.launch {
                                    runCatching {
                                        restClient.createCategory(instanceUrl, jwtToken, space.id, newCategoryName.trim())
                                        wsClient.refreshSpaceDetail(space.id)
                                    }.onSuccess {
                                        newCategoryName = ""
                                        actionFeedback = "Categoria criada!"
                                    }.onFailure { actionFeedback = it.message }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Criar categoria", color = colors.accentPrimary)
                        }
                    }

                    else -> {
                        Text("Opus default: ${bitrate.toInt()} kbps", color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                        AetherPurpleSlider(value = bitrate, onValueChange = { bitrate = it }, valueRange = 8f..512f)
                        Text("Opus max: ${maxBitrate.toInt()} kbps", color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                        AetherPurpleSlider(value = maxBitrate, onValueChange = { maxBitrate = it }, valueRange = 8f..512f)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Music mode", color = colors.textPrimary)
                            Switch(checked = allowMusic, onCheckedChange = { allowMusic = it })
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Stereo audio", color = colors.textPrimary)
                            Switch(checked = allowStereo, onCheckedChange = { allowStereo = it })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        UpdateSpaceAdminRequest(
                            name = name.trim().ifBlank { space.name },
                            description = description.trim(),
                            audioBitrateKbps = bitrate.toInt(),
                            maxAudioBitrateKbps = maxBitrate.toInt().coerceAtLeast(bitrate.toInt()),
                            allowMusicMode = allowMusic,
                            allowStereoAudio = allowStereo,
                        ),
                    )
                },
            ) {
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

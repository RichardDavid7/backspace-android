package com.backspace.feature.dm

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.backspace.core.designsystem.AetherAvatar
import com.backspace.core.designsystem.AetherSurfaceCard
import com.backspace.core.designsystem.AetherTheme
import com.backspace.core.model.DmChannel
import com.backspace.core.model.FriendItem
import com.backspace.core.model.FriendRequestItem
import com.backspace.core.network.BackspaceRestClient
import com.backspace.core.network.BackspaceWebSocketClient
import kotlinx.coroutines.launch

@Composable
fun DmListScreen(
    instanceUrl: String,
    jwtToken: String,
    restClient: BackspaceRestClient,
    wsClient: BackspaceWebSocketClient,
    title: String,
    conversationsTabLabel: String,
    friendsTabLabel: String,
    addFriendPlaceholder: String,
    pendingRequestsHeader: String,
    emptyText: String,
    groupBadge: String,
    onOpenDm: (DmChannel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AetherTheme.colors
    val scope = rememberCoroutineScope()
    val snapshot by wsClient.snapshot.collectAsStateWithLifecycle()
    val myUserId = snapshot.currentUser?.id

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Conversas, 1 = Amigos
    var friends by remember { mutableStateOf<List<FriendItem>>(emptyList()) }
    var requests by remember { mutableStateOf<List<FriendRequestItem>>(emptyList()) }
    var addUsername by remember { mutableStateOf("") }
    var socialFeedback by remember { mutableStateOf<String?>(null) }

    fun reloadSocial() {
        scope.launch {
            runCatching { restClient.getFriends(instanceUrl, jwtToken) }.onSuccess { friends = it }
            runCatching { restClient.getFriendRequests(instanceUrl, jwtToken) }.onSuccess { requests = it }
        }
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 1) reloadSocial()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgBase)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                label = { Text(conversationsTabLabel) },
            )
            FilterChip(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                label = { Text(friendsTabLabel) },
            )
        }

        Spacer(Modifier.height(10.dp))

        if (selectedTab == 0) {
            if (snapshot.dmChannels.isEmpty()) {
                AetherSurfaceCard {
                    Text(text = emptyText, color = colors.textSecondary)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(snapshot.dmChannels, key = { it.id }) { dm ->
                        val other = dm.members.firstOrNull { it.id != myUserId }
                        AetherSurfaceCard(
                            modifier = Modifier.clickable { onOpenDm(dm) },
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AetherAvatar(
                                    imageUrl = dm.avatarUrl(instanceUrl, myUserId),
                                    fallbackLabel = dm.displayTitle(myUserId),
                                    size = 40.dp,
                                    status = if (dm.isGroup) null else other?.status,
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (dm.isGroup) {
                                            Icon(
                                                imageVector = Icons.Default.Group,
                                                contentDescription = null,
                                                tint = colors.accentPrimary,
                                            )
                                            Spacer(Modifier.width(4.dp))
                                        }
                                        Text(
                                            text = dm.displayTitle(myUserId),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = colors.textPrimary,
                                        )
                                    }
                                    val sub = if (dm.isGroup) {
                                        "$groupBadge · ${dm.members.size}"
                                    } else {
                                        other?.customStatus?.takeIf { it.isNotBlank() } ?: "@${other?.username.orEmpty()}"
                                    }
                                    Text(
                                        text = sub,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textSecondary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // ── ABA AMIGOS ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = addUsername,
                    onValueChange = { addUsername = it },
                    placeholder = { Text(addFriendPlaceholder) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(6.dp))
                IconButton(
                    enabled = addUsername.isNotBlank(),
                    onClick = {
                        val target = addUsername.trim()
                        scope.launch {
                            socialFeedback = null
                            runCatching {
                                restClient.sendFriendRequest(instanceUrl, jwtToken, target)
                            }.onSuccess {
                                addUsername = ""
                                socialFeedback = "OK"
                                reloadSocial()
                            }.onFailure { err ->
                                socialFeedback = err.message
                            }
                        }
                    },
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = "Add friend", tint = colors.accentPrimary)
                }
            }

            if (!socialFeedback.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(socialFeedback!!, color = colors.accentMint, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(10.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val incoming = requests.filter { it.direction == "incoming" || it.toUserId == myUserId }
                if (incoming.isNotEmpty()) {
                    item {
                        Text(
                            text = "$pendingRequestsHeader (${incoming.size})",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.accentAmber,
                        )
                    }
                    items(incoming, key = { "req:${it.id}" }) { req ->
                        AetherSurfaceCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AetherAvatar(
                                        imageUrl = req.user?.avatarUrl(instanceUrl),
                                        fallbackLabel = req.label,
                                        size = 34.dp,
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(req.label, color = colors.textPrimary)
                                }
                                Row {
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                runCatching { restClient.respondFriendRequest(instanceUrl, jwtToken, req.id, true) }
                                                reloadSocial()
                                            }
                                        },
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = "Accept", tint = colors.accentMint)
                                    }
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                runCatching { restClient.respondFriendRequest(instanceUrl, jwtToken, req.id, false) }
                                                reloadSocial()
                                            }
                                        },
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Decline", tint = colors.accentPeach)
                                    }
                                }
                            }
                        }
                    }
                }

                items(friends, key = { "fr:${it.targetUserId}" }) { friend ->
                    AetherSurfaceCard(
                        modifier = Modifier.clickable {
                            scope.launch {
                                runCatching {
                                    val dm = restClient.createOrOpenDm(instanceUrl, jwtToken, friend.targetUserId)
                                    wsClient.addOrUpdateDmChannel(dm)
                                    dm
                                }.onSuccess { onOpenDm(it) }
                            }
                        },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AetherAvatar(
                                imageUrl = friend.user?.avatarUrl(instanceUrl),
                                fallbackLabel = friend.label,
                                size = 38.dp,
                                status = friend.user?.status ?: "offline",
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(friend.label, color = colors.textPrimary, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = friend.user?.customStatus?.takeIf { it.isNotBlank() }
                                        ?: "@${friend.user?.username.orEmpty()}",
                                    color = colors.textSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

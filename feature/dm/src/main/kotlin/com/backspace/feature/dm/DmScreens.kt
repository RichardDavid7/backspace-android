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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Group
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
import com.backspace.core.network.BackspaceRestClient
import com.backspace.core.network.BackspaceWebSocketClient
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DmListScreen(
    instanceUrl: String,
    wsClient: BackspaceWebSocketClient,
    title: String,
    emptyText: String,
    groupBadge: String,
    onOpenDm: (DmChannel) -> Unit,
    onStartDmCall: (DmChannel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AetherTheme.colors
    val snapshot by wsClient.snapshot.collectAsStateWithLifecycle()
    val myUserId = snapshot.currentUser?.id

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgBase)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)

        Spacer(Modifier.height(10.dp))

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
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f),
                            ) {
                                AetherAvatar(
                                    imageUrl = dm.avatarUrl(instanceUrl, myUserId),
                                    fallbackLabel = dm.displayTitle(myUserId),
                                    size = 38.dp,
                                    status = if (dm.isGroup) null else other?.status,
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
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
                            IconButton(onClick = { onStartDmCall(dm) }) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = "Call",
                                    tint = colors.accentMint,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DmChatScreen(
    instanceUrl: String,
    jwtToken: String,
    dmChannelId: String,
    dmTitle: String,
    restClient: BackspaceRestClient,
    wsClient: BackspaceWebSocketClient,
    messagePlaceholder: String,
    emptyText: String,
    onStartCall: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = AetherTheme.colors
    val scope = rememberCoroutineScope()
    val snapshot by wsClient.snapshot.collectAsStateWithLifecycle()
    val messages = snapshot.messagesByChannel[dmChannelId].orEmpty()
    val myUserId = snapshot.currentUser?.id
    val listState = rememberLazyListState()
    val timeFormatter = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }

    var draft by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(dmChannelId) {
        runCatching {
            val history = restClient.getDmMessages(instanceUrl, jwtToken, dmChannelId)
            wsClient.mergeChannelHistory(dmChannelId, history.map { it.toChannelMessage() })
        }.onFailure { err -> errorText = err.message }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgBase)
            .systemBarsPadding()
            .imePadding()
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
                }
                Text(text = dmTitle, style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
            }
            IconButton(onClick = onStartCall) {
                Icon(Icons.Default.Call, contentDescription = "Call", tint = colors.accentMint)
            }
        }

        if (!errorText.isNullOrBlank()) {
            Text(text = errorText!!, color = colors.accentPeach, style = MaterialTheme.typography.bodySmall)
        }

        if (messages.isEmpty()) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = emptyText, color = colors.textTertiary)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(messages, key = { it.id }) { msg ->
                    val isMine = msg.userId == myUserId
                    val time = if (msg.createdAt > 0L) timeFormatter.format(Date(msg.createdAt)) else ""
                    AetherSurfaceCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            if (isMine) {
                                Text(text = time, style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = msg.authorLabel, style = MaterialTheme.typography.labelLarge, color = colors.accentMint)
                                    Spacer(Modifier.width(6.dp))
                                    AetherAvatar(
                                        imageUrl = msg.user?.avatarUrl(instanceUrl),
                                        fallbackLabel = msg.authorLabel,
                                        size = 24.dp,
                                    )
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AetherAvatar(
                                        imageUrl = msg.user?.avatarUrl(instanceUrl),
                                        fallbackLabel = msg.authorLabel,
                                        size = 24.dp,
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(text = msg.authorLabel, style = MaterialTheme.typography.labelLarge, color = colors.accentSky)
                                }
                                Text(text = time, style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
                            }
                        }
                        if (msg.safeContent.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(text = msg.safeContent, color = colors.textPrimary)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text(messagePlaceholder) },
                modifier = Modifier.weight(1f),
                maxLines = 4,
            )
            Spacer(Modifier.width(6.dp))
            IconButton(
                enabled = draft.isNotBlank() && !isSending,
                onClick = {
                    val content = draft.trim()
                    if (content.isBlank()) return@IconButton
                    scope.launch {
                        isSending = true
                        errorText = null
                        runCatching {
                            val sent = restClient.sendDmMessage(instanceUrl, jwtToken, dmChannelId, content)
                            wsClient.appendLocalMessage(dmChannelId, sent.toChannelMessage())
                            draft = ""
                        }.onFailure { err -> errorText = err.message }
                        isSending = false
                    }
                },
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (draft.isNotBlank()) colors.accentPrimary else colors.textTertiary,
                )
            }
        }
    }
}

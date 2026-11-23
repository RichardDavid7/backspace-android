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
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.backspace.core.designsystem.AetherAvatar
import com.backspace.core.designsystem.AetherSurfaceCard
import com.backspace.core.designsystem.AetherTheme
import com.backspace.core.model.DmChannel
import com.backspace.core.network.BackspaceWebSocketClient

@Composable
fun DmListScreen(
    instanceUrl: String,
    wsClient: BackspaceWebSocketClient,
    title: String,
    emptyText: String,
    groupBadge: String,
    onOpenDm: (DmChannel) -> Unit,
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
    }
}

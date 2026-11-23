package com.backspace.feature.chat

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.backspace.core.designsystem.AetherSurfaceCard
import com.backspace.core.designsystem.AetherTheme
import com.backspace.core.network.BackspaceRestClient
import com.backspace.core.network.BackspaceWebSocketClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatChannelScreen(
    instanceUrl: String,
    jwtToken: String,
    channelId: String,
    channelName: String,
    restClient: BackspaceRestClient,
    wsClient: BackspaceWebSocketClient,
    messagePlaceholder: String,
    emptyChatText: String,
    onBack: () -> Unit,
) {
    val colors = AetherTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snapshot by wsClient.snapshot.collectAsStateWithLifecycle()
    val messages = snapshot.messagesByChannel[channelId].orEmpty()
    val listState = rememberLazyListState()
    val timeFormatter = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }

    var draft by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isSending = true
            errorText = null
            runCatching {
                val (fileName, mimeType, bytes) = readUriPayload(context, uri)
                val uploaded = restClient.uploadFileTus(
                    instanceUrl = instanceUrl,
                    token = jwtToken,
                    originalName = fileName,
                    mimeType = mimeType,
                    bytes = bytes,
                )
                val sent = restClient.sendMessage(
                    instanceUrl = instanceUrl,
                    token = jwtToken,
                    channelId = channelId,
                    content = draft.trim().ifBlank { null },
                    attachmentIds = listOf(uploaded.id),
                )
                wsClient.appendLocalMessage(channelId, sent)
                draft = ""
            }.onFailure { err ->
                errorText = err.message
            }
            isSending = false
        }
    }

    LaunchedEffect(channelId) {
        runCatching {
            val history = restClient.getChannelMessages(instanceUrl, jwtToken, channelId)
            wsClient.mergeChannelHistory(channelId, history)
            wsClient.sendChannelAck(channelId)
        }.onFailure { err ->
            errorText = err.message
        }
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
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = colors.textPrimary,
                )
            }
            Text(
                text = "# $channelName",
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
            )
        }

        if (!errorText.isNullOrBlank()) {
            Text(
                text = errorText!!,
                color = colors.accentPeach,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }

        if (messages.isEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = emptyChatText, color = colors.textTertiary)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(messages, key = { it.id }) { msg ->
                    AetherSurfaceCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = msg.authorLabel,
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.accentSky,
                            )
                            val formattedTime = if (msg.createdAt > 0L) {
                                timeFormatter.format(Date(msg.createdAt))
                            } else {
                                ""
                            }
                            Text(
                                text = formattedTime,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textTertiary,
                            )
                        }
                        if (msg.safeContent.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = msg.safeContent,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textPrimary,
                            )
                        }
                        if (msg.attachments.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            msg.attachments.forEach { att ->
                                val fullUrl = "$instanceUrl${att.downloadPath()}"
                                Text(
                                    text = "📎 ${att.displayName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.accentPrimary,
                                    modifier = Modifier
                                        .clickable {
                                            runCatching {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl)),
                                                )
                                            }
                                        }
                                        .padding(vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                enabled = !isSending,
                onClick = { filePickerLauncher.launch("*/*") },
            ) {
                Icon(
                    imageVector = Icons.Default.AttachFile,
                    contentDescription = "Attach file",
                    tint = colors.textSecondary,
                )
            }
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text(messagePlaceholder) },
                modifier = Modifier.weight(1f),
                maxLines = 4,
            )
            Spacer(Modifier.width(6.dp))
            if (isSending) {
                CircularProgressIndicator(
                    color = colors.accentPrimary,
                    modifier = Modifier.padding(8.dp),
                )
            } else {
                IconButton(
                    enabled = draft.isNotBlank(),
                    onClick = {
                        val content = draft.trim()
                        if (content.isBlank()) return@IconButton
                        scope.launch {
                            isSending = true
                            errorText = null
                            runCatching {
                                val sent = restClient.sendMessage(instanceUrl, jwtToken, channelId, content)
                                wsClient.appendLocalMessage(channelId, sent)
                                draft = ""
                            }.onFailure { err ->
                                errorText = err.message
                            }
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
}

private suspend fun readUriPayload(
    context: Context,
    uri: Uri,
): Triple<String, String, ByteArray> = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    var fileName = "attachment"
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) fileName = cursor.getString(idx) ?: fileName
        }
    }
    val mimeType = resolver.getType(uri) ?: "application/octet-stream"
    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
        ?: throw IllegalStateException("Cannot read selected file")
    Triple(fileName, mimeType, bytes)
}

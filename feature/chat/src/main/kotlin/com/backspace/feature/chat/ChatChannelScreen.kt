package com.backspace.feature.chat

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.backspace.core.designsystem.AetherSurfaceCard
import com.backspace.core.designsystem.AetherTheme
import com.backspace.core.model.Message
import com.backspace.core.model.PendingAttachmentPayload
import com.backspace.core.network.BackspaceRestClient
import com.backspace.core.network.BackspaceWebSocketClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private val QUICK_EMOJIS = listOf("👍", "❤️", "😂", "😮", "😢", "🔥")

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
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
    queuedLabel: String,
    replyingToPrefix: String,
    replyActionLabel: String,
    deleteActionLabel: String,
    onBack: () -> Unit,
) {
    val colors = AetherTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snapshot by wsClient.snapshot.collectAsStateWithLifecycle()
    val messages = snapshot.messagesByChannel[channelId].orEmpty()
    val queuedForChannel = remember(snapshot.queuedMessages, channelId) {
        snapshot.queuedMessages.filter { it.channelId == channelId }
    }
    val myUserId = snapshot.currentUser?.id

    // Mapeia cores dos cargos dos membros do Espaço deste canal
    val roleColorByUserId = remember(snapshot.spaces, channelId) {
        val space = snapshot.spaces.firstOrNull { s -> s.channels.any { it.id == channelId } }
        space?.members.orEmpty().mapNotNull { member ->
            val hex = member.highestRoleColorHex ?: return@mapNotNull null
            val parsed = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()
                ?: return@mapNotNull null
            member.userId to parsed
        }.toMap()
    }

    val listState = rememberLazyListState()
    val timeFormatter = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }

    var draft by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<Message?>(null) }
    var selectedMessageForActions by remember { mutableStateOf<Message?>(null) }
    var fullscreenImageUrl by remember { mutableStateOf<String?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val pendingAttachments = remember { mutableStateListOf<PendingAttachmentPayload>() }

    fun attachUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        scope.launch {
            uris.forEach { uri ->
                runCatching { readUriPayload(context, uri) }
                    .onSuccess { (fileName, mimeType, bytes) ->
                        pendingAttachments.add(
                            PendingAttachmentPayload(
                                id = UUID.randomUUID().toString(),
                                fileName = fileName,
                                mimeType = mimeType,
                                bytes = bytes,
                            ),
                        )
                    }
                    .onFailure { err -> errorText = err.message }
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
    ) { uris -> attachUris(uris) }

    // Permite colar imagens/prints direto do teclado Android (Gboard, Samsung Keyboard, etc.)
    val receiveContentListener = remember {
        ReceiveContentListener { transferableContent ->
            if (!transferableContent.hasMediaType(MediaType.Image)) {
                return@ReceiveContentListener transferableContent
            }
            val uris = mutableListOf<Uri>()
            val remaining = transferableContent.consume { item ->
                val uri = item.uri
                if (uri != null) {
                    uris.add(uri)
                    true
                } else {
                    false
                }
            }
            attachUris(uris)
            remaining
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

    LaunchedEffect(messages.size, queuedForChannel.size) {
        val total = messages.size + queuedForChannel.size
        if (total > 0) {
            listState.animateScrollToItem(total - 1)
        }
    }

    // Visualizador de imagem em tela cheia
    if (fullscreenImageUrl != null) {
        val imgUrl = fullscreenImageUrl!!
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            AsyncImage(
                model = imgUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
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
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(imgUrl))) }
                    },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f)),
                ) {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = "Open", tint = Color.White)
                }
                IconButton(
                    onClick = { fullscreenImageUrl = null },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f)),
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
        return
    }

    // Menu de interações da mensagem (Reagir, Responder, Apagar)
    selectedMessageForActions?.let { targetMsg ->
        AlertDialog(
            onDismissRequest = { selectedMessageForActions = null },
            containerColor = colors.bgChannel,
            title = {
                Text(
                    text = targetMsg.authorLabel,
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        QUICK_EMOJIS.forEach { emoji ->
                            Text(
                                text = emoji,
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable {
                                        val alreadyReacted = targetMsg.reactions.any {
                                            it.userId == myUserId && it.emoji == emoji
                                        }
                                        if (alreadyReacted) {
                                            wsClient.sendReactionRemove(targetMsg.id, emoji)
                                        } else {
                                            wsClient.sendReactionAdd(targetMsg.id, emoji)
                                        }
                                        selectedMessageForActions = null
                                    }
                                    .padding(6.dp),
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                replyingTo = targetMsg
                                selectedMessageForActions = null
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, tint = colors.accentSky)
                        Spacer(Modifier.width(10.dp))
                        Text(replyActionLabel, color = colors.textPrimary)
                    }

                    if (targetMsg.userId == myUserId) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val msgId = targetMsg.id
                                    selectedMessageForActions = null
                                    scope.launch {
                                        runCatching {
                                            restClient.deleteMessage(instanceUrl, jwtToken, msgId)
                                            wsClient.removeLocalMessage(channelId, msgId)
                                        }.onFailure { err -> errorText = err.message }
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = colors.accentPeach)
                            Spacer(Modifier.width(10.dp))
                            Text(deleteActionLabel, color = colors.accentPeach)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedMessageForActions = null }) {
                    Text("OK", color = colors.textSecondary)
                }
            },
        )
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

        if (messages.isEmpty() && queuedForChannel.isEmpty()) {
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
                    val isMine = msg.userId == myUserId
                    val authorColor = roleColorByUserId[msg.userId]
                        ?: if (isMine) colors.accentMint else colors.accentSky
                    val formattedTime = if (msg.createdAt > 0L) {
                        timeFormatter.format(Date(msg.createdAt))
                    } else {
                        ""
                    }

                    AetherSurfaceCard(
                        modifier = Modifier.combinedClickable(
                            onClick = {},
                            onLongClick = { selectedMessageForActions = msg },
                        ),
                    ) {
                        // Preview da mensagem respondida
                        msg.replyTo?.let { reply ->
                            Surface(
                                color = colors.bgBase.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp),
                            ) {
                                Text(
                                    text = "↩ ${reply.authorLabel}: ${reply.safeContent.ifBlank { "[anexo]" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }

                        // Cabeçalho: nome de quem enviou à direita nas suas mensagens (ou à esquerda nos outros)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (isMine) {
                                Text(
                                    text = formattedTime,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textTertiary,
                                )
                                Text(
                                    text = msg.authorLabel,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = authorColor,
                                )
                            } else {
                                Text(
                                    text = msg.authorLabel,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = authorColor,
                                )
                                Text(
                                    text = formattedTime,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textTertiary,
                                )
                            }
                        }

                        if (msg.safeContent.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = msg.safeContent,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textPrimary,
                            )
                        }

                        // Anexos recebidos: prévia compacta para imagens + card para outros arquivos
                        if (msg.attachments.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                msg.attachments.forEach { att ->
                                    val fullUrl = "$instanceUrl${att.downloadPath()}"
                                    if (att.isImage) {
                                        val thumbUrl = att.thumbnailFilename?.let { "$instanceUrl/api/uploads/$it" }
                                            ?: fullUrl
                                        AsyncImage(
                                            model = thumbUrl,
                                            contentDescription = att.displayName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth(0.75f)
                                                .heightIn(min = 90.dp, max = 180.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(colors.bgBase)
                                                .clickable { fullscreenImageUrl = fullUrl },
                                        )
                                    } else {
                                        Surface(
                                            color = colors.bgElevated,
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    runCatching {
                                                        context.startActivity(
                                                            Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl)),
                                                        )
                                                    }
                                                },
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Description,
                                                    contentDescription = null,
                                                    tint = colors.accentPrimary,
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = att.displayName,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = colors.textPrimary,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                    if (att.size > 0) {
                                                        Text(
                                                            text = formatFileSize(att.size),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = colors.textTertiary,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Reações agrupadas
                        if (msg.reactions.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            val grouped = msg.reactions.groupBy { it.emoji }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                grouped.forEach { (emoji, list) ->
                                    val iReacted = list.any { it.userId == myUserId }
                                    Surface(
                                        color = if (iReacted) colors.accentPrimary.copy(alpha = 0.28f) else colors.bgElevated,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.clickable {
                                            if (iReacted) {
                                                wsClient.sendReactionRemove(msg.id, emoji)
                                            } else {
                                                wsClient.sendReactionAdd(msg.id, emoji)
                                            }
                                        },
                                    ) {
                                        Text(
                                            text = "$emoji ${list.size}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = colors.textPrimary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Mensagens na fila offline
                items(queuedForChannel, key = { it.localId }) { q ->
                    AetherSurfaceCard {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = colors.accentAmber,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = queuedLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.accentAmber,
                            )
                        }
                        if (!q.content.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(text = q.content!!, color = colors.textSecondary)
                        }
                        if (q.attachments.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = q.attachments.joinToString(", ") { "📎 ${it.fileName}" },
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textTertiary,
                            )
                        }
                    }
                }
            }
        }

        // Barra "Respondendo a..."
        replyingTo?.let { reply ->
            Spacer(Modifier.height(6.dp))
            Surface(
                color = colors.bgChannel,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "$replyingToPrefix ${reply.authorLabel}",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.accentSky,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { replyingTo = null },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel reply", tint = colors.textSecondary)
                    }
                }
            }
        }

        // Bandeja de prévia de anexos pendentes (com miniatura de imagem ou ícone + nome + remover)
        if (pendingAttachments.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(pendingAttachments, key = { it.id }) { att ->
                    PendingAttachmentChip(
                        attachment = att,
                        onRemove = { pendingAttachments.remove(att) },
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { filePickerLauncher.launch("*/*") }) {
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
                modifier = Modifier
                    .weight(1f)
                    .contentReceiver(receiveContentListener),
                maxLines = 4,
            )
            Spacer(Modifier.width(6.dp))
            val canSend = draft.isNotBlank() || pendingAttachments.isNotEmpty()
            IconButton(
                enabled = canSend,
                onClick = {
                    val contentToSend = draft.trim().ifBlank { null }
                    val attsToSend = pendingAttachments.toList()
                    val replyId = replyingTo?.id
                    draft = ""
                    pendingAttachments.clear()
                    replyingTo = null
                    errorText = null
                    wsClient.enqueueOrSendMessage(
                        channelId = channelId,
                        content = contentToSend,
                        replyToId = replyId,
                        attachments = attsToSend,
                    )
                },
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (canSend) colors.accentPrimary else colors.textTertiary,
                )
            }
        }
    }
}

@Composable
private fun PendingAttachmentChip(
    attachment: PendingAttachmentPayload,
    onRemove: () -> Unit,
) {
    val colors = AetherTheme.colors
    val previewBitmap = remember(attachment.id) {
        if (attachment.isImage) {
            runCatching {
                BitmapFactory.decodeByteArray(attachment.bytes, 0, attachment.bytes.size)?.asImageBitmap()
            }.getOrNull()
        } else {
            null
        }
    }

    Surface(
        color = colors.bgChannel,
        shape = RoundedCornerShape(12.dp),
    ) {
        Box(modifier = Modifier.padding(8.dp)) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(84.dp),
            ) {
                if (previewBitmap != null) {
                    Image(
                        bitmap = previewBitmap,
                        contentDescription = attachment.fileName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.bgElevated),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = colors.accentPrimary,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = attachment.fileName,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.7f)),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_048_576L -> String.format(Locale.getDefault(), "%.1f MB", bytes / 1_048_576.0)
    bytes >= 1_024L -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1_024.0)
    else -> "$bytes B"
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

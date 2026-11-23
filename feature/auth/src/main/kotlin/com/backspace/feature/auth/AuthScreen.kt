package com.backspace.feature.auth

import androidx.compose.foundation.background
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.backspace.core.designsystem.AetherPrimaryButton
import com.backspace.core.designsystem.AetherSurfaceCard
import com.backspace.core.designsystem.AetherTheme
import com.backspace.core.network.BackspaceRestClient
import com.backspace.core.network.SecureSessionStorage
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    restClient: BackspaceRestClient,
    sessionStorage: SecureSessionStorage,
    title: String,
    subtitle: String,
    instanceUrlLabel: String,
    usernameLabel: String,
    passwordLabel: String,
    connectButtonText: String,
    onAuthenticated: (instanceUrl: String, token: String) -> Unit,
) {
    val colors = AetherTheme.colors
    val scope = rememberCoroutineScope()

    var isRegisterMode by remember { mutableStateOf(false) }
    var instanceUrl by remember { mutableStateOf("https://") }
    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var inviteToken by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgBase)
            .systemBarsPadding()
            .imePadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(24.dp))

        AetherSurfaceCard {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !isRegisterMode,
                    onClick = { isRegisterMode = false },
                    label = { Text(connectButtonText) },
                )
                FilterChip(
                    selected = isRegisterMode,
                    onClick = { isRegisterMode = true },
                    label = { Text("Criar Conta") },
                )
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = instanceUrl,
                onValueChange = { instanceUrl = it },
                label = { Text(instanceUrlLabel) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text(usernameLabel) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (isRegisterMode) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Nome de exibição (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(passwordLabel) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            if (isRegisterMode) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = inviteToken,
                    onValueChange = { inviteToken = it },
                    label = { Text("Código de convite (se exigido)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (!errorMessage.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = errorMessage!!,
                    color = colors.accentPeach,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(Modifier.height(18.dp))
            if (isLoading) {
                CircularProgressIndicator(
                    color = colors.accentPrimary,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            } else {
                AetherPrimaryButton(
                    text = if (isRegisterMode) "Criar Conta" else connectButtonText,
                    enabled = instanceUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
                    onClick = {
                        scope.launch {
                            isLoading = true
                            errorMessage = null
                            runCatching {
                                val normalized = SecureSessionStorage.normalizeInstanceUrl(instanceUrl)
                                restClient.getInstanceInfo(normalized)
                                val auth = if (isRegisterMode) {
                                    restClient.register(
                                        instanceUrl = normalized,
                                        username = username.trim(),
                                        password = password,
                                        displayName = displayName.trim().ifBlank { null },
                                        inviteToken = inviteToken.trim().ifBlank { null },
                                    )
                                } else {
                                    restClient.login(normalized, username.trim(), password)
                                }
                                sessionStorage.save(normalized, auth.token)
                                normalized to auth.token
                            }.onSuccess { (url, token) ->
                                onAuthenticated(url, token)
                            }.onFailure { err ->
                                errorMessage = err.message ?: "Authentication failed"
                            }
                            isLoading = false
                        }
                    },
                )
            }
        }
    }
}

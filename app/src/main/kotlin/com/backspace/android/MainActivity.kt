package com.backspace.android

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.backspace.core.designsystem.BackspaceAetherTheme
import com.backspace.core.network.StoredSession
import com.backspace.feature.auth.AuthScreen
import com.backspace.feature.spaces.SpacesScreen
import com.backspace.feature.voice.VoiceRoomScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as BackspaceApplication).container

        setContent {
            BackspaceAetherTheme {
                BackspaceRootApp(container = container)
            }
        }
    }
}

@Composable
private fun BackspaceRootApp(container: AppContainer) {
    val navController = rememberNavController()
    var session by remember { mutableStateOf(container.sessionStorage.load()) }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { }

    LaunchedEffect(Unit) {
        val perms = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        permissionsLauncher.launch(perms.toTypedArray())
    }

    LaunchedEffect(session) {
        val active = session
        if (active != null) {
            container.wsClient.connect(active.instanceUrl, active.token)
        }
    }

    val startDestination = if (session != null) "spaces" else "auth"

    NavHost(navController = navController, startDestination = startDestination) {
        composable("auth") {
            AuthScreen(
                restClient = container.restClient,
                sessionStorage = container.sessionStorage,
                title = stringResource(R.string.app_name),
                subtitle = stringResource(R.string.auth_subtitle),
                instanceUrlLabel = stringResource(R.string.auth_instance_url),
                usernameLabel = stringResource(R.string.auth_username),
                passwordLabel = stringResource(R.string.auth_password),
                connectButtonText = stringResource(R.string.auth_connect),
                onAuthenticated = { url, token ->
                    session = StoredSession(url, token)
                    navController.navigate("spaces") {
                        popUpTo("auth") { inclusive = true }
                    }
                },
            )
        }

        composable("spaces") {
            SpacesScreen(
                wsClient = container.wsClient,
                voiceEngine = container.voiceEngine,
                spacesTitle = stringResource(R.string.spaces_title),
                voiceChannelsHeader = stringResource(R.string.spaces_voice_channels),
                noSpacesText = stringResource(R.string.spaces_empty),
                activeVoiceBannerPrefix = stringResource(R.string.voice_connected_banner),
                onJoinVoiceChannel = { space, category, channel ->
                    val active = session ?: return@SpacesScreen
                    container.voiceEngine.joinChannel(
                        instanceUrl = active.instanceUrl,
                        jwtToken = active.token,
                        space = space,
                        category = category,
                        channel = channel,
                    )
                    navController.navigate("voice")
                },
                onOpenActiveVoice = { navController.navigate("voice") },
                onLogout = {
                    container.voiceEngine.leaveChannel()
                    container.wsClient.disconnect()
                    container.sessionStorage.clear()
                    session = null
                    navController.navigate("auth") {
                        popUpTo("spaces") { inclusive = true }
                    }
                },
            )
        }

        composable("voice") {
            VoiceRoomScreen(
                voiceEngine = container.voiceEngine,
                opusTitle = stringResource(R.string.voice_opus_title),
                telemetryTitle = stringResource(R.string.voice_telemetry_title),
                voiceModeLabel = stringResource(R.string.voice_mode_voice),
                musicModeLabel = stringResource(R.string.voice_mode_music),
                stereoLabel = stringResource(R.string.voice_stereo),
                autoBitrateLabel = stringResource(R.string.voice_bitrate_auto),
                idleSuspendLabel = stringResource(R.string.voice_idle_suspend),
                onBack = { navController.popBackStack() },
            )
        }
    }
}

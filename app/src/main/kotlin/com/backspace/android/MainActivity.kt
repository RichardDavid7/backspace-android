package com.backspace.android

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.backspace.core.designsystem.BackspaceAetherTheme
import com.backspace.core.network.StoredSession
import com.backspace.feature.auth.AuthScreen
import com.backspace.feature.chat.ChatChannelScreen
import com.backspace.feature.settings.SettingsScreen
import com.backspace.feature.soundboard.SoundboardScreen
import com.backspace.feature.spaces.SpacesScreen
import com.backspace.feature.voice.VoiceRoomScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    var session by remember { mutableStateOf(container.sessionStorage.load()) }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { }

    LaunchedEffect(Unit) {
        val perms = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.CAMERA)
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
    val updateUpToDateText = stringResource(R.string.updater_up_to_date)

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
                noSpacesText = stringResource(R.string.spaces_empty),
                activeVoiceBannerPrefix = stringResource(R.string.voice_connected_banner),
                statusPickerTitle = stringResource(R.string.status_picker_title),
                statusOnlineLabel = stringResource(R.string.status_online),
                statusIdleLabel = stringResource(R.string.status_idle),
                statusDndLabel = stringResource(R.string.status_dnd),
                customStatusPlaceholder = stringResource(R.string.status_custom_placeholder),
                revertTimerLabel = stringResource(R.string.status_revert_timer),
                saveLabel = stringResource(R.string.common_save),
                cancelLabel = stringResource(R.string.common_cancel),
                onOpenTextChannel = { channel ->
                    val encodedName = Uri.encode(channel.name)
                    navController.navigate("chat/${channel.id}/$encodedName")
                },
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
                onOpenSettings = { navController.navigate("settings") },
            )
        }

        composable("settings") {
            val active = session
            val currentUserLabel = container.wsClient.snapshot.value.currentUser?.label ?: "-"
            SettingsScreen(
                instanceUrl = active?.instanceUrl ?: "-",
                username = currentUserLabel,
                appVersion = "0.3.0-alpha",
                voiceEngine = container.voiceEngine,
                title = stringResource(R.string.settings_title),
                accountHeader = stringResource(R.string.settings_account),
                audioHeader = stringResource(R.string.voice_opus_title),
                videoHeader = stringResource(R.string.settings_video),
                voiceModeLabel = stringResource(R.string.voice_mode_voice),
                musicModeLabel = stringResource(R.string.voice_mode_music),
                stereoLabel = stringResource(R.string.voice_stereo),
                autoBitrateLabel = stringResource(R.string.voice_bitrate_auto),
                idleSuspendLabel = stringResource(R.string.voice_idle_suspend),
                soundboardVolumeLabel = stringResource(R.string.soundboard_volume),
                hideNonVideoLabel = stringResource(R.string.voice_hide_non_video),
                checkUpdatesLabel = stringResource(R.string.settings_check_updates),
                logoutLabel = stringResource(R.string.settings_logout),
                onCheckUpdates = {
                    scope.launch {
                        val repoSlug = context.getString(R.string.updater_repo_slug)
                        val result = container.releaseUpdater.checkLatestRelease(
                            repoSlug = repoSlug,
                            currentVersion = "0.3.0-alpha",
                        )
                        if (result != null && result.updateAvailable && result.downloadUrl.isNotBlank()) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.downloadUrl)))
                        } else {
                            Toast.makeText(context, updateUpToDateText, Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onLogout = {
                    container.voiceEngine.leaveChannel()
                    container.wsClient.disconnect()
                    container.sessionStorage.clear()
                    session = null
                    navController.navigate("auth") {
                        popUpTo("spaces") { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = "chat/{channelId}/{channelName}",
            arguments = listOf(
                navArgument("channelId") { type = NavType.StringType },
                navArgument("channelName") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val active = session ?: return@composable
            val channelId = backStackEntry.arguments?.getString("channelId").orEmpty()
            val channelName = Uri.decode(backStackEntry.arguments?.getString("channelName").orEmpty())
            ChatChannelScreen(
                instanceUrl = active.instanceUrl,
                jwtToken = active.token,
                channelId = channelId,
                channelName = channelName,
                restClient = container.restClient,
                wsClient = container.wsClient,
                messagePlaceholder = stringResource(R.string.chat_message_placeholder),
                emptyChatText = stringResource(R.string.chat_empty),
                onBack = { navController.popBackStack() },
            )
        }

        composable("voice") {
            val activeSpaceId = container.voiceEngine.state.value.spaceId
            VoiceRoomScreen(
                voiceEngine = container.voiceEngine,
                opusTitle = stringResource(R.string.voice_opus_title),
                telemetryTitle = stringResource(R.string.voice_telemetry_title),
                voiceModeLabel = stringResource(R.string.voice_mode_voice),
                musicModeLabel = stringResource(R.string.voice_mode_music),
                stereoLabel = stringResource(R.string.voice_stereo),
                autoBitrateLabel = stringResource(R.string.voice_bitrate_auto),
                idleSuspendLabel = stringResource(R.string.voice_idle_suspend),
                hideNonVideoLabel = stringResource(R.string.voice_hide_non_video),
                screenShareBadge = stringResource(R.string.voice_screen_share_badge),
                onOpenSoundboard = activeSpaceId?.let { spaceId ->
                    { navController.navigate("soundboard/$spaceId") }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = "soundboard/{spaceId}",
            arguments = listOf(navArgument("spaceId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val active = session ?: return@composable
            val spaceId = backStackEntry.arguments?.getString("spaceId").orEmpty()
            SoundboardScreen(
                instanceUrl = active.instanceUrl,
                jwtToken = active.token,
                spaceId = spaceId,
                restClient = container.restClient,
                voiceEngine = container.voiceEngine,
                title = stringResource(R.string.soundboard_title),
                searchPlaceholder = stringResource(R.string.soundboard_search),
                volumeLabel = stringResource(R.string.soundboard_volume),
                emptyText = stringResource(R.string.soundboard_empty),
                onBack = { navController.popBackStack() },
            )
        }
    }
}

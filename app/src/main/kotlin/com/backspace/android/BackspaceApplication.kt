package com.backspace.android

import android.app.Application
import com.backspace.core.network.BackspaceRestClient
import com.backspace.core.network.BackspaceWebSocketClient
import com.backspace.core.network.SecureSessionStorage
import com.backspace.core.updater.GitHubReleaseUpdater
import com.backspace.core.voice.BackspaceVoiceEngine

class AppContainer(application: Application) {
    val sessionStorage = SecureSessionStorage(application)
    val restClient = BackspaceRestClient()
    val wsClient = BackspaceWebSocketClient(restClient)
    val voiceEngine = BackspaceVoiceEngine(application, restClient, wsClient)
    val releaseUpdater = GitHubReleaseUpdater(restClient.okHttpClient)
}

class BackspaceApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

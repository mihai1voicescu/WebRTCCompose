package io.almer.webrtccompose

import android.app.Application
import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.shepeliev.webrtckmp.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow


private val tag = "MainApp"

class MainApp : Application() {
    val _callSimulation = MutableStateFlow<CallSimulation?>(null)
    val callSimulation = _callSimulation.asStateFlow()

    suspend fun start() {
        if (callSimulation.value == null) {
            restart()
        }
    }

    suspend fun restart() {
        _callSimulation.value = CallSimulation()
    }

    override fun onCreate() {
        super.onCreate()

        initializeWebRtc(this)

    }


    companion object {

        @Composable
        fun mainApp(): MainApp {
            return LocalContext.current.applicationContext!! as MainApp
        }
    }
}
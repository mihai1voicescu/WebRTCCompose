package io.almer.webrtccompose

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.Ref
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.shepeliev.webrtckmp.*
import io.almer.webrtccompose.MainApp.Companion.mainApp
import io.almer.webrtccompose.ui.theme.WebRTCComposeTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.webrtc.SurfaceViewRenderer


val TAG = "io.almer.webrtccompose"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val neededPermissions = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

        val toRequest = neededPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) !=
                    PackageManager.PERMISSION_GRANTED
        }

        if (toRequest.isNotEmpty())
            ActivityCompat.requestPermissions(
                this,
                toRequest.toTypedArray(),
                1
            );

        setContent {
            WebRTCComposeTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    WebRTCDance()
                }
            }
        }
    }
}


//@Composable
//fun Main() {
//    var stream by remember {
//        mutableStateOf<MediaStream?>(null)
//    }
//
//    LaunchedEffect(true) {
//        stream = MediaDevices.getUserMedia(audio = true, video = true)
//    }
//
//    stream?.let { stream ->
//        VideoLooper(stream = stream)
//    } ?: Text("Connecting")
//}

private val tag = "WebRTCDance"

@Composable
fun WebRTCDance(
) {
    val app = mainApp()

    val scope = rememberCoroutineScope()

    val callSimulation by app.callSimulation.collectAsState()

    var shouldShow by remember {
        mutableStateOf(true)
    }

    val cameras = remember {
        mutableStateListOf<Pair<String, String>>()
    }

    LaunchedEffect(true) {
        val devices = MediaDevices.enumerateDevices()
        cameras.clear()

        devices.filter { it.kind == MediaDeviceKind.VideoInput }.map { it.label to it.deviceId }.toCollection(cameras)
    }

    callSimulation?.apply {
        if (tracks.isEmpty()) {
            Text("Loading tracks")
        } else {
            if (shouldShow) {
                StreamPlayer(tracks)
            } else {
                Text("Press toogle button to view")
            }
        }
    } ?: Text("No call simulation")


    Box(contentAlignment = Alignment.BottomStart) {
        Column {
            cameras.forEach {
                Button(onClick = {
                    scope.launch {
                        app.callSimulation.value?.selectCamera(it.second)
                    }
                }) {
                    Text(it.first)
                }
            }
        }
    }

    Box(contentAlignment = Alignment.TopEnd) {
        Column {
            Button(onClick = {
                scope.launch {
                    app.restart()
                }
            }) {
                Text("Restart")
            }
            Button(onClick = {
                scope.launch {
                    shouldShow = !shouldShow
                }
            }) {
                Text("Toogle")
            }
            Button(onClick = {
                scope.launch {
                    app.callSimulation.value?.addTracks()
                }
            }) {
                Text("Add Tracks")
            }
            Button(onClick = {
                scope.launch {
                    app.callSimulation.value?.removeTracks()
                }
            }) {
                Text("Remove Tracks")
            }
        }

    }
}

//@Composable
//fun VideoLooper(
//    stream: MediaStream,
//) {
//
//    var i by remember {
//        mutableStateOf(0)
//    }
//
//
//    LaunchedEffect(true) {
//        while (true) {
//            delay(3000)
//            i++
//        }
//    }
//
//    i.let {
//        if (it % 2 == 0) Row {
//            StreamPlayer(stream = stream, title = i.toString(), width = 100.dp, height = 100.dp)
//            StreamPlayer(stream = stream, title = i.toString(), width = 100.dp, height = 100.dp)
//            StreamPlayer(stream = stream, title = i.toString(), width = 100.dp, height = 100.dp)
//            StreamPlayer(stream = stream, title = i.toString(), width = 100.dp, height = 100.dp)
//            StreamPlayer(stream = stream, title = i.toString(), width = 100.dp, height = 100.dp)
//        }
//        else Text("Kill")
//    }
//}

@Composable
fun StreamPlayer(
    stream: MediaStream,
    height: Dp = 720.dp,
    width: Dp = 1080.dp,
    title: String? = null
) {
    StreamPlayer(stream.tracks, height, width, title)
}

@Composable
fun StreamPlayer(
    tracks: List<MediaStreamTrack>,
    height: Dp = 720.dp,
    width: Dp = 1080.dp,
    title: String? = null
) {

    val surface = remember {
        Ref<SurfaceViewRenderer>()
    }

    Box {
        AndroidView(
            modifier = Modifier
                .height(height)
                .width(width),
            factory = { context: Context ->

                Log.i(TAG, "Factory()")
                val s = SurfaceViewRenderer(context)

                s.setEnableHardwareScaler(true)
                s.setMirror(false)
                s.init(eglBaseContext, null)

                s
            }, update = {
                Log.i(TAG, "Update()")
                surface.value = it

                tracks.forEach { track ->
                    if (track is VideoStreamTrack) {
                        track.addSink(it)
                    }
                }
            })

        if (title != null) {
            Text(title)
        }
    }

    DisposableEffect(true) {
        onDispose {
            Log.i(TAG, "Dispose()")

            surface.value?.let { surface ->
                tracks.forEach { track ->
                    if (track is VideoStreamTrack) {
                        track.removeSink(surface)
                    }
                }
                surface.release()
            }
        }
    }
}
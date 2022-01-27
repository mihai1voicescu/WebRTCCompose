package io.almer.webrtccompose

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.Ref
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.shepeliev.webrtckmp.*
import io.almer.webrtccompose.ui.theme.WebRTCComposeTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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

        initializeWebRtc(this)
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

private fun drainCandidates(
    candidates: MutableList<IceCandidate>,
    peerConnection: PeerConnection
) {
    if (candidates.isEmpty()) return
    candidates.forEach {
        Log.d(tag, "Drain candidate into PC")
        peerConnection.addIceCandidate(it)
    }
    candidates.clear()
}

private val tag = "WebRTCDance"

@Composable
fun WebRTCDance(
) {

    val scope = rememberCoroutineScope()


    var stream by remember {
        mutableStateOf<MediaStream?>(null)
    }

    val tracks = remember {
        mutableStateListOf<MediaStreamTrack>()
    }

    LaunchedEffect(true) {

        delay(4000)
        val localPeerConnection = com.shepeliev.webrtckmp.PeerConnection(RtcConfiguration())
        val remotePeerConnection = com.shepeliev.webrtckmp.PeerConnection(RtcConfiguration())

        val localIceCandidates = mutableListOf<IceCandidate>()
        val remoteIceCandidates = mutableListOf<IceCandidate>()

        with(localPeerConnection) {
            onSignalingStateChange
                .onEach {
                    Log.d(tag, "Local PC signaling state $it")
                    if (it == SignalingState.Stable) {
                        Log.d(tag, "Drain remote candidates into local PC")
                        drainCandidates(remoteIceCandidates, localPeerConnection)
                    }
                }
                .launchIn(scope)

            onIceCandidate
                .onEach {
                    Log.d(tag, "Local PC ICE candidate $it")
                    if (remotePeerConnection.signalingState == SignalingState.Stable) {
                        Log.d(tag, "Remote PC is in Stable state. Add Ice candidate")
                        remotePeerConnection.addIceCandidate(it)
                    } else {
                        Log.d(
                            tag,
                            "Remote PC is not in Stable state. Collect local candidate"
                        )
                        localIceCandidates += it
                    }
                }
                .launchIn(scope)
        }


        with(remotePeerConnection) {
            onSignalingStateChange
                .onEach {
                    Log.d(tag, "Remote PC signaling state $it")
                    if (it == SignalingState.Stable) {
                        Log.d(tag, "Drain local candidates into remote PC")
                        drainCandidates(localIceCandidates, remotePeerConnection)
                    }
                }
                .launchIn(scope)


            onIceCandidate
                .onEach {
                    Log.d(tag, "Remote PC ICE candidate $it")
                    if (localPeerConnection.signalingState == SignalingState.Stable) {
                        Log.d(tag, "Local PC is in Stable state. Add Ice candidate")
                        localPeerConnection.addIceCandidate(it)
                    } else {
                        Log.d(tag, "Local PC is not in Stable state. Collect Ice candidate")
                        remoteIceCandidates += it
                    }
                }
                .launchIn(scope)


            onTrack
                .onEach { trackEvent ->
                    Log.d(
                        tag,
                        "Remote PC on add track ${trackEvent.track}, streams: ${trackEvent.streams}"
                    )
                    trackEvent.streams.firstOrNull()?.also { stream = it }
                        ?: trackEvent.track?.takeIf { it.kind == MediaStreamTrackKind.Video }
                            ?.also { tracks.add(it) }
                }
                .launchIn(scope)

        }

        val localStream = MediaDevices.getUserMedia(audio = true, video = true)

        localStream.tracks.forEach {
            localPeerConnection.addTrack(it, localStream)
        }

        val offer = localPeerConnection.createOffer(OfferAnswerOptions())
        localPeerConnection.setLocalDescription(offer)

        remotePeerConnection.setRemoteDescription(offer)
        val answer = remotePeerConnection.createAnswer(OfferAnswerOptions())

        remotePeerConnection.setLocalDescription(answer)
        localPeerConnection.setRemoteDescription(answer)
    }

    if (tracks.isEmpty()) Text("Loading")
    else StreamPlayer(tracks)
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
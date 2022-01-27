package io.almer.webrtccompose

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.shepeliev.webrtckmp.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

private val tag = "CallSimulation"


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


class CallSimulation private constructor() {

    val tracks = mutableStateListOf<MediaStreamTrack>()

    val scope = CoroutineScope(Dispatchers.Main)

    var stream by mutableStateOf<MediaStream?>(null)

    private suspend fun start() {
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

    companion object {
        suspend operator fun invoke(): CallSimulation {
            return CallSimulation().apply { start() }
        }
    }
}
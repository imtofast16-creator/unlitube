package app.unlitube.android.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import app.unlitube.android.data.StreamOption

/**
 * UnliPlayer, the custom player core (PLAN.md Section 4D).
 *
 * A real Media3 ExoPlayer sits behind this small API so the Compose control
 * layer never talks to ExoPlayer directly. It plays single progressive
 * streams and merged adaptive video plus audio pairs resolved by Engine A.
 */
class UnliPlayer(context: Context) {

    private val appContext = context.applicationContext

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(appContext).build()

    // DefaultDataSource wraps the HTTP factory so the same player handles
    // streamed URLs and downloaded file:// URIs for offline playback.
    private val dataSourceFactory = DefaultDataSource.Factory(
        appContext,
        DefaultHttpDataSource.Factory()
            .setUserAgent("com.google.android.youtube/20.10.38 (Linux; U; Android 15)")
            .setAllowCrossProtocolRedirects(true)
    )

    fun play(url: String) {
        exoPlayer.setMediaItem(MediaItem.fromUri(url))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    fun play(option: StreamOption) {
        val audioUrl = option.audioUrl
        if (audioUrl.isNullOrBlank()) {
            play(option.videoUrl)
            return
        }
        val videoSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(Uri.parse(option.videoUrl)))
        val audioSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(Uri.parse(audioUrl)))
        exoPlayer.setMediaSource(MergingMediaSource(true, videoSource, audioSource))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    fun setSpeed(speed: Float) {
        exoPlayer.playbackParameters = PlaybackParameters(speed)
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun seekBy(deltaMs: Long) {
        seekTo(exoPlayer.currentPosition + deltaMs)
    }

    fun togglePlay() {
        exoPlayer.playWhenReady = !exoPlayer.playWhenReady
    }

    fun setPlaying(playing: Boolean) {
        exoPlayer.playWhenReady = playing
    }

    fun release() {
        exoPlayer.release()
    }
}

@Composable
fun PlayerSurface(player: UnliPlayer, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val playerView = remember {
        PlayerView(context).apply {
            useController = false // UnliTube ships fully custom controls (Section 4D)
        }
    }
    DisposableEffect(player) {
        playerView.player = player.exoPlayer
        onDispose { playerView.player = null }
    }
    AndroidView(factory = { playerView }, modifier = modifier)
}

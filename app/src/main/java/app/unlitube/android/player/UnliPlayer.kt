package app.unlitube.android.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

/**
 * UnliPlayer, the custom player core (PLAN.md Section 4D).
 *
 * Phase 0 scope: a real Media3 ExoPlayer wired behind our own API surface so the
 * Compose control layer (gestures, quality menu, chapters rail, SponsorBlock)
 * never talks to ExoPlayer directly. Phase 1 adds DASH track selection UI,
 * prefetch of the next queue item, and the background media service.
 */
class UnliPlayer(context: Context) {

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build()

    fun play(url: String) {
        exoPlayer.setMediaItem(MediaItem.fromUri(url))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    fun setSpeed(speed: Float) {
        exoPlayer.playbackParameters = PlaybackParameters(speed)
    }

    fun seekBy(deltaMs: Long) {
        val target = (exoPlayer.currentPosition + deltaMs).coerceAtLeast(0L)
        exoPlayer.seekTo(target)
    }

    fun togglePlay() {
        exoPlayer.playWhenReady = !exoPlayer.playWhenReady
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

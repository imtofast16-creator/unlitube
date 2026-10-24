package app.unlitube.android.player

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import app.unlitube.android.data.VideoItem

/**
 * Owns the single app-wide UnliPlayer so playback can move between the watch
 * screen, the mini player, picture in picture, and the background media
 * service without restarting. The watch screen borrows the player; it never
 * releases it.
 */
object PlayerHolder {

    private var unliPlayer: UnliPlayer? = null

    var nowPlaying: VideoItem? by mutableStateOf(null)
    var embedOnly: Boolean by mutableStateOf(false)
    var isPlaying: Boolean by mutableStateOf(false)
    var inPip: Boolean by mutableStateOf(false)

    fun get(context: Context): UnliPlayer {
        unliPlayer?.let { return it }
        val created = UnliPlayer(context.applicationContext)
        created.exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        })
        unliPlayer = created
        return created
    }

    fun peek(): UnliPlayer? = unliPlayer

    fun hasMedia(): Boolean = (unliPlayer?.exoPlayer?.mediaItemCount ?: 0) > 0

    fun startService(context: Context) {
        val intent = Intent(context.applicationContext, PlaybackService::class.java)
        ContextCompat.startForegroundService(context.applicationContext, intent)
    }

    fun stopAndClear() {
        unliPlayer?.exoPlayer?.stop()
        unliPlayer?.exoPlayer?.clearMediaItems()
        nowPlaying = null
        embedOnly = false
        isPlaying = false
    }

    /** Called by the service when it is destroyed and releases the player. */
    fun onPlayerReleased() {
        unliPlayer = null
        nowPlaying = null
        embedOnly = false
        isPlaying = false
    }
}

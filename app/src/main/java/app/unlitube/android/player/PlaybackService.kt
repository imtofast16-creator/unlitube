package app.unlitube.android.player

import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Background playback service (PLAN.md Phase 1). Hosts a MediaSession over
 * the shared PlayerHolder player, which gives UnliTube a media notification
 * with play, pause and seek controls and keeps audio playing with the screen
 * off or the app in the background.
 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = PlayerHolder.get(this)
        session = MediaSession.Builder(this, player.exoPlayer).build()
    }

    override fun onGetSession(info: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player != null && !player.playWhenReady) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        PlayerHolder.onPlayerReleased()
        super.onDestroy()
    }
}

package app.unlitube.android

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import app.unlitube.android.player.PlayerHolder
import app.unlitube.android.ui.HomeScreen
import app.unlitube.android.ui.theme.UnliTubeTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        val initialVideoId = extractVideoId(intent)
        setContent {
            UnliTubeTheme {
                HomeScreen(initialVideoId = initialVideoId)
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Picture in picture: leaving the app mid video shrinks it into a
        // floating window instead of stopping playback.
        if (PlayerHolder.nowPlaying != null && !PlayerHolder.embedOnly && PlayerHolder.isPlaying) {
            runCatching {
                enterPictureInPictureMode(
                    PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9))
                        .build()
                )
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        PlayerHolder.inPip = isInPictureInPictureMode
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
    }

    private fun extractVideoId(intent: Intent?): String? {
        val uri = intent?.data ?: return null
        uri.getQueryParameter("v")?.takeIf { it.isNotBlank() }?.let { return it }
        if (uri.host?.contains("youtu.be") == true) {
            return uri.lastPathSegment?.takeIf { it.isNotBlank() }
        }
        return null
    }
}

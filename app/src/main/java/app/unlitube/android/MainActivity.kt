package app.unlitube.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.unlitube.android.ui.HomeScreen
import app.unlitube.android.ui.theme.UnliTubeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UnliTubeTheme {
                HomeScreen()
            }
        }
    }
}

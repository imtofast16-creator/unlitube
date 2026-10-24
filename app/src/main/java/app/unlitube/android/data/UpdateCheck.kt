package app.unlitube.android.data

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class RemoteVersion(
    val versionName: String,
    val versionCode: Int,
    val apkUrl: String,
    val notes: String
)

/**
 * Slice 7 update checker. Reads the Vercel site's /api/version route, which
 * only ever reports a version number, notes and a download link. No device
 * data is sent with the check.
 */
object UpdateCheck {

    suspend fun fetch(): RemoteVersion? = withContext(Dispatchers.IO) {
        runCatching {
            val connection =
                URL("https://unlitube.vercel.app/api/version").openConnection() as HttpURLConnection
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.connect()
            if (connection.responseCode != 200) return@runCatching null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            RemoteVersion(
                versionName = json.optString("versionName", ""),
                versionCode = json.optInt("versionCode", 0),
                apkUrl = json.optString("apkUrl", ""),
                notes = json.optString("notes", "")
            )
        }.getOrNull()
    }
}

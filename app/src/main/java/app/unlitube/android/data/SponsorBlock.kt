package app.unlitube.android.data

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

data class SponsorSegment(
    val startMs: Long,
    val endMs: Long,
    val category: String
)

/**
 * Slice 7 SponsorBlock. Segments come from the public SponsorBlock API.
 * Two hosts are tried because sponsor.ajay.app returned 404 for
 * skipSegments from this VM on 2026-10-24, while sponsorblock.tv was
 * unreachable from the VM; a phone on a normal network may reach either.
 * Any failure returns an empty list, so playback is never blocked by this.
 */
object SponsorBlockClient {

    private val categories = listOf("sponsor", "intro", "outro", "selfpromo")
    private val hosts = listOf(
        "https://sponsor.ajay.app",
        "https://sponsorblock.tv"
    )

    suspend fun fetch(videoId: String): List<SponsorSegment> = withContext(Dispatchers.IO) {
        for (host in hosts) {
            val result = runCatching { fetchFromHost(host, videoId) }.getOrNull()
            if (result != null) return@withContext result
        }
        emptyList()
    }

    private fun fetchFromHost(host: String, videoId: String): List<SponsorSegment> {
        val query = categories.joinToString("&") { category ->
            "category=" + URLEncoder.encode(category, "UTF-8")
        }
        val url = "$host/api/skipSegments?videoID=" +
            URLEncoder.encode(videoId, "UTF-8") + "&" + query
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        connection.connect()
        if (connection.responseCode != 200) return emptyList()
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        if (body.isBlank()) return emptyList()
        val array = JSONArray(body)
        val segments = mutableListOf<SponsorSegment>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val segment = item.optJSONArray("segment") ?: continue
            if (segment.length() < 2) continue
            val start = (segment.optDouble(0) * 1000).toLong()
            val end = (segment.optDouble(1) * 1000).toLong()
            if (end > start) {
                segments.add(
                    SponsorSegment(start, end, item.optString("category", "sponsor"))
                )
            }
        }
        return segments.sortedBy { it.startMs }
    }
}

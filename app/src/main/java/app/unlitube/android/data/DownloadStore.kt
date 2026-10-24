package app.unlitube.android.data

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateMapOf
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Slice 6 downloads. Files are saved under the app's private files dir, so
 * they never need storage permission and are deleted with the app. A muxed
 * stream downloads as one file; an adaptive pair downloads as a video file
 * plus an audio file, and the watch page plays them merged, exactly like a
 * streamed adaptive pair. Nothing is faked: a download only counts as
 * completed when both files are fully written.
 */
object DownloadStore {

    private const val USER_AGENT = "com.google.android.youtube/20.10.38 (Linux; U; Android 15)"

    /** videoId to download progress percent, observed by the Library tab. */
    val progress = mutableStateMapOf<String, Int>()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val jobs = mutableMapOf<String, Job>()

    fun downloadDir(context: Context, videoId: String): File =
        File(context.filesDir, "downloads/$videoId")

    fun startDownload(context: Context, details: VideoDetails) {
        val video = details.video
        if (jobs[video.id]?.isActive == true) return
        val appContext = context.applicationContext
        jobs[video.id] = scope.launch {
            try {
                progress[video.id] = 0
                LibraryStore.upsertDownload(
                    DownloadEntity(
                        videoId = video.id,
                        title = video.title,
                        channel = video.channel,
                        channelId = video.channelId,
                        thumbnailUrl = video.thumbnailUrl,
                        videoPath = null,
                        audioPath = null,
                        status = "downloading",
                        progress = 0,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                val option = chooseDownloadStream(details)
                    ?: throw IllegalStateException("No downloadable stream")
                val dir = downloadDir(appContext, video.id)
                if (!dir.exists()) dir.mkdirs()
                val videoFile = File(dir, "video.mp4")
                downloadFile(option.videoUrl, videoFile) { pct ->
                    progress[video.id] = pct / 2
                }
                var audioPath: String? = null
                if (!option.audioUrl.isNullOrBlank()) {
                    val audioFile = File(dir, "audio.mp4")
                    downloadFile(option.audioUrl, audioFile) { pct ->
                        progress[video.id] = 50 + pct / 2
                    }
                    audioPath = audioFile.absolutePath
                }
                progress[video.id] = 100
                LibraryStore.upsertDownload(
                    DownloadEntity(
                        videoId = video.id,
                        title = video.title,
                        channel = video.channel,
                        channelId = video.channelId,
                        thumbnailUrl = video.thumbnailUrl,
                        videoPath = videoFile.absolutePath,
                        audioPath = audioPath,
                        status = "completed",
                        progress = 100,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            } catch (error: Exception) {
                progress.remove(video.id)
                LibraryStore.upsertDownload(
                    DownloadEntity(
                        videoId = video.id,
                        title = video.title,
                        channel = video.channel,
                        channelId = video.channelId,
                        thumbnailUrl = video.thumbnailUrl,
                        videoPath = null,
                        audioPath = null,
                        status = "failed",
                        progress = 0,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    suspend fun deleteDownload(context: Context, videoId: String) {
        jobs[videoId]?.cancel()
        jobs.remove(videoId)
        progress.remove(videoId)
        val dir = downloadDir(context, videoId)
        if (dir.exists()) dir.deleteRecursively()
        LibraryStore.deleteDownload(videoId)
    }

    fun localOption(download: DownloadEntity): StreamOption? {
        val videoPath = download.videoPath ?: return null
        if (!File(videoPath).exists()) return null
        val audioPath = download.audioPath
        if (audioPath != null && !File(audioPath).exists()) return null
        return StreamOption(
            id = "local",
            label = "Downloaded",
            detail = "Offline playback",
            videoUrl = Uri.fromFile(File(videoPath)).toString(),
            audioUrl = audioPath?.let { Uri.fromFile(File(it)).toString() },
            isMuxed = audioPath == null
        )
    }

    private fun chooseDownloadStream(details: VideoDetails): StreamOption? {
        val muxed = details.streams.filter { it.isMuxed }
            .maxByOrNull { it.height }
        if (muxed != null) return muxed
        val video = details.streams.filter { !it.isMuxed && it.audioUrl != null }
            .filter { it.height in 1..720 }
            .maxByOrNull { it.height }
        return video ?: details.streams.firstOrNull()
    }

    private fun downloadFile(url: String, outFile: File, onProgress: (Int) -> Unit) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.connectTimeout = 30_000
        connection.readTimeout = 30_000
        connection.connect()
        val total = connection.contentLengthLong
        val tmpFile = File(outFile.parentFile, outFile.name + ".tmp")
        connection.inputStream.use { input ->
            FileOutputStream(tmpFile).use { output ->
                val buffer = ByteArray(8192)
                var downloaded = 0L
                var lastReported = -1
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    downloaded += read
                    if (total > 0) {
                        val pct = ((downloaded * 100) / total).toInt().coerceIn(0, 100)
                        if (pct != lastReported && pct % 5 == 0) {
                            lastReported = pct
                            onProgress(pct)
                        }
                    }
                }
            }
        }
        if (outFile.exists()) outFile.delete()
        tmpFile.renameTo(outFile)
    }
}

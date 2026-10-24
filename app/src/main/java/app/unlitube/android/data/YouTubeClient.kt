package app.unlitube.android.data

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class VideoItem(
    val id: String,
    val title: String,
    val channel: String,
    val channelId: String? = null,
    val thumbnailUrl: String,
    val channelAvatarUrl: String? = null,
    val durationText: String? = null,
    val viewCountText: String? = null,
    val publishedText: String? = null,
    val isLive: Boolean = false
)

data class VideoPage(
    val items: List<VideoItem>,
    val continuation: String? = null
)

data class StreamOption(
    val id: String,
    val label: String,
    val detail: String,
    val videoUrl: String,
    val audioUrl: String? = null,
    val height: Int = 0,
    val bitrate: Int = 0,
    val isMuxed: Boolean = false
)

data class VideoDetails(
    val video: VideoItem,
    val description: String,
    val streams: List<StreamOption>,
    val defaultStreamIndex: Int,
    val related: List<VideoItem>
)

data class ChannelInfo(
    val id: String,
    val name: String,
    val subtitle: String? = null,
    val avatarUrl: String? = null,
    val description: String = ""
)

data class ChannelPage(
    val info: ChannelInfo,
    val videos: List<VideoItem>
)

data class CommentItem(
    val id: String,
    val author: String,
    val authorAvatarUrl: String? = null,
    val text: String,
    val publishedText: String? = null,
    val likeCountText: String? = null,
    val replyCount: Int = 0,
    val isPinned: Boolean = false,
    val repliesToken: String? = null
)

data class CommentsPage(
    val comments: List<CommentItem>,
    val continuation: String? = null
)

/**
 * Engine A from PLAN.md Section 3: public InnerTube calls made directly from
 * the phone. No API key account, no Vercel relay, and no viewing data leaves
 * the device except the requests YouTube itself receives.
 */
class YouTubeRepository {

    private val innerTube = InnerTubeClient()

    suspend fun loadHome(folder: String): List<VideoItem> {
        val browsed = runCatching { innerTube.browseHome() }.getOrDefault(emptyList())
        if (browsed.size >= MIN_BROWSE_RESULTS) return browsed

        val queries = homeQueries(folder).let { all ->
            if (SettingsStore.liteMode) all.take(2) else all
        }
        val pages = coroutineScope {
            queries.map { query ->
                async { runCatching { innerTube.search(query).items }.getOrDefault(emptyList()) }
            }.map { it.await() }
        }
        return interleave(pages).distinctBy { it.id }.take(MAX_HOME_ITEMS)
    }

    suspend fun search(query: String, continuation: String? = null): VideoPage =
        innerTube.search(query.trim(), continuation)

    suspend fun loadDetails(videoId: String): VideoDetails = innerTube.player(videoId)

    suspend fun loadChannel(channelId: String): ChannelPage = innerTube.channel(channelId)

    suspend fun loadComments(videoId: String): CommentsPage = innerTube.commentsFirstPage(videoId)

    suspend fun loadMoreComments(continuation: String): CommentsPage =
        innerTube.commentsPage(continuation)

    suspend fun loadReplies(token: String): List<CommentItem> = innerTube.replies(token)

    private fun homeQueries(folder: String): List<String> = when (folder) {
        "Study" -> listOf("study tips and tutorials", "science documentary", "math lessons")
        "Music" -> listOf("OPM hits", "new music videos", "lofi beats")
        "Gaming" -> listOf("Roblox gameplay", "Minecraft gameplay", "Mobile Legends highlights")
        "News" -> listOf("Philippines news today", "world news today", "breaking news Philippines")
        "Live" -> listOf("Philippines live news", "live music stream", "live gaming stream")
        "Shorts" -> listOf("funny shorts Philippines", "viral shorts today", "quick facts shorts")
        else -> listOf("Philippines trending today", "viral videos today", "Manila vlog")
    }

    private fun interleave(pages: List<List<VideoItem>>): List<VideoItem> {
        val result = mutableListOf<VideoItem>()
        val maxSize = pages.maxOfOrNull { it.size } ?: 0
        for (index in 0 until maxSize) {
            for (page in pages) {
                page.getOrNull(index)?.let(result::add)
            }
        }
        return result
    }

    private companion object {
        const val MIN_BROWSE_RESULTS = 8
        const val MAX_HOME_ITEMS = 36
    }
}

private class InnerTubeClient {

    private var visitorData: String? = null

    suspend fun browseHome(): List<VideoItem> = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("context", androidContext())
            .put("browseId", "FEwhat_to_watch")
        parseVideos(post("browse", body, android = true)).values.toList()
    }

    suspend fun search(query: String, continuation: String? = null): VideoPage =
        withContext(Dispatchers.IO) {
            if (query.isBlank() && continuation == null) return@withContext VideoPage(emptyList())
            val body = JSONObject().put("context", webContext())
            if (continuation != null) {
                body.put("continuation", continuation)
            } else {
                body.put("query", query)
            }
            val response = post("search", body, android = false)
            VideoPage(
                items = parseVideos(response).values.toList(),
                continuation = findContinuation(response)
            )
        }

    suspend fun player(videoId: String): VideoDetails = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("context", androidContext())
            .put("videoId", videoId)
            .put(
                "playbackContext",
                JSONObject().put(
                    "contentPlaybackContext",
                    JSONObject()
                        .put("vis", 0)
                        .put("splay", false)
                        .put("html5Preference", "HTML5_PREF_WANTS")
                )
            )
            .put("racyCheckOk", true)
            .put("contentCheckOk", true)
        val response = post("player", body, android = true)
        val playability = response.optJSONObject("playabilityStatus")
        val status = playability?.optString("status").orEmpty()
        if (status.isNotBlank() && status != "OK") {
            val reason = playability?.optString("reason")?.takeIf { it.isNotBlank() }
                ?: "YouTube marked this video unavailable"
            throw IllegalStateException(reason)
        }

        val details = response.optJSONObject("videoDetails")
            ?: throw IllegalStateException("YouTube returned no video details")
        val title = details.optString("title")
        val channel = details.optString("author")
        val channelId = details.optString("channelId").takeIf { it.isNotBlank() }
        val description = details.optString("shortDescription")
        val thumbnail = bestThumbnail(details.optJSONObject("thumbnail"))
        val video = VideoItem(
            id = videoId,
            title = title,
            channel = channel,
            channelId = channelId,
            thumbnailUrl = thumbnail,
            durationText = details.optString("lengthSeconds").toLongOrNull()?.let(::formatDuration),
            viewCountText = details.optString("viewCount").toLongOrNull()?.let(::formatCount)?.let { "$it views" },
            isLive = details.optBoolean("isLiveContent")
        )

        val streaming = response.optJSONObject("streamingData")
        val streams = parseStreams(streaming)
        val related = runCatching { loadRelated(videoId) }.getOrDefault(emptyList())
        VideoDetails(
            video = video,
            description = description,
            streams = streams,
            defaultStreamIndex = defaultStreamIndex(streams),
            related = related.filter { it.id != videoId }
        )
    }

    private fun loadRelated(videoId: String): List<VideoItem> {
        val body = JSONObject()
            .put("context", webContext())
            .put("videoId", videoId)
        val response = post("next", body, android = false)
        return parseVideos(response).values.toList()
    }

    suspend fun channel(channelId: String): ChannelPage = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("context", webContext())
            .put("browseId", channelId)
        val response = post("browse", body, android = false)
        var videos = parseVideos(response).values.toList()
        if (videos.size < 5) {
            // Ask for the Videos tab explicitly; newer channel layouts often
            // ignore the token, in which case the first response already has
            // the channel's video lockups and this simply merges nothing new.
            val tabBody = JSONObject()
                .put("context", webContext())
                .put("browseId", channelId)
                .put("params", "EgZ2aWRlb3M%3D")
            runCatching { post("browse", tabBody, android = false) }
                .getOrNull()
                ?.let { tabResponse ->
                    videos = (videos + parseVideos(tabResponse).values).distinctBy { it.id }
                }
        }
        ChannelPage(info = parseChannelInfo(response, channelId), videos = videos)
    }

    suspend fun commentsFirstPage(videoId: String): CommentsPage = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("context", webContext())
            .put("videoId", videoId)
        val response = post("next", body, android = false)
        val token = findCommentsSectionToken(response)
            ?: return@withContext CommentsPage(emptyList())
        commentsPage(token)
    }

    suspend fun commentsPage(continuation: String): CommentsPage = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("context", webContext())
            .put("continuation", continuation)
        val response = post("next", body, android = false)
        CommentsPage(
            comments = parseCommentThreads(response),
            continuation = lastContinuationToken(response)
        )
    }

    suspend fun replies(token: String): List<CommentItem> = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("context", webContext())
            .put("continuation", token)
        val response = post("next", body, android = false)
        val seen = linkedMapOf<String, CommentItem>()
        for (renderer in findObjects(response, "commentRenderer")) {
            parseCommentRenderer(renderer, repliesToken = null)?.let { seen.putIfAbsent(it.id, it) }
        }
        seen.values.toList()
    }

    private fun post(endpoint: String, body: JSONObject, android: Boolean): JSONObject {
        visitorData?.let { body.put("visitorData", it) }
        val key = if (android) ANDROID_API_KEY else WEB_API_KEY
        val userAgent = if (android) ANDROID_USER_AGENT else WEB_USER_AGENT
        val connection = (URL("https://www.youtube.com/youtubei/v1/$endpoint?key=$key&prettyPrint=false")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("X-YouTube-Client-Name", if (android) "3" else "1")
            setRequestProperty("X-YouTube-Client-Version", if (android) ANDROID_CLIENT_VERSION else WEB_CLIENT_VERSION)
            visitorData?.let { setRequestProperty("X-Goog-Visitor-Id", it) }
        }
        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.let {
            BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { reader -> reader.readText() }
        }.orEmpty()
        if (code !in 200..299) {
            throw IllegalStateException("YouTube request failed with HTTP $code")
        }
        val json = JSONObject(text)
        json.optJSONObject("responseContext")?.optString("visitorData")
            ?.takeIf { it.isNotBlank() }
            ?.let { visitorData = it }
        return json
    }

    private fun webContext(): JSONObject = JSONObject()
        .put(
            "client",
            JSONObject()
                .put("clientName", "WEB")
                .put("clientVersion", WEB_CLIENT_VERSION)
                .put("hl", "en")
                .put("gl", "PH")
                .put("platform", "DESKTOP")
                .put("userAgent", WEB_USER_AGENT)
        )

    private fun androidContext(): JSONObject = JSONObject()
        .put(
            "client",
            JSONObject()
                .put("clientName", "ANDROID")
                .put("clientVersion", ANDROID_CLIENT_VERSION)
                .put("androidSdkVersion", 35)
                .put("hl", "en")
                .put("gl", "PH")
                .put("deviceMake", "Google")
                .put("deviceModel", "Pixel 8")
                .put("osName", "Android")
                .put("osVersion", "15")
                .put("userAgent", ANDROID_USER_AGENT)
        )

    private companion object {
        const val WEB_API_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
        const val ANDROID_API_KEY = "AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vn_yYM39w"
        const val WEB_CLIENT_VERSION = "2.20251022.01.00"
        const val ANDROID_CLIENT_VERSION = "20.10.38"
        const val WEB_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/140.0.0.0 Safari/537.36"
        const val ANDROID_USER_AGENT =
            "com.google.android.youtube/20.10.38 (Linux; U; Android 15) gzip"
    }
}

private fun parseVideos(root: JSONObject): LinkedHashMap<String, VideoItem> {
    val result = linkedMapOf<String, VideoItem>()
    for (rendererName in listOf("videoRenderer", "compactVideoRenderer", "gridVideoRenderer")) {
        for (renderer in findObjects(root, rendererName)) {
            parseVideoRenderer(renderer)?.let { result.putIfAbsent(it.id, it) }
        }
    }
    for (lockup in findObjects(root, "lockupViewModel")) {
        parseLockup(lockup)?.let { result.putIfAbsent(it.id, it) }
    }
    return result
}

private fun parseVideoRenderer(renderer: JSONObject): VideoItem? {
    val id = renderer.optString("videoId").takeIf { it.isNotBlank() } ?: return null
    val title = textValue(renderer.opt("title"))
        .ifBlank { textValue(renderer.opt("headline")) }
    if (title.isBlank()) return null
    val channelNode = renderer.opt("longBylineText")
        ?: renderer.opt("ownerText")
        ?: renderer.opt("shortBylineText")
    val channel = textValue(channelNode)
    val thumbnail = bestThumbnail(renderer.optJSONObject("thumbnail"))
    val duration = textValue(renderer.opt("lengthText")).takeIf { it.isNotBlank() }
    val views = textValue(renderer.opt("viewCountText"))
        .ifBlank { textValue(renderer.opt("shortViewCountText")) }
    val published = textValue(renderer.opt("publishedTimeText"))
    val live = duration == null && hasLiveBadge(renderer)
    return VideoItem(
        id = id,
        title = title,
        channel = channel,
        channelId = findBrowseId(channelNode),
        thumbnailUrl = thumbnail,
        channelAvatarUrl = findChannelAvatar(renderer),
        durationText = duration,
        viewCountText = views.takeIf { it.isNotBlank() },
        publishedText = published.takeIf { it.isNotBlank() },
        isLive = live
    )
}

private fun parseLockup(lockup: JSONObject): VideoItem? {
    val contentType = lockup.optString("contentType")
    if (contentType.isNotBlank() && !contentType.contains("VIDEO")) return null
    val id = lockup.optString("contentId").takeIf { it.isNotBlank() } ?: return null
    val metadata = lockup.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
        ?: return null
    val title = metadata.optJSONObject("title")?.optString("content").orEmpty()
    if (title.isBlank()) return null
    val image = lockup.optJSONObject("contentImage")
        ?.optJSONObject("thumbnailViewModel")
        ?.optJSONObject("image")
    val thumbnail = bestThumbnail(image)
    val rows = metadata.optJSONArray("metadataRows")
    val parts = mutableListOf<String>()
    if (rows != null) {
        for (i in 0 until rows.length()) {
            val rowParts = rows.optJSONObject(i)?.optJSONArray("parts") ?: continue
            for (j in 0 until rowParts.length()) {
                rowParts.optJSONObject(j)?.optJSONObject("text")?.optString("content")
                    ?.takeIf { it.isNotBlank() }?.let(parts::add)
            }
        }
    }
    val badgeDuration = lockup.optJSONObject("contentImage")?.let { image ->
        findObjects(image, "thumbnailBadgeViewModel")
            .mapNotNull { it.optString("text").takeIf { text -> text.contains(":") } }
            .firstOrNull()
    }
    return VideoItem(
        id = id,
        title = title,
        channel = parts.getOrNull(0).orEmpty(),
        thumbnailUrl = thumbnail,
        durationText = badgeDuration,
        viewCountText = parts.getOrNull(1),
        publishedText = parts.getOrNull(2)
    )
}

private fun parseChannelInfo(root: JSONObject, fallbackId: String): ChannelInfo {
    val metadata = root.optJSONObject("metadata")?.optJSONObject("channelMetadataRenderer")
    val name = metadata?.optString("title")?.takeIf { it.isNotBlank() }
        ?: findHeaderChannelName(root)
        ?: "Channel"
    val avatar = metadata?.let { bestThumbnail(it.optJSONObject("avatar")) }?.takeIf { it.isNotBlank() }
    val description = metadata?.optString("description").orEmpty()
    return ChannelInfo(
        id = metadata?.optString("externalId")?.takeIf { it.isNotBlank() } ?: fallbackId,
        name = name,
        subtitle = findHeaderSubtitle(root),
        avatarUrl = avatar,
        description = description
    )
}

private fun findHeaderChannelName(root: JSONObject): String? {
    val header = root.optJSONObject("header") ?: return null
    for (viewModel in findObjects(header, "pageHeaderViewModel")) {
        val content = viewModel.optJSONObject("title")
            ?.optJSONObject("dynamicTextViewModel")
            ?.optJSONObject("text")
            ?.optString("content")
        if (!content.isNullOrBlank()) return content
    }
    return null
}

private fun findHeaderSubtitle(root: JSONObject): String? {
    val header = root.optJSONObject("header") ?: return null
    for (viewModel in findObjects(header, "contentMetadataViewModel")) {
        val rows = viewModel.optJSONArray("metadataRows") ?: continue
        val parts = mutableListOf<String>()
        for (i in 0 until rows.length()) {
            val rowParts = rows.optJSONObject(i)?.optJSONArray("parts") ?: continue
            for (j in 0 until rowParts.length()) {
                rowParts.optJSONObject(j)?.optJSONObject("text")?.optString("content")
                    ?.takeIf { it.isNotBlank() }?.let(parts::add)
            }
        }
        if (parts.isNotEmpty()) return parts.joinToString(" - ")
    }
    return null
}

private fun findCommentsSectionToken(root: JSONObject): String? {
    val panels = root.optJSONArray("engagementPanels") ?: return null
    for (i in 0 until panels.length()) {
        val section = panels.optJSONObject(i)?.optJSONObject("engagementPanelSectionListRenderer")
            ?: continue
        val identifier = section.optString("panelIdentifier")
        if (!identifier.contains("comments")) continue
        val tokens = findObjects(section, "continuationItemRenderer").mapNotNull { renderer ->
            renderer.optJSONObject("continuationEndpoint")
                ?.optJSONObject("continuationCommand")
                ?.optString("token")
                ?.takeIf { it.isNotBlank() }
        }
        if (tokens.isNotEmpty()) return tokens.first()
    }
    return null
}

private fun lastContinuationToken(root: JSONObject): String? {
    val renderers = findObjects(root, "continuationItemRenderer")
    for (renderer in renderers.asReversed()) {
        val token = renderer.optJSONObject("continuationEndpoint")
            ?.optJSONObject("continuationCommand")
            ?.optString("token")
            ?.takeIf { it.isNotBlank() }
        if (token != null) return token
    }
    return null
}

private fun parseCommentThreads(root: JSONObject): List<CommentItem> {
    val result = mutableListOf<CommentItem>()
    for (thread in findObjects(root, "commentThreadRenderer")) {
        val renderer = thread.optJSONObject("comment")?.optJSONObject("commentRenderer")
            ?: continue
        val repliesNode = thread.optJSONObject("replies")?.optJSONObject("commentRepliesRenderer")
        val token = repliesNode?.let { findContinuationCommandToken(it) }
        parseCommentRenderer(renderer, repliesToken = token)?.let(result::add)
    }
    return result
}

private fun parseCommentRenderer(renderer: JSONObject, repliesToken: String?): CommentItem? {
    val id = renderer.optString("commentId").takeIf { it.isNotBlank() } ?: return null
    val text = textValue(renderer.opt("contentText"))
    if (text.isBlank()) return null
    val likes = textValue(renderer.opt("voteCount")).takeIf { it.isNotBlank() }
    return CommentItem(
        id = id,
        author = textValue(renderer.opt("authorText")),
        authorAvatarUrl = bestThumbnail(renderer.optJSONObject("authorThumbnail"))
            .takeIf { it.isNotBlank() },
        text = text,
        publishedText = textValue(renderer.opt("publishedTimeText")).takeIf { it.isNotBlank() },
        likeCountText = likes,
        replyCount = renderer.optInt("replyCount"),
        isPinned = renderer.has("pinnedCommentBadge"),
        repliesToken = repliesToken
    )
}

private fun findContinuationCommandToken(node: Any?): String? {
    var result: String? = null
    fun walk(value: Any?) {
        if (result != null) return
        when (value) {
            is JSONObject -> {
                val token = value.optJSONObject("continuationCommand")?.optString("token")
                if (!token.isNullOrBlank()) {
                    result = token
                    return
                }
                val keys = value.keys()
                while (keys.hasNext() && result == null) walk(value.opt(keys.next()))
            }
            is JSONArray -> {
                for (i in 0 until value.length()) {
                    walk(value.opt(i))
                    if (result != null) return
                }
            }
        }
    }
    walk(node)
    return result
}

private fun parseStreams(streaming: JSONObject?): List<StreamOption> {
    if (streaming == null) return emptyList()
    val muxed = mutableListOf<StreamOption>()
    val adaptive = mutableListOf<StreamOption>()
    val audioFormats = mutableListOf<FormatInfo>()

    streaming.optJSONArray("formats")?.let { formats ->
        for (i in 0 until formats.length()) {
            val info = formats.optJSONObject(i)?.let(::formatInfo) ?: continue
            if (info.url == null) continue
            if (info.isVideo && info.hasAudio) muxed += info.toMuxedOption()
        }
    }
    streaming.optJSONArray("adaptiveFormats")?.let { formats ->
        for (i in 0 until formats.length()) {
            val info = formats.optJSONObject(i)?.let(::formatInfo) ?: continue
            if (info.isAudio) audioFormats += info
        }
        for (i in 0 until formats.length()) {
            val info = formats.optJSONObject(i)?.let(::formatInfo) ?: continue
            val url = info.url ?: continue
            if (!info.isVideo || info.hasAudio) continue
            val matchingAudio = audioFormats
                .filter { it.url != null && it.container == info.container }
                .maxByOrNull { it.bitrate }
                ?: audioFormats.filter { it.url != null }.maxByOrNull { it.bitrate }
            adaptive += StreamOption(
                id = "adaptive-${info.itag}",
                label = qualityLabel(info),
                detail = "Adaptive video + separate audio",
                videoUrl = url,
                audioUrl = matchingAudio?.url,
                height = info.height,
                bitrate = info.bitrate,
                isMuxed = false
            )
        }
    }

    streaming.optString("hlsManifestUrl").takeIf { it.isNotBlank() }?.let { hls ->
        adaptive += StreamOption(
            id = "hls-auto",
            label = "Auto HLS",
            detail = "Adaptive stream supplied by YouTube",
            videoUrl = hls,
            height = 0,
            bitrate = 0
        )
    }

    val uniqueAdaptive = adaptive
        .groupBy { it.label }
        .values
        .mapNotNull { options -> options.maxByOrNull { it.bitrate } }
        .sortedWith(compareByDescending<StreamOption> { it.height }.thenByDescending { it.bitrate })
    val uniqueMuxed = muxed
        .groupBy { it.label }
        .values
        .mapNotNull { options -> options.maxByOrNull { it.bitrate } }
        .sortedWith(compareByDescending<StreamOption> { it.height }.thenByDescending { it.bitrate })
    return (uniqueAdaptive + uniqueMuxed).take(14)
}

private fun defaultStreamIndex(streams: List<StreamOption>): Int {
    if (streams.isEmpty()) return -1
    val cap = SettingsStore.qualityCap
    if (cap > 0) {
        val capped = streams.withIndex()
            .filter { (_, stream) ->
                stream.height in 1..cap && (stream.audioUrl != null || stream.isMuxed)
            }
            .maxByOrNull { (_, stream) -> stream.height }
        if (capped != null) return capped.index
    }
    val preferred = streams.indexOfFirst { it.height in 361..720 && it.audioUrl != null }
    if (preferred >= 0) return preferred
    val withAudio = streams.indexOfFirst { it.audioUrl != null || it.isMuxed }
    if (withAudio >= 0) return withAudio
    return 0
}

private data class FormatInfo(
    val itag: Int,
    val url: String?,
    val mimeType: String,
    val codecs: String,
    val width: Int,
    val height: Int,
    val bitrate: Int,
    val fps: Int
) {
    val container: String get() = mimeType.substringBefore(';').substringAfter('/').lowercase()
    val isVideo: Boolean get() = mimeType.startsWith("video/")
    val isAudio: Boolean get() = mimeType.startsWith("audio/")
    val hasAudio: Boolean
        get() = codecs.contains("mp4a") || codecs.contains("opus") || codecs.contains("vorbis")
    val hasVideoCodec: Boolean
        get() = codecs.contains("avc1") || codecs.contains("vp9") || codecs.contains("vp09") ||
            codecs.contains("av01") || codecs.contains("hev1") || codecs.contains("hvc1")

    fun toMuxedOption(): StreamOption = StreamOption(
        id = "muxed-$itag",
        label = qualityLabel(this),
        detail = "Video and audio in one stream",
        videoUrl = requireNotNull(url),
        height = height,
        bitrate = bitrate,
        isMuxed = true
    )
}

private fun formatInfo(format: JSONObject): FormatInfo {
    val mime = format.optString("mimeType")
    val codecs = Regex("codecs=\"([^\"]+)\"").find(mime)?.groupValues?.getOrNull(1).orEmpty()
    return FormatInfo(
        itag = format.optInt("itag"),
        url = format.optString("url").takeIf { it.isNotBlank() },
        mimeType = mime,
        codecs = codecs,
        width = format.optInt("width"),
        height = format.optInt("height"),
        bitrate = format.optInt("bitrate"),
        fps = format.optInt("fps")
    )
}

private fun qualityLabel(format: FormatInfo): String {
    val height = format.height
    if (height <= 0) return "Auto"
    val fpsSuffix = if (format.fps > 30) format.fps.toString() else ""
    return "${height}p$fpsSuffix"
}

private fun findContinuation(root: JSONObject): String? {
    for (renderer in findObjects(root, "continuationItemRenderer")) {
        val token = renderer.optJSONObject("continuationEndpoint")
            ?.optJSONObject("continuationCommand")
            ?.optString("token")
            ?.takeIf { it.isNotBlank() }
        if (token != null) return token
    }
    return null
}

private fun findObjects(root: Any?, key: String): List<JSONObject> {
    val found = mutableListOf<JSONObject>()
    fun walk(node: Any?) {
        when (node) {
            is JSONObject -> {
                val direct = node.optJSONObject(key)
                if (direct != null) found += direct
                val keys = node.keys()
                while (keys.hasNext()) walk(node.opt(keys.next()))
            }
            is JSONArray -> {
                for (i in 0 until node.length()) walk(node.opt(i))
            }
        }
    }
    walk(root)
    return found
}

private fun textValue(node: Any?): String {
    if (node is String) return node
    if (node !is JSONObject) return ""
    val simple = node.optString("simpleText")
    if (simple.isNotBlank()) return simple
    val runs = node.optJSONArray("runs")
    if (runs != null) {
        val text = StringBuilder()
        for (i in 0 until runs.length()) {
            text.append(runs.optJSONObject(i)?.optString("text").orEmpty())
        }
        if (text.isNotBlank()) return text.toString()
    }
    val content = node.optString("content")
    if (content.isNotBlank()) return content
    return node.optJSONObject("accessibility")?.optJSONObject("accessibilityData")
        ?.optString("label").orEmpty()
}

private fun findBrowseId(node: Any?): String? {
    var result: String? = null
    fun walk(value: Any?) {
        if (result != null) return
        when (value) {
            is JSONObject -> {
                val browseId = value.optJSONObject("browseEndpoint")?.optString("browseId")
                if (browseId?.startsWith("UC") == true) result = browseId
                val keys = value.keys()
                while (keys.hasNext() && result == null) walk(value.opt(keys.next()))
            }
            is JSONArray -> {
                for (i in 0 until value.length()) {
                    walk(value.opt(i))
                    if (result != null) return
                }
            }
        }
    }
    walk(node)
    return result
}

private fun findChannelAvatar(renderer: JSONObject): String? {
    val supported = renderer.optJSONObject("channelThumbnailSupportedRenderers")
        ?.optJSONObject("channelThumbnailWithLinkRenderer")
        ?.optJSONObject("thumbnail")
    val direct = renderer.optJSONObject("channelThumbnail")
    return listOfNotNull(supported, direct)
        .map { bestThumbnail(it) }
        .firstOrNull { it.isNotBlank() }
}

private fun hasLiveBadge(renderer: JSONObject): Boolean {
    val badges = renderer.optJSONArray("badges") ?: return false
    for (i in 0 until badges.length()) {
        val label = badges.optJSONObject(i)
            ?.optJSONObject("metadataBadgeRenderer")
            ?.optString("label")
        if (label.equals("LIVE", ignoreCase = true)) return true
    }
    return false
}

private fun bestThumbnail(thumbnail: JSONObject?): String {
    if (thumbnail == null) return ""
    val sources = thumbnail.optJSONArray("thumbnails") ?: thumbnail.optJSONArray("sources")
    if (sources == null || sources.length() == 0) return ""
    var bestUrl = ""
    var bestWidth = -1
    for (i in 0 until sources.length()) {
        val item = sources.optJSONObject(i) ?: continue
        val width = item.optInt("width")
        if (width >= bestWidth) {
            bestWidth = width
            bestUrl = item.optString("url")
        }
    }
    return when {
        bestUrl.startsWith("//") -> "https:$bestUrl"
        else -> bestUrl
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private fun formatCount(value: Long): String = when {
    value >= 1_000_000_000 -> trimCount(value / 1_000_000_000.0) + "B"
    value >= 1_000_000 -> trimCount(value / 1_000_000.0) + "M"
    value >= 1_000 -> trimCount(value / 1_000.0) + "K"
    else -> value.toString()
}

private fun trimCount(value: Double): String =
    if (value >= 100) value.toInt().toString() else String.format("%.1f", value).removeSuffix(".0")
